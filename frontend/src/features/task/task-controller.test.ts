import { afterEach, beforeEach, expect, test, vi } from "vitest";
import { ApiError } from "@/lib/api-client";
import { authApi, type AccountView } from "@/features/identity/auth-api";
import { AuthSession } from "@/features/identity/auth-session";
import { accountA, accountB, deferred } from "@/test/fixtures";
import { accepted, TASK_A, TASK_B, taskSnapshot as view } from "@/test/task-fixtures";
import { taskApi } from "./task-api";
import { TaskController } from "./task-controller";
import { TASK_CONFLICT } from "./task-state";
import type { TaskView } from "./task-contract";

let session: AuthSession;
let controller: TaskController;
let current: AccountView | null;
let controllers: TaskController[];
async function settle() { for (let i = 0; i < 20; i++) await Promise.resolve(); }
function create() {
  const state = session.getSnapshot();
  const created = new TaskController(session, { ownerId: state.account!.ownerId, identityEpoch: state.identityEpoch });
  controllers.push(created); return created;
}
async function start(tasks: TaskView[]) {
  vi.mocked(taskApi.history).mockResolvedValueOnce(tasks); controller.start(); await settle();
}
beforeEach(async () => {
  vi.useFakeTimers(); current = accountA; controllers = [];
  vi.spyOn(authApi, "session").mockImplementation(async () => current);
  vi.spyOn(authApi, "csrf").mockResolvedValue({ headerName: "X-CSRF-TOKEN", token: "fake-token" });
  vi.spyOn(taskApi, "history").mockResolvedValue([]);
  vi.spyOn(taskApi, "detail").mockImplementation(async (id) => view(id));
  session = new AuthSession(); session.start(); await settle(); controller = create();
});
afterEach(() => { controllers.forEach((item) => item.stop()); session.stop(); vi.clearAllTimers(); vi.useRealTimers(); vi.restoreAllMocks(); });

test.each([{ tasks: [] }, { tasks: [view(TASK_A, "SUCCEEDED", "FINALIZE", 4)] }, { tasks: [view(TASK_A, "FAILED", "GENERATE", 3)] }])("empty or terminal-only history never starts an idle polling loop", async ({ tasks }) => {
  await start(tasks);
  expect(controller.getSnapshot().history).toMatchObject({ loaded: true, error: null, loading: false });
  expect(vi.getTimerCount()).toBe(0);
  await vi.advanceTimersByTimeAsync(20_000);
  expect(taskApi.history).toHaveBeenCalledTimes(1); expect(taskApi.detail).not.toHaveBeenCalled();
});

test("two tasks poll independently, never overlap automatic reads, and schedule only after settle", async () => {
  const pending = deferred<TaskView>();
  vi.mocked(taskApi.detail).mockImplementation(async (id) => id === TASK_A ? pending.promise : view(TASK_B, "FAILED", "GENERATE", 3));
  await start([view(), view(TASK_B)]);
  await vi.advanceTimersByTimeAsync(2000);
  expect(taskApi.detail).toHaveBeenCalledTimes(2);
  expect(controller.getSnapshot().tasksById[TASK_B].snapshot?.status).toBe("FAILED");
  await vi.advanceTimersByTimeAsync(20_000);
  expect(taskApi.detail).toHaveBeenCalledTimes(2);
  pending.resolve(view(TASK_A, "RUNNING", "GENERATE", 2)); await settle();
  await vi.advanceTimersByTimeAsync(1999); expect(taskApi.detail).toHaveBeenCalledTimes(2);
  vi.mocked(taskApi.detail).mockResolvedValueOnce(view(TASK_A, "SUCCEEDED", "FINALIZE", 4));
  await vi.advanceTimersByTimeAsync(1);
  expect(taskApi.detail).toHaveBeenCalledTimes(3);
  expect(controller.getSnapshot().tasksById[TASK_A].snapshot?.status).toBe("SUCCEEDED");
  expect(vi.getTimerCount()).toBe(0);
});

