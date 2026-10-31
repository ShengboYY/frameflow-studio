import { afterEach, expect, test, vi } from "vitest";
import { accountA, accountB } from "@/test/fixtures";
import { TASK_A, TASK_B, taskSnapshot } from "@/test/task-fixtures";
import { taskApi } from "./task-api";
import { parseTaskAccepted, parseTaskView } from "./task-contract";

afterEach(() => vi.unstubAllGlobals());
const signal = () => new AbortController().signal;

test.each([
  taskSnapshot(TASK_A, "PENDING", null, 0),
  taskSnapshot(), taskSnapshot(TASK_A, "RUNNING", "GENERATE", 2), taskSnapshot(TASK_A, "RUNNING", "FINALIZE", 3),
  taskSnapshot(TASK_A, "SUCCEEDED", "FINALIZE", 4), taskSnapshot(TASK_A, "FAILED", null, 1),
  taskSnapshot(TASK_A, "FAILED", "CHECK_ASSETS", 2), taskSnapshot(TASK_A, "FAILED", "GENERATE", 3), taskSnapshot(TASK_A, "FAILED", "FINALIZE", 4),
])("parses the Java snapshot combination $status/$currentStage as a successful read", async (snapshot) => {
  const fetcher = vi.fn().mockResolvedValue(Response.json(snapshot));
  vi.stubGlobal("fetch", fetcher);
  expect(await taskApi.detail(TASK_A, accountA.ownerId, signal())).toEqual(snapshot);
  expect(fetcher).toHaveBeenCalledTimes(1);
  expect(fetcher.mock.calls[0][0]).toBe(`/api/v1/tasks/${TASK_A}`);
  expect(fetcher.mock.calls[0][1]).toMatchObject({ cache: "no-store", credentials: "same-origin" });
});

test("history is fully parsed, has no owner query parameter, and keeps per-task revisions", async () => {
  const tasks = [taskSnapshot(TASK_A, "SUCCEEDED", "FINALIZE", 4), taskSnapshot(TASK_B)];
  const fetcher = vi.fn().mockResolvedValue(Response.json({ items: tasks }));
  vi.stubGlobal("fetch", fetcher);
  expect(await taskApi.history(accountA.ownerId, signal())).toEqual(tasks);
  expect(fetcher.mock.calls[0][0]).toBe("/api/v1/tasks");
});

test.each([
  { ...taskSnapshot(), taskId: "../auth/session" },
  { ...taskSnapshot(), ownerId: accountB.ownerId },
  { ...taskSnapshot(), templateId: "../templates" },
  { ...taskSnapshot(), revision: -1 }, { ...taskSnapshot(), revision: 1.5 }, { ...taskSnapshot(), revision: Number.MAX_SAFE_INTEGER + 1 },
  { ...taskSnapshot(), status: "UNKNOWN" }, { ...taskSnapshot(), currentStage: "NONE" },
  { ...taskSnapshot(), result: undefined }, { ...taskSnapshot(), failure: undefined },
  { ...taskSnapshot(), createdAt: "2026-02-30T00:00:00Z" },
  { ...taskSnapshot(), updatedAt: "2026-10-31T03:00:00+00:00" },
  { ...taskSnapshot(TASK_A, "SUCCEEDED", "FINALIZE", 4), currentStage: "GENERATE" },
  { ...taskSnapshot(TASK_A, "SUCCEEDED", "FINALIZE", 4), result: { previewUrl: "https://external.example/private", mediaType: "image/jpeg" } },
  { ...taskSnapshot(TASK_A, "FAILED", "GENERATE", 3), failure: { kind: "STAGE", stage: "FINALIZE", code: "MOCK_STAGE_FAILURE", message: "失败" } },
  { ...taskSnapshot(TASK_A, "FAILED", null, 1), failure: { kind: "DISPATCH", stage: null, code: "UNKNOWN", message: "失败" } },
])("rejects invalid snapshot data without inventing a task state: %j", async (value) => {
  vi.stubGlobal("fetch", vi.fn().mockResolvedValue(Response.json(value)));
  await expect(taskApi.detail(TASK_A, accountA.ownerId, signal())).rejects.toMatchObject({ code: "INVALID_RESPONSE" });
});

