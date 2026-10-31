import { StrictMode } from "react";
import { act, cleanup, fireEvent, render, renderHook, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, expect, test, vi } from "vitest";
import { ApiError } from "@/lib/api-client";
import { authApi, type AccountView } from "@/features/identity/auth-api";
import { AuthProvider } from "@/features/identity/auth-provider";
import { AuthSession } from "@/features/identity/auth-session";
import { accountA, accountB, asset, deferred, portrait, postcard } from "@/test/fixtures";
import { AssetInputs } from "./asset-inputs";
import { assetApi, type AssetView } from "./asset-api";
import { useUploadSlots } from "./use-upload-slots";

let current: AccountView | null;
let requests: { file: File; templateId: string; signal: AbortSignal; pending: ReturnType<typeof deferred<AssetView>> }[];
let createUrl = vi.fn<(value: Blob | MediaSource) => string>();
let revokeUrl = vi.fn<(value: string) => void>();
const photo = (name = "photo.jpg", size = 4, type = "image/jpeg") => new File([new Uint8Array(size)], name, { type });
async function choose(file: File, index = 1) { fireEvent.change(await screen.findByLabelText(`图片 ${index}`), { target: { files: [file] } }); }
async function finish(index: number, suffix: string, owner = accountA.ownerId) {
  await act(async () => requests[index].pending.resolve(asset(requests[index].file, suffix, owner)));
}

beforeEach(() => {
  current = accountA; requests = [];
  vi.spyOn(authApi, "session").mockImplementation(async () => current);
  vi.spyOn(authApi, "csrf").mockResolvedValue({ headerName: "X-CSRF-TOKEN", token: "fake-token" });
  vi.spyOn(assetApi, "upload").mockImplementation((templateId, file, _, signal) => {
    const pending = deferred<AssetView>(); requests.push({ file, templateId, signal, pending }); return pending.promise;
  });
  let nextUrl = 0;
  createUrl = vi.fn<(value: Blob | MediaSource) => string>(() => `blob:http://localhost/preview-${++nextUrl}`);
  revokeUrl = vi.fn<(value: string) => void>();
  vi.stubGlobal("URL", class extends URL {
    static createObjectURL(value: Blob | MediaSource): string { return String(createUrl(value)); }
    static revokeObjectURL(value: string) { revokeUrl(value); }
  });
});
afterEach(() => { cleanup(); vi.restoreAllMocks(); vi.unstubAllGlobals(); });

test("renders the template's exact upload positions and only reports ready when every upload succeeds", async () => {
  render(<AuthProvider><AssetInputs template={postcard} /></AuthProvider>);
  await choose(photo("first.jpg"));
  await choose(photo("second.jpg"), 2);
  expect(requests.map((request) => request.templateId)).toEqual([postcard.templateId, postcard.templateId]);
  expect(screen.getAllByText("正在上传…")).toHaveLength(2);
  await finish(1, "1");
  expect(screen.getByText("已就绪 1/2 张，请完成全部图片上传")).toBeVisible();
  await finish(0, "0");
  expect(screen.getByText("素材已准备完成（2/2）")).toBeVisible();
  expect(screen.getAllByRole("img")).toHaveLength(2);
});

test.each(["same account", "503"])("focus %s preserves ready inputs, previews, and an upload in flight", async (outcome) => {
  render(<AuthProvider><AssetInputs template={postcard} /></AuthProvider>);
  await choose(photo("first.jpg")); await finish(0, "0");
  await choose(photo("second.jpg"), 2);
  const pending = deferred<AccountView | null>();
  vi.mocked(authApi.session).mockReturnValueOnce(pending.promise);
  act(() => window.dispatchEvent(new Event("focus")));
  expect(screen.getByText("已就绪 1/2 张，请完成全部图片上传")).toBeVisible();
  expect(requests[1].signal.aborted).toBe(false);
  await act(async () => {
    if (outcome === "same account") pending.resolve(accountA);
    else pending.reject(new ApiError("unavailable", 503, "SERVICE_UNAVAILABLE"));
  });
  expect(screen.getByText("first.jpg")).toBeVisible();
  expect(screen.getByText("second.jpg")).toBeVisible();
  expect(revokeUrl).not.toHaveBeenCalled();
  expect(authApi.csrf).toHaveBeenCalledTimes(1);
  await finish(1, "1");
  expect(screen.getByText("素材已准备完成（2/2）")).toBeVisible();
  expect(assetApi.upload).toHaveBeenCalledTimes(2);
});

