import { StrictMode } from "react";
import { act, cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, beforeEach, expect, test, vi } from "vitest";
import { ApiError } from "@/lib/api-client";
import { AuthProvider } from "@/features/identity/auth-provider";
import { authApi, type AccountView } from "@/features/identity/auth-api";
import { assetApi } from "@/features/asset/asset-api";
import { accountA, accountB, asset, deferred, portrait, postcard } from "@/test/fixtures";
import { accepted, TASK_A, TASK_B, taskSnapshot } from "@/test/task-fixtures";
import type { TaskAccepted, TaskView } from "./task-contract";
import { taskApi } from "./task-api";
import { ProductionWorkspace, TaskHistory } from "./production-workspace";

let current: AccountView | null;
let createUrl: ReturnType<typeof vi.fn<(blob: Blob | MediaSource) => string>>;
let revokeUrl: ReturnType<typeof vi.fn<(url: string) => void>>;
const file = (name = "photo.jpg") => new File(["jpeg"], name, { type: "image/jpeg" });
const card = (id = TASK_A) => within(screen.getByTestId(`task-${id}`));
async function ready(index = 1) {
  fireEvent.change(await screen.findByLabelText(`图片 ${index}`), { target: { files: [file(`photo-${index}.jpg`)] } });
  await screen.findByText(/素材已准备完成/);
}
function workspace(template = portrait) { return <AuthProvider><ProductionWorkspace template={template} /></AuthProvider>; }
function changeAccount(account: AccountView | null) { current = account; act(() => window.dispatchEvent(new Event("focus"))); }

beforeEach(() => {
  current = accountA;
  vi.spyOn(authApi, "session").mockImplementation(async () => current);
  vi.spyOn(authApi, "csrf").mockResolvedValue({ headerName: "X-CSRF-TOKEN", token: "test-token" });
  vi.spyOn(assetApi, "upload").mockImplementation(async (_, photo) => asset(photo));
  vi.spyOn(taskApi, "history").mockResolvedValue([]);
  vi.spyOn(taskApi, "detail").mockImplementation(async (id, owner) => taskSnapshot(id, "SUCCEEDED", "FINALIZE", 4, owner));
  vi.spyOn(taskApi, "submit").mockResolvedValue(accepted());
  vi.spyOn(taskApi, "result").mockResolvedValue(new Blob(["jpeg"], { type: "image/jpeg" }));
  let url = 0;
  createUrl = vi.fn(() => `blob:http://localhost/ip14-${++url}`);
  revokeUrl = vi.fn();
  vi.stubGlobal("URL", class extends URL {
    static createObjectURL(value: Blob | MediaSource) { return createUrl(value); }
    static revokeObjectURL(value: string) { revokeUrl(value); }
  });
});
afterEach(() => { cleanup(); vi.restoreAllMocks(); vi.unstubAllGlobals(); });

test("locks the fixed form with a synchronous gate and releases at acceptance, while two tasks stay independent", async () => {
  const post = deferred<TaskAccepted>(), first = deferred<TaskView>();
  vi.mocked(taskApi.submit).mockReturnValueOnce(post.promise).mockResolvedValueOnce(accepted(TASK_B));
  vi.mocked(taskApi.detail).mockReturnValueOnce(first.promise).mockResolvedValueOnce(taskSnapshot(TASK_B, "FAILED", "GENERATE", 3));
  render(workspace());
  expect(await screen.findByRole("button", { name: "开始制作" })).toBeDisabled();
  await ready();
  const form = screen.getByRole("form", { name: "制作表单" });
  act(() => { fireEvent.submit(form); fireEvent.submit(form); });
  expect(taskApi.submit).toHaveBeenCalledTimes(1);
  expect(screen.getByLabelText("图片 1")).toBeDisabled();
  expect(screen.getByRole("button", { name: "移除图片 1" })).toBeDisabled();
  fireEvent.change(screen.getByLabelText("图片 1"), { target: { files: [file("late.jpg")] } });
  expect(assetApi.upload).toHaveBeenCalledTimes(1);
  expect(taskApi.submit).toHaveBeenCalledWith({ templateId: portrait.templateId, assetIds: [asset(file()).assetId] }, accountA.ownerId, { headerName: "X-CSRF-TOKEN", token: "test-token" }, expect.any(AbortSignal));
  await act(async () => post.resolve(accepted()));
  expect(card().getByText("已受理，等待读取实际状态")).toBeVisible();
  expect(screen.getByRole("button", { name: "开始制作" })).toBeEnabled();
  fireEvent.click(screen.getByRole("button", { name: "开始制作" }));
  await screen.findByTestId(`task-${TASK_B}`);
  expect(await card(TASK_B).findByText("执行失败")).toBeVisible();
  await act(async () => first.resolve(taskSnapshot(TASK_A, "SUCCEEDED", "FINALIZE", 4)));
  expect(card().getByText("制作成功")).toBeVisible();
  expect(card(TASK_B).getByText(/失败原因：指定阶段失败/)).toBeVisible();
  expect(card().queryByText(/当前阶段|失败环节|正在准备预览/)).not.toBeInTheDocument();
  expect(card(TASK_B).queryByText(/当前阶段|失败环节/)).not.toBeInTheDocument();
  expect(await card().findByRole("img")).toHaveAttribute("src", expect.stringContaining("blob:"));
});

