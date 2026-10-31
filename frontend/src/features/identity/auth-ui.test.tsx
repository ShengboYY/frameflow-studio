import { StrictMode } from "react";
import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, expect, test, vi } from "vitest";
import { AuthProvider, useAuth } from "./auth-provider";
import { AuthNavigation } from "./auth-navigation";
import { LoginForm } from "./login-form";
import { AUTH_CHANGE_KEY } from "./auth-session";

const { replace } = vi.hoisted(() => ({ replace: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => ({ replace }) }));
const a = { ownerId: "82dd291e-4673-4500-8fe3-97a71a6cbe40", username: "demo-a", displayName: "演示用户 A" };
const b = { ownerId: "82dd291e-4673-4500-8fe3-97a71a6cbe41", username: "demo-b", displayName: "演示用户 B" };
let current: typeof a | null;
let failure: number | null;
let posts: { path: string; init: RequestInit }[];

function error(status: number, code: string) {
  return Response.json({ error: { code, message: "controlled error", details: [] }, requestId: "test-request" }, { status });
}
function Harness() {
  const { identityEpoch, status } = useAuth();
  return <><AuthNavigation /><LoginForm /><output aria-label="身份作用域">{identityEpoch}:{status}</output></>;
}
beforeEach(() => {
  current = null; failure = null; posts = []; replace.mockClear();
  vi.stubGlobal("fetch", vi.fn(async (path: string, init: RequestInit) => {
    if (path.endsWith("/csrf")) return Response.json({ token: `fake-${current?.username ?? "anonymous"}`, headerName: "X-CSRF-TOKEN" });
    if (path.endsWith("/session")) return failure ? error(failure, "SERVICE_UNAVAILABLE") : current ? Response.json(current) : error(401, "AUTHENTICATION_REQUIRED");
    posts.push({ path, init });
    if (path.endsWith("/login")) {
      const body = new URLSearchParams(String(init.body));
      if (body.get("password") !== "测试 +& password") return error(401, "INVALID_CREDENTIALS");
      current = body.get("username") === "demo-b" ? b : a;
      return Response.json(current);
    }
    current = null;
    return new Response(null, { status: 204 });
  }));
});
afterEach(() => { vi.unstubAllGlobals(); vi.restoreAllMocks(); localStorage.clear(); });

test("renders labelled login fields, serializes encoded credentials, clears password, and shows account/logout", async () => {
  render(<AuthProvider><Harness /></AuthProvider>);
  const button = screen.getByRole("button", { name: "登录" });
  await waitFor(() => expect(button).toBeEnabled());
  fireEvent.change(screen.getByLabelText("用户名"), { target: { value: "demo-a" } });
  fireEvent.change(screen.getByLabelText("密码"), { target: { value: "测试 +& password" } });
  fireEvent.click(button);
  await screen.findByText("演示用户 A（demo-a）");
  expect(screen.getByLabelText("密码")).toHaveValue("");
  expect(replace).toHaveBeenCalledWith("/");
  expect(posts).toHaveLength(1);
  expect(posts[0].init.headers).toMatchObject({ "X-CSRF-TOKEN": "fake-anonymous" });
  expect(new URLSearchParams(String(posts[0].init.body)).get("password")).toBe("测试 +& password");
  fireEvent.click(screen.getByRole("button", { name: "退出登录" }));
  await waitFor(() => expect(button).toBeEnabled());
  expect(screen.queryByText("演示用户 A（demo-a）")).not.toBeInTheDocument();
  expect(posts).toHaveLength(2);
  expect(posts[1].init.headers).toMatchObject({ "X-CSRF-TOKEN": "fake-demo-a" });
  expect(screen.queryByRole("link", { name: /注册/ })).not.toBeInTheDocument();
});

test("shows generic credentials error and does not auto-retry", async () => {
  render(<AuthProvider><Harness /></AuthProvider>);
  const button = screen.getByRole("button", { name: "登录" });
  await waitFor(() => expect(button).toBeEnabled());
  fireEvent.change(screen.getByLabelText("用户名"), { target: { value: "demo-a" } });
  fireEvent.change(screen.getByLabelText("密码"), { target: { value: "wrong" } });
  fireEvent.click(button);
  expect(await screen.findByRole("alert")).toHaveTextContent("账号或密码错误");
  expect(posts).toHaveLength(1);
});

test("cross-tab notification invalidates synchronously and focus rechecks shared Cookie account", async () => {
  current = a;
  render(<AuthProvider><Harness /></AuthProvider>);
  await screen.findByText("演示用户 A（demo-a）");
  const before = screen.getByLabelText("身份作用域").textContent;
  current = b;
  act(() => window.dispatchEvent(new StorageEvent("storage", { key: AUTH_CHANGE_KEY, newValue: "random-event" })));
  expect(screen.queryByText("演示用户 A（demo-a）")).not.toBeInTheDocument();
  await screen.findByText("演示用户 B（demo-b）");
  expect(screen.getByLabelText("身份作用域").textContent).not.toBe(before);
  current = null;
  act(() => window.dispatchEvent(new Event("focus")));
  await waitFor(() => expect(screen.getByRole("button", { name: "登录" })).toBeEnabled());
  expect(posts).toHaveLength(0);
});

test("focus 503 preserves the account and recovers on the next focus without authentication writes", async () => {
  current = a;
  render(<AuthProvider><Harness /></AuthProvider>);
  await screen.findByText("演示用户 A（demo-a）");
  failure = 503;
  act(() => window.dispatchEvent(new Event("focus")));
  expect(await screen.findByRole("alert")).toHaveTextContent("会话核对暂不可用");
  expect(screen.getByText("演示用户 A（demo-a）")).toBeVisible();
  expect(screen.getByRole("button", { name: "登录" })).toBeDisabled();
  expect(posts).toHaveLength(0);
  failure = null; act(() => window.dispatchEvent(new Event("focus")));
  await waitFor(() => expect(screen.queryByRole("alert")).not.toBeInTheDocument());
});

test("Strict Mode mount/cleanup only reads; subscriptions are removed on unmount", async () => {
  const rendered = render(<StrictMode><AuthProvider><Harness /></AuthProvider></StrictMode>);
  await waitFor(() => expect(screen.getByRole("button", { name: "登录" })).toBeEnabled());
  expect(posts).toHaveLength(0);
  rendered.unmount();
  const count = vi.mocked(fetch).mock.calls.length;
  act(() => { window.dispatchEvent(new Event("focus")); window.dispatchEvent(new StorageEvent("storage", { key: AUTH_CHANGE_KEY })); });
  expect(vi.mocked(fetch).mock.calls).toHaveLength(count);
});
