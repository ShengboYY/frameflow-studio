import { afterEach, beforeEach, expect, test, vi } from "vitest";
import { ApiError } from "@/lib/api-client";
import { authApi, type AccountView } from "./auth-api";
import { AUTH_CHANGE_KEY, AuthSession } from "./auth-session";

const a: AccountView = { ownerId: "82dd291e-4673-4500-8fe3-97a71a6cbe40", username: "demo-a", displayName: "演示用户 A" };
const b: AccountView = { ownerId: "82dd291e-4673-4500-8fe3-97a71a6cbe41", username: "demo-b", displayName: "演示用户 B" };
let current: AccountView | null;
let session: AuthSession;

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (cause: unknown) => void;
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
}
async function settle() { for (let i = 0; i < 15; i++) await Promise.resolve(); }

beforeEach(() => {
  current = a;
  vi.spyOn(authApi, "session").mockImplementation(async () => current);
  vi.spyOn(authApi, "csrf").mockImplementation(async () => ({ token: `fake-${current?.username ?? "anonymous"}`, headerName: "X-CSRF-TOKEN" }));
  vi.spyOn(authApi, "login").mockImplementation(async (username) => { current = username === "demo-a" ? a : b; return current; });
  vi.spyOn(authApi, "logout").mockImplementation(async () => { current = null; });
  session = new AuthSession();
});
afterEach(() => { session.stop(); vi.restoreAllMocks(); localStorage.clear(); });

test("restores the account and only publishes a CSRF context after confirming the same account", async () => {
  session.start();
  expect(session.captureWriteContext()).toBeNull();
  await settle();
  expect(session.getSnapshot()).toMatchObject({ account: a, status: "authenticated" });
  expect(authApi.session).toHaveBeenCalledTimes(2);
  expect(session.captureWriteContext()).toMatchObject({ ownerId: a.ownerId, csrf: { token: "fake-demo-a" } });
});

test("503 retains known account information, disables private work, and allows manual recovery", async () => {
  session.start(); await settle();
  vi.mocked(authApi.session).mockRejectedValueOnce(new ApiError("会话查询暂不可用", 503, "SERVICE_UNAVAILABLE"));
  await session.refresh();
  expect(session.getSnapshot()).toMatchObject({ status: "error", account: a, message: "会话查询暂不可用" });
  expect(session.captureWriteContext()).toBeNull();
  await session.refresh();
  expect(session.getSnapshot().status).toBe("authenticated");
});

test("logout invalidates the scope synchronously and uses the captured original CSRF once", async () => {
  session.start(); await settle();
  const scope = session.captureWriteContext()!;
  const pending = deferred<void>();
  vi.mocked(authApi.logout).mockImplementationOnce(async () => { await pending.promise; current = null; });
  const logout = session.logout();
  expect(session.isCurrent(scope)).toBe(false);
  expect(session.getSnapshot().account).toBeNull();
  expect(await session.logout()).toBe(false);
  expect(await session.login("demo-b", "fake-password")).toBe(false);
  expect(authApi.logout).toHaveBeenCalledExactlyOnceWith({ headerName: "X-CSRF-TOKEN", token: "fake-demo-a" });
  pending.resolve(); await logout;
  expect(session.getSnapshot()).toMatchObject({ status: "anonymous", account: null, busy: false });
});

test("login and logout serialize through reconciliation; notifications contain no account or credential", async () => {
  current = null; session.start(); await settle();
  const store = vi.spyOn(Storage.prototype, "setItem");
  const pending = deferred<AccountView>();
  vi.mocked(authApi.login).mockImplementationOnce(async () => { await pending.promise; current = a; return a; });
  const login = session.login("demo-a", "fake-password");
  expect(await session.login("demo-b", "another-password")).toBe(false);
  await session.refresh(true);
  pending.resolve(a); await login;
  expect(authApi.login).toHaveBeenCalledTimes(1);
  expect(session.getSnapshot()).toMatchObject({ status: "authenticated", account: a, busy: false });
  expect(store).toHaveBeenCalledWith(AUTH_CHANGE_KEY, expect.any(String));
  expect(store.mock.calls[0][1]).toMatch(/^[0-9a-f-]{36}$/);
  expect(JSON.stringify(store.mock.calls)).not.toMatch(/fake-password|demo-a|ownerId|token/);
});

