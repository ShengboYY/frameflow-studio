"use client";

import Image from "next/image";
import Link from "next/link";
import { useState } from "react";
import { useAuth } from "@/features/identity/auth-provider";
import type { AuthSession } from "@/features/identity/auth-session";
import type { TemplateView } from "@/features/template/template-api";
import { useUploadSlots } from "./use-upload-slots";

function LocalPreview({ url, name }: { url: string; name: string }) {
  const [failed, setFailed] = useState(false);
  return failed ? <p role="status">本地预览暂不可用，上传状态见下方</p>
    : <Image className="input-preview" src={url} alt={`已选择的图片：${name}`} width={640} height={400} unoptimized onError={() => setFailed(true)} />;
}

function UploadInputs({ template, session }: { template: TemplateView; session: AuthSession }) {
  const { slots, select, remove, getPreparedInputs } = useUploadSlots(template, session);
  const count = slots.filter((slot) => slot.status === "ready").length;
  const ready = getPreparedInputs() !== null;
  return <>
    <ol className="upload-slots" aria-label="图片上传位置">
      {slots.map((slot, index) => <li key={index} className="upload-slot">
        <label htmlFor={`upload-${template.templateId}-${index}`}>图片 {index + 1}</label>
        {slot.previewUrl && slot.file && <LocalPreview key={slot.previewUrl} url={slot.previewUrl} name={slot.file.name} />}
        <input id={`upload-${template.templateId}-${index}`} type="file" accept={template.requirements.acceptedMimeTypes.join(",")}
          aria-describedby={`upload-feedback-${index}`} onChange={(event) => {
            select(index, Array.from(event.currentTarget.files ?? []));
            event.currentTarget.value = "";
          }} />
        {slot.file && <p className="file-name">{slot.file.name}</p>}
        <p id={`upload-feedback-${index}`} role={slot.error ? "alert" : "status"}>
          {slot.status === "empty" ? "请选择一张图片" : slot.status === "uploading" ? "正在上传…"
            : slot.status === "ready" ? "上传完成，可用于制作" : slot.error}
        </p>
        <div className="upload-actions">
          {slot.status === "error" && slot.file && <button type="button" onClick={() => select(index, [slot.file!])}>重新上传图片 {index + 1}</button>}
          {slot.status !== "empty" && <button type="button" onClick={() => remove(index)}>移除图片 {index + 1}</button>}
        </div>
      </li>)}
    </ol>
    <p className={ready ? "materials-ready" : "materials-pending"} role="status">
      {ready ? `素材已准备完成（${count}/${slots.length}）` : `已就绪 ${count}/${slots.length} 张，请完成全部图片上传`}
    </p>
  </>;
}

export function AssetInputs({ template }: { template: TemplateView }) {
  const { status, identityEpoch, busy, session } = useAuth();
  return <section className="upload-panel" aria-labelledby="upload-title">
    <h2 id="upload-title">准备图片</h2>
    <p>每个位置选择一张图片，也可以重新选择来替换。</p>
    {status === "authenticated" && !busy ? <UploadInputs key={`${template.templateId}:${identityEpoch}`} template={template} session={session} />
      : status === "anonymous" ? <p><Link href="/login">登录后上传图片</Link>，模板信息可以直接浏览。</p>
        : <p role="status">{status === "error" ? "会话查询暂不可用，请在上方重新核对后继续" : "正在核对登录状态…"}</p>}
  </section>;
}
