# FrameFlow Studio Frontend

由官方 `create-next-app@16.4.3` 空模板生成：App Router、`src/`、TypeScript、ESLint。
具体技术版本、验证结果和全栈启动方式见根目录 `README.md`。

使用 Node.js 22 LTS，在本目录运行：

```bash
npm ci
JAVA_API_ORIGIN=http://127.0.0.1:8080 npm run dev
```

工程检查与生产模式：

```bash
npm run lint
npm run typecheck
npm run test:ci
npm run build
JAVA_API_ORIGIN=http://127.0.0.1:8080 HOSTNAME=127.0.0.1 PORT=3000 npm run start
```

`typecheck` 先执行 `next typegen`，因此全新检出后也可在 build 前检查类型。
`postbuild` 将静态资源和 public 复制进 standalone 产物；`start` 直接运行该产物。
`/login` 已提供登录表单，根布局通过 AuthProvider 核对会话并显示用户名/退出入口。
登录成功目前返回 `/`；`/templates` 提供公开分类/卡片，`/templates/[templateId]` 提供详情、真实素材要求和登录后的上传交互。
素材准备完成会显示就绪反馈；任务查询/合并/独立轮询已在 IP-13 实现，制作提交、任务卡片和个人记录的页面接入仍归 IP-14。
`src/app/api/v1/[...path]/route.ts` 在 Node runtime 按白名单转发现有 14 项 API。
`JAVA_API_ORIGIN` 为必填服务端运行时 origin，不可带路径/凭证，不使用 NEXT_PUBLIC；构建不依赖 Java 在线。
保持原始表单、JSON 和 multipart 字节，逐条保留 Set-Cookie，图片流/状态/Location/requestId 透传；全部代理 no-store。
POST 不重放；代理等待响应头 60 秒，客户端读取及解析预算 30 秒。未知写入结果先核对会话/记录，不推断后台失败。
会话和 CSRF 只驻留内存；退出和跨标签页认证通知先失效旧身份作用域。
匿名页面只核对会话，不预取 CSRF；用户提交登录时才获取新 token，再确认会话仍匿名及操作作用域有效，随后仅提交一次。
这样退出后的多个标签页不会因后台 CSRF 读取创建不同匿名 Session；准备期间的账号变化/卸载会取消旧读取并阻止旧登录。
focus 只核对账号，同一账号保留素材、原 CSRF 和任务跟踪；账号变化或会话失效才清理旧作用域。
focus 查询失败提示异常、保留状态并在下次聚焦重试；初始化/显式会话复核失败仍可手动恢复。
后续私有请求应捕获 `captureWriteContext()` 并用 `isCurrent()` 检查全部回调；401/403 通过 `handleRequestError` 失效当前作用域，不能刷新 token 后重放旧请求。
后续目录规划见 `../docs/architecture.md`，Agent 还需阅读本目录 `AGENTS.md`。

## 模板与素材（IP-12）

`features/template` 通过同源 API 获取目录和规则，区分加载、空结果、不存在与错误，分类/详情旧响应不会覆盖新选择。
`features/asset` 用原始 File + FormData 上传，不手写 multipart Content-Type；本地 MIME/大小检查不替代 Java 的真实图片校验。
每个位置有独立选择版本、请求序号、AbortController 与 blob；替换/移除、模板/身份切换及离页会失效旧绑定并回收预览。
私有上传响应还校验 owner、媒体类型、字节数和固定内容 URL；401/403 核对会话，503 只影响当前上传，任何 POST 都不自动重试。
`useUploadSlots` 挂载在 templateId + identityEpoch 作用域下，`getPreparedInputs()` 仅在全部位置就绪时返回有序服务器 assetIds；
该方法每次同步核对当前身份和选择，供 IP-14 提交时读取，不把本地 blob 地址当素材 ID。前端替换/移除不调用删除接口。

## 任务查询与轮询（IP-13）

`features/task/task-contract.ts` 是 TaskView/TaskAccepted 的客户端契约，校验字段、归属、资源 ID、版本、UTC 时间及状态/结果/失败组合；保留微秒精度。
`task-api.ts` 只读取 Java 的个人历史/单项任务；不传 owner 查询参数、不创建任务、不推进服务端状态。
`useTaskTracking()` 应在需要跟踪的 Client 页面中调用；每个挂载页面及身份 epoch 独占一个 TaskController，不在根 AuthProvider 挂全局轮询。
纯 `taskReducer` 同步合并 `tasksById`，controller 通过 useSyncExternalStore 通知 hook；查询请求状态与服务端快照分开。
`trackAccepted(value)` 接收真实 202 元数据，插入等待快照的占位并立即读取，不假造 PENDING；`refreshTask(id)` 和 `refreshHistory()` 提供显式恢复。
每任务请求结束后递归间隔 2 秒；终态、空集合/全部结束无自动请求；503/404/超时仅暂停相应查询，401/403 交身份作用域复核。
历史读取按 ID 合并而非覆盖集合，晚到列表不会移除新占位；小 revision 丢弃，同版本矛盾、终态回退或不合法阶段跃迁保留已有快照并提示契约异常。
页面/身份清理先失效 generation 再取消请求/timer；全部回调同时检查身份、页面和请求序号，A→B→A 也不复用旧上下文。
这些内部能力已通过小型 React 宿主测试；业务页面、制作 POST、图片媒体和刷新恢复的完整组合归 IP-14。

## 测试入口（IP-01）

测试依赖要求 Node.js 22.13.0 或更高的 22.x；本机使用 22.21.1，不改变 Node 22 LTS 版本线。
Vitest、Vite、React 插件、jsdom 和 Testing Library 均在 package.json 中精确锁定。
配置遵循安装版 Next.js 的 `node_modules/next/dist/docs/01-app/02-guides/testing/vitest.md`；
Vite 8 自带 `resolve.tsconfigPaths`，直接复用 tsconfig 的 `@/*`，无需额外路径解析插件。

```bash
npm test             # 本地 watch
npm run test:ci      # 与 CI 相同，单次运行，失败或未发现测试均非零退出
```

`vitest.config.mts` 使用 jsdom；`vitest.setup.ts` 注册 DOM 断言并在每项测试后清理 DOM。
测试显式导入 Vitest API，不启用 globals；禁止 `.only`，不配置空测试放行。
JUnit 报告生成于 `test-results/junit.xml`，已被根目录忽略规则覆盖，不提交生成物。
GitHub Actions 在成功或失败后保存报告为 `frontend-test-reports`，报告缺失也会使步骤失败。

IP-11 已接入真实 loopback HTTP 上游的 Route Handler 测试、客户端超时/解析、身份作用域和登录组件测试。
IP-12 增加模板/素材响应校验、分类/详情旧读取隔离、上传替换/移除/模板与 A→B→A 身份切换的成功/错误/finally、数量/类型/大小边界及 URL 清理测试。
HTTP 测试需允许监听 loopback 临时端口；不跳过环境失败。Java 实际编码与生产 Next 联调证据另见 V-11。
IP-13 已在同一 test:ci 入口加入 R-12 的多任务不串扰、查询异常与执行失败区分及轮询/清理行为测试；页面整合归 IP-14，浏览器共享 Cookie 的跨标签页人工验收仍归 IP-16。
此入口适用于同步组件和普通逻辑；异步 Server Components 仍需合适的集成/端到端验证，不能直接用本项渲染测试推断。
IP-01 实际命令、失败注入及未验证项见 `../docs/tasks.md` 的 V-01 记录。
