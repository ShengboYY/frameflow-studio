"use client";

import Link from "next/link";
import { AssetInputs } from "@/features/asset/asset-inputs";
import type { TemplateView } from "@/features/template/template-api";
import { TemplateCover } from "@/features/template/template-cover";
import { TemplateExamples } from "@/features/template/template-examples";
import { TaskList } from "./task-list";
import { useTaskTracking } from "./use-task-tracking";

export function ProductionWorkspace({ template }: { template: TemplateView }) {
  const tracking = useTaskTracking();
  return <div className="production-workspace">
    <div className="creation-panel">
      <div className="creation-heading"><Link href="/templates" className="back-link" aria-label="返回模板列表">←</Link><h1>{template.title}</h1><span className="creation-type">图片创作</span></div>
      <section className="template-information" aria-label="模板与素材要求">
        <TemplateCover key={template.coverUrl} src={template.coverUrl} title={template.title} />
        <p className="template-description">{template.description}</p>
        <TemplateExamples key={template.templateId} templateId={template.templateId} />
        <div className="requirements"><h2>素材要求</h2><ul>
          <li>图片数量：{template.requirements.requiredImageCount} 张</li>
          <li>图片格式：{template.requirements.acceptedMimeTypes.map((type) => type === "image/jpeg" ? "JPEG" : "PNG").join("、")}</li>
          <li>图片大小：每张不超过 {template.requirements.maxFileSizeBytes / (1024 * 1024)} MiB</li>
        </ul></div>
      </section>
      <AssetInputs template={template} onAccepted={tracking.trackAccepted} onCheckTask={tracking.refreshTask} />
    </div>
    <TaskList tracking={tracking} templateId={template.templateId} />
  </div>;
}

export function TaskHistory() {
  const tracking = useTaskTracking();
  return <main className="history-page">
    <div className="page-heading history-heading"><div><p className="eyebrow">YOUR CREATIONS</p><h1>制作历史</h1><p>每一次灵感，都在这里。</p></div><Link href="/templates" className="primary-link">＋ 开始新创作</Link></div>
    <TaskList tracking={tracking} />
  </main>;
}
