import { ApiError } from "@/lib/api-client";
import { authApi, type AccountView, type CsrfView } from "./auth-api";

export const AUTH_CHANGE_KEY = "frameflow.auth-change";
const RECHECK_FAILED = "会话核对暂不可用，请稍后重试";
export type AuthState = {
  status: "checking" | "authenticated" | "anonymous" | "error";
  account: AccountView | null;
  identityEpoch: number;
  busy: boolean;
  message: string | null;
};
export type WriteContext = Readonly<{ ownerId: string; identityEpoch: number; csrf: CsrfView }>;
const initialState: AuthState = { status: "checking", account: null, identityEpoch: 0, busy: false, message: null };

/** A provider-owned session scope: synchronous invalidation precedes React cleanup. */
export class AuthSession {
  private state = initialState;
  private csrf: CsrfView | null = null;
  private active = false;
  private writeGate = false;
  private pendingCheck = false;
  private read: AbortController | null = null;
  private listeners = new Set<() => void>();

  getSnapshot = () => this.state;
  getServerSnapshot = () => initialState;
  subscribe = (listener: () => void) => {
    this.listeners.add(listener);
    return () => { this.listeners.delete(listener); };
  };

  private update(patch: Partial<AuthState>) {
    this.state = { ...this.state, ...patch };
    this.listeners.forEach((listener) => listener());
  }

  private invalidate(clearAccount: boolean) {
    this.csrf = null;
    const previous = this.read;
    this.read = null;
    this.update({
      identityEpoch: this.state.identityEpoch + 1,
      status: "checking", account: clearAccount ? null : this.state.account,
    });
    previous?.abort();
    return this.state.identityEpoch;
  }

  start = () => {
    this.active = true;
    this.update({ busy: this.writeGate });
    void this.refresh();
  };
  stop = () => { this.active = false; this.invalidate(true); };

  private current(epoch: number) { return this.active && this.state.identityEpoch === epoch; }

  refresh = async (clearAccount = false, message: string | null = null) => {
    if (!this.active) return;
    const epoch = this.invalidate(clearAccount);
    this.update({ message });
    if (this.writeGate) { this.pendingCheck = true; return; }
    await this.check(epoch);
  };

  recheck = async () => {
    if (!this.active || this.writeGate || this.state.status === "checking") return;
    if (this.state.status === "error") { await this.refresh(); return; }
    const epoch = this.state.identityEpoch;
    const previous = this.read;
    const read = new AbortController();
    this.read = read;
    previous?.abort();
    const current = () => this.current(epoch) && this.read === read;
    try {
      const account = await authApi.session(read.signal);
      if (!current()) return;
      // Focus alone does not invalidate forms or replace their captured CSRF token.
      if (account?.ownerId !== this.state.account?.ownerId) await this.refresh(true);
      else if (this.state.message === RECHECK_FAILED) this.update({ message: null });
    } catch {
      if (current()) this.update({ message: RECHECK_FAILED });
    } finally {
      if (current()) this.read = null;
    }
  };

  private async check(epoch: number) {
    const read = new AbortController();
    this.read = read;
    try {
      const account = await authApi.session(read.signal);
      if (!this.current(epoch)) return;
      const csrf = await authApi.csrf(read.signal);
      if (!this.current(epoch)) return;
      // Cookie can change in another tab while CSRF is being read. Verify before publishing it.
      const confirmed = await authApi.session(read.signal);
      if (!this.current(epoch)) return;
      if (account?.ownerId !== confirmed?.ownerId) {
        this.update({ account: null });
        throw new ApiError("登录账号已变化，请重新核对会话", 409, "SESSION_CHANGED");
      }
      this.csrf = csrf;
      this.update({ account: confirmed, status: confirmed ? "authenticated" : "anonymous" });
    } catch (cause) {
      if (!this.current(epoch)) return;
      this.update({ status: "error", message: cause instanceof ApiError ? cause.message : "会话查询暂不可用，请重试" });
    } finally {
      if (this.current(epoch) && this.read === read) this.read = null;
    }
  }

  captureWriteContext = (): WriteContext | null => {
    if (!this.active || this.state.status !== "authenticated" || !this.state.account || !this.csrf || this.writeGate) return null;
    return Object.freeze({ ownerId: this.state.account.ownerId, identityEpoch: this.state.identityEpoch, csrf: Object.freeze({ ...this.csrf }) });
  };

  isCurrent = (context: Pick<WriteContext, "ownerId" | "identityEpoch">): boolean => {
    return this.current(context.identityEpoch) && this.state.status === "authenticated"
      && this.state.account?.ownerId === context.ownerId;
  };

  handleRequestError = async (cause: unknown, identityEpoch: number) => {
    if (!this.current(identityEpoch) || !(cause instanceof ApiError)) return;
    if (cause.status === 401) await this.refresh(true, "登录已失效，请重新登录");
    if (cause.status === 403) await this.refresh(true, "会话保护已变化，请核对账号和表单后重新提交");
  };

  private notifyOtherTabs() {
    try { localStorage.setItem(AUTH_CHANGE_KEY, crypto.randomUUID()); }
    catch { /* Focus revalidation also works when browser storage is unavailable. */ }
  }

  login = (username: string, password: string) => this.write("login", username, password);
  logout = () => this.write("logout");

  private async write(kind: "login" | "logout", username = "", password = ""): Promise<boolean> {
    if (!this.active || this.writeGate || !this.csrf
        || this.state.status !== (kind === "login" ? "anonymous" : "authenticated")) return false;
    const csrf = this.csrf;
    this.writeGate = true;
    this.invalidate(true);
    this.update({ busy: true, message: null });
    let message: string | null = null;
    let succeeded = false;
    try {
      if (kind === "login") await authApi.login(username, password, csrf);
      else await authApi.logout(csrf);
      succeeded = true;
    } catch (cause) {
      message = cause instanceof ApiError && cause.status === 401 && kind === "login" ? "账号或密码错误"
        : cause instanceof ApiError && cause.status === 403 ? "会话保护已变化，请核对账号和表单后重新提交"
        : cause instanceof ApiError && cause.status === 409 ? "请先退出当前账号"
        : cause instanceof ApiError && cause.status >= 400 && cause.status < 500 ? cause.message
        : "操作结果暂未确认，请核对当前会话后再操作";
    } finally {
      // Even an uncertain write may have changed the HttpOnly Cookie; never replay it.
      this.notifyOtherTabs();
      while (this.active) {
        this.pendingCheck = false;
        const epoch = this.invalidate(true);
        this.update({ message });
        await this.check(epoch);
        if (!this.pendingCheck) break;
      }
      this.writeGate = false;
      if (this.active) this.update({ busy: false });
    }
    return succeeded;
  }
}
