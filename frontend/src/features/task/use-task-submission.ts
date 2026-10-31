"use client";

import { useEffect, useRef, useState } from "react";
import { ApiError } from "@/lib/api-client";
import type { PreparedInputs } from "@/features/asset/use-upload-slots";
import type { AuthSession } from "@/features/identity/auth-session";
import { taskApi } from "./task-api";
import type { TaskAccepted, TaskSimulation } from "./task-contract";

type Submission =
  | { status: "idle" | "submitting" }
  | { status: "accepted"; taskId: string }
  | { status: "error" | "outcomeUnknown"; message: string; taskId?: string };

export function useTaskSubmission(session: AuthSession, getPreparedInputs: () => PreparedInputs | null, onAccepted?: (value: TaskAccepted) => boolean) {
  const [state, setState] = useState<Submission>({ status: "idle" });
  const active = useRef(false);
  const request = useRef<AbortController | null>(null);
  const releaseSubscription = useRef<(() => void) | null>(null);
  useEffect(() => {
    active.current = true;
    return () => {
      active.current = false;
      const previous = request.current; request.current = null;
      releaseSubscription.current?.(); releaseSubscription.current = null;
      previous?.abort();
    };
  }, []);

  const isSubmitting = () => request.current !== null;
  async function submit(simulation?: TaskSimulation) {
    if (!active.current || isSubmitting() || !onAccepted) return;
    const input = getPreparedInputs(), context = session.captureWriteContext();
    if (!input || !context || input.ownerId !== context.ownerId || input.identityEpoch !== context.identityEpoch) return;
    // Freeze this task's inputs and demo settings before yielding; later submissions may use different settings.
    const body = { templateId: input.templateId, assetIds: [...input.assetIds], ...(simulation ? { simulation: { ...simulation } } : {}) };
    const controller = new AbortController(); request.current = controller;
    const current = () => active.current && request.current === controller && session.isCurrent(context);
    const unsubscribe = session.subscribe(() => {
      if (!session.isCurrent(context)) { if (request.current === controller) request.current = null; controller.abort(); }
    });
    releaseSubscription.current = unsubscribe;
    setState({ status: "submitting" });
    try {
      const accepted = await taskApi.submit(body, context.ownerId, context.csrf, controller.signal);
      if (!current()) return;
      if (!onAccepted(accepted)) throw new ApiError("受理信息需要重新核对", 202, "INVALID_RESPONSE");
      setState({ status: "accepted", taskId: accepted.taskId });
    } catch (cause) {
      if (!current()) return;
      const knownRejection = cause instanceof ApiError && cause.status >= 400 && cause.status < 500;
      setState(knownRejection ? { status: "error", message: cause.message }
        : { status: "outcomeUnknown", message: "提交结果暂未确认，可先查看记录；再次提交可能创建另一项任务。", taskId: cause instanceof ApiError ? cause.taskId : undefined });
      void session.handleRequestError(cause, context.identityEpoch);
    } finally {
      unsubscribe();
      if (releaseSubscription.current === unsubscribe) releaseSubscription.current = null;
      if (request.current === controller) request.current = null;
    }
  }
  return { state, submit, isSubmitting };
}