test.each(["success", "error"])("replacement ignores the old request's late %s and finally", async (outcome) => {
  render(<AuthProvider><AssetInputs template={portrait} /></AuthProvider>);
  await choose(photo("old.jpg")); await choose(photo("new.jpg"));
  expect(requests[0].signal.aborted).toBe(true);
  expect(revokeUrl).toHaveBeenCalledWith("blob:http://localhost/preview-1");
  await act(async () => {
    if (outcome === "success") requests[0].pending.resolve(asset(requests[0].file, "0"));
    else requests[0].pending.reject(new Error("old failure"));
  });
  expect(screen.getByText("正在上传…")).toBeVisible();
  expect(screen.getByText("new.jpg")).toBeVisible();
  expect(screen.queryByText("old.jpg")).not.toBeInTheDocument();
  expect(requests[1].signal.aborted).toBe(false);
  await finish(1, "1");
  expect(screen.getByText("素材已准备完成（1/1）")).toBeVisible();
});

test.each(["success", "error"])("remove prevents a late %s from reviving the slot", async (outcome) => {
  render(<AuthProvider><AssetInputs template={portrait} /></AuthProvider>);
  await choose(photo());
  fireEvent.click(screen.getByRole("button", { name: "移除图片 1" }));
  await act(async () => {
    if (outcome === "success") requests[0].pending.resolve(asset(requests[0].file));
    else requests[0].pending.reject(new Error("old failure"));
  });
  expect(screen.queryByRole("img")).not.toBeInTheDocument();
  expect(screen.getByText("已就绪 0/1 张，请完成全部图片上传")).toBeVisible();
  expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  expect(assetApi.upload).toHaveBeenCalledTimes(1);
});

test.each(["success", "error"])("template A → B → A discards the first template's late %s", async (outcome) => {
  const page = render(<AuthProvider><AssetInputs template={portrait} /></AuthProvider>);
  await choose(photo("old.jpg"));
  page.rerender(<AuthProvider><AssetInputs template={postcard} /></AuthProvider>);
  expect(await screen.findByLabelText("图片 2")).toBeVisible();
  page.rerender(<AuthProvider><AssetInputs template={portrait} /></AuthProvider>);
  await choose(photo("new.jpg"));
  await act(async () => {
    if (outcome === "success") requests[0].pending.resolve(asset(requests[0].file));
    else requests[0].pending.reject(new Error("old error"));
  });
  expect(screen.getByText("正在上传…")).toBeVisible();
  expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  await finish(1, "1");
  expect(screen.getByText("素材已准备完成（1/1）")).toBeVisible();
});

test.each(["success", "error"])("identity A → B → A isolates late upload %s, including stale 403", async (outcome) => {
  render(<AuthProvider><AssetInputs template={portrait} /></AuthProvider>);
  await choose(photo("old.jpg"));
  current = accountB;
  act(() => window.dispatchEvent(new Event("focus")));
  await waitFor(() => expect(requests[0].signal.aborted).toBe(true));
  await screen.findByLabelText("图片 1");
  current = accountA;
  act(() => window.dispatchEvent(new Event("focus")));
  await waitFor(() => expect(authApi.csrf).toHaveBeenCalledTimes(3));
  await choose(photo("new.jpg"));
  await act(async () => {
    if (outcome === "success") requests[0].pending.resolve(asset(requests[0].file));
    else requests[0].pending.reject(new ApiError("stale csrf", 403, "CSRF_INVALID"));
  });
  expect(screen.getByText("new.jpg")).toBeVisible();
  expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  await finish(1, "1");
  expect(screen.getByText("素材已准备完成（1/1）")).toBeVisible();
});

test("unmount aborts pending work and revokes local previews, even when cancellation is ignored", async () => {
  const page = render(<AuthProvider><AssetInputs template={portrait} /></AuthProvider>);
  await choose(photo()); page.unmount();
  expect(requests[0].signal.aborted).toBe(true);
  expect(revokeUrl).toHaveBeenCalledWith("blob:http://localhost/preview-1");
  await finish(0, "0");
  expect(createUrl).toHaveBeenCalledTimes(1);
});

test.each([
  ["empty.jpg", 0, "image/jpeg", "图片不能为空"],
  ["large.jpg", 5242881, "image/jpeg", "图片超过此模板的单文件大小上限"],
  ["file.svg", 10, "image/svg+xml", "图片类型不符合模板要求"],
])("rejects invalid file %s without sending an upload", async (name, size, type, message) => {
  render(<AuthProvider><AssetInputs template={portrait} /></AuthProvider>);
  await choose(photo(String(name), Number(size), String(type)));
  expect(screen.getByRole("alert")).toHaveTextContent(String(message));
  expect(assetApi.upload).not.toHaveBeenCalled();
  expect(createUrl).not.toHaveBeenCalled();
});

test.each([[portrait, 5242880], [postcard, 8388608]] as const)("accepts the inclusive size limit from %j", async (template, size) => {
  render(<AuthProvider><AssetInputs template={template} /></AuthProvider>);
  await choose(photo("limit.jpg", size));
  expect(assetApi.upload).toHaveBeenCalledTimes(1);
  expect(requests[0].file.size).toBe(size);
});

