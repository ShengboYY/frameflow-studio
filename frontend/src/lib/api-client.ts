export const CLIENT_TIMEOUT_MS = 30_000;

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly code: string,
    readonly requestId?: string,
    readonly details: readonly unknown[] = [],
    readonly taskId?: string,
  ) { super(message); this.name = "ApiError"; }
}

export function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

async function responseError(response: Response): Promise<ApiError> {
  try {
    const value: unknown = await response.json();
    if (isRecord(value) && isRecord(value.error) && typeof value.error.code === "string"
        && typeof value.error.message === "string" && Array.isArray(value.error.details)
        && typeof value.requestId === "string") {
      const taskId = typeof value.error.taskId === "string"
        && /^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/.test(value.error.taskId) ? value.error.taskId : undefined;
      return new ApiError(value.error.message, response.status, value.error.code, value.requestId, value.error.details, taskId);
    }
  } catch { /* Non-JSON and truncated error bodies are request failures too. */ }
  return new ApiError("服务响应无法识别，请稍后重试", response.status, "INVALID_RESPONSE");
}

/** One attempt only, including writes. The budget includes consuming the response body. */
export async function apiRequest<T>(
  path: string,
  init: RequestInit,
  decode: (response: Response) => Promise<T>,
): Promise<T> {
  if (!path.startsWith("/api/v1/") || path.includes("\\") || path.includes("#")) {
    throw new ApiError("请求路径无效", 0, "INVALID_REQUEST");
  }
  const controller = new AbortController();
  let timer: ReturnType<typeof setTimeout> | undefined;
  let cancel: () => void = () => {};
  const interrupted = new Promise<never>((_, reject) => {
    cancel = () => { controller.abort(); reject(new DOMException("Request cancelled", "AbortError")); };
    timer = setTimeout(() => {
      controller.abort();
      reject(new ApiError("请求超时，写入结果可能尚未确认", 0, "REQUEST_TIMEOUT"));
    }, CLIENT_TIMEOUT_MS);
    init.signal?.addEventListener("abort", cancel, { once: true });
    if (init.signal?.aborted) cancel();
  });
  try {
    return await Promise.race([
      interrupted,
      (async () => {
        const response = await fetch(path, {
          ...init, credentials: "same-origin", cache: "no-store", redirect: "error", signal: controller.signal,
        });
        if (!response.ok) throw await responseError(response);
        return await decode(response);
      })(),
    ]);
  } catch (cause) {
    if (cause instanceof ApiError || (cause instanceof DOMException && cause.name === "AbortError")) throw cause;
    throw new ApiError("网络或服务响应异常，请稍后重试", 0, "REQUEST_FAILED");
  } finally {
    clearTimeout(timer);
    init.signal?.removeEventListener("abort", cancel);
  }
}

export async function jsonBody(response: Response): Promise<unknown> {
  if (response.headers.get("content-type")?.split(";")[0].trim().toLowerCase() !== "application/json") {
    throw new ApiError("服务响应无法识别", response.status, "INVALID_RESPONSE");
  }
  return response.json();
}
