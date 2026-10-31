import { apiRequest, isRecord, jsonBody } from "@/lib/api-client";
import { isTaskId, parseTaskView, taskContractError, type TaskView } from "./task-contract";

export const taskApi = {
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