test("query 503 preserves RUNNING and pauses only that query while a real FAILED is a normal snapshot", async () => {
  vi.mocked(taskApi.detail).mockImplementation(async (id) => {
    if (id === TASK_A) throw new ApiError("状态查询暂不可用", 503, "SERVICE_UNAVAILABLE");
    return view(TASK_B, "FAILED", "GENERATE", 3);
  });
  await start([view(), view(TASK_B)]);
  await vi.advanceTimersByTimeAsync(2000);
  const state = controller.getSnapshot();
  expect(state.tasksById[TASK_A]).toMatchObject({ snapshot: { status: "RUNNING", failure: null }, query: { paused: true, loading: false, error: { code: "SERVICE_UNAVAILABLE" } } });
  expect(state.tasksById[TASK_B]).toMatchObject({ snapshot: { status: "FAILED", failure: { stage: "GENERATE" } }, query: { paused: false, error: null } });
  expect(session.getSnapshot().status).toBe("authenticated");
  await vi.advanceTimersByTimeAsync(20_000); expect(taskApi.detail).toHaveBeenCalledTimes(2);
  vi.mocked(taskApi.detail).mockResolvedValueOnce(view(TASK_A, "RUNNING", "GENERATE", 2));
  await controller.refreshTask(TASK_A);
  expect(controller.getSnapshot().tasksById[TASK_A].query).toEqual({ loading: false, paused: false, error: null });
  expect(vi.getTimerCount()).toBe(1);
});

test("initial history failure is not empty history; retry recovers without dropping accepted records", async () => {
  vi.mocked(taskApi.history).mockRejectedValueOnce(new ApiError("历史暂不可用", 503, "SERVICE_UNAVAILABLE"));
  controller.start(); await settle();
  expect(controller.getSnapshot().history).toMatchObject({ loaded: false, error: { code: "SERVICE_UNAVAILABLE" } });
  expect(vi.getTimerCount()).toBe(0);
  vi.mocked(taskApi.history).mockResolvedValueOnce([view()]);
  await controller.refreshHistory();
  expect(controller.getSnapshot().history).toEqual({ loaded: true, loading: false, error: null });
  expect(vi.getTimerCount()).toBe(1);
});

test("late history never removes an accepted placeholder and its first read may already be terminal", async () => {
  const history = deferred<TaskView[]>(), first = deferred<TaskView>();
  vi.mocked(taskApi.history).mockReturnValueOnce(history.promise); vi.mocked(taskApi.detail).mockReturnValueOnce(first.promise);
  controller.start();
  expect(controller.trackAccepted(accepted())).toBe(true);
  expect(controller.getSnapshot().tasksById[TASK_A]).toMatchObject({ snapshot: null, accepted: accepted(), query: { loading: true } });
  history.resolve([]); await settle();
  expect(controller.getSnapshot().tasksById[TASK_A].accepted).toEqual(accepted());
  first.resolve(view(TASK_A, "SUCCEEDED", "FINALIZE", 4)); await settle();
  expect(controller.getSnapshot().tasksById[TASK_A]).toMatchObject({ snapshot: { status: "SUCCEEDED" }, accepted: null });
  expect(vi.getTimerCount()).toBe(0);
});

test("late smaller revision history cannot roll back a newer single snapshot", async () => {
  await start([view()]);
  const history = deferred<TaskView[]>(); vi.mocked(taskApi.history).mockReturnValueOnce(history.promise);
  const reading = controller.refreshHistory();
  vi.mocked(taskApi.detail).mockResolvedValueOnce(view(TASK_A, "RUNNING", "GENERATE", 2));
  await controller.refreshTask(TASK_A);
  history.resolve([view()]); await reading;
  expect(controller.getSnapshot().tasksById[TASK_A].snapshot?.revision).toBe(2);
  expect(vi.getTimerCount()).toBe(1);
});

test("nonadjacent snapshots are legal; another task's revision never orders this task", async () => {
  await start([view(TASK_A, "PENDING", null, 0), view(TASK_B, "RUNNING", "GENERATE", 2)]);
  vi.mocked(taskApi.detail).mockResolvedValueOnce(view(TASK_A, "SUCCEEDED", "FINALIZE", 4));
  await controller.refreshTask(TASK_A);
  expect(controller.getSnapshot().tasksById[TASK_A].snapshot?.status).toBe("SUCCEEDED");
  expect(controller.getSnapshot().tasksById[TASK_B].snapshot?.revision).toBe(2);
  expect(vi.getTimerCount()).toBe(1);
});

