# FrameFlow Studio

全栈 Assessment 项目骨架，后续可逐步演进为 AI 素材制作平台。
**Assessment 原文已取得，R-01～R-14 需求分析已完成，见 [requirements.md](docs/requirements.md)。**
当前代码仍为 scaffolding，没有业务实现，业务验收尚未执行；具体领域模型、API、表结构和用户身份方案留待设计。

## 技术版本

| 技术 | 项目版本 / 约束 | 本次本机验证 |
| --- | --- | --- |
| Java | 17，编译 release 17 | JDK 17.0.12 |
| Spring Boot | 4.0.9 | 编译、测试、可执行 JAR 启动通过 |
| MyBatis Spring Boot Starter | 4.0.1 | 装配通过；底层 MyBatis 3.5.19 |
| MySQL | Docker `mysql:8.4` LTS | 未启动，未连接数据库 |
| MySQL Connector/J | 9.7.0，由 Boot BOM 管理 | 依赖解析通过 |
| Maven | 3.9.x（Boot 最低要求 3.6.3） | 3.9.10；Docker 构建配置使用 3.9.11 |
| Node.js | 22 LTS，见 `.nvmrc` 和 package engines | 22.21.1 |
| npm | 10.x，提交 package-lock.json | 10.9.4 |
| Next.js / create-next-app | 16.4.3 | App Router、Turbopack 构建通过 |
| React / React DOM | 19.3.0 | 模板配套版本，构建通过 |
| TypeScript | 5.9.3，strict | 类型检查通过 |
| ESLint / eslint-config-next | 9.39.5 / 16.4.3 | lint 通过；维护限制见下文 |
| Docker Compose | Compose v2 | 2.40.3 配置解析通过；daemon 未运行 |
| GitHub Actions | checkout v7 / setup-java v6 / setup-node v7 | 工作流已配置，未远程执行 |

Spring Boot 4.0、MyBatis Starter 4.0 与 Java 17 的组合已对照官方兼容说明并实际构建。
前端直接依赖固定精确版本，传递依赖由 lockfile 锁定；后端依赖由 Boot BOM 管理。
容器镜像固定兼容版本线，未锁定 digest，镜像构建和拉取尚未验证。

**已知维护限制：** 官方脚手架仍默认 ESLint 9，当前配套的 React/import/a11y 插件 peer
范围尚未覆盖 ESLint 10；本次保留兼容组合，不绕过 peer 约束。ESLint 9 已 EOL，
需要在上游插件兼容后升级，不能将此组合描述为全部依赖均受维护。
原因、替代方案与官方依据见 [ADR-002](docs/architecture-decisions.md)。

## 项目结构

```text
.
├── AGENTS.md                         # 统一开发约定和依赖规则
├── README.md
├── .env.example                      # 本地环境变量占位，无真实凭据
├── .gitignore                        # 保留原有配置，覆盖构建/依赖/秘密/运行数据
├── .nvmrc
├── compose.yaml
├── .github/workflows/ci.yml
├── backend/
│   ├── Dockerfile
│   ├── .dockerignore
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/com/frameflow/studio/
│       │   │   ├── FrameflowStudioApplication.java
│       │   │   ├── api/package-info.java
│       │   │   ├── trigger/package-info.java
│       │   │   ├── cases/package-info.java
│       │   │   ├── domain/
│       │   │   │   ├── task/package-info.java
│       │   │   │   ├── template/package-info.java
│       │   │   │   ├── asset/package-info.java
│       │   │   │   └── identity/package-info.java
│       │   │   ├── infrastructure/package-info.java
│       │   │   └── types/package-info.java
│       │   └── resources/application.yml
│       └── test/
│           ├── java/com/frameflow/studio/FrameflowStudioApplicationTests.java
│           └── resources/application-test.yml
├── frontend/
│   ├── AGENTS.md                     # 官方 Next.js 版本指引
│   ├── README.md
│   ├── Dockerfile
│   ├── .dockerignore
│   ├── .gitignore
│   ├── package.json
│   ├── package-lock.json
│   ├── next.config.ts
│   ├── tsconfig.json
│   ├── eslint.config.mjs
│   ├── public/.gitkeep
│   ├── scripts/prepare-standalone.mjs
│   └── src/app/{layout,page}.tsx
└── docs/
    ├── requirements.md
    ├── architecture.md
    ├── api-contract.md
    ├── tasks.md
    └── architecture-decisions.md
```

