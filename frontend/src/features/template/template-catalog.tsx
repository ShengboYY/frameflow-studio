"use client";

import Link from "next/link";
import { useCallback, useState } from "react";
import { useResource } from "@/lib/use-resource";
import { templateApi } from "./template-api";
import { TemplateCover } from "./template-cover";

function TemplateResults({ categoryId }: { categoryId: string | null }) {
  const load = useCallback((signal: AbortSignal) => templateApi.list(categoryId, signal), [categoryId]);
  const { resource, retry } = useResource(categoryId ?? "all", load);
  if (resource.status === "loading") return <p role="status">正在加载模板…</p>;
  if (resource.status !== "ready") return <div role="alert"><p>模板加载失败：{resource.message}</p><button type="button" onClick={retry}>重新加载模板</button></div>;
  if (resource.data.length === 0) return <p role="status">当前分类暂无模板</p>;
  return <ul className="template-grid" aria-label="模板列表">
    {resource.data.map((template) => <li key={template.templateId}>
      <Link href={`/templates/${template.templateId}`} className="template-card" aria-label={`查看${template.title}`}>
        <TemplateCover src={template.coverUrl} title={template.title} />
        <div className="template-card-copy"><span className="template-card-label">图片模板</span><h2>{template.title}</h2><span className="template-card-action">开始创作 <span aria-hidden="true">↗</span></span></div>
      </Link>
    </li>)}
  </ul>;
}

export function TemplateCatalog() {
  const [categoryId, setCategoryId] = useState<string | null>(null);
  const { resource: categories, retry } = useResource("categories", templateApi.categories);
  return <main className="catalog-page">
    <div className="page-heading catalog-heading"><div><p className="eyebrow">THE CREATIVE STUDIO</p><h1>选择你的灵感起点<span aria-hidden="true">.</span></h1><p>从一张图片开始，让创意发生。</p></div><span className="catalog-note">精选模板 · 即刻创作</span></div>
    <div className="catalog-layout">
      <nav className="category-nav" aria-label="模板分类">
        <h2 className="visually-hidden">模板分类</h2>
        <button type="button" aria-pressed={categoryId === null} onClick={() => setCategoryId(null)}>全部模板</button>
        {categories.status === "ready" && categories.data.map((category) => <button key={category.categoryId} type="button" aria-pressed={categoryId === category.categoryId} onClick={() => setCategoryId(category.categoryId)}>{category.name}</button>)}
        {categories.status === "loading" && <p role="status">正在加载分类…</p>}
        {(categories.status === "error" || categories.status === "missing") && <div role="alert"><p>分类加载失败：{categories.message}</p><button type="button" onClick={retry}>重新加载分类</button></div>}
      </nav>
      <section className="catalog-results" aria-label="可用模板"><TemplateResults categoryId={categoryId} /></section>
    </div>
  </main>;
}
