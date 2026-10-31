import { ApiError, isRecord } from "@/lib/api-client";

export const TASK_STAGES = ["CHECK_ASSETS", "GENERATE", "FINALIZE"] as const;
export type TaskStage = typeof TASK_STAGES[number];
type TaskFacts = {
  taskId: string; ownerId: string; templateId: string; revision: number; createdAt: string; updatedAt: string;
};
export type TaskView = TaskFacts & (
  | { status: "PENDING"; currentStage: null; result: null; failure: null }
  | { status: "RUNNING"; currentStage: TaskStage; result: null; failure: null }
  | { status: "SUCCEEDED"; currentStage: "FINALIZE"; result: { previewUrl: string; mediaType: "image/jpeg" }; failure: null }
  | { status: "FAILED"; currentStage: TaskStage; result: null; failure: { kind: "STAGE"; stage: TaskStage; code: string; message: string } }
  | { status: "FAILED"; currentStage: null; result: null; failure: { kind: "DISPATCH"; stage: null; code: "DISPATCH_REJECTED"; message: string } }
);
export type TaskAccepted = { taskId: string; ownerId: string; createdAt: string };
const uuid = /^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/;
const stageCodes = ["MOCK_STAGE_FAILURE", "ASSET_UNAVAILABLE", "GENERATION_FAILED", "RESULT_UNAVAILABLE", "STAGE_EXECUTION_FAILED"];
export function isTaskId(value: unknown): value is string { return typeof value === "string" && uuid.test(value); }
export function taskContractError() { return new ApiError("任务响应无法识别，请重新查询", 200, "INVALID_RESPONSE"); }

/** Compare equivalent UTC spellings without discarding the database's microseconds. */
function instant(value: unknown): string {
  if (typeof value !== "string") throw taskContractError();
  const match = /^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.(\d{1,6}))?Z$/.exec(value);
  if (!match || !Number.isFinite(Date.parse(value)) || new Date(value).toISOString().slice(0, 19) !== match[1]) throw taskContractError();
  return `${match[1]}.${(match[2] ?? "").padEnd(6, "0")}Z`;
}
function stage(value: unknown): value is TaskStage { return TASK_STAGES.some((item) => item === value); }

export function parseTaskAccepted(value: unknown, ownerId: string): TaskAccepted {
  if (!isRecord(value) || !isTaskId(value.taskId) || value.ownerId !== ownerId) throw taskContractError();
  return { taskId: value.taskId, ownerId, createdAt: instant(value.createdAt) };
}

export function parseTaskView(value: unknown, ownerId: string, taskId?: string): TaskView {
  if (!isRecord(value) || !isTaskId(value.taskId) || (taskId !== undefined && value.taskId !== taskId)
      || value.ownerId !== ownerId || typeof value.templateId !== "string" || !/^[a-z0-9][a-z0-9-]{0,63}$/.test(value.templateId)
      || typeof value.revision !== "number" || !Number.isSafeInteger(value.revision) || value.revision < 0) throw taskContractError();
  const facts: TaskFacts = {
    taskId: value.taskId, ownerId, templateId: value.templateId, revision: value.revision,
    createdAt: instant(value.createdAt), updatedAt: instant(value.updatedAt),
  };
  if (value.status === "PENDING" && value.currentStage === null && value.result === null && value.failure === null) {
    return { ...facts, status: "PENDING", currentStage: null, result: null, failure: null };
  }
  if (value.status === "RUNNING" && stage(value.currentStage) && value.result === null && value.failure === null) {
    return { ...facts, status: "RUNNING", currentStage: value.currentStage, result: null, failure: null };
  }
  if (value.status === "SUCCEEDED" && value.currentStage === "FINALIZE" && value.failure === null && isRecord(value.result)
      && value.result.previewUrl === `/api/v1/tasks/${value.taskId}/result` && value.result.mediaType === "image/jpeg") {
    return { ...facts, status: "SUCCEEDED", currentStage: "FINALIZE", result: { previewUrl: value.result.previewUrl, mediaType: "image/jpeg" }, failure: null };
  }
  if (value.status === "FAILED" && value.result === null && isRecord(value.failure)
      && typeof value.failure.message === "string" && value.failure.message.trim()) {
    const failure = value.failure;
    if (value.currentStage === null && failure.kind === "DISPATCH" && failure.stage === null && failure.code === "DISPATCH_REJECTED") {
      return { ...facts, status: "FAILED", currentStage: null, result: null,
        failure: { kind: "DISPATCH", stage: null, code: "DISPATCH_REJECTED", message: failure.message as string } };
    }
    if (stage(value.currentStage) && failure.kind === "STAGE" && failure.stage === value.currentStage
        && typeof failure.code === "string" && stageCodes.includes(failure.code)) {
      return { ...facts, status: "FAILED", currentStage: value.currentStage, result: null,
        failure: { kind: "STAGE", stage: value.currentStage, code: failure.code, message: failure.message as string } };
    }
  }
  throw taskContractError();
}

export function isTerminal(task: TaskView | null): boolean { return task?.status === "SUCCEEDED" || task?.status === "FAILED"; }
