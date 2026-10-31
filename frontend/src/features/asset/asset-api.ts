import { ApiError, apiRequest, isRecord, jsonBody } from "@/lib/api-client";
import type { CsrfView } from "@/features/identity/auth-api";

export type AssetView = { assetId: string; ownerId: string; mediaType: string; sizeBytes: number; createdAt: string; previewUrl: string };
const uuid = /^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/;

export const assetApi = {
  async upload(templateId: string, file: File, csrf: CsrfView, signal: AbortSignal): Promise<AssetView> {
    const body = new FormData();
    body.append("templateId", templateId);
    body.append("file", file);
    const value = await apiRequest("/api/v1/assets", {
      method: "POST", body, signal, headers: { [csrf.headerName]: csrf.token },
    }, jsonBody);
    if (!isRecord(value) || typeof value.assetId !== "string" || !uuid.test(value.assetId)
        || typeof value.ownerId !== "string" || !uuid.test(value.ownerId)
        || !(value.mediaType === "image/jpeg" || value.mediaType === "image/png")
        || value.sizeBytes !== file.size || file.size === 0
        || typeof value.createdAt !== "string" || !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d{1,6})?Z$/.test(value.createdAt) || !Number.isFinite(Date.parse(value.createdAt))
        || value.previewUrl !== `/api/v1/assets/${value.assetId}/content`) {
      throw new ApiError("上传响应无法识别，请核对后重新选择图片", 201, "INVALID_RESPONSE");
    }
    return { assetId: value.assetId, ownerId: value.ownerId, mediaType: value.mediaType, sizeBytes: file.size, createdAt: value.createdAt, previewUrl: value.previewUrl };
  },
};
