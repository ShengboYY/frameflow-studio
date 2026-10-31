// @vitest-environment node
import { createServer, type Server, type IncomingMessage, type ServerResponse } from "node:http";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { GET, POST, DELETE } from "@/app/api/v1/[...path]/route";
import { forwardToJava, UPSTREAM_HEADER_TIMEOUT_MS } from "./java-proxy";

let server: Server;
let origin: string;
let received: { path?: string; method?: string; headers: IncomingMessage["headers"]; body: Buffer }[];
let respond: (request: IncomingMessage, response: ServerResponse, body: Buffer) => void;

beforeEach(async () => {
  received = [];
  respond = (_, response) => { response.setHeader("Content-Type", "application/json"); response.end('{"items":[]}'); };
  server = createServer(async (request, response) => {
    const chunks: Buffer[] = [];
    for await (const chunk of request) chunks.push(Buffer.from(chunk));
    const body = Buffer.concat(chunks);
    received.push({ path: request.url, method: request.method, headers: request.headers, body });
    respond(request, response, body);
  });
  await new Promise<void>((resolve, reject) => {
    server.once("error", reject);
    server.listen(0, "127.0.0.1", resolve);
  });
  const address = server.address();
  if (!address || typeof address === "string") throw new Error("Expected TCP address");
  origin = `http://127.0.0.1:${address.port}`;
  vi.stubEnv("JAVA_API_ORIGIN", origin);
});
afterEach(async () => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
  server.closeAllConnections();
  await new Promise<void>((resolve) => server.close(() => resolve()));
});

const id = "82dd291e-4673-4500-8fe3-97a71a6cbe40";
const cases = [
  ["POST", "auth/login"], ["GET", "auth/session"], ["GET", "templates/categories"],
  ["GET", "templates?categoryId=travel"], ["GET", "templates/portrait-card"],
  ["GET", "demo-media/portrait-cover"], ["POST", "assets"], ["GET", `assets/${id}/content`],
  ["POST", "tasks"], ["GET", "tasks"], ["GET", `tasks/${id}`],
  ["GET", `tasks/${id}/result`], ["POST", "auth/logout"], ["GET", "auth/csrf"],
];

