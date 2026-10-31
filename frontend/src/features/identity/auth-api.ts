import { ApiError, apiRequest, isRecord, jsonBody } from "@/lib/api-client";

export type AccountView = { ownerId: string; username: string; displayName: string };
export type CsrfView = { token: string; headerName: "X-CSRF-TOKEN" };

function account(value: unknown): AccountView {
  if (!isRecord(value) || typeof value.ownerId !== "string"
      || !/^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/.test(value.ownerId)
      || typeof value.username !== "string" || !value.username
      || typeof value.displayName !== "string" || !value.displayName) {
    throw new ApiError("账号响应无法识别", 200, "INVALID_RESPONSE");
  }
  return { ownerId: value.ownerId, username: value.username, displayName: value.displayName };
}

export const authApi = {
  async session(signal: AbortSignal): Promise<AccountView | null> {
    try { return account(await apiRequest("/api/v1/auth/session", { signal }, jsonBody)); }
    catch (cause) {
      if (cause instanceof ApiError && cause.status === 401) return null;
      throw cause;
    }
  },
  async csrf(signal: AbortSignal): Promise<CsrfView> {
    const value = await apiRequest("/api/v1/auth/csrf", { signal }, jsonBody);
    if (!isRecord(value) || value.headerName !== "X-CSRF-TOKEN" || typeof value.token !== "string" || !value.token) {
      throw new ApiError("会话保护响应无法识别", 200, "INVALID_RESPONSE");
    }
    return { headerName: value.headerName, token: value.token };
  },
  async login(username: string, password: string, csrf: CsrfView): Promise<AccountView> {
    return account(await apiRequest("/api/v1/auth/login", {
      method: "POST", headers: { [csrf.headerName]: csrf.token, "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8" },
      body: new URLSearchParams({ username, password }),
    }, jsonBody));
  },
  async logout(csrf: CsrfView): Promise<void> {
    await apiRequest("/api/v1/auth/logout", {
      method: "POST", headers: { [csrf.headerName]: csrf.token },
    }, async (response) => {
      if (response.status !== 204) throw new ApiError("退出响应无法识别", response.status, "INVALID_RESPONSE");
    });
  },
};
