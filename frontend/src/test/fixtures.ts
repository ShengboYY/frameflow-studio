import type { AccountView } from "@/features/identity/auth-api";
import type { AssetView } from "@/features/asset/asset-api";
import type { TemplateView } from "@/features/template/template-api";

export const accountA: AccountView = { ownerId: "82dd291e-4673-4500-8fe3-97a71a6cbe40", username: "demo-a", displayName: "演示用户 A" };
export const accountB: AccountView = { ownerId: "82dd291e-4673-4500-8fe3-97a71a6cbe41", username: "demo-b", displayName: "演示用户 B" };
export const portrait: TemplateView = {
  templateId: "portrait-card", title: "肖像卡片", categoryId: "creative", coverUrl: "/api/v1/demo-media/portrait-cover",
  description: "上传一张图片，演示卡片制作流程",
  requirements: { requiredImageCount: 1, acceptedMimeTypes: ["image/jpeg", "image/png"], maxFileSizeBytes: 5242880 },
};
export const postcard: TemplateView = {
  templateId: "travel-postcard", title: "旅行明信片", categoryId: "travel", coverUrl: "/api/v1/demo-media/postcard-cover",
  description: "上传两张图片，演示旅行明信片制作流程",
  requirements: { requiredImageCount: 2, acceptedMimeTypes: ["image/jpeg", "image/png"], maxFileSizeBytes: 8388608 },
};
export function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (cause: unknown) => void;
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
}
export function asset(file: File, suffix = "0", ownerId = accountA.ownerId): AssetView {
  const assetId = `92dd291e-4673-4500-8fe3-97a71a6cbe4${suffix}`;
  return { assetId, ownerId, mediaType: file.type, sizeBytes: file.size, createdAt: "2026-10-31T03:00:00Z", previewUrl: `/api/v1/assets/${assetId}/content` };
}
