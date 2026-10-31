"use client";

import Image from "next/image";
import { useState } from "react";

// Curated public examples for the two bundled templates, never a user's uploaded media.
const examples: Record<string, { sources: string[]; result: string }> = {
  "portrait-card": { sources: ["portrait-source"], result: "portrait-result" },
  "travel-postcard": { sources: ["travel-coast-source", "travel-town-source"], result: "postcard-result" },
};

function ExampleImage({ name, alt, source = false }: { name: string; alt: string; source?: boolean }) {
  const [failed, setFailed] = useState(false);
  return failed ? <p role="status">{alt}暂不可用</p>
    : <Image src={`/demo-examples/${name}.jpg`} alt={alt} width={source ? 1200 : 640} height={source ? 800 : 400} unoptimized onError={() => setFailed(true)} />;
}

export function TemplateExamples({ templateId }: { templateId: string }) {
  const example = examples[templateId];
  if (!example) return null;
  return <details className="template-examples">
    <summary>查看原图与模板效果</summary>
    <div className="example-comparison">
      <div><h3>原始图片</h3>{example.sources.map((name, index) => <figure key={name}>
        <ExampleImage name={name} alt={`示例原图 ${index + 1}`} source />
        <figcaption><a href={`/demo-examples/${name}.jpg`} download>下载原图 {index + 1}</a></figcaption>
      </figure>)}</div>
      <div><h3>套用模板后</h3><figure><ExampleImage name={example.result} alt="套用当前模板的效果示例" /></figure></div>
    </div>
    <p>以上为示例原图及模板效果。当前制作流程为模拟演示，返回当前模板的固定示例图，不会根据本次上传图片生成。</p>
  </details>;
}
