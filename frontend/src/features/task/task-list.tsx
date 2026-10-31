"use client";

import Link from "next/link";
import { useAuth } from "@/features/identity/auth-provider";
import type { TaskStage } from "./task-contract";
import type { useTaskTracking } from "./use-task-tracking";
import { ResultPreview } from "./result-preview";

const stages: Record<TaskStage, string> = { CHECK_ASSETS: "素材检查", GENERATE: "模拟生成", FINALIZE: "结果整理" };
const statuses = { PENDING: "等待处理", RUNNING: "处理中", SUCCEEDED: "制作成功", FAILED: "执行失败" };

export function TaskList({ tracking, templateId }: { tracking: ReturnType<typeof useTaskTracking>; templateId?: string }) {
  const { status, busy, identityEpoch } = useAuth();
  // Snapshot-less entries are this page's submissions/reconciliation reads; keep their immediate feedback.
  const entries = Object.entries(tracking.tasksById).filter(([, entry]) => !templateId || !entry.snapshot || entry.snapshot.templateId === templateId);
  return <section className="task-section" aria-labelledby="tasks-title">
    <div className="task-section-heading"><div><p className="eyebrow">HISTORY</p><h2 id="tasks-title">{templateId ? "本模板制作记录" : "我的制作记录"}{entries.length > 0 && <span className="record-count">{entries.length}</span>}</h2></div>
      {status === "authenticated" && !busy && <button className="refresh-button" type="button" disabled={tracking.history.loading} onClick={() => { void tracking.refreshHistory(); }}><span aria-hidden="true">↻</span> 刷新记录</button>}
    </div>
    {status === "anonymous" ? <div className="history-empty"><span className="empty-symbol" aria-hidden="true">▧</span><h3>你的创作，从这里开始</h3><p><Link href="/login">登录后查看记录</Link></p></div>
      : status !== "authenticated" || busy ? <p role="status">{status === "error" ? "会话查询暂不可用，请先重新核对会话。" : "正在核对登录状态…"}</p>
        : <>
          {tracking.history.loading && <p role="status">正在读取记录…</p>}
          {tracking.history.error && <p role="alert">记录读取暂不可用：{tracking.history.error.message}。可点击“刷新记录”重试。</p>}
          {tracking.history.loaded && !tracking.history.error && entries.length === 0 && <div className="history-empty"><span className="empty-symbol" aria-hidden="true">▧</span><h3>留一个位置，给你的灵感</h3><p>{templateId ? "暂无本模板制作记录，上传图片开始制作。" : "暂无制作记录，选择模板开始制作。"}</p></div>}
          <ul className="task-grid">
            {entries.map(([taskId, entry]) => {
              const task = entry.snapshot, createdAt = task?.createdAt ?? entry.accepted?.createdAt;
              return <li key={taskId}><article className={`task-card task-${task?.status.toLowerCase() ?? "awaiting"}`} aria-label={`任务 ${taskId}`}>
                <div className="task-visual">
                  {task?.status === "SUCCEEDED" ? <ResultPreview key={`${identityEpoch}:${taskId}:${task.result.previewUrl}`} taskId={taskId} />
                    : <div className="task-state-visual"><span className={task?.status === "RUNNING" ? "processing-ring" : "task-state-symbol"} aria-hidden="true">{task?.status === "FAILED" ? "!" : task?.status === "RUNNING" ? "" : "◷"}</span><p>{task?.status === "FAILED" ? "这次创作未能完成" : task?.status === "RUNNING" ? "正在让灵感成形" : task ? "创作已加入队列" : entry.accepted ? "任务已受理" : "等待核对任务记录"}</p></div>}
                  <p className={`task-status status-${task?.status.toLowerCase() ?? "awaiting"}`} role="status"><span className="status-dot" aria-hidden="true" />{task ? statuses[task.status] : entry.accepted ? "已受理，等待读取实际状态" : "等待核对任务记录"}</p>
                </div>
                <div className="task-card-details">
                  <p className="task-id">任务 {taskId}</p>
                  {task && <Link href={`/templates/${task.templateId}`}>模板：{task.templateId}</Link>}
                  {createdAt && <p>创建时间：<time dateTime={createdAt}>{new Date(createdAt).toLocaleString("zh-CN", { hour12: false })}</time></p>}
                  {task && <p>当前阶段：{task.currentStage ? stages[task.currentStage] : "尚未开始"}</p>}
                  {task?.status === "FAILED" && <p className="execution-failure">失败原因：{task.failure.message}（{task.failure.code}）</p>}
                  {entry.query.loading && <p role="status">正在查询任务状态…</p>}
                  {entry.query.error && <div role="alert"><p>状态查询暂不可用：{entry.query.error.message}。{entry.query.paused && "自动查询已暂停。"}</p>
                    <button type="button" disabled={entry.query.loading} onClick={() => { void tracking.refreshTask(taskId); }}>重新查询状态</button>
                  </div>}
                </div>
              </article></li>;
            })}
          </ul>
        </>}
  </section>;
}
