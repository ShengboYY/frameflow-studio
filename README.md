# FrameFlow Studio

全栈 Assessment 项目骨架，后续可逐步演进为 AI 素材制作平台。
**Assessment 原文已取得，R-01～R-14 需求分析已完成，见 [requirements.md](docs/requirements.md)。**
领域建模与详细架构设计已形成文档，见 [domain-model.md](docs/domain-model.md) 和
[detailed-architecture.md](docs/detailed-architecture.md)。IP-02 的 Task 领域模型与状态机已实现并通过本地验证，
用户已于 2026-10-30 验收通过，见 [V-02](docs/tasks.md#ip-02-实际执行记录v-02)。
IP-03 已提供初始四表 SQL、真实 MySQL 集成测试和 CI 入口，实施证据见
[V-03](docs/tasks.md#ip-03-实际执行记录v-03)，本地与远程 CI 均通过，PR #2 已合入 main。
IP-04 已实现 Java 预置账号、登录/退出、Cookie 会话及 CSRF，验证与待验收状态见
[V-04](docs/tasks.md#ip-04-实际执行记录v-04)。IP-05 已实现只读模板目录、素材规则和打包示例图片，
本地验证与待验收状态见 [V-05](docs/tasks.md#ip-05-实际执行记录v-05)；上传、任务编排和前端业务仍未实现，整体业务验收尚未执行。

## 技术版本

| 技术 | 项目版本 / 约束 | 本次本机验证 |
| --- | --- | --- |
| Java | 17，编译 release 17 | JDK 17.0.12 |
| Spring Boot | 4.0.9 | 编译、测试、可执行 JAR 启动通过 |
| Spring Security | 7.0.8，由 Boot BOM 管理 | IP-04 会话/CSRF/真实 HTTP 测试通过 |
| MyBatis Spring Boot Starter | 4.0.1 | 装配通过；底层 MyBatis 3.5.19 |
| Lombok | 1.18.48，由 Boot BOM 管理，仅编译期 | @Getter 生成成功；260 tests 通过，运行时 JAR 不含 Lombok |
| MySQL | Docker `mysql:8.4` LTS | IP-03 使用独立临时容器验证，见 V-03；不是业务库运行证据 |
| MySQL Connector/J | 9.7.0，由 Boot BOM 管理 | 依赖解析通过 |
| Maven | 3.9.x（Boot 最低要求 3.6.3） | 3.9.10；Docker 构建配置使用 3.9.11 |
| Node.js | 22 LTS，见 `.nvmrc` 和 package engines | 22.21.1 |
| npm | 10.x，提交 package-lock.json | 10.9.4 |
| Next.js / create-next-app | 16.4.3 | App Router、Turbopack 构建通过 |
| React / React DOM | 19.3.0 | 模板配套版本，构建通过 |
| TypeScript | 5.9.3，strict | 类型检查通过 |
| ESLint / eslint-config-next | 9.39.5 / 16.4.3 | lint 通过；维护限制见下文 |
| Vitest / React Testing Library / jsdom | 5.0.5 / 16.3.3 / 29.1.1 | IP-01 占位页 1 test 通过；不是业务测试 |
| Docker Compose | Compose v2 | 2.40.3 配置解析通过；IP-03 运行了临时 MySQL 容器，三服务业务环境未验证 |
| GitHub Actions | checkout v7 / setup-java v6 / setup-node v7 | IP-01 独立验证分支的三个 job 与报告上传均通过 |

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
│       │   │   ├── api/                     # 包说明、identity/error DTO
│       │   │   ├── trigger/                 # 包说明、HTTP 认证/错误出口
│       │   │   ├── cases/                   # 包说明、identity 用例
│       │   │   ├── domain/
│       │   │   │   ├── task/
│       │   │   │   │   ├── package-info.java
│       │   │   │   │   └── model/{aggregate,valobj}/ # IP-02 任务聚合与不可变值
│       │   │   │   ├── template/            # 只读模型、素材要求、目录查询契约
│       │   │   │   ├── asset/               # 打包图片与媒体输出契约；上传待实现
│       │   │   │   └── identity/            # 只读 Account 与输出契约
│       │   │   ├── infrastructure/          # identity 适配器、DAO/PO、安全装配
│       │   │   └── types/package-info.java
│       │   └── resources/             # 配置、SQL/XML、templates/catalog.json 与 demo-media/
│       └── test/
│           ├── java/com/frameflow/studio/FrameflowStudioApplicationTests.java
│           ├── java/com/frameflow/studio/domain/task/ # IP-02 领域行为与依赖规则测试
│           ├── java/com/frameflow/studio/infrastructure/dao/SchemaIT.java # IP-03 真库结构测试
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
│   ├── vitest.config.mts
│   ├── vitest.setup.ts
│   ├── public/.gitkeep
│   ├── scripts/prepare-standalone.mjs
│   └── src/app/{layout,page}.tsx、page.test.tsx
└── docs/
    ├── requirements.md
    ├── architecture.md
    ├── domain-model.md
    ├── detailed-architecture.md
    ├── api-contract.md
    ├── database-design.md
    ├── frontend-state-design.md
    ├── future-roadmap.md
    ├── tasks.md
    └── architecture-decisions.md
```

后端采用单工程模块化单体，按 `ddd-skill` 的职责分层组织，再在 Domain 内按业务分包：
Trigger 处理入口，API 定义外部 DTO，Cases 负责用例/事务，Domain 维护业务规则，
Infrastructure 实现输出契约，Types 只存真正共用的纯类型。Java 包使用合法名称 `cases`。
`domain.task` 管任务规则，`domain.template` 管模板概念，`domain.asset` 管素材规则与存储契约，
`domain.identity` 为按需出现的身份概念预留边界；跨域与异步流程属于 Cases。
Domain 不依赖 Spring/MyBatis/HTTP；Controller 不访问 Repository/DAO；领域接口由外层适配器实现。
当前已实现 Task 聚合、值对象及状态机，Identity 的查询/密码 Port、Case、XML DAO 和认证入口，
以及 Template 目录/规则和 Asset 打包媒体能力。源码按业务分布在各层，通用错误出口位于 api.error / trigger.http.error，装配位于 infrastructure.config。与技能模板的明确适配、依赖矩阵及未来代码位置见
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
数据库启动后，按下节显式执行 V001 初始结构脚本。应用启动不会自动建表；
随后按“预置账号初始化（IP-04）”显式创建账号。Identity 已有 XML Mapper；模板和示例文件已随后端打包，无须写入数据库。

在一个终端从项目根目录启动后端：

```bash
set -a
. ./.env
set +a
cd backend
mvn spring-boot:run
```

默认监听 `http://localhost:8080`。已有 `/api/v1/auth/{csrf,login,session,logout}` 和下述公开模板/封面端点，契约见 api-contract.md；
`GET /` 未认证返回 JSON 401，没有健康端点。连接池按需建立连接，启动成功不代表数据库连接已验证。
本地 loopback HTTP 须在 `.env` 显式设置 `SESSION_COOKIE_SECURE=false`；配置默认 true，浏览器对外 HTTPS 时保持 true。

另一个终端启动前端：

```bash
cd frontend
npm ci
npm run dev
```

访问 `http://localhost:3000`，只显示工程占位文字。前后端尚未通信，不需要前端环境变量。

## 只读模板和示例图片（IP-05）

Java 启动时读取 `backend/src/main/resources/templates/catalog.json`，按文件顺序提供两个模板：

| 模板 | 分类 | 图片数 | 支持类型 | 单文件上限（含等号） |
| --- | --- | --- | --- | --- |
| portrait-card / 肖像卡片 | creative / 创意图片 | 1 | JPEG、PNG | 5 MiB（5242880 字节） |
| travel-postcard / 旅行明信片 | travel / 旅行 | 2 | JPEG、PNG | 8 MiB（8388608 字节） |

无需登录，可直接访问 Java：

```bash
curl http://localhost:8080/api/v1/templates/categories
curl 'http://localhost:8080/api/v1/templates?categoryId=travel'
curl http://localhost:8080/api/v1/templates/portrait-card
curl --output /tmp/frameflow-portrait-cover.jpg http://localhost:8080/api/v1/demo-media/portrait-cover
```

不带分类返回全部，未知分类返回空 `items`，未知模板/封面返回标准 JSON 404。
仅列表接受一个合法 `categoryId` 参数；未知/重复参数和非法 ID 返回 400。
单张图片校验只检查类型/大小；完整提交另外检查精确数量，重复素材按位置计数。这些规则已提供 Case，上传与任务端点由后续任务接入。

两张封面和固定 Mock 结果统一为 JPEG（640 × 400，`image/jpeg`），均为本项目通过 Java2D 绘制的原创几何示例图，使用 CC0-1.0，
制作代码由 Codex 辅助编写；没有下载第三方图片，不代表根据上传内容真实生成结果。
来源、用途及重新生成命令见 [SOURCES.md](backend/src/main/resources/demo-media/SOURCES.md)，
生成器为 [GenerateDemoMedia.java](backend/scripts/GenerateDemoMedia.java)。图片已提交到资源目录，普通构建不运行生成器。

公开媒体端点只接受 `portrait-cover`、`postcard-cover` 两个登记 key，不能读取文件路径或内部 `demo:mock-result`。
目录 ID 重复、规则无效、封面未登记或打包图片缺失/不可解码时，应用启动明确失败。
目录运行时只读，不建立模板表、编辑后台或热更新；此处 Java 端点已验证，Next 转发及浏览器页面仍待实现。

## 数据库初始化（IP-03）

初始脚本为 [`backend/src/main/resources/db/schema/V001__initial_schema.sql`](backend/src/main/resources/db/schema/V001__initial_schema.sql)，
仅用于已选定的空 MySQL 8.4 数据库，创建四张 InnoDB 表、索引和约束，不创建数据库/账号、不写演示数据。
先检查目标库和 `SHOW FULL TABLES`，确认没有已有表或视图；不要对已有业务结构直接重放。
MySQL 使用默认严格模式（`STRICT_TRANS_TABLES`）；字符集为 utf8mb4，机器值为 ascii_bin。
时间列为 UTC `DATETIME(6)`，应用适配器须显式按 UTC 转换 Instant，不能依赖宿主机时区。

根目录已配置并导出 `.env` 后，本机 MySQL 客户端执行：

```bash
MYSQL_PWD="$DB_PASSWORD" mysql --host="$DB_HOST" --port="$DB_PORT" \
  --user="$DB_USERNAME" --database="$DB_NAME" --execute='SHOW FULL TABLES'
MYSQL_PWD="$DB_PASSWORD" mysql --host="$DB_HOST" --port="$DB_PORT" \
  --user="$DB_USERNAME" --database="$DB_NAME" --default-character-set=utf8mb4 \
  < backend/src/main/resources/db/schema/V001__initial_schema.sql
```

使用根 Compose 的 MySQL 时，可不安装宿主机客户端，执行等价的容器命令：

```bash
docker compose exec -T mysql sh -c \
  'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --user="$MYSQL_USER" --database="$MYSQL_DATABASE" --execute="SHOW FULL TABLES"'
docker compose exec -T mysql sh -c \
  'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --user="$MYSQL_USER" --database="$MYSQL_DATABASE" --default-character-set=utf8mb4' \
  < backend/src/main/resources/db/schema/V001__initial_schema.sql
```

四张表初始均为空；预置账号及带盐密码编码按下节显式初始化，真实密码不进入 SQL。
`spring.sql.init.mode=never` 保持关闭，未引入 Flyway/Liquibase，也没有自动升级/降级流程。
MySQL DDL 会隐式提交；脚本失败可能留下部分结构，不能假定全部回滚或用删卷来修复。
脚本不使用 DROP/IF NOT EXISTS；对已初始化数据库重放会明确失败，不会清空历史或掩盖结构差异。
已有卷中的空库可以按以上命令显式初始化；存在表/数据时应先检查实际版本、备份并另行设计迁移。

当前 `compose.yaml` 尚未挂载 SQL，空卷启动后也需要上述手工步骤。
IP-15 将接入容器首次初始化挂载；MySQL 镜像的 `/docker-entrypoint-initdb.d` 脚本只在首次空数据目录初始化时执行，
已有卷不会因重启、重新挂载或修改环境变量而重跑脚本。不要删除用户卷来触发初始化。
核对脚本版本内容可运行 `shasum -a 256 backend/src/main/resources/db/schema/V001__initial_schema.sql`，
本次校验和记录于 V-03；后续升级不能靠修改 V001 覆盖已有结构。

## 预置账号初始化（IP-04）

先在目标库执行 V001 结构脚本，并核对 DB_* 指向预期的本地数据库。
在自己的 `.env` 中为 `DEMO_A_PASSWORD` / `DEMO_B_PASSWORD` 设置不同的本地密码并导出，
或由终端/IDE 注入环境变量；首次缺失账号的密码必须为 8～72 个 UTF-8 字节。
不把密码放进命令行参数、SQL、源码或日志。两个账号为 demo-a / demo-b，显示名为演示用户 A / B，均无管理员能力。

在项目根目录已经导出环境变量后执行：

```bash
cd backend
mvn --batch-mode --no-transfer-progress verify
java -jar target/frameflow-studio-0.0.1-SNAPSHOT.jar \
  --spring.main.web-application-type=none --app.accounts.initialize=true
```

初始化命令完成后退出；失败为非零退出码。普通 Web 启动不自动建表或初始化账号。
BCrypt cost 12 编码（带 `{bcrypt}` 标识和随机盐）在事务外生成，再用独立短事务插入缺失账号。
重复执行时不改已有密码、account_id 或历史归属，已有账号不要求再次提供初始化密码；
修改环境变量也不会重置密码。需要补一个缺失账号时，仅要求该账号的密码。
成功后可移除初始化密码环境变量再启动普通后端，登录仍通过数据库编码验证。

使用已有 backend 容器镜像时，可在根目录、对应变量已导出的终端显式执行：

```bash
docker compose run --rm -e DEMO_A_PASSWORD -e DEMO_B_PASSWORD backend \
  --spring.main.web-application-type=none --app.accounts.initialize=true
```

此命令依赖已初始化的数据库与可用镜像；本任务只验证本地 JAR 命令，完整三服务镜像验收归 IP-15。

Java API 调用顺序：GET `/api/v1/auth/csrf` 保存 FF_SESSION Cookie 与返回 token →
POST `/api/v1/auth/login`，body 为恰好 username/password 的 UTF-8 form-urlencoded，header 带 X-CSRF-TOKEN →
GET `/api/v1/auth/session` 恢复账号。登录后重新 GET csrf，再 POST logout；退出后同样需要重新获取 csrf。
登录密码保留空格，1～72 个 UTF-8 字节；无注册端点、默认可用密码、HTML 登录页或 Basic 登录。
所有 POST 均校验 CSRF；token 只从 X-CSRF-TOKEN 接收，不能放在查询串或表单中。

Cookie 为 host-only、Path=/api、HttpOnly、SameSite=Lax，最长 8 小时；登录换 Session ID，
空闲 30 分钟或绝对 8 小时失效，退出立即失效。服务器内存会话在重启后丢失，账号 ID 保持稳定。
401/403/503 使用统一 JSON 与 X-Request-Id，认证与 CSRF 响应 no-store；数据库故障不冒充密码错误/未登录。
Next 同源代理和登录界面归 IP-11，当前前端仍为占位页。

## 真实 MySQL 集成测试（IP-03 / IP-04）

普通 `mvn verify` 只运行快速测试，不要求数据库。真实 SQL 检查使用独立入口：

```bash
cd backend
mvn --batch-mode --no-transfer-progress -Pintegration-tests verify
```

执行前，配置以下专用环境变量，不复用业务库的 DB_* 配置：

| 变量 | 要求 |
| --- | --- |
| `IT_DB_HOST` / `IT_DB_PORT` | 默认 `127.0.0.1` / `3306`；指定隔离 MySQL 8.4 实例的实际地址 |
| `IT_DB_NAME` | 必填；必须是 `frameflow_it` 或以 `frameflow_it_` 开头的小写字母/数字/下划线名称 |
| `IT_DB_USERNAME` / `IT_DB_PASSWORD` | 必填；仅对该测试库有建表、读写和删表权限的临时测试账号 |

测试要求目标库完全为空，不会自动清理已有结构来开始测试。通过空库检查后，按同一 V001 脚本建表，
SchemaIT 每个测试回滚数据；IdentityIT 在自有 schema 内按测试清理账号。两套件串行运行，各自结束仅删除本次创建的四张表。中断可能遗留测试结构，建议重建本次专用的临时实例。
此入口仅用于可信的隔离测试网络（连接关闭 TLS），不要指向业务库或不可信远程服务。
环境变量缺失、版本不符、数据库不可达、库不为空或没有发现 `*IT` 测试时必须失败，不按跳过处理。
Failsafe 版本沿用 Boot 父 POM 管理，JUnit 报告在 `backend/target/failsafe-reports/`。

可用一次性 Docker 容器准备本地环境。下面在同一终端生成并导出随机临时密码，结束只删除该命名测试容器：

```bash
export IT_DB_HOST=127.0.0.1 IT_DB_PORT=13306 IT_DB_NAME=frameflow_it IT_DB_USERNAME=schema_test
export IT_DB_PASSWORD="$(openssl rand -hex 24)"
export MYSQL_ROOT_PASSWORD="$(openssl rand -hex 24)"
docker run --detach --name frameflow-mysql-it --tmpfs /var/lib/mysql \
  --publish "127.0.0.1:$IT_DB_PORT:3306" \
  --env MYSQL_ROOT_PASSWORD --env MYSQL_PASSWORD="$IT_DB_PASSWORD" \
  --env MYSQL_DATABASE="$IT_DB_NAME" --env MYSQL_USER="$IT_DB_USERNAME" mysql:8.4
# 以下 SQL 成功后再运行 Maven；启动过程中可等待几秒后重查。
docker exec frameflow-mysql-it sh -c \
  'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --host=127.0.0.1 --user="$MYSQL_USER" --database="$MYSQL_DATABASE" --execute="SELECT 1"'
(cd backend && mvn --batch-mode --no-transfer-progress -Pintegration-tests verify)
docker rm --force --volumes frameflow-mysql-it
unset IT_DB_PASSWORD MYSQL_ROOT_PASSWORD
```

`mysql-integration` CI job 独立启动 MySQL 8.4 service，使用本次 run 的临时测试凭据和动态端口，
执行相同 profile 并上传 Surefire/Failsafe 报告。工作流已配置与远程 runner 实际通过分开记录，发布后的运行证据见 V-03。
[远程 CI 41066158015](https://github.com/ShengboYY/frameflow-studio/actions/runs/41066158015) 在修复提交 `5d1097a` 上四个 job 全部通过，
MySQL job 实际运行 260 个快速测试和 13 个 SchemaIT，报告上传成功；首次 runner 引号故障与修复记录均保留于 V-03。
上述远程结果仅证明 IP-03 初始结构、约束和 JDBC 事务能力。
IP-04 在同一 profile 追加 IdentityIT，实际通过 DAO 加载 XML、查询/插入、密码编码/稳定 ID、初始化回滚与真实 HTTP A/B 登录切换；
本地结果见 V-04，当前分支远程 CI 尚未运行。Task 条件更新及其他业务 API 仍属后续任务。

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
npm run test:ci
npm run lint
npm run typecheck
npm run build
HOSTNAME=127.0.0.1 PORT=3000 npm run start
```

`typecheck` 使用 `next typegen && tsc --noEmit`，可在首次 build 前运行。
`build` 生成 `.next/standalone`，`postbuild` 补齐静态资源和 public；`start` 运行独立产物。
没有外部字体下载，也不依赖构建时后端服务。业务路由、features 与共享组件目录按真实代码需要创建。

IP-01 已接入 Vitest + React Testing Library；测试依赖需要 Node 22.13.0+，本机 22.21.1 满足要求。
`npm test` 为 watch，`npm run test:ci` 为 CI 同一单次入口；前端 JUnit 报告在
`frontend/test-results/junit.xml`，后端报告在 `backend/target/surefire-reports/`。
push/PR 工作流保留全部原有门禁并运行前端测试，成功或失败后分别保存为
`frontend-test-reports` / `backend-test-reports` artifacts，缺报告时报错。
本次实际验证与失败注入证据见 [V-01 记录](docs/tasks.md#ip-01-实际执行记录v-01)。
当前前端仅占位页渲染测试；后端快速测试包括上下文、IP-02 领域行为及 IP-04 用例/API/依赖边界（本地全量 302 tests 通过）。
IP-02 的本分支远程 CI 未运行；真实 MySQL 门禁在 IP-03、容器业务门禁在 IP-15 引入。
2026-10-30 已在独立分支 `ci/ip-01-validation-20261030` 完成
[远程 CI 验证](https://github.com/ShengboYY/frameflow-studio/actions/runs/41052413658)：
三个 job 全部成功，两份 artifact 下载后确认前后端各 1 test、0 failures/errors/skips。
该分支只包含 6 个 CI/测试文件，验证提交为 `9975f8bc89d27a9633637de45ebb47be6f10c574`；
首次独立验证时，设计文档提交保留在本地，远程 main 未合并此验证分支。
用户随后明确授权 CI 自助验收及 commit/push；IP-01 已完成验收，CI 改动和证据随当前 main 发布。
main 的分支摘要显示未保护、必需检查 enforcement 为 off，规则集为空，当前 CI 失败不会强制阻止合并；
详细保护接口返回连接权限 403，本次仅核对设置，未调整分支保护。

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
- [domain-model.md](docs/domain-model.md)：领域边界、ProductionTask、任务状态机及不变量。
- [detailed-architecture.md](docs/detailed-architecture.md)：模块契约、预置账号登录/Cookie 会话、异步事务及并发保护方案；IP-04/05 已实现 Java 认证、目录和打包媒体，其余待实施。
- [api-contract.md](docs/api-contract.md)：API v1 路径、字段、鉴权和错误契约；IP-04/05 已实现认证、模板和封面端点。
- [database-design.md](docs/database-design.md)：四表结构、约束、索引、文件及初始化设计；IP-03 初始 SQL 与真库验证见 V-03。
- [frontend-state-design.md](docs/frontend-state-design.md)：多任务轮询、请求清理、旧响应隔离及刷新恢复设计，未实现。
- [tasks.md](docs/tasks.md)：任务依赖、完成情况及后续验收。
- [architecture-decisions.md](docs/architecture-decisions.md)：技术取舍、替代方案、风险与待定事项。
- [AGENTS.md](AGENTS.md)：后续 Agent 必读规范。

需求分析、领域建模、详细架构设计及 Implementation Planning 已完成，实施任务、依赖与 AC 追踪见 [tasks.md](docs/tasks.md)。
IP-01 已按用户授权完成自助验收，IP-02 已验收；IP-03 实施和验证记录见 V-03，待用户验收；IP-04/05 实现和本地验证见 V-04/05，待用户验收；IP-06～18 尚未开始。
MediaGenerator、AssetStorage、TaskDispatcher 的职责已在设计文档明确，接口源码随真实实现创建。
AI SDK、MQ/独立 Worker、独立 generation/result 模块及完整用户系统仍为未来方向。
用户已确认本阶段提供预置演示账号的登录/退出，暂不注册；后续实施时从环境配置演示密码，不提供默认通用密码或访客创建器。
上文 2026-10-29 运行记录属于脚手架阶段；2026-10-30 的 IP-01 证据单独记录，不代表登录页面、账号表或业务验收已经完成。