test("rejects a different resource ID, duplicate history entries and foreign-owner history atomically", async () => {
  vi.stubGlobal("fetch", vi.fn().mockResolvedValueOnce(Response.json(taskSnapshot(TASK_B)))
    .mockResolvedValueOnce(Response.json({ items: [taskSnapshot(), taskSnapshot()] }))
    .mockResolvedValueOnce(Response.json({ items: [taskSnapshot(), taskSnapshot(TASK_B, "PENDING", null, 0, accountB.ownerId)] })));
  await expect(taskApi.detail(TASK_A, accountA.ownerId, signal())).rejects.toMatchObject({ code: "INVALID_RESPONSE" });
  await expect(taskApi.history(accountA.ownerId, signal())).rejects.toMatchObject({ code: "INVALID_RESPONSE" });
  await expect(taskApi.history(accountA.ownerId, signal())).rejects.toMatchObject({ code: "INVALID_RESPONSE" });
});

test("normalizes equivalent UTC timestamps while preserving microseconds and accepted metadata", () => {
  const value = { ...taskSnapshot(), createdAt: "2026-10-31T03:00:00Z", updatedAt: "2026-10-31T03:00:05.123456Z" };
  expect(parseTaskView(value, accountA.ownerId).createdAt).toBe("2026-10-31T03:00:00.000000Z");
  expect(parseTaskView(value, accountA.ownerId).updatedAt).toBe(value.updatedAt);
  expect(parseTaskAccepted(value, accountA.ownerId)).toEqual({ taskId: TASK_A, ownerId: accountA.ownerId, createdAt: "2026-10-31T03:00:00.000000Z" });
  expect(() => parseTaskAccepted(value, accountB.ownerId)).toThrow();
});

test("submission sends exactly the fixed ordered inputs and current CSRF, accepting only 202", async () => {
  const body = { templateId: "travel-postcard", assetIds: [TASK_B, TASK_A, TASK_A] };
  const accepted = { taskId: TASK_A, ownerId: accountA.ownerId, createdAt: "2026-10-31T03:00:00Z" };
  const fetcher = vi.fn().mockResolvedValueOnce(Response.json(accepted, { status: 202 })).mockResolvedValueOnce(Response.json(accepted));
  vi.stubGlobal("fetch", fetcher);
  expect(await taskApi.submit(body, accountA.ownerId, { headerName: "X-CSRF-TOKEN", token: "test-token" }, signal())).toMatchObject({ taskId: TASK_A });
  expect(fetcher.mock.calls[0]).toEqual(["/api/v1/tasks", expect.objectContaining({ method: "POST", body: JSON.stringify(body), credentials: "same-origin", cache: "no-store", headers: { "Content-Type": "application/json", "X-CSRF-TOKEN": "test-token" } })]);
  await expect(taskApi.submit(body, accountA.ownerId, { headerName: "X-CSRF-TOKEN", token: "test-token" }, signal())).rejects.toMatchObject({ code: "INVALID_RESPONSE" });
});

test("submission rejects foreign accepted identity and never retries an unknown write", async () => {
  const fetcher = vi.fn().mockResolvedValueOnce(Response.json({ taskId: TASK_A, ownerId: accountB.ownerId, createdAt: "2026-10-31T03:00:00Z" }, { status: 202 }))
    .mockResolvedValueOnce(Response.json({ error: { code: "SUBMISSION_OUTCOME_UNKNOWN", message: "未确认", details: [], taskId: TASK_A }, requestId: "test-request" }, { status: 503 }));
  vi.stubGlobal("fetch", fetcher);
  const submit = () => taskApi.submit({ templateId: "portrait-card", assetIds: [TASK_A] }, accountA.ownerId, { headerName: "X-CSRF-TOKEN", token: "test-token" }, signal());
  await expect(submit()).rejects.toMatchObject({ code: "INVALID_RESPONSE" });
  await expect(submit()).rejects.toMatchObject({ code: "SUBMISSION_OUTCOME_UNKNOWN", taskId: TASK_A });
  expect(fetcher).toHaveBeenCalledTimes(2);
});

test.each(["application/json", "text/html", "image/svg+xml", "image/png", "empty"])('result rejects %s without creating display URLs', async (contentType) => {
  vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(contentType === "empty" ? null : "body", { headers: { "Content-Type": contentType === "empty" ? "image/jpeg" : contentType } })));
  await expect(taskApi.result(TASK_A, signal())).rejects.toMatchObject({ code: "INVALID_RESPONSE" });
});

test("result returns an authenticated no-store JPEG blob", async () => {
  const fetcher = vi.fn().mockResolvedValue(new Response(new Uint8Array([255, 216, 255, 217]), { headers: { "Content-Type": "image/jpeg" } }));
  vi.stubGlobal("fetch", fetcher);
  expect((await taskApi.result(TASK_A, signal())).size).toBe(4);
  expect(fetcher.mock.calls[0]).toEqual([`/api/v1/tasks/${TASK_A}/result`, expect.objectContaining({ credentials: "same-origin", cache: "no-store", headers: { Accept: "image/jpeg" } })]);
});
