# FrameFlow Studio Frontend

由官方 `create-next-app@16.4.3` 空模板生成：App Router、`src/`、TypeScript、ESLint。
具体技术版本、验证结果和全栈启动方式见根目录 `README.md`。

使用 Node.js 22 LTS，在本目录运行：

```bash
npm ci
npm run dev
```

工程检查与生产模式：

```bash
npm run lint
npm run typecheck
npm run test:ci
npm run build
HOSTNAME=127.0.0.1 PORT=3000 npm run start
```

`typecheck` 先执行 `next typegen`，因此全新检出后也可在 build 前检查类型。
`postbuild` 将静态资源和 public 复制进 standalone 产物；`start` 直接运行该产物。
没有外部字体、业务页面、API 客户端或环境变量依赖。
后续目录规划见 `../docs/architecture.md`，Agent 还需阅读本目录 `AGENTS.md`。

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

当前只有 `src/app/page.test.tsx` 对真实占位页面的最小渲染检查，验证 TSX、路径别名、DOM 和断言接通。
它不证明业务功能或 R-12 多任务/查询异常要求；IP-11～14 将接入实际组件、Hook 和转发测试。
此入口适用于同步组件和普通逻辑；异步 Server Components 仍需合适的集成/端到端验证，不能直接用本项渲染测试推断。
IP-01 实际命令、失败注入及未验证项见 `../docs/tasks.md` 的 V-01 记录。
