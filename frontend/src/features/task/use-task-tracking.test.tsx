import { StrictMode } from "react";
import { act, cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import { afterEach, beforeEach, expect, test, vi } from "vitest";
import { ApiError } from "@/lib/api-client";
import { authApi, type AccountView } from "@/features/identity/auth-api";
import { AuthProvider } from "@/features/identity/auth-provider";
import { accountA, accountB, deferred } from "@/test/fixtures";
import { accepted, TASK_A, TASK_B, taskSnapshot as view } from "@/test/task-fixtures";
import { taskApi } from "./task-api";
import type { TaskView } from "./task-contract";
import { useTaskTracking } from "./use-task-tracking";

let current: AccountView | null;
async function flush() { await act(async () => { for (let i = 0; i < 30; i++) await Promise.resolve(); }); }
async function advance(milliseconds: number) { await act(async () => { await vi.advanceTimersByTimeAsync(milliseconds); }); }

function TestHost() {
  const { active, tasksById, history, refreshHistory, refreshTask, trackAccepted } = useTaskTracking();
  return <main>
    <output aria-label="作用域">{active ? "有效" : "无效"}</output>
    <button onClick={() => { void refreshHistory(); }}>刷新历史</button>
    <button onClick={() => trackAccepted(accepted(TASK_A))}>受理 A</button>
    <button onClick={() => trackAccepted(accepted(TASK_B))}>受理 B</button>
    {history.loading && <p>正在读取历史</p>}
    {history.error && <p role="alert">历史错误：{history.error.message}</p>}
    {history.loaded && Object.keys(tasksById).length === 0 && <p>暂无记录</p>}
    {Object.entries(tasksById).map(([id, entry]) => <section key={id} aria-label={id}>
      <p>{entry.snapshot ? `${entry.snapshot.status}/${entry.snapshot.currentStage}/${entry.snapshot.revision}` : entry.accepted ? "已受理，等待快照" : "等待读取"}</p>
      {entry.snapshot?.failure && <p>执行失败：{entry.snapshot.failure.message}</p>}
      {entry.query.loading && <p>正在查询</p>}
      {entry.query.error && <p role="alert">查询异常：{entry.query.error.code}</p>}
      <button onClick={() => { void refreshTask(id); }}>重新查询 {id}</button>
    </section>)}
  </main>;
}

beforeEach(() => {
  vi.useFakeTimers(); current = accountA;
  vi.spyOn(authApi, "session").mockImplementation(async () => current);
  vi.spyOn(authApi, "csrf").mockResolvedValue({ headerName: "X-CSRF-TOKEN", token: "fake-token" });
  vi.spyOn(authApi, "login"); vi.spyOn(authApi, "logout");
  vi.spyOn(taskApi, "history").mockResolvedValue([view(), view(TASK_B)]);
  vi.spyOn(taskApi, "detail").mockImplementation(async (id) => view(id));
});
afterEach(() => { cleanup(); vi.clearAllTimers(); vi.useRealTimers(); vi.restoreAllMocks(); vi.unstubAllGlobals(); });

test("a real hook host distinguishes one query failure from another task's execution failure and allows recovery", async () => {
  vi.mocked(taskApi.detail).mockImplementation(async (id) => {
    if (id === TASK_A) throw new ApiError("暂不可用", 503, "SERVICE_UNAVAILABLE");
    return view(TASK_B, "FAILED", "GENERATE", 3);
  });
  render(<AuthProvider><TestHost /></AuthProvider>); await flush(); await advance(2000);
  const a = within(screen.getByRole("region", { name: TASK_A })), b = within(screen.getByRole("region", { name: TASK_B }));
  expect(a.getByText("RUNNING/CHECK_ASSETS/1")).toBeVisible();
  expect(a.getByRole("alert")).toHaveTextContent("查询异常：SERVICE_UNAVAILABLE");
  expect(a.queryByText(/执行失败/)).not.toBeInTheDocument();
  expect(b.getByText("FAILED/GENERATE/3")).toBeVisible();
  expect(b.getByText("执行失败：指定阶段失败")).toBeVisible();
  expect(b.queryByRole("alert")).not.toBeInTheDocument();
  vi.mocked(taskApi.detail).mockResolvedValueOnce(view(TASK_A, "SUCCEEDED", "FINALIZE", 4));
  fireEvent.click(a.getByRole("button")); await flush();
  expect(a.getByText("SUCCEEDED/FINALIZE/4")).toBeVisible(); expect(a.queryByRole("alert")).not.toBeInTheDocument();
  expect(vi.getTimerCount()).toBe(0);
});

test("waits for Java session restoration before reading history, and first failure is not empty history", async () => {
  const session = deferred<AccountView | null>(); vi.mocked(authApi.session).mockReturnValueOnce(session.promise);
  vi.mocked(taskApi.history).mockRejectedValueOnce(new ApiError("503", 503, "SERVICE_UNAVAILABLE"));
  render(<AuthProvider><TestHost /></AuthProvider>);
  expect(taskApi.history).not.toHaveBeenCalled(); expect(screen.queryByText("暂无记录")).not.toBeInTheDocument();
  await act(async () => session.resolve(accountA)); await flush();
  expect(taskApi.history).toHaveBeenCalledTimes(1); expect(screen.getByRole("alert")).toHaveTextContent("历史错误");
  expect(screen.queryByText("暂无记录")).not.toBeInTheDocument(); expect(vi.getTimerCount()).toBe(0);
  vi.mocked(taskApi.history).mockResolvedValueOnce([]); fireEvent.click(screen.getByRole("button", { name: "刷新历史" })); await flush();
  expect(screen.getByText("暂无记录")).toBeVisible();
});

test("two accepted IDs keep independent placeholders and never invent a PENDING snapshot", async () => {
  vi.mocked(taskApi.history).mockResolvedValue([]);
  const a = deferred<TaskView>(), b = deferred<TaskView>();
  vi.mocked(taskApi.detail).mockImplementation(async (id) => id === TASK_A ? a.promise : b.promise);
  render(<AuthProvider><TestHost /></AuthProvider>); await flush();
  fireEvent.click(screen.getByRole("button", { name: "受理 A" })); fireEvent.click(screen.getByRole("button", { name: "受理 B" }));
  expect(screen.getAllByText("已受理，等待快照")).toHaveLength(2); expect(screen.queryByText(/PENDING/)).not.toBeInTheDocument();
  await act(async () => b.resolve(view(TASK_B, "SUCCEEDED", "FINALIZE", 4))); await flush();
  expect(within(screen.getByRole("region", { name: TASK_A })).getByText("正在查询")).toBeVisible();
  expect(within(screen.getByRole("region", { name: TASK_B })).getByText("SUCCEEDED/FINALIZE/4")).toBeVisible();
  await act(async () => a.resolve(view(TASK_A, "FAILED", "CHECK_ASSETS", 2))); await flush();
  expect(vi.getTimerCount()).toBe(0);
});

test.each(["success", "error"])("leaving and returning to a page rejects its previous %s callbacks", async (outcome) => {
  const pending = deferred<TaskView>(); vi.mocked(taskApi.detail).mockReturnValueOnce(pending.promise);
  const page = render(<AuthProvider><TestHost /></AuthProvider>); await flush(); await advance(2000);
  const signals = vi.mocked(taskApi.detail).mock.calls.map((call) => call[2]);
  page.rerender(<AuthProvider><p>其他页面</p></AuthProvider>);
  expect(signals[0].aborted).toBe(true); expect(vi.getTimerCount()).toBe(0);
  vi.mocked(taskApi.history).mockResolvedValueOnce([view(TASK_B, "SUCCEEDED", "FINALIZE", 4)]);
  page.rerender(<AuthProvider><TestHost /></AuthProvider>); await flush();
  await act(async () => { if (outcome === "success") pending.resolve(view(TASK_A, "SUCCEEDED", "FINALIZE", 4)); else pending.reject(new ApiError("old", 401, "AUTHENTICATION_REQUIRED")); });
  await flush();
  expect(screen.queryByRole("region", { name: TASK_A })).not.toBeInTheDocument();
  expect(screen.getByText("SUCCEEDED/FINALIZE/4")).toBeVisible(); expect(vi.getTimerCount()).toBe(0);
});

test.each(["success", "error", "timeout"])("identity A → B → A does not accept its original %s/cleanup callbacks", async (outcome) => {
  const pending = deferred<TaskView>(); vi.mocked(taskApi.detail).mockReturnValueOnce(pending.promise);
  render(<AuthProvider><TestHost /></AuthProvider>); await flush(); await advance(2000);
  current = accountB;
  vi.mocked(taskApi.history).mockResolvedValueOnce([view(TASK_B, "SUCCEEDED", "FINALIZE", 4, accountB.ownerId)]);
  act(() => window.dispatchEvent(new Event("focus"))); expect(screen.queryByRole("region", { name: TASK_A })).not.toBeInTheDocument(); await flush();
  current = accountA;
  vi.mocked(taskApi.history).mockResolvedValueOnce([view(TASK_B, "FAILED", "GENERATE", 3)]);
  act(() => window.dispatchEvent(new Event("focus"))); await flush();
  await act(async () => {
    if (outcome === "success") pending.resolve(view(TASK_A, "SUCCEEDED", "FINALIZE", 4));
    else pending.reject(new ApiError("old", outcome === "error" ? 401 : 0, outcome === "timeout" ? "REQUEST_TIMEOUT" : "AUTHENTICATION_REQUIRED"));
  }); await flush();
  expect(screen.queryByRole("region", { name: TASK_A })).not.toBeInTheDocument(); expect(screen.getByText("FAILED/GENERATE/3")).toBeVisible();
  expect(vi.getTimerCount()).toBe(0);
});

test("the real client timeout pauses the query, preserves the snapshot, and late transport success cannot revive polling", async () => {
  vi.mocked(taskApi.detail).mockRestore(); vi.mocked(taskApi.history).mockResolvedValueOnce([view()]);
  const pending = deferred<Response>(); vi.stubGlobal("fetch", vi.fn().mockReturnValue(pending.promise));
  render(<AuthProvider><TestHost /></AuthProvider>); await flush(); await advance(2000); await advance(30_000);
  expect(screen.getByText("RUNNING/CHECK_ASSETS/1")).toBeVisible(); expect(screen.getByRole("alert")).toHaveTextContent("REQUEST_TIMEOUT");
  expect(vi.getTimerCount()).toBe(0); expect(fetch).toHaveBeenCalledTimes(1);
  await act(async () => pending.resolve(Response.json(view(TASK_A, "SUCCEEDED", "FINALIZE", 4)))); await flush(); await advance(60_000);
  expect(screen.getByText("RUNNING/CHECK_ASSETS/1")).toBeVisible(); expect(fetch).toHaveBeenCalledTimes(1);
});

test("Strict Mode only performs GET reads and unmount releases all polling/subscriptions", async () => {
  vi.mocked(taskApi.history).mockRestore(); vi.mocked(taskApi.detail).mockRestore();
  const fetcher = vi.fn().mockResolvedValue(Response.json({ items: [] })); vi.stubGlobal("fetch", fetcher);
  const page = render(<StrictMode><AuthProvider><TestHost /></AuthProvider></StrictMode>); await flush();
  expect(screen.getByText("暂无记录")).toBeVisible(); expect(vi.getTimerCount()).toBe(0);
  expect(fetcher.mock.calls.every(([, init]) => !init.method || init.method === "GET")).toBe(true);
  expect(authApi.login).not.toHaveBeenCalled(); expect(authApi.logout).not.toHaveBeenCalled();
  page.unmount(); const count = fetcher.mock.calls.length;
  act(() => window.dispatchEvent(new Event("focus"))); await advance(60_000);
  expect(fetcher).toHaveBeenCalledTimes(count); expect(vi.getTimerCount()).toBe(0);
});