test("submits only after all ordered positions are uploaded, keeping duplicates", async () => {
  vi.mocked(taskApi.detail).mockImplementation(async (id, owner) => ({ ...taskSnapshot(id, "SUCCEEDED", "FINALIZE", 4, owner), templateId: postcard.templateId }));
  render(workspace(postcard));
  fireEvent.change(await screen.findByLabelText("图片 1"), { target: { files: [file()] } });
  await screen.findByText("上传完成，可用于制作");
  fireEvent.submit(screen.getByRole("form", { name: "制作表单" }));
  expect(taskApi.submit).not.toHaveBeenCalled();
  await ready(2);
  fireEvent.click(screen.getByRole("button", { name: "开始制作" }));
  await screen.findByTestId(`task-${TASK_A}`);
  expect(screen.queryByText(/已受理任务.*可继续/)).not.toBeInTheDocument();
  expect(taskApi.submit).toHaveBeenCalledWith({ templateId: postcard.templateId, assetIds: [asset(file()).assetId, asset(file()).assetId] }, accountA.ownerId, expect.anything(), expect.any(AbortSignal));
});

test.each(["network", "timeout", "503 with ID", "invalid acceptance"])("unknown submission %s retains input and offers reconciliation without replay", async (outcome) => {
  const cause = outcome === "network" ? new Error("network") : outcome === "timeout" ? new ApiError("timeout", 0, "REQUEST_TIMEOUT")
    : outcome === "503 with ID" ? new ApiError("unknown", 503, "SUBMISSION_OUTCOME_UNKNOWN", "test-request", [], TASK_A) : new ApiError("invalid", 200, "INVALID_RESPONSE");
  vi.mocked(taskApi.submit).mockRejectedValue(cause);
  render(workspace()); await ready();
  fireEvent.click(screen.getByRole("button", { name: "开始制作" }));
  expect(await screen.findByText(/提交结果暂未确认/)).toBeVisible();
  expect(screen.getByRole("button", { name: "仍要再次提交" })).toBeEnabled();
  expect(screen.getByText("素材已准备完成（1/1）")).toBeVisible();
  expect(screen.queryByText("执行失败")).not.toBeInTheDocument();
  expect(screen.getByRole("link", { name: "查看我的记录" })).toHaveAttribute("href", "/history");
  expect(taskApi.submit).toHaveBeenCalledTimes(1);
  if (outcome === "503 with ID") {
    vi.mocked(taskApi.detail).mockRejectedValueOnce(new ApiError("未找到", 404, "RESOURCE_NOT_FOUND"));
    fireEvent.click(screen.getByRole("button", { name: "查询可能的任务" }));
    expect(await card().findByText(/状态查询暂不可用/)).toBeVisible();
    expect(screen.getByText(/提交结果暂未确认/)).toBeVisible();
  }
});

test("known validation rejection creates no failed task, and explicit correction can be submitted", async () => {
  vi.mocked(taskApi.submit).mockRejectedValueOnce(new ApiError("素材数量不符", 422, "TEMPLATE_REQUIREMENTS_MISMATCH"));
  render(workspace()); await ready();
  fireEvent.click(screen.getByRole("button", { name: "开始制作" }));
  expect(await screen.findByText("提交未受理：素材数量不符")).toBeVisible();
  expect(screen.queryByRole("article")).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "开始制作" }));
  await screen.findByTestId(`task-${TASK_A}`);
  expect(await card().findByText("制作成功")).toBeVisible();
});

