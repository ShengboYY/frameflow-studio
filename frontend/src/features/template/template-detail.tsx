"use client";

import Link from "next/link";
import { useCallback } from "react";
import { useResource } from "@/lib/use-resource";
import { ProductionWorkspace } from "@/features/task/production-workspace";
import { templateApi } from "./template-api";

export function TemplateDetail({ templateId }: { templateId: string }) {
  const load = useCallback((signal: AbortSignal) => templateApi.detail(templateId, signal), [templateId]);
  const { resource, retry } = useResource(templateId, load);
  return <main className="detail-page">
    {resource.status === "loading" ? <p role="status">正在加载模板详情…</p>
      : resource.status === "missing" ? <section><h1>模板不存在</h1><p>请选择其他模板继续。</p></section>
        : resource.status === "error" ? <section role="alert"><h1>模板详情加载失败</h1><p>{resource.message}</p><button type="button" onClick={retry}>重新加载详情</button></section>
          : <ProductionWorkspace key={resource.data.templateId} template={resource.data} />}
    {resource.status !== "ready" && <Link href="/templates" className="back-link">← 返回模板列表</Link>}
  </main>;
}
