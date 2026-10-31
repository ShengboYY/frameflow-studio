"use client";

import Link from "next/link";
import { useAuth } from "./auth-provider";

export function AuthNavigation() {
  const { status, account, busy, message, session } = useAuth();
  return (
    <header className="site-header">
      <Link href="/" className="brand">FrameFlow Studio</Link>
      <nav aria-label="账号导航">
        <Link href="/templates">模板</Link>
        {status === "authenticated" && account ? <>
          <span>{account.displayName}（{account.username}）</span>
          <button type="button" disabled={busy} onClick={() => { void session.logout(); }}>退出登录</button>
        </> : status === "anonymous" ? <Link href="/login">登录</Link>
          : <span role="status">{busy ? "正在处理登录状态…" : status === "checking" ? "正在核对会话…" : "会话查询暂不可用"}</span>}
        {status === "error" && <button type="button" disabled={busy} onClick={() => { void session.refresh(); }}>重新核对会话</button>}
      </nav>
      {message && <p className="auth-message" role="alert">{message}</p>}
    </header>
  );
}
