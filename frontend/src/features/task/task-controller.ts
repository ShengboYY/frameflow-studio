import { ApiError } from "@/lib/api-client";
import type { AuthSession } from "@/features/identity/auth-session";
import { taskApi } from "./task-api";
import { isTaskId, isTerminal, parseTaskAccepted, taskContractError, type TaskView } from "./task-contract";
import { idleQuery, initialTaskState, TASK_CONFLICT, taskReducer, type QueryError } from "./task-state";

export const TASK_POLL_INTERVAL_MS = 2000;
type IdentityScope = { ownerId: string; identityEpoch: number };
type TaskRequest = { generation: number; seq: number; controller: AbortController; baselineRevision: number | undefined };
type TaskResource = { seq: number; timer: ReturnType<typeof setTimeout> | null; request: TaskRequest | null };
type HistoryRequest = { generation: number; controller: AbortController; taskSequences: Map<string, number> };
const serverState = initialTaskState();

function queryError(cause: unknown): QueryError {
  return cause instanceof ApiError ? { code: cause.code, message: cause.code === "REQUEST_TIMEOUT" || cause.status === 504 ? "状态查询超时，请重试" : cause.message, requestId: cause.requestId }
    : { code: "REQUEST_FAILED", message: "状态查询暂不可用，请重试" };
}

/** One page/identity scope. The reducer owns facts; this instance owns only read lifecycles. */
export class TaskController {
  private state = initialTaskState();
  private active = false;
  private generation = 0;
  private resources = new Map<string, TaskResource>();
  private historyRequest: HistoryRequest | null = null;
  private unsubscribe: (() => void) | null = null;
  private listeners = new Set<() => void>();

  constructor(private readonly session: AuthSession, private readonly identity: IdentityScope) {}
  getSnapshot = () => this.state;
  getServerSnapshot = () => serverState;
  subscribe = (listener: () => void) => {
    this.listeners.add(listener);
    return () => { this.listeners.delete(listener); };
  };
  private send(action: Parameters<typeof taskReducer>[1]) {
    const next = taskReducer(this.state, action);
    if (next === this.state) return;
    this.state = next;
    this.listeners.forEach((listener) => listener());
  }
  private current(generation = this.generation) { return this.active && generation === this.generation && this.session.isCurrent(this.identity); }
  private resource(taskId: string) {
    let resource = this.resources.get(taskId);
    if (!resource) { resource = { seq: 0, timer: null, request: null }; this.resources.set(taskId, resource); }
    return resource;
  }
  private clearTimer(resource: TaskResource) { if (resource.timer !== null) clearTimeout(resource.timer); resource.timer = null; }
  private release(resource: TaskResource) {
    resource.seq++;
    this.clearTimer(resource);
    const previous = resource.request; resource.request = null;
    previous?.controller.abort();
  }
  private requestCurrent(resource: TaskResource, request: TaskRequest) {
    return this.current(request.generation) && resource.seq === request.seq && resource.request === request;
  }

  start = () => {
    if (this.active || !this.session.isCurrent(this.identity)) return;
    this.active = true; this.generation++;
    this.send({ type: "reset", active: true });
    this.unsubscribe = this.session.subscribe(() => { if (!this.session.isCurrent(this.identity)) this.stop(); });
    void this.refreshHistory();
  };
  stop = () => {
    // Invalidate callbacks before aborting; an uncooperative transport may still settle later.
    this.active = false; this.generation++;
    this.unsubscribe?.(); this.unsubscribe = null;
    const history = this.historyRequest; this.historyRequest = null;
    this.resources.forEach((resource) => this.release(resource)); this.resources.clear();
    history?.controller.abort();
    this.send({ type: "reset", active: false });
  };

  private schedule(taskId: string) {
    if (!this.current()) return;
    const entry = this.state.tasksById[taskId], resource = this.resource(taskId);
    if (!entry || entry.query.paused || isTerminal(entry.snapshot)) { this.clearTimer(resource); return; }
    if (resource.timer !== null || resource.request !== null) return;
    const generation = this.generation, seq = resource.seq;
    const timer = setTimeout(() => {
      if (!this.current(generation) || resource.seq !== seq || resource.timer !== timer) return;
      resource.timer = null;
      const latest = this.state.tasksById[taskId];
      if (!latest || latest.query.paused || isTerminal(latest.snapshot) || resource.request) return;
      void this.refreshTask(taskId);
    }, TASK_POLL_INTERVAL_MS);
    resource.timer = timer;
  }

