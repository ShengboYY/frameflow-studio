"use client";

import { useEffect, useMemo, useSyncExternalStore } from "react";
import { useAuth } from "@/features/identity/auth-provider";
import { TaskController } from "./task-controller";
import { initialTaskState } from "./task-state";

const inactive = initialTaskState();
const inactiveSnapshot = () => inactive;
const noSubscription = () => () => {};
const noRefresh = async () => {};
const noAccepted = () => false;

/** Each mounted page owns a controller; there is no global history cache or polling loop. */
export function useTaskTracking() {
  const { account, identityEpoch, status, busy, session } = useAuth();
  const ownerId = status === "authenticated" && !busy ? account?.ownerId ?? null : null;
  const controller = useMemo(() => ownerId ? new TaskController(session, { ownerId, identityEpoch }) : null, [session, ownerId, identityEpoch]);
  useEffect(() => {
    controller?.start();
    return () => controller?.stop();
  }, [controller]);
  const state = useSyncExternalStore(controller?.subscribe ?? noSubscription, controller?.getSnapshot ?? inactiveSnapshot, controller?.getServerSnapshot ?? inactiveSnapshot);
  return {
    ...state,
    refreshHistory: controller?.refreshHistory ?? noRefresh,
    refreshTask: controller?.refreshTask ?? noRefresh,
    trackAccepted: controller?.trackAccepted ?? noAccepted,
  };
}
