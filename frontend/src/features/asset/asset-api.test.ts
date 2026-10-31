import { afterEach, expect, test, vi } from "vitest";
import { assetApi } from "./asset-api";
import { asset } from "@/test/fixtures";

afterEach(() => vi.unstubAllGlobals());
const file = new File(["image-test-bytes"], "input.png", { type: "image/png" });
const csrf = { headerName: "X-CSRF-TOKEN", token: "fake-token" } as const;

test("sends exactly templateId and file as FormData without replacing the browser boundary", async () => {
  const fetcher = vi.fn().mockResolvedValue(Response.json(asset(file), { status: 201 }));
  vi.stubGlobal("fetch", fetcher);
  expect(await assetApi.upload("portrait-card", file, csrf, new AbortController().signal)).toEqual(asset(file));
  const init = fetcher.mock.calls[0][1] as RequestInit;
  expect(init.method).toBe("POST");
  expect(new Headers(init.headers).get("content-type")).toBeNull();
  expect(new Headers(init.headers).get("x-csrf-token")).toBe("fake-token");
  const body = init.body as FormData;
  expect(Array.from(body.keys())).toEqual(["templateId", "file"]);
  expect(body.get("templateId")).toBe("portrait-card");
  expect((body.get("file") as File).name).toBe("input.png");
  expect(fetcher).toHaveBeenCalledTimes(1);
});

test.each([
  { ...asset(file), previewUrl: "https://external.example/input.png" },
  { ...asset(file), sizeBytes: file.size + 1 },
  { ...asset(file), assetId: "../tasks" },
  { ...asset(file), ownerId: "not-an-owner" },
  { ...asset(file), createdAt: "invalid" },
  { ...asset(file), mediaType: "text/html" },
])("rejects invalid upload metadata: %j", async (response) => {
  vi.stubGlobal("fetch", vi.fn().mockResolvedValue(Response.json(response, { status: 201 })));
  await expect(assetApi.upload("portrait-card", file, csrf, new AbortController().signal)).rejects.toMatchObject({ code: "INVALID_RESPONSE" });
});
