import { afterEach, expect, test, vi } from "vitest";
import { templateApi } from "./template-api";
import { portrait, postcard } from "@/test/fixtures";

afterEach(() => vi.unstubAllGlobals());
const signal = () => new AbortController().signal;
test("reads categories, filtered summaries and rules from the Java contract", async () => {
  const fetcher = vi.fn().mockResolvedValueOnce(Response.json({ items: [{ categoryId: "travel", name: "旅行" }] }))
    .mockResolvedValueOnce(Response.json({ items: [postcard] })).mockResolvedValueOnce(Response.json(portrait));
  vi.stubGlobal("fetch", fetcher);
  expect(await templateApi.categories(signal())).toEqual([{ categoryId: "travel", name: "旅行" }]);
  expect((await templateApi.list("travel", signal()))[0].templateId).toBe(postcard.templateId);
  expect(await templateApi.detail(portrait.templateId, signal())).toEqual(portrait);
  expect(fetcher.mock.calls.map((call) => call[0])).toEqual(["/api/v1/templates/categories", "/api/v1/templates?categoryId=travel", "/api/v1/templates/portrait-card"]);
});

test.each([
  { ...portrait, coverUrl: "https://external.example/private" },
  { ...portrait, templateId: "other-template" },
  { ...portrait, requirements: { ...portrait.requirements, requiredImageCount: 0 } },
  { ...portrait, requirements: { ...portrait.requirements, maxFileSizeBytes: 8388609 } },
  { ...portrait, requirements: { ...portrait.requirements, acceptedMimeTypes: ["image/svg+xml"] } },
])("rejects malformed detail data instead of enabling upload: %j", async (value) => {
  vi.stubGlobal("fetch", vi.fn().mockResolvedValue(Response.json(value)));
  await expect(templateApi.detail(portrait.templateId, signal())).rejects.toMatchObject({ code: "INVALID_RESPONSE" });
});

test("rejects duplicate IDs, wrong category results and unsafe paths", async () => {
  const fetcher = vi.fn().mockResolvedValueOnce(Response.json({ items: [portrait, portrait] }))
    .mockResolvedValueOnce(Response.json({ items: [portrait] }));
  vi.stubGlobal("fetch", fetcher);
  await expect(templateApi.list(null, signal())).rejects.toMatchObject({ code: "INVALID_RESPONSE" });
  await expect(templateApi.list("travel", signal())).rejects.toMatchObject({ code: "INVALID_RESPONSE" });
  await expect(templateApi.detail("../auth/csrf", signal())).rejects.toMatchObject({ code: "INVALID_REQUEST" });
  expect(fetcher).toHaveBeenCalledTimes(2);
});
