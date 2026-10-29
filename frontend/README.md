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
npm run build
HOSTNAME=127.0.0.1 PORT=3000 npm run start
```

`typecheck` 先执行 `next typegen`，因此全新检出后也可在 build 前检查类型。
`postbuild` 将静态资源和 public 复制进 standalone 产物；`start` 直接运行该产物。
没有外部字体、业务页面、API 客户端或环境变量依赖。
后续目录规划见 `../docs/architecture.md`，Agent 还需阅读本目录 `AGENTS.md`。