test.each([401, 403])("current submission %i invalidates private inputs without replay", async (status) => {
  const post = deferred<TaskAccepted>(); vi.mocked(taskApi.submit).mockReturnValueOnce(post.promise);
  render(workspace()); await ready(); fireEvent.click(screen.getByRole("button", { name: "开始制作" }));
  current = status === 401 ? null : accountB;
  await act(async () => post.reject(new ApiError("会话变化", status, status === 401 ? "AUTHENTICATION_REQUIRED" : "CSRF_INVALID")));
  if (status === 401) expect(await screen.findByRole("link", { name: "登录后上传图片" })).toBeVisible();
  else expect(await screen.findByText("已就绪 0/1 张，请完成全部图片上传")).toBeVisible();
  expect(screen.queryByText("photo-1.jpg")).not.toBeInTheDocument();
  expect(taskApi.submit).toHaveBeenCalledTimes(1);
});

test.each(["success", "403", "network"])("A → B → A discards stale submission %s and leaves the new form unlocked", async (outcome) => {
  const post = deferred<TaskAccepted>(); vi.mocked(taskApi.submit).mockReturnValueOnce(post.promise);
  render(workspace()); await ready(); fireEvent.click(screen.getByRole("button", { name: "开始制作" }));
  const signal = vi.mocked(taskApi.submit).mock.calls[0][3];
  changeAccount(accountB); await screen.findByText("已就绪 0/1 张，请完成全部图片上传");
  changeAccount(accountA); await waitFor(() => expect(authApi.csrf).toHaveBeenCalledTimes(3)); await ready();
  await act(async () => {
    if (outcome === "success") post.resolve(accepted());
    else post.reject(outcome === "403" ? new ApiError("stale", 403, "CSRF_INVALID") : new Error("stale"));
  });
  expect(signal.aborted).toBe(true);
  expect(screen.queryByRole("article")).not.toBeInTheDocument();
  expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  expect(screen.getByRole("button", { name: "开始制作" })).toBeEnabled();
  expect(authApi.csrf).toHaveBeenCalledTimes(3);
});

test.each(["success", "error"])("unmount aborts a submit and ignores its late %s", async (outcome) => {
  const post = deferred<TaskAccepted>(); vi.mocked(taskApi.submit).mockReturnValueOnce(post.promise);
  const page = render(workspace()); await ready(); fireEvent.click(screen.getByRole("button", { name: "开始制作" }));
  const signal = vi.mocked(taskApi.submit).mock.calls[0][3]; page.unmount();
  await act(async () => { if (outcome === "success") post.resolve(accepted()); else post.reject(new Error("late")); });
  expect(signal.aborted).toBe(true); expect(taskApi.detail).not.toHaveBeenCalled();
  expect(revokeUrl).toHaveBeenCalledWith("blob:http://localhost/ip14-1");
});

test("a remounted history waits for session verification and restores server status/time/result for every record", async () => {
  const snapshots = [taskSnapshot(TASK_A, "SUCCEEDED", "FINALIZE", 4), taskSnapshot(TASK_B, "FAILED", "GENERATE", 3)];
  vi.mocked(taskApi.history).mockResolvedValue(snapshots);
  const session = deferred<AccountView | null>(); vi.mocked(authApi.session).mockReturnValueOnce(session.promise);
  const page = render(<AuthProvider><TaskHistory /></AuthProvider>);
  expect(taskApi.history).not.toHaveBeenCalled();
  await act(async () => session.resolve(accountA));
  expect(await card().findByText("制作成功")).toBeVisible();
  expect(card(TASK_B).getByText("执行失败")).toBeVisible();
  expect(screen.getAllByText(/创建时间/)).toHaveLength(2);
  await card().findByRole("img"); page.unmount();
  render(<AuthProvider><TaskHistory /></AuthProvider>);
  await screen.findByTestId(`task-${TASK_A}`);
  expect(card().getByText("制作成功")).toBeVisible();
  expect(taskApi.history).toHaveBeenCalledTimes(2); expect(taskApi.detail).not.toHaveBeenCalled();
});

