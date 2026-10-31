"use client";

import Image from "next/image";
import { useEffect, useState } from "react";
import { useAuth } from "@/features/identity/auth-provider";
import { taskApi } from "./task-api";

type Preview = { status: "loading" } | { status: "ready"; url: string } | { status: "error" };

/** Mounted with an identity/resource key. Blob lifetime never outlives that scope. */
export function ResultPreview({ taskId }: { taskId: string }) {
  const { session } = useAuth();
  const [context] = useState(() => session.captureWriteContext());
  const [preview, setPreview] = useState<Preview>({ status: "loading" });
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    let active = true, url: string | null = null;
    const current = () => active && context !== null && session.isCurrent(context);
    const dispose = () => {
      active = false; controller.abort();
      if (url) URL.revokeObjectURL(url);
      url = null;
    };
    const unsubscribe = session.subscribe(() => { if (!context || !session.isCurrent(context)) dispose(); });
    if (current()) void taskApi.result(taskId, controller.signal).then((blob) => {
      if (!current()) return;
      const created = URL.createObjectURL(blob);
      if (!current()) { URL.revokeObjectURL(created); return; }
      url = created; setPreview({ status: "ready", url: created });
    }).catch((cause: unknown) => {
      if (!current()) return;
      setPreview({ status: "error" });
      void session.handleRequestError(cause, context!.identityEpoch);
    });
    return () => { dispose(); unsubscribe(); };
  }, [session, context, taskId, attempt]);

  function retry() { setPreview({ status: "loading" }); setAttempt((value) => value + 1); }
  return <div className="result-preview" aria-label="成功结果预览">
    {preview.status === "loading" ? <p role="status">正在加载结果图片…</p>
      : preview.status === "error" ? <div role="alert"><p>结果预览暂不可用，任务仍已成功。</p><button type="button" onClick={retry}>重新加载预览</button></div>
        : <Image src={preview.url} alt="制作结果预览" width={960} height={640} unoptimized onError={() => {
          if (context && session.isCurrent(context)) setPreview((latest) => latest.status === "ready" && latest.url === preview.url ? { status: "error" } : latest);
        }} />}
  </div>;
}
