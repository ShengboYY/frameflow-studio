import { ApiError, apiRequest, isRecord, jsonBody } from "@/lib/api-client";

export type CategoryView = { categoryId: string; name: string };
export type TemplateSummary = { templateId: string; title: string; categoryId: string; coverUrl: string };
export type AssetRequirements = { requiredImageCount: number; acceptedMimeTypes: string[]; maxFileSizeBytes: number };
export type TemplateView = TemplateSummary & { description: string; requirements: AssetRequirements };
const slug = /^[a-z0-9][a-z0-9-]{0,63}$/;

function invalid(): never { throw new ApiError("模板数据无法识别，请重新加载", 200, "INVALID_RESPONSE"); }
function identifier(value: unknown): value is string { return typeof value === "string" && slug.test(value); }

function summary(value: unknown): TemplateSummary {
  if (!isRecord(value) || !identifier(value.templateId) || !identifier(value.categoryId)
      || typeof value.title !== "string" || !value.title
      || typeof value.coverUrl !== "string" || !/^\/api\/v1\/demo-media\/[a-z0-9][a-z0-9-]{0,63}$/.test(value.coverUrl)) invalid();
  return { templateId: value.templateId, title: value.title, categoryId: value.categoryId, coverUrl: value.coverUrl };
}

function items<T>(value: unknown, parse: (item: unknown) => T): T[] {
  if (!isRecord(value) || !Array.isArray(value.items)) invalid();
  return value.items.map(parse);
}

export const templateApi = {
  async categories(signal: AbortSignal): Promise<CategoryView[]> {
    const result = items(await apiRequest("/api/v1/templates/categories", { signal }, jsonBody), (value) => {
      if (!isRecord(value) || !identifier(value.categoryId) || typeof value.name !== "string" || !value.name) invalid();
      return { categoryId: value.categoryId, name: value.name };
    });
    if (new Set(result.map((item) => item.categoryId)).size !== result.length) invalid();
    return result;
  },
  async list(categoryId: string | null, signal: AbortSignal): Promise<TemplateSummary[]> {
    const query = categoryId === null ? "" : `?categoryId=${encodeURIComponent(categoryId)}`;
    const result = items(await apiRequest(`/api/v1/templates${query}`, { signal }, jsonBody), summary);
    if (new Set(result.map((item) => item.templateId)).size !== result.length
        || (categoryId !== null && result.some((item) => item.categoryId !== categoryId))) invalid();
    return result;
  },
  async detail(templateId: string, signal: AbortSignal): Promise<TemplateView> {
    if (!identifier(templateId)) throw new ApiError("模板标识无效", 400, "INVALID_REQUEST");
    const value = await apiRequest(`/api/v1/templates/${templateId}`, { signal }, jsonBody);
    const base = summary(value);
    if (!isRecord(value) || base.templateId !== templateId || typeof value.description !== "string" || !isRecord(value.requirements)) invalid();
    const rules = value.requirements;
    if (typeof rules.requiredImageCount !== "number" || !Number.isSafeInteger(rules.requiredImageCount) || rules.requiredImageCount < 1
        || typeof rules.maxFileSizeBytes !== "number" || !Number.isSafeInteger(rules.maxFileSizeBytes) || rules.maxFileSizeBytes < 1 || rules.maxFileSizeBytes > 8 * 1024 * 1024
        || !Array.isArray(rules.acceptedMimeTypes) || rules.acceptedMimeTypes.length === 0
        || !rules.acceptedMimeTypes.every((type) => type === "image/jpeg" || type === "image/png")
        || new Set(rules.acceptedMimeTypes).size !== rules.acceptedMimeTypes.length) invalid();
    return { ...base, description: value.description, requirements: {
      requiredImageCount: rules.requiredImageCount, maxFileSizeBytes: rules.maxFileSizeBytes, acceptedMimeTypes: rules.acceptedMimeTypes,
    } };
  },
};