test("rejects more than one file for a single position and discards a previously ready binding", async () => {
  render(<AuthProvider><AssetInputs template={portrait} /></AuthProvider>);
  await choose(photo()); await finish(0, "0");
  fireEvent.change(screen.getByLabelText("图片 1"), { target: { files: [photo("a.jpg"), photo("b.jpg")] } });
  expect(screen.getByRole("alert")).toHaveTextContent("每个位置请选择一张图片");
  expect(screen.queryByText("素材已准备完成（1/1）")).not.toBeInTheDocument();
  expect(assetApi.upload).toHaveBeenCalledTimes(1);
});

test("upload failure keeps its own preview and requires an explicit manual retry", async () => {
  render(<AuthProvider><AssetInputs template={portrait} /></AuthProvider>);
  await choose(photo());
  await act(async () => requests[0].pending.reject(new ApiError("服务暂不可用", 503, "SERVICE_UNAVAILABLE")));
  expect(screen.getByRole("alert")).toHaveTextContent("服务暂不可用");
  expect(screen.getByRole("img")).toBeVisible();
  expect(assetApi.upload).toHaveBeenCalledTimes(1);
  fireEvent.click(screen.getByRole("button", { name: "重新上传图片 1" }));
  expect(assetApi.upload).toHaveBeenCalledTimes(2);
  await finish(1, "1");
  expect(screen.getByText("素材已准备完成（1/1）")).toBeVisible();
});

test.each([401, 403])("current upload %i rechecks identity, clears private inputs and never replays", async (status) => {
  render(<AuthProvider><AssetInputs template={portrait} /></AuthProvider>);
  await choose(photo());
  current = status === 401 ? null : accountB;
  await act(async () => requests[0].pending.reject(new ApiError("会话已变化", status, status === 401 ? "AUTHENTICATION_REQUIRED" : "CSRF_INVALID")));
  if (status === 401) expect(await screen.findByRole("link", { name: "登录后上传图片" })).toBeVisible();
  else expect(await screen.findByText("已就绪 0/1 张，请完成全部图片上传")).toBeVisible();
  expect(screen.queryByRole("img")).not.toBeInTheDocument();
  expect(revokeUrl).toHaveBeenCalledWith("blob:http://localhost/preview-1");
  expect(assetApi.upload).toHaveBeenCalledTimes(1);
});

test("cancelling the file picker preserves an already prepared selection", async () => {
  render(<AuthProvider><AssetInputs template={portrait} /></AuthProvider>);
  await choose(photo()); await finish(0, "0");
  fireEvent.change(screen.getByLabelText("图片 1"), { target: { files: [] } });
  expect(screen.getByText("素材已准备完成（1/1）")).toBeVisible();
  expect(assetApi.upload).toHaveBeenCalledTimes(1);
});

test("rejects another owner's successful upload response instead of marking it ready", async () => {
  render(<AuthProvider><AssetInputs template={portrait} /></AuthProvider>);
  await choose(photo()); await finish(0, "0", accountB.ownerId);
  expect(screen.getByRole("alert")).toHaveTextContent("上传响应与当前账号或模板不一致");
  expect(screen.queryByText("素材已准备完成（1/1）")).not.toBeInTheDocument();
});

test("a broken local preview is separate from the server's completed upload", async () => {
  render(<AuthProvider><AssetInputs template={portrait} /></AuthProvider>);
  await choose(photo()); await finish(0, "0");
  fireEvent.error(screen.getByRole("img"));
  expect(screen.getByText("本地预览暂不可用，上传状态见下方")).toBeVisible();
  expect(screen.getByText("素材已准备完成（1/1）")).toBeVisible();
});

test("Strict Mode never uploads on mount and selecting the same file again is a new request", async () => {
  render(<StrictMode><AuthProvider><AssetInputs template={portrait} /></AuthProvider></StrictMode>);
  await screen.findByLabelText("图片 1");
  expect(assetApi.upload).not.toHaveBeenCalled();
  const file = photo(); await choose(file); await choose(file);
  expect(assetApi.upload).toHaveBeenCalledTimes(2);
  expect(requests[0].signal.aborted).toBe(true);
});

test("prepared inputs use ordered server IDs and become invalid synchronously on replacement/removal", async () => {
  const session = new AuthSession(); session.start();
  await waitFor(() => expect(session.getSnapshot().status).toBe("authenticated"));
  const hook = renderHook(() => useUploadSlots(postcard, session));
  expect(hook.result.current.getPreparedInputs()).toBeNull();
  act(() => { hook.result.current.select(0, [photo("first.jpg")]); hook.result.current.select(1, [photo("second.jpg")]); });
  await finish(1, "1"); expect(hook.result.current.getPreparedInputs()).toBeNull();
  await finish(0, "0");
  expect(hook.result.current.getPreparedInputs()).toMatchObject({
    templateId: postcard.templateId, ownerId: accountA.ownerId,
    assetIds: [asset(requests[0].file, "0").assetId, asset(requests[1].file, "1").assetId],
  });
  act(() => {
    hook.result.current.remove(0);
    expect(hook.result.current.getPreparedInputs()).toBeNull();
  });
  hook.unmount(); session.stop();
});