  private receive(snapshot: TaskView, feedbackAllowed: boolean, source?: TaskRequest) {
    const id = snapshot.taskId, before = this.state.tasksById[id], resource = this.resource(id);
    this.send({ type: "snapshot", value: snapshot });
    const after = this.state.tasksById[id];
    if (after.query.error?.code === TASK_CONFLICT && after.query !== before?.query) {
      this.release(resource);
      return;
    }
    const advanced = after.snapshot !== before?.snapshot;
    if (advanced && resource.request !== source) this.release(resource);
    if (advanced || feedbackAllowed) this.send({ type: "query", taskId: id, value: idleQuery() });
    this.schedule(id);
  }

  refreshHistory = async (): Promise<void> => {
    if (!this.current()) return;
    const previous = this.historyRequest;
    const request: HistoryRequest = {
      generation: this.generation, controller: new AbortController(),
      taskSequences: new Map(Array.from(this.resources, ([id, resource]) => [id, resource.seq])),
    };
    this.historyRequest = request;
    previous?.controller.abort();
    this.send({ type: "history", value: { ...this.state.history, loading: true, error: null } });
    try {
      const tasks = await taskApi.history(this.identity.ownerId, request.controller.signal);
      if (!this.current(request.generation) || this.historyRequest !== request) return;
      for (const task of tasks) {
        const resource = this.resource(task.taskId);
        const feedbackAllowed = resource.request === null && resource.seq === (request.taskSequences.get(task.taskId) ?? 0);
        this.receive(task, feedbackAllowed);
      }
      this.send({ type: "history", value: { loading: false, loaded: true, error: null } });
    } catch (cause) {
      if (!this.current(request.generation) || this.historyRequest !== request) return;
      this.send({ type: "history", value: { ...this.state.history, loading: false, error: queryError(cause) } });
      void this.session.handleRequestError(cause, this.identity.identityEpoch);
    } finally {
      if (this.current(request.generation) && this.historyRequest === request) this.historyRequest = null;
    }
  };

  refreshTask = async (taskId: string): Promise<void> => {
    if (!this.current()) return;
    if (!isTaskId(taskId)) throw taskContractError();
    const resource = this.resource(taskId);
    this.release(resource);
    const request: TaskRequest = {
      generation: this.generation, seq: resource.seq, controller: new AbortController(),
      baselineRevision: this.state.tasksById[taskId]?.snapshot?.revision,
    };
    resource.request = request;
    this.send({ type: "query", taskId, value: { loading: true, paused: false, error: null } });
    try {
      const task = await taskApi.detail(taskId, this.identity.ownerId, request.controller.signal);
      if (!this.current(request.generation)) return;
      // Data uses revision even when another read superseded this request's feedback token.
      this.receive(task, this.requestCurrent(resource, request), request);
    } catch (cause) {
      if (!this.requestCurrent(resource, request) || this.state.tasksById[taskId]?.snapshot?.revision !== request.baselineRevision) return;
      // A history read begun before this failure must not silently resume the paused query.
      this.release(resource);
      this.send({ type: "query", taskId, value: { loading: false, paused: true, error: queryError(cause) } });
      void this.session.handleRequestError(cause, this.identity.identityEpoch);
    } finally {
      if (this.requestCurrent(resource, request)) {
        resource.request = null;
        this.schedule(taskId);
      }
    }
  };

  trackAccepted = (value: unknown): boolean => {
    if (!this.current()) return false;
    const accepted = parseTaskAccepted(value, this.identity.ownerId);
    this.send({ type: "accepted", value: accepted });
    const entry = this.state.tasksById[accepted.taskId], resource = this.resource(accepted.taskId);
    if (entry.query.error?.code === TASK_CONFLICT) { this.release(resource); return false; }
    if (!isTerminal(entry.snapshot) && !resource.request) void this.refreshTask(accepted.taskId);
    return true;
  };
}
