import Link from "next/link";

export default function Home() {
  return (
    <main>
      <h1>FrameFlow Studio</h1>
      <p>使用演示账号登录 FrameFlow Studio。</p>
      <Link href="/login">进入账号</Link>
    </main>
  );
}
