"use client";

import Link from "next/link";
import { useRef } from "react";
import { useAuth } from "./auth-provider";

function AccountDetails() {
  const { account } = useAuth();
  const dialog = useRef<HTMLDialogElement>(null);
  if (!account) return null;
  return <>
    <button className="account-button" type="button" aria-haspopup="dialog" onClick={() => dialog.current?.showModal()}><span className="avatar" aria-hidden="true">{account.displayName.slice(0, 1)}</span><span>{account.displayName}（{account.username}）</span></button>
    <dialog className="account-dialog" ref={dialog} aria-labelledby="account-title">
      <div className="account-dialog-heading"><div><p className="eyebrow">YOUR ACCOUNT</p><h2 id="account-title">账户信息</h2></div><button className="close-button" type="button" aria-label="关闭账户信息" onClick={() => dialog.current?.close()}>×</button></div>
      <div className="account-profile"><span className="avatar" aria-hidden="true">{account.displayName.slice(0, 1)}</span><div><h3>{account.displayName}</h3><p>@{account.username}</p></div></div>
      <dl className="account-fields"><div><dt>用户名</dt><dd>{account.username}</dd></div><div><dt>账户标识</dt><dd>{account.ownerId}</dd></div></dl>
      <Link className="account-history-link" href="/history" onClick={() => dialog.current?.close()}>我的制作记录 <span aria-hidden="true">↗</span></Link>
    </dialog>
  </>;
}

export function AuthNavigation() {
  const { status, account, busy, message, session, identityEpoch } = useAuth();
  return (
    <header className="site-header">
      <Link href="/" className="brand"><span className="brand-mark" aria-hidden="true">f<span /></span><span>FrameFlow<span className="brand-studio"> Studio</span></span></Link>
      <nav aria-label="账号导航">
        <Link href="/templates">模板</Link>
        {status === "authenticated" && account ? <>
          <Link href="/history">我的记录</Link>
          <AccountDetails key={identityEpoch} />
          <button className="logout-button" type="button" disabled={busy} onClick={() => { void session.logout(); }}>退出登录</button>
        </> : status === "anonymous" ? <Link href="/login">登录</Link>
          : <span role="status">{busy ? "正在处理登录状态…" : status === "checking" ? "正在核对会话…" : "会话查询暂不可用"}</span>}
        {status === "error" && <button type="button" disabled={busy} onClick={() => { void session.refresh(); }}>重新核对会话</button>}
      </nav>
      {message && <p className="auth-message" role="alert">{message}</p>}
    </header>
  );
}
