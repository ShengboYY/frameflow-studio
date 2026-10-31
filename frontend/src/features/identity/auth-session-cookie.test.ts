import { afterEach, expect, test, vi } from "vitest";
import { AuthSession } from "./auth-session";

const a = { ownerId: "82dd291e-4673-4500-8fe3-97a71a6cbe40", username: "demo-a", displayName: "演示用户 A" };
const b = { ownerId: "82dd291e-4673-4500-8fe3-97a71a6cbe41", username: "demo-b", displayName: "演示用户 B" };
const pages: AuthSession[] = [];
afterEach(() => { pages.forEach((page) => page.stop()); pages.length = 0; vi.restoreAllMocks(); vi.unstubAllGlobals(); localStorage.clear(); });

test.each(["first", "second"])("after shared-Cookie logout, the %s page switches accounts on its first login attempt", async (loginPage) => {
  const serverSessions = new Map<string, { account: typeof a | null; token: string }>();
  const requests: { path: string; method: string; status: number }[] = [];
  let browserCookie: string | null = null;
  let nextId = 0;
  function create(account: typeof a | null) {
    const id = `session-${++nextId}`;
    serverSessions.set(id, { account, token: `token-${id}` });
    return id;
  }
  vi.stubGlobal("fetch", async (path: string, init: RequestInit = {}) => {
    // Capture the shared Cookie at dispatch; concurrent CSRF reads may each create a session.
    const sentCookie = browserCookie;
    await Promise.resolve();
    let held = sentCookie ? serverSessions.get(sentCookie) : undefined;
    let response: Response;
    const failure = (status: number, code: string) => Response.json({ error: { code, message: code, details: [] }, requestId: "cookie-race-test" }, { status });
    if (path.endsWith("/session")) response = held?.account ? Response.json(held.account) : failure(401, "AUTHENTICATION_REQUIRED");
    else if (path.endsWith("/csrf")) {
      if (!held) {
        browserCookie = create(null);
        held = serverSessions.get(browserCookie)!;
      }
      response = Response.json({ token: held.token, headerName: "X-CSRF-TOKEN" });
    } else if (!held || new Headers(init.headers).get("X-CSRF-TOKEN") !== held.token) {
      response = failure(403, "CSRF_INVALID");
    } else if (path.endsWith("/logout")) {
      serverSessions.delete(sentCookie!);
      browserCookie = null;
      response = new Response(null, { status: 204 });
    } else {
      const username = new URLSearchParams(String(init.body)).get("username");
      const account = username === "demo-a" ? a : b;
      serverSessions.delete(sentCookie!);
      browserCookie = create(account);
      response = Response.json(account);
    }
    requests.push({ path, method: init.method ?? "GET", status: response.status });
    return response;
  });
  const first = new AuthSession(), second = new AuthSession(); pages.push(first, second);
  const ready = async (page: AuthSession, status: "anonymous" | "authenticated") => {
    await vi.waitFor(() => expect(page.getSnapshot()).toMatchObject({ status, busy: false }));
  };
  first.start(); await ready(first, "anonymous");
  expect(await first.login("demo-a", "test-password")).toBe(true);
  second.start(); await ready(second, "authenticated");
  // Model the other document's storage listener, while each page keeps its own memory.
  let writer = first;
  vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => { void (writer === first ? second : first).refresh(true); });
  requests.length = 0;
  expect(await first.logout()).toBe(true);
  await ready(second, "anonymous");
  expect(requests.filter((request) => request.path.endsWith("/csrf"))).toHaveLength(0);
  const beforeLogin = requests.length;
  writer = loginPage === "first" ? first : second;
  expect(await writer.login("demo-b", "test-password")).toBe(true);
  await ready(writer === first ? second : first, "authenticated");
  expect(requests.slice(beforeLogin).filter((request) => request.path.endsWith("/login"))).toEqual([
    { path: "/api/v1/auth/login", method: "POST", status: 200 },
  ]);
  expect(first.getSnapshot().account).toEqual(b);
  expect(second.getSnapshot().account).toEqual(b);
});