test("initial history error is not an empty history and manual refresh recovers", async () => {
  vi.mocked(taskApi.history).mockRejectedValueOnce(new ApiError("暂不可用", 503, "SERVICE_UNAVAILABLE"));
  render(<AuthProvider><TaskHistory /></AuthProvider>);
  expect(await screen.findByRole("alert")).toHaveTextContent("记录读取暂不可用");
  expect(screen.queryByText(/暂无制作记录/)).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "刷新记录" }));
  expect(await screen.findByText(/暂无制作记录/)).toBeVisible();
});

test("image load and decode failures preserve SUCCEEDED and retry only the media request", async () => {
  vi.mocked(taskApi.history).mockResolvedValue([taskSnapshot(TASK_A, "SUCCEEDED", "FINALIZE", 4)]);
  vi.mocked(taskApi.result).mockRejectedValueOnce(new ApiError("unavailable", 503, "RESOURCE_UNAVAILABLE"));
  render(<AuthProvider><TaskHistory /></AuthProvider>);
  expect(await screen.findByText("结果预览暂不可用，任务仍已成功。")).toBeVisible();
  expect(card().getByText("制作成功")).toBeVisible();
  fireEvent.click(screen.getByRole("button", { name: "重新加载预览" }));
  const image = await card().findByRole("img"); fireEvent.error(image);
  fireEvent.click(screen.getByRole("button", { name: "重新加载预览" }));
  await card().findByRole("img");
  expect(revokeUrl).toHaveBeenCalledWith("blob:http://localhost/ip14-1");
  expect(taskApi.result).toHaveBeenCalledTimes(3); expect(taskApi.submit).not.toHaveBeenCalled();
});

test.each(["success", "403"])("identity change aborts media and rejects its late %s before creating a Blob URL", async (outcome) => {
  const media = deferred<Blob>();
  vi.mocked(taskApi.history).mockResolvedValueOnce([taskSnapshot(TASK_A, "SUCCEEDED", "FINALIZE", 4)]);
  vi.mocked(taskApi.result).mockReturnValueOnce(media.promise);
  render(<AuthProvider><TaskHistory /></AuthProvider>);
  await screen.findByText("正在加载结果图片…");
  const signal = vi.mocked(taskApi.result).mock.calls[0][1];
  changeAccount(accountB); await screen.findByText(/暂无制作记录/);
  changeAccount(accountA); await waitFor(() => expect(authApi.csrf).toHaveBeenCalledTimes(3));
  await act(async () => { if (outcome === "success") media.resolve(new Blob(["old"])); else media.reject(new ApiError("stale", 403, "CSRF_INVALID")); });
  expect(signal.aborted).toBe(true); expect(createUrl).not.toHaveBeenCalled();
  expect(screen.queryByRole("alert")).not.toBeInTheDocument(); expect(screen.queryByRole("img")).not.toBeInTheDocument();
});

test("identity change and unmount revoke loaded private result URLs", async () => {
  vi.mocked(taskApi.history).mockImplementation(async (owner) => [taskSnapshot(TASK_A, "SUCCEEDED", "FINALIZE", 4, owner)]);
  const page = render(<AuthProvider><TaskHistory /></AuthProvider>);
  await screen.findByRole("img"); changeAccount(accountB);
  await waitFor(() => expect(revokeUrl).toHaveBeenCalledWith("blob:http://localhost/ip14-1"));
  await screen.findByRole("img"); page.unmount();
  expect(revokeUrl).toHaveBeenCalledWith("blob:http://localhost/ip14-2");
});

test("Strict Mode mount performs reads without submitting or duplicating visible records", async () => {
  vi.mocked(taskApi.history).mockResolvedValue([taskSnapshot(TASK_A, "FAILED", null, 1)]);
  render(<StrictMode>{workspace()}</StrictMode>);
  expect(await screen.findByText("执行失败")).toBeVisible();
  expect(screen.getAllByRole("article")).toHaveLength(1);
  expect(taskApi.submit).not.toHaveBeenCalled(); expect(assetApi.upload).not.toHaveBeenCalled();
});

