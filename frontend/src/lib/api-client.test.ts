// @vitest-environment node
import { afterEach, expect, test, vi } from "vitest";
import { ApiError, apiRequest, CLIENT_TIMEOUT_MS, jsonBody } from "./api-client";

afterEach(() => { vi.unstubAllGlobals(); vi.useRealTimers(); });

test("uses same-origin Cookie, no-store and one attempt for a POST", async () => {
  const fetcher = vi.fn().mockResolvedValue(Response.json({ ok: true }));
  vi.stubGlobal("fetch", fetcher);
  expect(await apiRequest("/api/v1/tasks", { method: "POST", body: "{}" }, jsonBody)).toEqual({ ok: true });
  expect(fetcher).toHaveBeenCalledTimes(1);
  expect(fetcher.mock.calls[0][1]).toMatchObject({ credentials: "same-origin", cache: "no-store", redirect: "error", method: "POST" });
});

test.each([401, 403, 503])("retains structured %i status and diagnostic ID", async (status) => {
  const taskId = "82dd291e-4673-4500-8fe3-97a71a6cbe40";
  const details = [{ field: "assetIds", code: "COUNT_MISMATCH", message: "需要一张图片" }];
  vi.stubGlobal("fetch", vi.fn().mockResolvedValue(Response.json({ error: { code: "TEST_ERROR", message: "请求异常", details, taskId }, requestId: "test-id" }, { status })));
  await expect(apiRequest("/api/v1/tasks", {}, jsonBody)).rejects.toMatchObject({ status, code: "TEST_ERROR", requestId: "test-id", details, taskId });
});

test("treats HTML, malformed JSON, and network failures as request errors", async () => {
  for (const response of [new Response("<html>error</html>", { status: 503 }), new Response("{", { headers: { "Content-Type": "application/json" } })]) {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(response));
    await expect(apiRequest("/api/v1/tasks", {}, jsonBody)).rejects.toBeInstanceOf(ApiError);
  }
  const fetcher = vi.fn().mockRejectedValue(new TypeError("network"));
  vi.stubGlobal("fetch", fetcher);
  await expect(apiRequest("/api/v1/tasks", { method: "POST" }, jsonBody)).rejects.toMatchObject({ code: "REQUEST_FAILED" });
  expect(fetcher).toHaveBeenCalledTimes(1);
});

test("times out at 30 seconds even if fetch ignores abort, and never replays the write", async () => {
  vi.useFakeTimers();
  const fetcher = vi.fn(() => new Promise<Response>(() => {}));
  vi.stubGlobal("fetch", fetcher);
  const pending = apiRequest("/api/v1/tasks", { method: "POST" }, jsonBody);
  const assertion = expect(pending).rejects.toMatchObject({ code: "REQUEST_TIMEOUT" });
  await vi.advanceTimersByTimeAsync(CLIENT_TIMEOUT_MS);
  await assertion;
  expect(fetcher).toHaveBeenCalledTimes(1);
});

test("includes body consumption in the request budget", async () => {
  vi.useFakeTimers();
  vi.stubGlobal("fetch", vi.fn().mockResolvedValue(Response.json({ ok: true })));
  const pending = apiRequest("/api/v1/tasks", {}, async () => new Promise<void>(() => {}));
  const assertion = expect(pending).rejects.toMatchObject({ code: "REQUEST_TIMEOUT" });
  await vi.advanceTimersByTimeAsync(CLIENT_TIMEOUT_MS);
  await assertion;
});

test("context cleanup aborts and settles even if the transport ignores cancellation", async () => {
  vi.stubGlobal("fetch", vi.fn(() => new Promise<Response>(() => {})));
  const scope = new AbortController();
  const pending = apiRequest("/api/v1/tasks", { signal: scope.signal }, jsonBody);
  scope.abort();
  await expect(pending).rejects.toMatchObject({ name: "AbortError" });
});
