"use client";

import Link from "next/link";
import { useCallback } from "react";
import { useResource } from "@/lib/use-resource";
import { AssetInputs } from "@/features/asset/asset-inputs";
import { templateApi } from "./template-api";
import { TemplateCover } from "./template-cover";

export function TemplateDetail({ templateId }: { templateId: string }) {
  const load = useCallback((signal: AbortSignal) => templateApi.detail(templateId, signal), [templateId]);
  const { resource, retry } = useResource(templateId, load);
  return <main className="detail-page">
    <Link href="/templates" className="back-link">← 返回模板列表</Link>
    {resource.status === "loading" ? <p role="status">正在加载模板详情…</p>
      : resource.status === "missing" ? <section><h1>模板不存在</h1><p>请选择其他模板继续。</p></section>
        : resource.status === "error" ? <section role="alert"><h1>模板详情加载失败</h1><p>{resource.message}</p><button type="button" onClick={retry}>重新加载详情</button></section>
          : <>
            <div className="page-heading"><p className="eyebrow">MAKE IT YOURS</p><h1>{resource.data.title}</h1><p>{resource.data.description}</p></div>
            <div className="detail-grid">
              <section className="template-information" aria-label="模板与素材要求">
                <TemplateCover key={resource.data.coverUrl} src={resource.data.coverUrl} title={resource.data.title} />
                <div className="requirements"><h2>素材要求</h2><ul>
                  <li>需要 {resource.data.requirements.requiredImageCount} 张图片</li>
                  <li>支持 {resource.data.requirements.acceptedMimeTypes.map((type) => type === "image/jpeg" ? "JPEG" : "PNG").join("、")}</li>
                  <li>每张不超过 {resource.data.requirements.maxFileSizeBytes / (1024 * 1024)} MiB（{resource.data.requirements.maxFileSizeBytes.toLocaleString()} 字节，包含上限）</li>
                </ul><p>上传后由服务器检查实际图片内容。</p></div>
              </section>
              <AssetInputs template={resource.data} />
            </div>
          </>}
  </main>;
}