test.each(["RUNNING", "SUCCEEDED", "FAILED"] as const)("a higher revision %s cannot replace a known terminal", async (status) => {
  const terminal = view(TASK_A, "SUCCEEDED", "FINALIZE", 4);
  await start([terminal]);
  vi.mocked(taskApi.detail).mockResolvedValueOnce(view(TASK_A, status, "FINALIZE", 6));
  await controller.refreshTask(TASK_A);
  expect(controller.getSnapshot().tasksById[TASK_A]).toMatchObject({ snapshot: terminal, query: { paused: true, error: { code: TASK_CONFLICT } } });
  expect(vi.getTimerCount()).toBe(0);
});

test("same-version contradiction and backward stage keep the snapshot and can be manually recovered", async () => {
  await start([view()]);
  vi.mocked(taskApi.detail).mockResolvedValueOnce(view(TASK_A, "RUNNING", "GENERATE", 1));
  await controller.refreshTask(TASK_A);
  expect(controller.getSnapshot().tasksById[TASK_A]).toMatchObject({ snapshot: { revision: 1, currentStage: "CHECK_ASSETS" }, query: { paused: true, error: { code: TASK_CONFLICT } } });
  vi.mocked(taskApi.detail).mockResolvedValueOnce(view(TASK_A, "RUNNING", "GENERATE", 2)); await controller.refreshTask(TASK_A);
  vi.mocked(taskApi.detail).mockResolvedValueOnce(view(TASK_A, "RUNNING", "CHECK_ASSETS", 3)); await controller.refreshTask(TASK_A);
  expect(controller.getSnapshot().tasksById[TASK_A]).toMatchObject({ snapshot: { revision: 2 }, query: { error: { code: TASK_CONFLICT } } });
  vi.mocked(taskApi.detail).mockResolvedValueOnce(view(TASK_A, "RUNNING", "GENERATE", 2)); await controller.refreshTask(TASK_A);
  expect(controller.getSnapshot().tasksById[TASK_A].query.error).toBeNull();
  expect(vi.getTimerCount()).toBe(1);
});

test.each(["newer", "terminal"])("history %s data makes an in-flight old error/finally ineffective", async (kind) => {
  await start([view()]); const pending = deferred<TaskView>();
  vi.mocked(taskApi.detail).mockReturnValueOnce(pending.promise); const reading = controller.refreshTask(TASK_A);
  const candidate = kind === "terminal" ? view(TASK_A, "SUCCEEDED", "FINALIZE", 4) : view(TASK_A, "RUNNING", "GENERATE", 2);
  vi.mocked(taskApi.history).mockResolvedValueOnce([candidate]); await controller.refreshHistory();
  const known = controller.getSnapshot();
  pending.reject(new ApiError("old 401", 401, "AUTHENTICATION_REQUIRED")); await reading;
  expect(controller.getSnapshot()).toBe(known);
  expect(session.getSnapshot().status).toBe("authenticated");
  expect(vi.getTimerCount()).toBe(kind === "terminal" ? 0 : 1);
});

test("same revision history cannot clear a current read's loading, and older history cannot resume a later query error", async () => {
  await start([view()]); const pending = deferred<TaskView>();
  vi.mocked(taskApi.detail).mockReturnValueOnce(pending.promise); const reading = controller.refreshTask(TASK_A);
  vi.mocked(taskApi.history).mockResolvedValueOnce([view()]); await controller.refreshHistory();
  expect(controller.getSnapshot().tasksById[TASK_A].query.loading).toBe(true);
  const history = deferred<TaskView[]>(); vi.mocked(taskApi.history).mockReturnValueOnce(history.promise);
  const listing = controller.refreshHistory();
  pending.reject(new ApiError("503", 503, "SERVICE_UNAVAILABLE")); await reading;
  history.resolve([view()]); await listing;
  // This history started during the same request; it cannot release that request's later pause.
  expect(controller.getSnapshot().tasksById[TASK_A].query.paused).toBe(true);
  expect(vi.getTimerCount()).toBe(0);
  vi.mocked(taskApi.history).mockResolvedValueOnce([view()]); await controller.refreshHistory();
  expect(controller.getSnapshot().tasksById[TASK_A].query.paused).toBe(false);
  expect(vi.getTimerCount()).toBe(1);
});

