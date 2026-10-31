"use client";

import { useEffect, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "./auth-provider";

export function LoginForm() {
  const { status, busy, session } = useAuth();
  const router = useRouter();
  useEffect(() => {
    if (status === "authenticated") router.replace("/history");
  }, [status, router]);

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const values = new FormData(form);
    const password = String(values.get("password") ?? "");
    const username = String(values.get("username") ?? "");
    const passwordInput = form.elements.namedItem("password");
    if (passwordInput instanceof HTMLInputElement) passwordInput.value = "";
    void session.login(username, password);
  }

  return (
    <main className="login-page">
      <section className="login-panel" aria-labelledby="login-title">
        <p className="eyebrow">FRAMEFLOW STUDIO</p>
        <h1 id="login-title">登录</h1>
        <p>使用已配置的演示账号登录 FrameFlow Studio。</p>
        <form onSubmit={submit} aria-busy={busy}>
          <fieldset disabled={busy || status !== "anonymous"}>
            <legend className="visually-hidden">账号登录</legend>
            <label htmlFor="username">用户名</label>
            <input id="username" name="username" autoComplete="username" required minLength={3} maxLength={32} autoCapitalize="none" spellCheck={false} />
            <label htmlFor="password">密码</label>
            <input id="password" name="password" type="password" autoComplete="current-password" required />
            <button type="submit">{busy ? "正在登录…" : "登录"}</button>
          </fieldset>
        </form>
      </section>
    </main>
  );
}
