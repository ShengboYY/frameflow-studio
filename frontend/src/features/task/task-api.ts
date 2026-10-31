import { ApiError, apiRequest, isRecord, jsonBody } from "@/lib/api-client";
import type { CsrfView } from "@/features/identity/auth-api";
import { isTaskId, parseTaskAccepted, parseTaskView, taskContractError, type TaskAccepted, type TaskSimulation, type TaskView } from "./task-contract";

export const taskApi = {
  async submit(input: { templateId: string; assetIds: readonly string[]; simulation?: TaskSimulation }, ownerId: string, csrf: CsrfView, signal: AbortSignal): Promise<TaskAccepted> {
    return apiRequest("/api/v1/tasks", {
      method: "POST", signal, headers: { "Content-Type": "application/json", [csrf.headerName]: csrf.token },
      body: JSON.stringify({ templateId: input.templateId, assetIds: input.assetIds,
        ...(input.simulation ? { simulation: { failAtStage: input.simulation.failAtStage, stageDelayMs: input.simulation.stageDelayMs } } : {}) }),
    }, async (response) => {
      if (response.status !== 202) throw new ApiError("受理响应无法识别，请先核对个人记录", response.status, "INVALID_RESPONSE");
      return parseTaskAccepted(await jsonBody(response), ownerId);
    });
  },
  async result(taskId: string, signal: AbortSignal): Promise<Blob> {
    if (!isTaskId(taskId)) throw taskContractError();
    return apiRequest(`/api/v1/tasks/${taskId}/result`, { signal, headers: { Accept: "image/jpeg" } }, async (response) => {
      if (response.headers.get("content-type")?.split(";")[0].trim().toLowerCase() !== "image/jpeg") {
        throw new ApiError("结果图片响应无法识别", response.status, "INVALID_RESPONSE");
      }
      const blob = await response.blob();
      if (!blob.size) throw new ApiError("结果图片内容为空", response.status, "INVALID_RESPONSE");
      return blob;
    });
  },
  async history(ownerId: string, signal: AbortSignal): Promise<TaskView[]> {
    const value = await apiRequest("/api/v1/tasks", { signal }, jsonBody);
    if (!isRecord(value) || !Array.isArray(value.items)) throw taskContractError();
    const tasks = value.items.map((item) => parseTaskView(item, ownerId));
    if (new Set(tasks.map((task) => task.taskId)).size !== tasks.length) throw taskContractError();
    return tasks;
  },
  async detail(taskId: string, ownerId: string, signal: AbortSignal): Promise<TaskView> {
    if (!isTaskId(taskId)) throw taskContractError();
    return parseTaskView(await apiRequest(`/api/v1/tasks/${taskId}`, { signal }, jsonBody), ownerId, taskId);
  },
};
