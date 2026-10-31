# 在容器中复现 API 流程

先按 [README 的快速运行步骤](../README.md#快速运行)启动 Compose，并启用 `MOCK_CONTROLS_ENABLED=true`。以下命令在项目根目录的 Bash / zsh 终端执行，交互输入自己设置的 demo-a 密码（不回显）。Node.js 程序在已有前端容器内运行，使用内置 fetch，不安装额外客户端依赖。

```bash
printf '输入 demo-a 密码（不回显）：'
read -r -s DEMO_A_PASSWORD
export DEMO_A_PASSWORD
docker compose exec -T -e DEMO_A_PASSWORD frontend node --input-type=module <<'JS'
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
assert(process.env.DEMO_A_PASSWORD, '请先提供 demo-a 的密码环境变量');
const base = 'http://127.0.0.1:3000/api/v1';
let cookie = '';
async function request(path, init = {}) {
  const response = await fetch(base + path, {
    ...init, headers: { Cookie: cookie, ...init.headers }
  });
  for (const value of response.headers.getSetCookie()) {
    if (value.startsWith('FF_SESSION=')) cookie = value.split(';')[0];
  }
  assert(response.ok, `${path}: HTTP ${response.status}`);
  return response;
}
async function json(path, init) { return (await request(path, init)).json(); }
let csrf = await json('/auth/csrf');
await json('/auth/login', {
  method: 'POST', headers: { 'X-CSRF-TOKEN': csrf.token },
  body: new URLSearchParams({ username: 'demo-a', password: process.env.DEMO_A_PASSWORD })
});
csrf = await json('/auth/csrf');
const form = new FormData();
form.set('templateId', 'portrait-card');
form.set('file', new Blob([await readFile('public/demo-examples/portrait-source.jpg')],
  { type: 'image/jpeg' }), 'portrait-source.jpg');
const asset = await json('/assets', {
  method: 'POST', headers: { 'X-CSRF-TOKEN': csrf.token }, body: form
});
const tasks = [];
for (const stage of [null, 'CHECK_ASSETS', 'GENERATE', 'FINALIZE']) {
  const accepted = await json('/tasks', {
    method: 'POST',
    headers: { 'X-CSRF-TOKEN': csrf.token, 'Content-Type': 'application/json' },
    body: JSON.stringify({ templateId: 'portrait-card', assetIds: [asset.assetId],
      simulation: { failAtStage: stage, stageDelayMs: 1200 } })
  });
  tasks.push({ id: accepted.taskId, stage });
  console.log('已受理', accepted.taskId, stage ?? '正常成功');
}
for (const task of tasks) {
  const deadline = Date.now() + 150000;
  let view;
  do {
    view = await json('/tasks/' + task.id);
    if (['SUCCEEDED', 'FAILED'].includes(view.status)) break;
    assert(Date.now() < deadline, '等待任务结束超时');
    await new Promise(resolve => setTimeout(resolve, 1000));
  } while (true);
  assert.equal(view.status, task.stage ? 'FAILED' : 'SUCCEEDED');
  if (task.stage) assert.equal(view.failure.stage, task.stage);
  else assert((await request('/tasks/' + task.id + '/result')).headers.get('content-type').startsWith('image/jpeg'));
  console.log(view.taskId, view.status, view.failure?.stage ?? '图片可读');
}
await request('/auth/logout', { method: 'POST', headers: { 'X-CSRF-TOKEN': csrf.token } });
JS
unset DEMO_A_PASSWORD
```

程序会连续受理四个独立任务，再核对成功图片和各失败阶段；每个 POST 只发送一次。可把 `stageDelayMs: 1200` 改为 0～30000 的整数来观察不同耗时。程序退出登录不取消任务；在网页重新登录同一账号、刷新“我的记录”可查看这些服务器记录。失败注入后的后续零调用由后端 Case 测试验证，单靠最终查询结果不宣称证明内部调用顺序。
