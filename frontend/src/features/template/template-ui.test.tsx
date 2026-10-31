import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, expect, test, vi } from "vitest";
import { ApiError } from "@/lib/api-client";
import { AuthProvider } from "@/features/identity/auth-provider";
import { authApi } from "@/features/identity/auth-api";
import { TemplateCatalog } from "./template-catalog";
import { TemplateDetail } from "./template-detail";
import { templateApi } from "./template-api";
import { deferred, portrait, postcard } from "@/test/fixtures";

beforeEach(() => {
  vi.spyOn(templateApi, "categories").mockResolvedValue([{ categoryId: "creative", name: "创意图片" }, { categoryId: "travel", name: "旅行" }]);
  vi.spyOn(templateApi, "list").mockImplementation(async (category) => category === null ? [portrait, postcard] : category === "travel" ? [postcard] : [portrait]);
  vi.spyOn(templateApi, "detail").mockImplementation(async (id) => id === portrait.templateId ? portrait : postcard);
  vi.spyOn(authApi, "session").mockResolvedValue(null);
  vi.spyOn(authApi, "csrf").mockResolvedValue({ headerName: "X-CSRF-TOKEN", token: "fake-token" });
});
afterEach(() => vi.restoreAllMocks());

test("shows Java categories/cards, filters by category, and links to the matching detail", async () => {
  render(<TemplateCatalog />);
  expect(screen.getByText("正在加载模板…")).toBeVisible();
  const travel = await screen.findByRole("button", { name: "旅行" });
  expect(await screen.findByRole("link", { name: "查看肖像卡片" })).toHaveAttribute("href", "/templates/portrait-card");
  fireEvent.click(travel);
  await waitFor(() => expect(screen.queryByRole("link", { name: "查看肖像卡片" })).not.toBeInTheDocument());
  expect(await screen.findByRole("link", { name: "查看旅行明信片" })).toHaveAttribute("href", "/templates/travel-postcard");
  expect(travel).toHaveAttribute("aria-pressed", "true");
  expect(templateApi.list).toHaveBeenLastCalledWith("travel", expect.any(AbortSignal));
});

test("distinguishes empty results from loading/error and can manually retry a failed query", async () => {
  vi.mocked(templateApi.list).mockRejectedValueOnce(new ApiError("服务暂不可用", 503, "SERVICE_UNAVAILABLE")).mockResolvedValueOnce([]);
  render(<TemplateCatalog />);
  fireEvent.click(await screen.findByRole("button", { name: "重新加载模板" }));
  expect(await screen.findByText("当前分类暂无模板")).toBeVisible();
  expect(screen.queryByRole("alert")).not.toBeInTheDocument();
});

test("category failures have their own recovery without hiding otherwise available templates", async () => {
  vi.mocked(templateApi.categories).mockRejectedValueOnce(new Error("unavailable"));
  render(<TemplateCatalog />);
  expect(await screen.findByRole("link", { name: "查看肖像卡片" })).toBeVisible();
  fireEvent.click(await screen.findByRole("button", { name: "重新加载分类" }));
  expect(await screen.findByRole("button", { name: "旅行" })).toBeVisible();
});

test.each(["success", "error"])("late category list %s cannot replace a newer filter", async (outcome) => {
  const pending = deferred<typeof portrait[]>();
  vi.mocked(templateApi.list).mockReturnValueOnce(pending.promise);
  render(<TemplateCatalog />);
  fireEvent.click(await screen.findByRole("button", { name: "旅行" }));
  await screen.findByRole("link", { name: "查看旅行明信片" });
  await act(async () => { if (outcome === "success") pending.resolve([portrait]); else pending.reject(new Error("old failure")); });
  expect(screen.queryByRole("link", { name: "查看肖像卡片" })).not.toBeInTheDocument();
  expect(screen.queryByRole("alert")).not.toBeInTheDocument();
});

test.each([portrait, postcard])("shows concise requirements and a single upload login action for $templateId", async (template) => {
  render(<AuthProvider><TemplateDetail templateId={template.templateId} /></AuthProvider>);
  expect(await screen.findByRole("heading", { name: template.title })).toBeVisible();
  expect(screen.getByText(`图片数量：${template.requirements.requiredImageCount} 张`)).toBeVisible();
  expect(screen.getByText("图片格式：JPEG、PNG")).toBeVisible();
  expect(screen.getByText(`图片大小：每张不超过 ${template.requirements.maxFileSizeBytes / (1024 * 1024)} MiB`)).toBeVisible();
  expect(screen.queryByText(/字节|每个位置选择|模板信息可以直接浏览/)).not.toBeInTheDocument();
  expect(await screen.findByRole("link", { name: "登录后上传图片" })).toHaveAttribute("href", "/login");
});

test("missing template and service failure have distinct messages and recovery", async () => {
  vi.mocked(templateApi.detail).mockRejectedValueOnce(new ApiError("不存在", 404, "RESOURCE_NOT_FOUND"));
  const page = render(<AuthProvider><TemplateDetail templateId="missing" /></AuthProvider>);
  expect(await screen.findByRole("heading", { name: "模板不存在" })).toBeVisible();
  page.unmount();
  vi.mocked(templateApi.detail).mockRejectedValueOnce(new ApiError("服务暂不可用", 503, "SERVICE_UNAVAILABLE"));
  render(<AuthProvider><TemplateDetail templateId={portrait.templateId} /></AuthProvider>);
  fireEvent.click(await screen.findByRole("button", { name: "重新加载详情" }));
  expect(await screen.findByRole("heading", { name: portrait.title })).toBeVisible();
});

test("a late detail does not appear after the selected template changed", async () => {
  const pending = deferred<typeof portrait>();
  vi.mocked(templateApi.detail).mockReturnValueOnce(pending.promise);
  const page = render(<AuthProvider><TemplateDetail templateId={portrait.templateId} /></AuthProvider>);
  page.rerender(<AuthProvider><TemplateDetail templateId={postcard.templateId} /></AuthProvider>);
  await screen.findByRole("heading", { name: postcard.title });
  await act(async () => pending.resolve(portrait));
  expect(screen.queryByRole("heading", { name: portrait.title })).not.toBeInTheDocument();
});

test("unavailable cover is expressed as an image issue while keeping the card usable", async () => {
  render(<TemplateCatalog />);
  fireEvent.error(await screen.findByAltText("肖像卡片示例"));
  expect(screen.getByText("示例图片暂不可用")).toBeVisible();
  expect(screen.getByRole("link", { name: "查看肖像卡片" })).toBeVisible();
});
