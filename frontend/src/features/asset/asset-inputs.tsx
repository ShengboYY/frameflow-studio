"use client";

import Image from "next/image";
import Link from "next/link";
import { useState } from "react";
import { useAuth } from "@/features/identity/auth-provider";
import type { AuthSession } from "@/features/identity/auth-session";
import type { TemplateView } from "@/features/template/template-api";
import { useTaskSubmission } from "@/features/task/use-task-submission";
import type { TaskAccepted } from "@/features/task/task-contract";
import { useUploadSlots } from "./use-upload-slots";

type SubmissionHandler = { onAccepted?: (value: TaskAccepted) => boolean; onCheckTask?: (taskId: string) => Promise<void> };

function LocalPreview({ url, name }: { url: string; name: string }) {
  const [failed, setFailed] = useState(false);
  return failed ? <p role="status">本地预览暂不可用，上传状态见下方</p>
    : <Image className="input-preview" src={url} alt={`已选择的图片：${name}`} width={640} height={400} unoptimized onError={() => setFailed(true)} />;
}

function UploadInputs({ template, session, onAccepted, onCheckTask }: { template: TemplateView; session: AuthSession } & SubmissionHandler) {
  const { slots, select, remove, getPreparedInputs } = useUploadSlots(template, session);
  const { state: submission, submit, isSubmitting } = useTaskSubmission(session, getPreparedInputs, onAccepted);
  const count = slots.filter((slot) => slot.status === "ready").length;
  const ready = getPreparedInputs() !== null;
  return <>
    <form aria-label="制作表单" onSubmit={(event) => { event.preventDefault(); void submit(); }} aria-busy={submission.status === "submitting"}>
      <fieldset disabled={submission.status === "submitting"}>
        <legend className="visually-hidden">制作素材</legend>
        <ol className="upload-slots" aria-label="图片上传位置">
          {slots.map((slot, index) => <li key={index} className="upload-slot">
            <div className="upload-preview">
              <label className="upload-target" htmlFor={`upload-${template.templateId}-${index}`} title={slot.file ? "点击图片替换" : undefined}>
                {slot.previewUrl && slot.file ? <LocalPreview key={slot.previewUrl} url={slot.previewUrl} name={slot.file.name} />
                  : <><span className="upload-icon" aria-hidden="true">＋</span><span>{slots.length === 1 ? "选择图片" : `图片 ${index + 1}`}</span><span className="upload-hint">点击选择图片</span></>}
              </label>
              {slot.status !== "empty" && <button className="remove-image" type="button" aria-label={`移除图片 ${index + 1}`} title="移除图片" onClick={() => { if (!isSubmitting()) remove(index); }}><span aria-hidden="true">×</span></button>}
            </div>
            <input id={`upload-${template.templateId}-${index}`} aria-label={`图片 ${index + 1}`} type="file" accept={template.requirements.acceptedMimeTypes.join(",")}
              aria-describedby={`upload-feedback-${index}`} onChange={(event) => {
                if (!isSubmitting()) select(index, Array.from(event.currentTarget.files ?? []));
                event.currentTarget.value = "";
              }} />
            {slot.file && <p className="file-name">{slot.file.name}</p>}
            <p id={`upload-feedback-${index}`} role={slot.error ? "alert" : "status"}>
              {slot.status === "empty" ? "请选择一张图片" : slot.status === "uploading" ? "正在上传…"
                : slot.status === "ready" ? "上传完成，可用于制作" : slot.error}
            </p>
            <div className="upload-actions">
              {slot.status === "error" && slot.file && <button type="button" onClick={() => { if (!isSubmitting()) select(index, [slot.file!]); }}>重新上传图片 {index + 1}</button>}
            </div>
          </li>)}
        </ol>
        <p className={ready ? "materials-ready" : "materials-pending"} role="status">
          {ready ? `素材已准备完成（${count}/${slots.length}）` : `已就绪 ${count}/${slots.length} 张，请完成全部图片上传`}
        </p>
      </fieldset>
      {onAccepted && <button className="make-button" type="submit" disabled={!ready || submission.status === "submitting"}>
        {submission.status === "submitting" ? "正在提交…" : submission.status === "outcomeUnknown" ? "仍要再次提交" : "开始制作"}
      </button>}
    </form>
    {(submission.status === "error" || submission.status === "outcomeUnknown") && <div role="alert">
      <p>{submission.status === "error" ? `提交未受理：${submission.message}` : submission.message}</p>
      {submission.status === "outcomeUnknown" && <>
        {submission.taskId && <><p>可能的任务 ID：{submission.taskId}</p><button type="button" onClick={() => { void onCheckTask?.(submission.taskId!); }}>查询可能的任务</button></>}
        <p><Link href="/history">查看我的记录</Link>核对实际受理情况。</p>
      </>}
    </div>}
  </>;
}

export function AssetInputs({ template, onAccepted, onCheckTask }: { template: TemplateView } & SubmissionHandler) {
  const { status, identityEpoch, busy, session } = useAuth();
  return <section className="upload-panel" aria-label="准备图片">
    {status === "authenticated" && !busy ? <UploadInputs key={`${template.templateId}:${identityEpoch}`} template={template} session={session} onAccepted={onAccepted} onCheckTask={onCheckTask} />
      : status === "anonymous" ? <Link href="/login" className="primary-link upload-login-button">登录后上传图片</Link>
        : <p role="status">{status === "error" ? "会话查询暂不可用，请在上方重新核对后继续" : "正在核对登录状态…"}</p>}
  </section>;
}