test("a superseded read's higher revision data can merge while stale errors cannot change feedback", async () => {
  await start([view()]); const old = deferred<TaskView>(), latest = deferred<TaskView>();
  vi.mocked(taskApi.detail).mockReturnValueOnce(old.promise).mockReturnValueOnce(latest.promise);
  const first = controller.refreshTask(TASK_A), second = controller.refreshTask(TASK_A);
  latest.resolve(view(TASK_A, "RUNNING", "GENERATE", 2)); await second;
  old.resolve(view(TASK_A, "SUCCEEDED", "FINALIZE", 4)); await first;
  expect(controller.getSnapshot().tasksById[TASK_A].snapshot?.status).toBe("SUCCEEDED");
  expect(vi.getTimerCount()).toBe(0);
});

test.each(["success", "error"])("a superseded history's late %s cannot change the current history request", async (kind) => {
  const old = deferred<TaskView[]>(), latest = deferred<TaskView[]>();
  vi.mocked(taskApi.history).mockReturnValueOnce(old.promise).mockReturnValueOnce(latest.promise);
  controller.start(); const reading = controller.refreshHistory();
  if (kind === "success") old.resolve([view()]); else old.reject(new Error("old history"));
  await settle(); expect(controller.getSnapshot().history.loading).toBe(true);
  latest.resolve([]); await reading;
  expect(controller.getSnapshot().tasksById).toEqual({});
  expect(controller.getSnapshot().history.error).toBeNull();
});

test.each(["success", "401", "503", "timeout"])("page/identity A → B → A rejects an old %s callback and cannot restart its timers", async (outcome) => {
  await start([view()]); const pending = deferred<TaskView>();
  vi.mocked(taskApi.detail).mockReturnValueOnce(pending.promise); const reading = controller.refreshTask(TASK_A);
  current = accountB; await session.refresh(true);
  const b = create(); vi.mocked(taskApi.history).mockResolvedValueOnce([view(TASK_B, "RUNNING", "CHECK_ASSETS", 1, accountB.ownerId)]); b.start(); await settle();
  current = accountA; await session.refresh(true);
  const again = create(); vi.mocked(taskApi.history).mockResolvedValueOnce([view(TASK_B)]); again.start(); await settle();
  const known = again.getSnapshot();
  if (outcome === "success") pending.resolve(view(TASK_A, "SUCCEEDED", "FINALIZE", 4));
  else pending.reject(new ApiError("old error", outcome === "401" ? 401 : outcome === "503" ? 503 : 0, outcome === "timeout" ? "REQUEST_TIMEOUT" : "SERVICE_UNAVAILABLE"));
  await reading;
  expect(controller.getSnapshot()).toMatchObject({ active: false, tasksById: {} });
  expect(again.getSnapshot()).toBe(known); expect(session.getSnapshot().account).toEqual(accountA);
  expect(vi.getTimerCount()).toBe(1);
});

test("stop clears in-flight reads/timers; restart uses a fresh page generation", async () => {
  await start([view()]); const pending = deferred<TaskView>();
  vi.mocked(taskApi.detail).mockReturnValueOnce(pending.promise); const reading = controller.refreshTask(TASK_A);
  const signal = vi.mocked(taskApi.detail).mock.calls[0][2]; controller.stop();
  expect(signal.aborted).toBe(true); expect(vi.getTimerCount()).toBe(0);
  vi.mocked(taskApi.history).mockResolvedValueOnce([view(TASK_B)]); controller.start(); await settle();
  pending.resolve(view(TASK_A, "SUCCEEDED", "FINALIZE", 4)); await reading;
  expect(controller.getSnapshot().tasksById[TASK_A]).toBeUndefined();
  expect(vi.getTimerCount()).toBe(1);
});

test("current 401 clears private state, and foreign accepted metadata is rejected", async () => {
  await start([view()]);
  expect(() => controller.trackAccepted(accepted(TASK_B, accountB.ownerId))).toThrow();
  vi.mocked(taskApi.detail).mockRejectedValueOnce(new ApiError("登录已失效", 401, "AUTHENTICATION_REQUIRED")); current = null;
  await controller.refreshTask(TASK_A); await settle();
  expect(controller.getSnapshot()).toMatchObject({ active: false, tasksById: {} });
  expect(session.getSnapshot().status).toBe("anonymous"); expect(vi.getTimerCount()).toBe(0);
});