describe("Node Route Handler with a real HTTP upstream", () => {
  test.each(cases)("forwards %s %s", async (method, path) => {
    const response = await (method === "GET" ? GET : POST)(new Request(`http://frontend/api/v1/${path}`, { method }));
    expect(response.status).toBe(200);
    expect(await response.json()).toEqual({ items: [] });
    expect(response.headers.get("cache-control")).toBe("no-store");
    expect(received).toHaveLength(1);
    expect(received[0]).toMatchObject({ path: `/api/v1/${path}`, method });
  });

  test("preserves form bytes, Cookie/CSRF, status, request ID and individual Set-Cookie attributes", async () => {
    const cookies = [
      "FF_SESSION=fake-a; Path=/api; Max-Age=28800; HttpOnly; SameSite=Lax; Secure",
      "extra=fake-b; Expires=Wed, 21 Oct 2037 07:28:00 GMT; Path=/api; HttpOnly",
    ];
    respond = (_, response) => {
      response.writeHead(200, { "Content-Type": "application/json", "Set-Cookie": cookies, "X-Request-Id": "test-id" });
      response.end('{"username":"demo-a"}');
    };
    const body = "username=demo-a&password=%E6%B5%8B%E8%AF%95%2B%26";
    const response = await POST(new Request("http://frontend/api/v1/auth/login", {
      method: "POST", body,
      headers: { Cookie: "FF_SESSION=fake-before", "X-CSRF-TOKEN": "fake-token", "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8", Authorization: "Bearer must-not-pass", "X-Forwarded-Host": "evil.example" },
    }));
    expect(await response.json()).toEqual({ username: "demo-a" });
    expect(response.headers.getSetCookie()).toEqual(cookies);
    expect(response.headers.get("x-request-id")).toBe("test-id");
    expect(received[0].body.toString()).toBe(body);
    expect(received[0].headers).toMatchObject({ cookie: "FF_SESSION=fake-before", "x-csrf-token": "fake-token", "content-type": "application/x-www-form-urlencoded;charset=UTF-8" });
    expect(received[0].headers.authorization).toBeUndefined();
    expect(received[0].headers["x-forwarded-host"]).toBeUndefined();
  });

  test("preserves multipart boundary and binary bytes, including a legal 8 MiB file", async () => {
    const boundary = "frameflow-test-boundary";
    const body = Buffer.concat([
      Buffer.from(`--${boundary}\r\nContent-Disposition: form-data; name="templateId"\r\n\r\ntravel-postcard\r\n--${boundary}\r\nContent-Disposition: form-data; name="file"; filename="input.png"\r\nContent-Type: image/png\r\n\r\n`),
      Buffer.alloc(8 * 1024 * 1024, 0x91), Buffer.from(`\r\n--${boundary}--\r\n`),
    ]);
    respond = (_, response) => { response.writeHead(201, { "Content-Type": "application/json" }); response.end('{"assetId":"test-asset"}'); };
    const response = await POST(new Request("http://frontend/api/v1/assets", {
      method: "POST", headers: { "Content-Type": `multipart/form-data; boundary=${boundary}` }, body,
    }));
    expect(response.status).toBe(201);
    await response.text();
    expect(received[0].body.equals(body)).toBe(true);
    expect(received[0].headers["content-type"]).toBe(`multipart/form-data; boundary=${boundary}`);
  });

  test("preserves JSON/202/relative Location, 204 cookie clearing and image bytes", async () => {
    const taskBody = JSON.stringify({ templateId: "portrait-card", assetIds: [id] });
    respond = (_, response) => { response.writeHead(202, { "Content-Type": "application/json", Location: `/api/v1/tasks/${id}` }); response.end(taskBody); };
    const accepted = await POST(new Request("http://frontend/api/v1/tasks", { method: "POST", headers: { "Content-Type": "application/json" }, body: taskBody }));
    expect(accepted.status).toBe(202);
    expect(accepted.headers.get("location")).toBe(`/api/v1/tasks/${id}`);
    expect(await accepted.text()).toBe(taskBody);
    expect(received[0].body.toString()).toBe(taskBody);
    respond = (_, response) => { response.writeHead(204, { "Set-Cookie": "FF_SESSION=; Max-Age=0; Path=/api; HttpOnly; SameSite=Lax" }); response.end(); };
    const logout = await POST(new Request("http://frontend/api/v1/auth/logout", { method: "POST" }));
    expect(logout.status).toBe(204);
    expect(await logout.text()).toBe("");
    expect(logout.headers.getSetCookie()[0]).toContain("Max-Age=0");
    const image = Buffer.from([0xff, 0xd8, 0x00, 0x80, 0xff, 0xd9]);
    respond = (_, response) => { response.writeHead(200, { "Content-Type": "image/jpeg", "X-Content-Type-Options": "nosniff" }); response.end(image); };
    const result = await GET(new Request(`http://frontend/api/v1/tasks/${id}/result`));
    expect(result.headers.get("content-type")).toBe("image/jpeg");
    expect(result.headers.get("x-content-type-options")).toBe("nosniff");
    expect(Buffer.from(await result.arrayBuffer()).equals(image)).toBe(true);
  });

  test.each([400, 401, 403, 404, 409, 413, 415, 422, 500, 503])("preserves Java %i error responses", async (status) => {
    const failure = { error: { code: "TEST_ERROR", message: "测试错误", details: [] }, requestId: "test-id" };
    respond = (_, response) => { response.writeHead(status, { "Content-Type": "application/json" }); response.end(JSON.stringify(failure)); };
    const response = await GET(new Request("http://frontend/api/v1/tasks", { headers: { Accept: "image/jpeg" } }));
    expect(response.status).toBe(status);
    expect(await response.json()).toEqual(failure);
  });

  test.each(["ftp://localhost", "file:///private/tmp", "http://user:pass@localhost", "http://localhost/path", "http://localhost?target=x", ""]) ("rejects invalid server origin %s", async (value) => {
    vi.stubEnv("JAVA_API_ORIGIN", value);
    const response = await GET(new Request("http://frontend/api/v1/templates"));
    expect(response.status).toBe(503);
    expect(received).toHaveLength(0);
    expect(await response.text()).not.toContain("Invalid origin");
  });
});