后端采用单工程模块化单体，按 `ddd-skill` 的职责分层组织，再在 Domain 内按业务分包：
Trigger 处理入口，API 定义外部 DTO，Cases 负责用例/事务，Domain 维护业务规则，
Infrastructure 实现输出契约，Types 只存真正共用的纯类型。Java 包使用合法名称 `cases`。
`domain.task` 管任务规则，`domain.template` 管模板概念，`domain.asset` 管素材规则与存储契约，
`domain.identity` 为按需出现的身份概念预留边界；跨域与异步流程属于 Cases。
Domain 不依赖 Spring/MyBatis/HTTP；Controller 不访问 Repository/DAO；领域接口由外层适配器实现。
当前只有包级说明，没有业务类、Port 或 DAO。与技能模板的明确适配、依赖矩阵及未来代码位置见
[architecture.md](docs/architecture.md) 与 [ADR-006](docs/architecture-decisions.md#adr-006基于-ddd-skill-改为分层优先的六边形包结构)。

## 环境准备

需要 Java 17、Maven 3.9.x、Node.js 22 和 npm；容器方式还需要运行中的 Docker Engine/Compose v2。
请确认 `mvn -version` 显示的 JVM 版本，避免 Maven 与终端 `java` 使用不同 JDK。

根目录创建本地配置：

```bash
cp .env.example .env
```

在 `.env` 中将两个 `CHANGE_ME_*` 密码换为自己的本地值，不要提交 `.env`。
Compose 自动读取根 `.env`；Spring Boot **不会自动读取它**。
以下 Unix 本地启动示例显式导出该文件；如值含空格、`$` 或 shell 特殊字符，请正确引用，
或直接通过 IDE/终端的环境变量配置注入。不要 source 来历不明的环境文件。

## 本地开发

先启动数据库（本机没有其他 MySQL 时）：

```bash
docker compose up -d mysql
docker compose ps mysql
```

等待 MySQL 为 healthy。也可以使用自己的 MySQL 8.4，并在 `.env` 中调整 DB_HOST/DB_PORT 等参数。
当前骨架尚无业务表、SQL 初始化、迁移或种子数据；MVP 实现阶段需补齐题面要求的数据库结构和初始化数据。

在一个终端从项目根目录启动后端：

```bash
set -a
. ./.env
set +a
cd backend
mvn spring-boot:run
```

默认监听 `http://localhost:8080`。没有任何业务或健康端点；`GET /` 返回 404 是预期。
数据库连接池按需建立连接，启动成功不代表数据库连接已验证。
当前没有 Mapper，启动时出现 `No MyBatis mapper was found` 是预期提示，不为消除提示创建空 Mapper。

另一个终端启动前端：

```bash
cd frontend
npm ci
npm run dev
```

访问 `http://localhost:3000`，只显示工程占位文字。前后端尚未通信，不需要前端环境变量。

## 构建与检查

后端（verify 包含编译、基础测试和可执行 JAR 打包）：

```bash
cd backend
mvn --batch-mode --no-transfer-progress verify
```

配置环境变量后，可直接运行产物：

```bash
java -jar target/frameflow-studio-0.0.1-SNAPSHOT.jar
```

前端：

```bash
cd frontend
npm ci
npm run lint
npm run typecheck
npm run build
HOSTNAME=127.0.0.1 PORT=3000 npm run start
```

`typecheck` 使用 `next typegen && tsc --noEmit`，可在首次 build 前运行。
`build` 生成 `.next/standalone`，`postbuild` 补齐静态资源和 public；`start` 运行独立产物。
没有外部字体下载，也不依赖构建时后端服务。业务路由、features 与共享组件目录按真实代码需要创建。

## Docker Compose

根目录验证和启动命令：

```bash
docker compose --env-file .env.example config --quiet
# 准备好 .env 且 Docker daemon 已启动后：
docker compose up --build -d
docker compose ps
docker compose logs -f backend frontend
```

默认端口为 frontend 3000 / backend 8080 / MySQL 3306，均只绑定本机 loopback，
可在 `.env` 修改宿主机端口。后端容器始终通过 mysql:3306 访问数据库。
数据库保存在 `mysql-data` 命名卷，后端等待数据库 SQL 健康检查通过后启动。
前后端 Dockerfile 均为多阶段构建和非 root 运行；没有上传功能，所以没有预建素材卷。

```bash
docker compose down
```

以上命令保留数据库卷。首次初始化数据库后，修改 `.env` 不会自动修改已有数据库账号或密码，
需在数据库中同步修改。不要为更新密码随意删除数据库卷。
**本次未启动容器、未构建镜像；这些运行步骤仍待有 Docker daemon 的环境验证。**

## 实际验证记录（2026-10-29，Asia/Shanghai）

本次显式选择本机 JDK 17 和 Node 22，未使用默认 Maven JDK 23。
Maven 仓库缓存位于 `/private/tmp/frameflow-m2`，npm 缓存位于 `/private/tmp/frameflow-npm-cache`，
避免修改全局工具配置。实际执行的核心命令如下；`npm ci --offline` 使用前序联网安装的缓存。

```bash
# backend/，JAVA_HOME 指向本机 JDK 17
mvn -Dmaven.repo.local=/private/tmp/frameflow-m2 --batch-mode --no-transfer-progress verify

# frontend/，PATH 优先使用本机 Node 22
npm install --save-exact
npm install --save-dev --save-exact @types/node@22 @types/react@19 @types/react-dom@19 eslint@9 typescript@5
npm install --package-lock-only --offline
npm ci --offline
npm run lint
NEXT_TELEMETRY_DISABLED=1 npm run typecheck
NEXT_TELEMETRY_DISABLED=1 npm run build

# 项目根目录
docker compose --env-file .env.example config --quiet
```

| 检查 | 结果 | 证明范围 |
| --- | --- | --- |
| Maven verify | 通过；1 test，0 failures/errors/skips | Java 17 编译、真实 Web 上下文、数据源/MyBatis 装配、打包 |
| npm ci --offline | 通过；依赖审计报告 0 vulnerabilities | lockfile 可重复安装；仍有 ESLint EOL 提示 |
| ESLint | 通过，0 warnings | 当前源文件和配置 |
| TypeScript | 通过 | 路由类型生成和 strict 类型检查 |
| Next.js build + postbuild | 通过 | 生产构建、静态入口、standalone 资源打包 |
| 本地后端 JAR HTTP 检查 | 通过 | 占位环境变量下启动，GET / 返回预期 404；未连接 MySQL |
| 本地前端 standalone HTTP 检查 | 通过 | GET / 返回 200、占位 HTML 正确、静态 JS 返回 200 |
| Compose config | 通过 | 变量插值和配置解析，不代表容器运行 |
| 缺失 Compose 密码的负向检查 | 通过，按预期拒绝启动配置 | 使用空 env 文件并移除密码变量，不泄漏真实凭据 |
| YAML / lockfile / 文件审查 | 通过 | 工作流及应用 YAML 可解析；直接依赖与 lockfile 一致；忽略规则和新增文件空白检查通过 |
| Docker daemon 探测 | 失败：本机 socket 不存在 | 容器构建、三服务启动、真实 MySQL 连接未执行 |
| GitHub Actions | 配置已创建，远程未执行 | 本地已执行其中的构建检查，不能替代远程 runner 验证 |

本地 HTTP 检查临时使用 18080/13000，后端使用明确的测试占位账号，不读真实数据库凭据，
检查完成后已停止进程。普通沙箱首次阻止监听后端端口（Operation not permitted），
随后通过允许本机监听的执行权限重跑成功；这是环境限制，不是应用构建失败。
首次 Next.js 构建提示主目录存在无关 lockfile，显式设置 `turbopack.root` 后重跑通过，提示已消除。
默认 `/usr/bin/git` 受 Xcode 许可证阻挡；使用已安装的 Command Line Tools Git 完成状态与文件检查，
原有 `.gitignore` 未改动。没有提交、推送或触发远程 CI。

基础测试使用 `application-test.yml` 的测试占位值，不调用 JDBC，不能证明 SQL、账号或 MySQL 连通性。
没有执行业务功能测试，因为业务功能不在本次范围内。已知兼容/维护风险只有上述 ESLint 工具链限制；
容器与真实数据库集成因未运行而保留未验证状态。

## 文档与下一步

### 后端架构重设计

2026-10-29 根据用户指定的 `ddd-skill` 2.2.4 将原 feature-first 骨架调整为
`trigger / api / cases / domain / infrastructure / types`，保留单 Maven 工程及原构建/启动方式。
只迁移包说明并更新文档，不生成新工程、不引入业务逻辑。验证记录见下表，
上文的前端、HTTP 和容器检查属于初始化阶段记录，不代表本次重新执行。

| 本次检查 | 结果 |
| --- | --- |
| Java 17 Maven clean verify | 通过；1 test，0 failures/errors/skips；可执行 JAR 打包成功 |
| 包路径、旧规范引用与改动范围审查 | 通过；迁移 5 处旧包说明，增加 4 处层说明；6 份文档同步，无其他文件变化 |
| 前端、真实 MySQL、容器、远程 CI | 本次未执行；本次没有修改其实现或配置 |

本次在 `backend/` 实际执行（JAVA_HOME 指向本机 JDK 17）：

```bash
mvn -Dmaven.repo.local=/private/tmp/frameflow-m2 --batch-mode --no-transfer-progress clean verify
```

`clean` 清除了旧产物，本次编译 10 个源文件（启动类 + 9 份包说明），未新增业务类或测试依赖。
审查对比了调整前的文件快照，确认依赖、配置、测试和前端未变化，并检查了包声明、旧路径、
新增文件空白与文档本地链接。Domain 目前只有包说明，尚不能用此验证未来业务代码的依赖正确性。
没有新增针对空骨架的架构测试；真实业务代码出现后再补有实际检查对象的规则测试。

### 开发文档

- [requirements.md](docs/requirements.md)：已完成 R-01～R-14 需求分析、验收条件和验证计划；业务验收未执行。
- [architecture.md](docs/architecture.md)：模块、依赖、分层、前端组织与演进方向。
- [api-contract.md](docs/api-contract.md)：API 待定项，不预设业务接口。
- [tasks.md](docs/tasks.md)：任务依赖、完成情况及后续验收。
- [architecture-decisions.md](docs/architecture-decisions.md)：技术取舍、替代方案、风险与待定事项。
- [AGENTS.md](AGENTS.md)：后续 Agent 必读规范。

需求分析已完成。下一阶段依据 requirements.md 细化领域模型、身份机制、任务状态表示及并发/事务边界、
素材校验与存储方案、API 和表模型，并切分最小可验收业务纵切。
MediaGenerator、AssetStorage、TaskDispatcher 等 Port 在首次对应业务实现时再设计。
AI SDK、MQ/Worker、独立 generation/result 模块及完整用户系统均只是未来方向，本次没有实现。