test("refresh restores unfinished tracking; query failure pauses only that read, and retry recovers the actual terminal result", async () => {
  vi.useFakeTimers();
  try {
    vi.mocked(taskApi.history).mockResolvedValue([taskSnapshot(), taskSnapshot(TASK_B, "FAILED", null, 1)]);
    vi.mocked(taskApi.detail).mockRejectedValueOnce(new ApiError("离线", 503, "SERVICE_UNAVAILABLE"));
    const page = render(<AuthProvider><TaskHistory /></AuthProvider>);
    await act(async () => {});
    expect(card().getByText("处理中")).toBeVisible();
    await act(async () => vi.advanceTimersByTimeAsync(2000));
    expect(card().getByText("处理中")).toBeVisible();
    expect(card().getByRole("alert")).toHaveTextContent("自动查询已暂停");
    expect(card(TASK_B).getByText("执行失败")).toBeVisible();
    await act(async () => vi.advanceTimersByTimeAsync(6000));
    expect(taskApi.detail).toHaveBeenCalledTimes(1);
    await act(async () => fireEvent.click(card().getByRole("button", { name: "重新查询状态" })));
    expect(card().getByText("制作成功")).toBeVisible();
    await act(async () => vi.advanceTimersByTimeAsync(6000));
    expect(taskApi.detail).toHaveBeenCalledTimes(2);
    page.unmount(); expect(vi.getTimerCount()).toBe(0);
  } finally { vi.useRealTimers(); }
});

test("remount during processing fetches server records again and resumes only unfinished tasks", async () => {
  vi.useFakeTimers();
  try {
    vi.mocked(taskApi.history).mockResolvedValue([taskSnapshot()]);
    const first = render(<AuthProvider><TaskHistory /></AuthProvider>); await act(async () => {});
    first.unmount(); expect(vi.getTimerCount()).toBe(0);
    const second = render(<AuthProvider><TaskHistory /></AuthProvider>); await act(async () => {});
    expect(card().getByText("处理中")).toBeVisible();
    await act(async () => vi.advanceTimersByTimeAsync(2000));
    expect(card().getByText("制作成功")).toBeVisible();
    expect(taskApi.history).toHaveBeenCalledTimes(2); expect(taskApi.detail).toHaveBeenCalledTimes(1);
    await act(async () => vi.advanceTimersByTimeAsync(6000));
    expect(taskApi.detail).toHaveBeenCalledTimes(1); second.unmount(); expect(vi.getTimerCount()).toBe(0);
  } finally { vi.useRealTimers(); }
});

test("a real client budget expiry releases the submission gate without automatically POSTing again", async () => {
  vi.useFakeTimers();
  try {
    vi.mocked(taskApi.submit).mockRestore();
    const fetcher = vi.fn(() => new Promise<Response>(() => {})); vi.stubGlobal("fetch", fetcher);
    const page = render(workspace()); await act(async () => {});
    await act(async () => fireEvent.change(screen.getByLabelText("图片 1"), { target: { files: [file()] } }));
    fireEvent.click(screen.getByRole("button", { name: "开始制作" }));
    expect(screen.getByRole("button", { name: "正在提交…" })).toBeDisabled();
    await act(async () => vi.advanceTimersByTimeAsync(30_000));
    expect(screen.getByText(/提交结果暂未确认/)).toBeVisible();
    expect(screen.getByRole("button", { name: "仍要再次提交" })).toBeEnabled();
    expect(fetcher).toHaveBeenCalledTimes(1);
    page.unmount(); expect(vi.getTimerCount()).toBe(0);
  } finally { vi.useRealTimers(); }
});

test.each(["success", "error"])("leaving the page aborts pending media and discards its late %s", async (outcome) => {
  const media = deferred<Blob>();
  vi.mocked(taskApi.history).mockResolvedValue([taskSnapshot(TASK_A, "SUCCEEDED", "FINALIZE", 4)]);
  vi.mocked(taskApi.result).mockReturnValueOnce(media.promise);
  const page = render(<AuthProvider><TaskHistory /></AuthProvider>);
  await screen.findByText("正在加载结果图片…");
  const signal = vi.mocked(taskApi.result).mock.calls[0][1]; page.unmount();
  await act(async () => { if (outcome === "success") media.resolve(new Blob(["old"])); else media.reject(new Error("late")); });
  expect(signal.aborted).toBe(true); expect(createUrl).not.toHaveBeenCalled();
});

