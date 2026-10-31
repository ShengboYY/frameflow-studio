import { isTerminal, TASK_STAGES, type TaskAccepted, type TaskView } from "./task-contract";

export type QueryError = { code: string; message: string; requestId?: string };
export type QueryState = { loading: boolean; paused: boolean; error: QueryError | null };
export type TaskEntry = { snapshot: TaskView | null; accepted: TaskAccepted | null; query: QueryState };
export type TaskState = {
  active: boolean;
  tasksById: Readonly<Record<string, TaskEntry>>;
  history: { loading: boolean; loaded: boolean; error: QueryError | null };
};
export const idleQuery = (): QueryState => ({ loading: false, paused: false, error: null });
export const emptyEntry = (): TaskEntry => ({ snapshot: null, accepted: null, query: idleQuery() });
export const initialTaskState = (active = false): TaskState => ({ active, tasksById: {}, history: { loading: false, loaded: false, error: null } });
export const TASK_CONFLICT = "TASK_SNAPSHOT_CONFLICT";

function sameSnapshot(left: TaskView, right: TaskView) {
  return left.taskId === right.taskId && left.ownerId === right.ownerId && left.templateId === right.templateId
    && left.createdAt === right.createdAt && left.updatedAt === right.updatedAt && left.revision === right.revision
    && left.status === right.status && left.currentStage === right.currentStage
    && left.result?.previewUrl === right.result?.previewUrl && left.result?.mediaType === right.result?.mediaType
    && left.failure?.kind === right.failure?.kind && left.failure?.stage === right.failure?.stage
    && left.failure?.code === right.failure?.code && left.failure?.message === right.failure?.message;
}

function reachable(left: TaskView, right: TaskView) {
  if (isTerminal(left) || right.status === "PENDING") return false;
  const from = left.status === "PENDING" ? 0 : TASK_STAGES.indexOf(left.currentStage!) + 1;
  const to = right.status === "SUCCEEDED" ? 4 : right.status === "RUNNING" ? TASK_STAGES.indexOf(right.currentStage) + 1
    : right.currentStage === null ? 1 : TASK_STAGES.indexOf(right.currentStage) + 2;
  if (right.status === "FAILED" && right.currentStage === null && left.status !== "PENDING") return false;
  if (right.status === "FAILED" && right.currentStage !== null && TASK_STAGES.indexOf(right.currentStage) + 1 < from) return false;
  return to > from && right.revision - left.revision >= to - from;
}

function conflict(entry: TaskEntry): TaskEntry {
  return { ...entry, query: { loading: false, paused: true, error: { code: TASK_CONFLICT, message: "任务状态响应不一致，已保留现有信息，请重新查询" } } };
}

type TaskAction =
  | { type: "reset"; active: boolean }
  | { type: "accepted"; value: TaskAccepted }
  | { type: "snapshot"; value: TaskView }
  | { type: "query"; taskId: string; value: QueryState }
  | { type: "history"; value: TaskState["history"] };

/** No requests or timers here. Revision ordering and immutable task facts are shared by all reads. */
export function taskReducer(state: TaskState, action: TaskAction): TaskState {
  if (action.type === "reset") return initialTaskState(action.active);
  if (action.type === "history") return { ...state, history: action.value };
  const id = action.type === "query" ? action.taskId : action.value.taskId;
  const previous = state.tasksById[id] ?? emptyEntry();
  let next: TaskEntry;
  if (action.type === "query") next = { ...previous, query: action.value };
  else if (action.type === "accepted") {
    const known = previous.snapshot ?? previous.accepted;
    next = known ? known.ownerId === action.value.ownerId && known.createdAt === action.value.createdAt ? previous : conflict(previous)
      : { ...previous, accepted: action.value };
  } else {
    const candidate = action.value, known = previous.snapshot;
    if (known && candidate.revision < known.revision) return state;
    const identity = known ?? previous.accepted;
    if (identity && (identity.ownerId !== candidate.ownerId || identity.createdAt !== candidate.createdAt)) next = conflict(previous);
    else if (known && (known.templateId !== candidate.templateId
        || (candidate.revision === known.revision ? !sameSnapshot(known, candidate) : !reachable(known, candidate)))) next = conflict(previous);
    else next = known && candidate.revision === known.revision ? previous : { ...previous, snapshot: candidate, accepted: null };
  }
  return next === previous ? state : { ...state, tasksById: { ...state.tasksById, [id]: next } };
}
