"use client";

import { useEffect, useState } from "react";
import { ApiError } from "./api-client";

type Resource<T> = { status: "loading" } | { status: "ready"; data: T }
  | { status: "error"; message: string } | { status: "missing"; message: string };

/** Component-scoped reads: both cancellation and late success/error isolation are required. */
export function useResource<T>(key: string, load: (signal: AbortSignal) => Promise<T>) {
  const [attempt, setAttempt] = useState(0);
  const [result, setResult] = useState<{ key: string; attempt: number; resource: Resource<T> } | null>(null);
  useEffect(() => {
    let active = true;
    const controller = new AbortController();
    load(controller.signal).then((data) => {
      if (active) setResult({ key, attempt, resource: { status: "ready", data } });
    }).catch((cause: unknown) => {
      if (active) setResult({ key, attempt, resource: {
        status: cause instanceof ApiError && cause.status === 404 ? "missing" : "error",
        message: cause instanceof ApiError ? cause.message : "读取暂不可用，请重试",
      } });
    });
    return () => { active = false; controller.abort(); };
  }, [key, load, attempt]);
  const resource: Resource<T> = result?.key === key && result.attempt === attempt ? result.resource : { status: "loading" };
  return { resource, retry: () => setAttempt((value) => value + 1) };
}