test.each(["/api/v1/admin", "/api/v1/tasks/not-a-uuid", "/api/v1/templates/%2Fsecret", "/api/v1/templates/%252e%252e", "/api/v1//evil.example", "/other"]) ("rejects paths outside the API allowlist: %s", async (path) => {
  const response = await GET(new Request(`http://frontend${path}`));
  expect([400, 404]).toContain(response.status);
  expect(received).toHaveLength(0);
});

test("rejects unsupported methods without contacting Java", async () => {
  const response = await DELETE(new Request("http://frontend/api/v1/tasks", { method: "DELETE" }));
  expect(response.status).toBe(405);
  expect(response.headers.get("allow")).toBe("GET, POST");
  expect(received).toHaveLength(0);
});

test.each(["tasks/not-a-uuid", "templates/UPPERCASE", `assets/${id.toUpperCase()}/content`])("returns the contracted 400 for malformed IDs: %s", async (path) => {
  expect((await GET(new Request(`http://frontend/api/v1/${path}`))).status).toBe(400);
  expect(received).toHaveLength(0);
});

test("enforces byte limits even without a Content-Length header", async () => {
  const response = await POST(new Request("http://frontend/api/v1/tasks", { method: "POST", body: "a".repeat(64 * 1024 + 1) }));
  expect(response.status).toBe(413);
  expect(received).toHaveLength(0);
});

test("maps connection failures once and does not follow external redirects", async () => {
  respond = (_, response) => { response.writeHead(302, { Location: "https://evil.example" }); response.end(); };
  const response = await GET(new Request("http://frontend/api/v1/auth/csrf"));
  expect(response.status).toBe(503);
  expect(received).toHaveLength(1);
  const fetcher = vi.fn().mockRejectedValue(new TypeError("Internal secret URL"));
  vi.stubGlobal("fetch", fetcher);
  const unavailable = await POST(new Request("http://frontend/api/v1/tasks", { method: "POST" }));
  expect(unavailable.status).toBe(503);
  const failure = await unavailable.json();
  expect(failure.error.code).toBe("UPSTREAM_UNAVAILABLE");
  expect(failure.requestId).toBe(unavailable.headers.get("x-request-id"));
  expect(JSON.stringify(failure)).not.toContain("secret");
  expect(fetcher).toHaveBeenCalledTimes(1);
});

test("aborts at the 60 second response-header budget without replaying POST", async () => {
  vi.useFakeTimers();
  const fetcher = vi.fn((_, init: RequestInit) => new Promise<Response>((_, reject) => {
    init.signal?.addEventListener("abort", () => reject(new DOMException("aborted", "AbortError")));
  }));
  vi.stubGlobal("fetch", fetcher);
  const pending = forwardToJava(new Request("http://frontend/api/v1/tasks", { method: "POST" }));
  await vi.advanceTimersByTimeAsync(UPSTREAM_HEADER_TIMEOUT_MS - 1);
  expect(fetcher.mock.calls[0][1].signal?.aborted).toBe(false);
  await vi.advanceTimersByTimeAsync(1);
  const response = await pending;
  expect(response.status).toBe(504);
  expect((await response.json()).error.code).toBe("UPSTREAM_TIMEOUT");
  expect(fetcher).toHaveBeenCalledTimes(1);
});