test.each([portrait, postcard])("$templateId workspace shows only its own history and count", async (template) => {
  const portraitTask = taskSnapshot(TASK_A, "SUCCEEDED", "FINALIZE", 4);
  const postcardTask = { ...taskSnapshot(TASK_B, "SUCCEEDED", "FINALIZE", 4), templateId: postcard.templateId };
  vi.mocked(taskApi.history).mockResolvedValue([portraitTask, postcardTask]);
  render(workspace(template));
  const ownId = template.templateId === portrait.templateId ? TASK_A : TASK_B;
  const otherId = ownId === TASK_A ? TASK_B : TASK_A;
  await screen.findByTestId(`task-${ownId}`);
  expect(screen.queryByTestId(`task-${otherId}`)).not.toBeInTheDocument();
  expect(screen.getByRole("heading", { name: "本模板制作记录1" })).toBeVisible();
  expect(taskApi.result).not.toHaveBeenCalledWith(otherId, expect.anything());
  fireEvent.click(screen.getByRole("button", { name: "刷新记录" }));
  await waitFor(() => expect(taskApi.history).toHaveBeenCalledTimes(2));
  expect(screen.getAllByRole("article")).toHaveLength(1);
});

test("a workspace with only other-template tasks shows its own empty state", async () => {
  vi.mocked(taskApi.history).mockResolvedValue([{ ...taskSnapshot(TASK_B, "FAILED", "GENERATE", 3), templateId: postcard.templateId }]);
  render(workspace());
  expect(await screen.findByText("暂无本模板制作记录，上传图片开始制作。")).toBeVisible();
  expect(screen.queryByRole("article")).not.toBeInTheDocument();
  expect(screen.getByRole("heading", { name: "本模板制作记录" })).toBeVisible();
});

test("my records keeps every template's tasks after remount", async () => {
  vi.mocked(taskApi.history).mockResolvedValue([
    taskSnapshot(TASK_A, "SUCCEEDED", "FINALIZE", 4),
    { ...taskSnapshot(TASK_B, "SUCCEEDED", "FINALIZE", 4), templateId: postcard.templateId },
  ]);
  const page = render(<AuthProvider><TaskHistory /></AuthProvider>);
  await screen.findByTestId(`task-${TASK_B}`);
  expect(screen.getAllByRole("article")).toHaveLength(2);
  expect(screen.getByRole("heading", { name: "我的制作记录2" })).toBeVisible();
  page.unmount();
  render(<AuthProvider><TaskHistory /></AuthProvider>);
  await screen.findByTestId(`task-${TASK_B}`);
  expect(screen.getAllByRole("article")).toHaveLength(2);
});

test.each(["workspace", "history"])("%s uses an accessible icon refresh and omits IDs and finished-stage details", async (page) => {
  vi.mocked(taskApi.history).mockResolvedValue([taskSnapshot(TASK_A, "SUCCEEDED", "FINALIZE", 4)]);
  render(page === "workspace" ? workspace() : <AuthProvider><TaskHistory /></AuthProvider>);
  await screen.findByTestId(`task-${TASK_A}`);
  const refresh = screen.getByRole("button", { name: "刷新记录" });
  expect(refresh).not.toHaveTextContent("刷新记录");
  expect(refresh).toHaveTextContent("↻");
  expect(screen.getByRole("article", { name: "制作记录 1" })).not.toHaveTextContent(TASK_A);
  expect(screen.queryByRole("article", { name: new RegExp(TASK_A) })).not.toBeInTheDocument();
  expect(screen.queryByText(/当前阶段|正在准备预览/)).not.toBeInTheDocument();
  fireEvent.click(refresh);
  await waitFor(() => expect(taskApi.history).toHaveBeenCalledTimes(2));
});

test.each([
  ["CHECK_ASSETS", 1],
  ["GENERATE", 2],
  ["FINALIZE", 3],
] as const)("running %s shows overall status without step details", async (stage, revision) => {
  vi.mocked(taskApi.history).mockResolvedValue([taskSnapshot(TASK_A, "RUNNING", stage, revision)]);
  render(workspace());
  expect(await screen.findByText("处理中")).toBeVisible();
  expect(screen.queryByText(/当前阶段|失败环节|正在检查上传图片|正在制作图片|正在准备预览/)).not.toBeInTheDocument();
});

test("late history cannot remove a newly accepted task or undo its terminal snapshot", async () => {
  const history = deferred<TaskView[]>(); vi.mocked(taskApi.history).mockReturnValueOnce(history.promise);
  render(workspace()); await ready();
  fireEvent.click(screen.getByRole("button", { name: "开始制作" }));
  await screen.findByText("制作成功");
  await act(async () => history.resolve([]));
  expect(card().getByText("制作成功")).toBeVisible();
  expect(screen.queryByText(/暂无制作记录/)).not.toBeInTheDocument();
});
