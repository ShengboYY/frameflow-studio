"use client";

import { useEffect, useReducer, useRef, useState } from "react";
import { ApiError } from "@/lib/api-client";
import type { AuthSession } from "@/features/identity/auth-session";
import type { TemplateView } from "@/features/template/template-api";
import { assetApi } from "./asset-api";

export type UploadSlot = {
  status: "empty" | "uploading" | "ready" | "error";
  file: File | null; previewUrl: string | null; assetId: string | null; error: string | null;
};
export type PreparedInputs = { templateId: string; ownerId: string; identityEpoch: number; assetIds: string[] };
type Resource = { selectionRevision: number; requestSeq: number; controller: AbortController | null; previewUrl: string | null; assetId: string | null };
const empty = (): UploadSlot => ({ status: "empty", file: null, previewUrl: null, assetId: null, error: null });

/** Mounted with a template + identity epoch key; each selection owns its request and local URL. */
export function useUploadSlots(template: TemplateView, session: AuthSession) {
  const [context] = useState(() => session.captureWriteContext());
  const [slots, replace] = useReducer((previous: UploadSlot[], action: { index: number; value: UploadSlot }) =>
    previous.map((slot, index) => index === action.index ? action.value : slot),
  template.requirements.requiredImageCount, (count) => Array.from({ length: count }, empty));
  const resources = useRef<Resource[]>(slots.map(() => ({ selectionRevision: 0, requestSeq: 0, controller: null, previewUrl: null, assetId: null })));
  const active = useRef(true);

  function release(resource: Resource) {
    resource.selectionRevision++;
    resource.requestSeq++;
    resource.assetId = null;
    resource.controller?.abort(); resource.controller = null;
    if (resource.previewUrl) URL.revokeObjectURL(resource.previewUrl);
    resource.previewUrl = null;
  }

  useEffect(() => {
    active.current = true;
    const held = resources.current;
    const dispose = () => { active.current = false; held.forEach(release); };
    const unsubscribe = session.subscribe(() => {
      if (!context || !session.isCurrent(context)) dispose();
    });
    if (!context || !session.isCurrent(context)) dispose();
    return () => { dispose(); unsubscribe(); };
  }, [session, context]);

  function getPreparedInputs(): PreparedInputs | null {
    if (!active.current || !context || !session.isCurrent(context) || resources.current.some((slot) => slot.assetId === null)) return null;
    return { templateId: template.templateId, ownerId: context.ownerId, identityEpoch: context.identityEpoch, assetIds: resources.current.map((slot) => slot.assetId!) };
  }

  function remove(index: number) {
    if (!active.current || !context || !session.isCurrent(context)) return;
    release(resources.current[index]);
    replace({ index, value: empty() });
  }

  function select(index: number, files: readonly File[]) {
    if (!active.current || !context || !session.isCurrent(context) || files.length === 0) return;
    const resource = resources.current[index];
    release(resource);
    const file = files[0];
    const error = files.length !== 1 ? "每个位置请选择一张图片"
      : !template.requirements.acceptedMimeTypes.includes(file.type) ? "图片类型不符合模板要求"
      : file.size <= 0 ? "图片不能为空"
      : file.size > template.requirements.maxFileSizeBytes ? "图片超过此模板的单文件大小上限" : null;
    if (error) { replace({ index, value: { ...empty(), status: "error", error } }); return; }
    let previewUrl: string;
    try { previewUrl = URL.createObjectURL(file); }
    catch { replace({ index, value: { ...empty(), status: "error", error: "无法预览所选图片，请重新选择" } }); return; }
    const controller = new AbortController();
    resource.previewUrl = previewUrl;
    resource.controller = controller;
    const revision = resource.selectionRevision;
    const sequence = ++resource.requestSeq;
    const current = () => active.current && session.isCurrent(context)
      && resource.selectionRevision === revision && resource.requestSeq === sequence;
    const selected = { file, previewUrl, assetId: null, error: null };
    replace({ index, value: { ...selected, status: "uploading" } });
    void assetApi.upload(template.templateId, file, context.csrf, controller.signal).then((asset) => {
      if (!current()) return;
      if (asset.ownerId !== context.ownerId || !template.requirements.acceptedMimeTypes.includes(asset.mediaType)) {
        throw new ApiError("上传响应与当前账号或模板不一致，请重新核对", 201, "INVALID_RESPONSE");
      }
      resource.assetId = asset.assetId;
      replace({ index, value: { ...selected, status: "ready", assetId: asset.assetId } });
    }).catch((cause: unknown) => {
      if (!current()) return;
      replace({ index, value: { ...selected, status: "error", error: cause instanceof ApiError ? cause.message : "上传暂不可用，请重试" } });
      void session.handleRequestError(cause, context.identityEpoch);
    }).finally(() => {
      if (current() && resource.controller === controller) resource.controller = null;
    });
  }

  return { slots, select, remove, getPreparedInputs };
}
