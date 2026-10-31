import { parseTaskView, type TaskStage, type TaskView, type TaskAccepted } from "@/features/task/task-contract";
import { accountA } from "./fixtures";

export const TASK_A = "a2dd291e-4673-4500-8fe3-97a71a6cbe40";
export const TASK_B = "a2dd291e-4673-4500-8fe3-97a71a6cbe41";
export const CREATED_AT = "2026-10-31T03:00:00.000000Z";
export function accepted(taskId = TASK_A, ownerId = accountA.ownerId): TaskAccepted { return { taskId, ownerId, createdAt: CREATED_AT }; }
export function taskSnapshot(taskId = TASK_A, status: TaskView["status"] = "RUNNING", currentStage: TaskStage | null = "CHECK_ASSETS", revision = 1, ownerId = accountA.ownerId): TaskView {
  return parseTaskView({
    taskId, ownerId, templateId: "portrait-card", status, currentStage, revision,
    createdAt: CREATED_AT, updatedAt: "2026-10-31T03:00:05.123456Z",
    result: status === "SUCCEEDED" ? { previewUrl: `/api/v1/tasks/${taskId}/result`, mediaType: "image/jpeg" } : null,
    failure: status === "FAILED" ? currentStage === null
      ? { kind: "DISPATCH", stage: null, code: "DISPATCH_REJECTED", message: "任务队列已满" }
      : { kind: "STAGE", stage: currentStage, code: "MOCK_STAGE_FAILURE", message: "指定阶段失败" } : null,
  }, ownerId);
}
