import { randomUUID } from "node:crypto";

const PREFIX = "/api/v1/";
export const UPSTREAM_HEADER_TIMEOUT_MS = 60_000;
const slug = "[a-z0-9][a-z0-9-]{0,63}";
const uuid = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
const endpoints = [
  { path: /^auth\/(login|logout)$/, methods: ["POST"] },
  { path: /^auth\/(session|csrf)$/, methods: ["GET"] },
  { path: /^templates$/, methods: ["GET"] },
  { path: new RegExp(`^templates/${slug}$`), methods: ["GET"] },
  { path: new RegExp(`^demo-media/${slug}$`), methods: ["GET"] },
  { path: /^assets$/, methods: ["POST"] },
  { path: new RegExp(`^assets/${uuid}/content$`), methods: ["GET"] },
  { path: /^tasks$/, methods: ["GET", "POST"] },
  { path: new RegExp(`^tasks/${uuid}(/result)?$`), methods: ["GET"] },
];

function error(status: number, code: string, message: string, allow?: string[]) {
  const requestId = randomUUID();
  return Response.json(
    { error: { code, message, details: [] }, requestId },
    { status, headers: {
      "Cache-Control": "no-store", "X-Request-Id": requestId,
      "X-Content-Type-Options": "nosniff",
      ...(allow ? { Allow: allow.join(", ") } : {}),
    } },
  );
}

function javaOrigin() {
  const value = process.env.JAVA_API_ORIGIN;
  if (!value || value.trim() !== value) throw new Error("Invalid origin");
  const url = new URL(value);
  if (!(["http:", "https:"].includes(url.protocol)) || url.username || url.password
      || url.pathname !== "/" || url.search || url.hash) throw new Error("Invalid origin");
  return url.origin;
}

// Keep the original multipart bytes/boundary. Bound buffering before any upstream write.
async function readBody(request: Request, limit: number): Promise<Uint8Array | undefined> {
  if (!request.body) return undefined;
  const reader = request.body.getReader();
  const chunks: Uint8Array[] = [];
  let length = 0;
  try {
    while (true) {
      const { value, done } = await reader.read();
      if (done) break;
      length += value.byteLength;
      if (length > limit) {
        await reader.cancel();
        throw new RangeError("Payload too large");
      }
      chunks.push(value);
    }
  } finally {
    reader.releaseLock();
  }
  if (length === 0) return undefined;
  const body = new Uint8Array(length);
  let offset = 0;
  for (const chunk of chunks) { body.set(chunk, offset); offset += chunk.byteLength; }
  return body;
}

export async function forwardToJava(request: Request): Promise<Response> {
  const incoming = new URL(request.url);
  const path = incoming.pathname.slice(PREFIX.length);
  if (!incoming.pathname.startsWith(PREFIX) || /[%\\]/.test(path)) {
    return error(400, "INVALID_REQUEST", "请求路径无效");
  }
  const endpoint = endpoints.find((candidate) => candidate.path.test(path));
  if (!endpoint) {
    const malformedId = /^(templates|demo-media)\/[^/]+$/.test(path)
      || /^assets\/[^/]+\/content$/.test(path) || /^tasks\/[^/]+(\/result)?$/.test(path);
    return malformedId ? error(400, "INVALID_REQUEST", "资源标识无效")
      : error(404, "RESOURCE_NOT_FOUND", "资源不存在");
  }
  if (!endpoint.methods.includes(request.method)) {
    return error(405, "METHOD_NOT_ALLOWED", "请求方法不支持", endpoint.methods);
  }

  let target: URL;
  try { target = new URL(incoming.pathname + incoming.search, javaOrigin()); }
  catch { return error(503, "UPSTREAM_UNAVAILABLE", "服务暂不可用"); }

  const headers = new Headers();
  for (const name of ["cookie", "x-csrf-token", "accept", "content-type"]) {
    const value = request.headers.get(name);
    if (value !== null) headers.set(name, value);
  }

  let body: Uint8Array | undefined;
  try {
    if (request.method === "POST") {
      const limit = path === "assets" ? 9 * 1024 * 1024
        : path === "auth/login" ? 4096 : path === "auth/logout" ? 0 : 64 * 1024;
      body = await readBody(request, limit);
    }
  } catch (cause) {
    if (cause instanceof RangeError) {
      return path === "auth/logout"
        ? error(400, "INVALID_REQUEST", "退出请求不能包含请求体")
        : error(413, "PAYLOAD_TOO_LARGE", "请求过大");
    }
    return error(400, "INVALID_REQUEST", "请求内容无法读取");
  }

  const controller = new AbortController();
  const cancel = () => controller.abort();
  request.signal.addEventListener("abort", cancel, { once: true });
  if (request.signal.aborted) cancel();
  let timedOut = false;
  const timer = setTimeout(() => { timedOut = true; controller.abort(); }, UPSTREAM_HEADER_TIMEOUT_MS);
  try {
    const upstream = await fetch(target, {
      method: request.method, headers, body: body as BodyInit | undefined,
      cache: "no-store", redirect: "manual", signal: controller.signal,
    });
    if (upstream.status >= 300 && upstream.status < 400) {
      await upstream.body?.cancel();
      return error(503, "UPSTREAM_UNAVAILABLE", "服务响应异常");
    }
    const responseHeaders = new Headers({ "Cache-Control": "no-store" });
    for (const name of ["content-type", "location", "x-content-type-options", "x-request-id"]) {
      const value = upstream.headers.get(name);
      if (value !== null) responseHeaders.set(name, value);
    }
    for (const cookie of upstream.headers.getSetCookie()) responseHeaders.append("Set-Cookie", cookie);
    // Fetch decodes content encoding; forwarding its old length/encoding corrupts responses.
    return new Response(upstream.body, { status: upstream.status, headers: responseHeaders });
  } catch {
    return timedOut
      ? error(504, "UPSTREAM_TIMEOUT", "服务响应超时，写入结果可能尚未确认")
      : error(503, "UPSTREAM_UNAVAILABLE", "服务暂不可用");
  } finally {
    clearTimeout(timer);
    request.signal.removeEventListener("abort", cancel);
  }
}