test("invalid credentials use a generic message and uncertain writes reconcile without retry", async () => {
  current = null; session.start(); await settle();
  vi.mocked(authApi.login).mockRejectedValueOnce(new ApiError("not disclosed", 401, "INVALID_CREDENTIALS"));
  await session.login("demo-a", "fake-password");
  expect(session.getSnapshot()).toMatchObject({ status: "anonymous", message: "账号或密码错误" });
  vi.mocked(authApi.login).mockImplementationOnce(async () => { current = b; throw new ApiError("timeout", 0, "REQUEST_TIMEOUT"); });
  await session.login("demo-b", "fake-password");
  expect(session.getSnapshot()).toMatchObject({ status: "authenticated", account: b });
  expect(session.getSnapshot().message).toContain("结果暂未确认");
  expect(authApi.login).toHaveBeenCalledTimes(2);
});

test.each(["success", "error"])("ignores late session %s across A → B → A, including its finally", async (outcome) => {
  session.start(); await settle();
  const original = session.captureWriteContext()!;
  const pending = deferred<AccountView | null>();
  vi.mocked(authApi.session).mockReturnValueOnce(pending.promise);
  const oldRefresh = session.refresh();
  current = b; await session.refresh(true);
  current = a; await session.refresh(true);
  const final = session.getSnapshot();
  if (outcome === "success") pending.resolve(b); else pending.reject(new Error("old error"));
  await oldRefresh;
  expect(session.getSnapshot()).toBe(final);
  expect(session.isCurrent(original)).toBe(false);
  expect(session.getSnapshot().account).toEqual(a);
});

test("rejects a token read across a change of account", async () => {
  vi.mocked(authApi.session).mockResolvedValueOnce(a).mockResolvedValueOnce(b);
  session.start(); await settle();
  expect(session.getSnapshot()).toMatchObject({ status: "error", account: null });
  expect(session.captureWriteContext()).toBeNull();
});

test("private 401/403 invalidate old contexts; 503 and old-epoch errors cannot log out a new scope", async () => {
  session.start(); await settle();
  const original = session.captureWriteContext()!;
  await session.handleRequestError(new ApiError("unavailable", 503, "RESOURCE_UNAVAILABLE"), original.identityEpoch);
  expect(session.isCurrent(original)).toBe(true);
  current = b;
  await session.handleRequestError(new ApiError("csrf", 403, "CSRF_INVALID"), original.identityEpoch);
  expect(session.isCurrent(original)).toBe(false);
  expect(session.getSnapshot().account).toEqual(b);
  const fresh = session.captureWriteContext()!;
  await session.handleRequestError(new ApiError("expired", 401, "AUTHENTICATION_REQUIRED"), original.identityEpoch);
  expect(session.isCurrent(fresh)).toBe(true);
  current = null;
  await session.handleRequestError(new ApiError("expired", 401, "AUTHENTICATION_REQUIRED"), fresh.identityEpoch);
  expect(session.getSnapshot().status).toBe("anonymous");
  expect(authApi.login).not.toHaveBeenCalled();
  expect(authApi.logout).not.toHaveBeenCalled();
});

test("storage failures do not prevent logout; stop ignores stale work and restart rechecks", async () => {
  session.start(); await settle();
  vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => { throw new Error("storage disabled"); });
  await session.logout();
  expect(session.getSnapshot().status).toBe("anonymous");
  const pending = deferred<AccountView | null>();
  vi.mocked(authApi.session).mockReturnValueOnce(pending.promise);
  const refresh = session.refresh(); session.stop();
  pending.resolve(a); await refresh;
  expect(session.getSnapshot().account).toBeNull();
  current = b; session.start(); await settle();
  expect(session.getSnapshot().account).toEqual(b);
});

test.each(["success", "error"])("late CSRF %s cannot replace a new account token or error state", async (outcome) => {
  const pending = deferred<{ token: string; headerName: "X-CSRF-TOKEN" }>();
  vi.mocked(authApi.csrf).mockReturnValueOnce(pending.promise);
  session.start(); await settle();
  current = b; await session.refresh(true);
  const snapshot = session.getSnapshot();
  if (outcome === "success") pending.resolve({ token: "fake-old-token", headerName: "X-CSRF-TOKEN" });
  else pending.reject(new Error("old CSRF failure"));
  await settle();
  expect(session.getSnapshot()).toBe(snapshot);
  expect(session.captureWriteContext()?.csrf.token).toBe("fake-demo-b");
});

test("restarting after an authentication write settled while stopped releases the UI gate", async () => {
  session.start(); await settle();
  const pending = deferred<void>();
  vi.mocked(authApi.logout).mockImplementationOnce(async () => { await pending.promise; current = null; });
  const logout = session.logout(); session.stop();
  pending.resolve(); await logout;
  session.start(); await settle();
  expect(session.getSnapshot()).toMatchObject({ status: "anonymous", busy: false });
});
