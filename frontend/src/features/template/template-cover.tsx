"use client";

import Image from "next/image";
import { useState } from "react";

export function TemplateCover({ src, title }: { src: string; title: string }) {
  const [failed, setFailed] = useState(false);
  return <div className="template-cover">
    {failed ? <p role="status">示例图片暂不可用</p>
      : <Image src={src} alt={`${title}示例`} width={640} height={400} unoptimized onError={() => setFailed(true)} />}
  </div>;
}