test("one paused query does not prevent another running task's next automatic read", async () => {
  vi.mocked(taskApi.detail).mockRejectedValueOnce(new ApiError("503", 503, "SERVICE_UNAVAILABLE"))
    .mockResolvedValueOnce(view(TASK_B, "RUNNING", "GENERATE", 2))
    .mockResolvedValueOnce(view(TASK_B, "SUCCEEDED", "FINALIZE", 4));
  await start([view(), view(TASK_B)]);
  await vi.advanceTimersByTimeAsync(4000);
  expect(vi.mocked(taskApi.detail).mock.calls.map((call) => call[0])).toEqual([TASK_A, TASK_B, TASK_B]);
  expect(controller.getSnapshot().tasksById[TASK_A].query.paused).toBe(true);
  expect(controller.getSnapshot().tasksById[TASK_B].snapshot?.status).toBe("SUCCEEDED");
  expect(vi.getTimerCount()).toBe(0);
});

test.each(["templateId", "createdAt"] as const)("a higher revision cannot change immutable %s", async (field) => {
  await start([view()]);
  const newer = view(TASK_A, "RUNNING", "GENERATE", 2);
  vi.mocked(taskApi.detail).mockResolvedValueOnce({ ...newer, [field]: field === "templateId" ? "travel-postcard" : "2026-10-31T03:00:00.000001Z" });
  await controller.refreshTask(TASK_A);
  expect(controller.getSnapshot().tasksById[TASK_A]).toMatchObject({ snapshot: { revision: 1 }, query: { paused: true, error: { code: TASK_CONFLICT } } });
});

test("resource 404 pauses only the query and retains an existing task fact", async () => {
  await start([view()]); vi.mocked(taskApi.detail).mockRejectedValueOnce(new ApiError("资源不存在", 404, "RESOURCE_NOT_FOUND"));
  await controller.refreshTask(TASK_A);
  expect(controller.getSnapshot().tasksById[TASK_A]).toMatchObject({ snapshot: { status: "RUNNING" }, query: { paused: true, error: { code: "RESOURCE_NOT_FOUND" } } });
  expect(session.getSnapshot().status).toBe("authenticated"); expect(vi.getTimerCount()).toBe(0);
});

test("a later history failure preserves loaded facts and does not stop task-specific tracking", async () => {
  await start([view()]); vi.mocked(taskApi.history).mockRejectedValueOnce(new ApiError("历史异常", 503, "SERVICE_UNAVAILABLE"));
  await controller.refreshHistory();
  expect(controller.getSnapshot().history).toMatchObject({ loaded: true, error: { code: "SERVICE_UNAVAILABLE" } });
  vi.mocked(taskApi.detail).mockResolvedValueOnce(view(TASK_A, "SUCCEEDED", "FINALIZE", 4));
  await vi.advanceTimersByTimeAsync(2000);
  expect(controller.getSnapshot().tasksById[TASK_A].snapshot?.status).toBe("SUCCEEDED");
});

test.each(["CSRF_INVALID", "REQUEST_TIMEOUT"])("a superseded request's %s cannot clear the latest loading or request handle", async (code) => {
  await start([view()]); const old = deferred<TaskView>(), latest = deferred<TaskView>();
  vi.mocked(taskApi.detail).mockReturnValueOnce(old.promise).mockReturnValueOnce(latest.promise);
  const first = controller.refreshTask(TASK_A), second = controller.refreshTask(TASK_A);
  old.reject(new ApiError("old request", code === "CSRF_INVALID" ? 403 : 0, code)); await first;
  expect(controller.getSnapshot().tasksById[TASK_A].query).toMatchObject({ loading: true, error: null, paused: false });
  expect(vi.mocked(taskApi.detail).mock.calls[1][2].aborted).toBe(false);
  expect(session.getSnapshot().status).toBe("authenticated"); expect(vi.getTimerCount()).toBe(0);
  latest.resolve(view(TASK_A, "RUNNING", "GENERATE", 2)); await second;
  expect(vi.getTimerCount()).toBe(1);
});
