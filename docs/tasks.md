# 任务追踪与 Implementation Plan（Assessment / Track A）

- 规划日期：2026-10-30
- 状态同步：2026-10-31（Asia/Shanghai）

## 当前进度

核心业务、前端与容器能力已实现，交付材料已准备。各任务的验证与验收状态如下；最终验收和对外交付仍待用户确认。

| 任务 | 内容 | 当前状态 | 详细记录 |
| --- | --- | --- | --- |
| IP-01 | CI 与测试入口 | 已验收 | [V-01](#ip-01-实际执行记录v-01) |
| IP-02 | Task 领域模型与状态机 | 已验收（2026-10-30） | [V-02](#ip-02-实际执行记录v-02) |
| IP-03 | 数据库结构与真库测试 | 本地/CI 通过；待验收 | [V-03](#ip-03-实际执行记录v-03) |
| IP-04 | 预置账号、会话与 CSRF | 本地验证通过；待验收 | [V-04](#ip-04-实际执行记录v-04) |
| IP-05 | 模板目录与打包媒体 | 本地验证通过；待验收 | [V-05](#ip-05-实际执行记录v-05) |
| IP-06 | 上传与本地素材存储 | 本地验证通过；待验收 | [V-06](#ip-06-实际执行记录v-06) |
| IP-07 | 任务仓储、本人查询与条件更新 | 本地验证通过；待验收 | [V-07](#ip-07-实际执行记录v-07) |
| IP-08 | 后台三阶段、Mock 与有界派发 | 本地验证通过；待验收 | [V-08](#ip-08-实际执行记录v-08) |
| IP-09 | 任务受理 API | 本地验证通过；待验收 | [V-09](#ip-09-实际执行记录v-09) |
| IP-10 | 本人任务、历史与结果 API | 本地验证通过；待验收 | [V-10](#ip-10-实际执行记录v-10) |
| IP-11 | Next 转发、登录与身份作用域 | 本地验证通过；待验收 | [V-11](#ip-11-实际执行记录v-11) |
| IP-12 | 模板页面与上传交互 | 自动化及浏览器回归通过；待验收 | [V-12](#ip-12-实际执行记录v-12) |
| IP-13 | 任务快照合并与独立轮询 | 本地验证通过；待验收 | [V-13](#ip-13-实际执行记录v-13) |
| IP-14 | 制作、历史、结果预览与演示设置 | 已验收（2026-10-31） | [V-14](#ip-14-实际执行记录v-14) |
| IP-15 | Compose 初始化与持久化 | 本地/CI 通过；待验收 | [V-15](#ip-15-实际执行记录v-15) |
| IP-16 | 联合回归与缺陷修复 | 本地/CI 通过；待验收 | [V-16](#ip-16-实际执行记录v-16) |
| IP-17 | README、截图与交付材料 | 材料及本地复现已完成；待审查和最终版本 | [V-17](#ip-17-本轮材料整理记录v-17) |
| IP-18 | 最终验收与约定交付 | 未开始；待用户确认 | [交付要求](#ip-18用户最终审查验收与约定交付) |

IP-03 的 PR #2 已合入 main，任务验收仍单独登记。后续补充的浏览器回归见 V-16，远程 CI 见下表。

当前实现与验证状态统一在上表维护；[第 10.1 节](#101-当前阶段与实施-checklist)仅登记验收勾选。第 4 节的折叠记录保留各次执行当时的命令、结果、历史失败和未验证范围，不代表当前状态。

### 已核实的远程 CI（2026-10-31 更新）

| 提交 | 运行 | 实际结果 |
| --- | --- | --- |
| `a2ff7dce226513dbd94358565e574e4489d9acad` | [41208223619](https://github.com/ShengboYY/frameflow-studio/actions/runs/41208223619) | backend、frontend、mysql-integration、compose 均 success，四份测试报告/日志上传步骤均 success |
| `98e8b92175b3c4ed03d5905024658533e628edf6` | [41207183652](https://github.com/ShengboYY/frameflow-studio/actions/runs/41207183652) | 四个 job 均 success；包含 IP-16 扩展回归和 IP-17 材料 |
| `f70420f2dcfb45104f107a0ce4f0ccfc3ca4c280` | [41203034846](https://github.com/ShengboYY/frameflow-studio/actions/runs/41203034846) | IP-16 发布提交的 CI 已完成并 success |

已核对提交 SHA、run 状态及最新运行的四个 job/报告上传步骤；后续工作区文档修改尚未提交，不将上述结果视为未提交版本的 CI 证明。远程通过不替代用户验收或对外交付。

## 1. Implementation Overview

### 1.1 基线与范围

依据 [requirements.md](requirements.md) 的 ASMT-R-01～14、48 条 AC 与 VT-01～14，
[domain-model.md](domain-model.md)、[detailed-architecture.md](detailed-architecture.md)、
[api-contract.md](api-contract.md)、[database-design.md](database-design.md)、
[frontend-state-design.md](frontend-state-design.md)、[architecture.md](architecture.md)、
[architecture-decisions.md](architecture-decisions.md) 及根目录/前端 AGENTS。
已检查现有源码、POM、npm scripts、Dockerfile/Compose 和 CI。

按用户指定参考 ddd-skill 2.2.4 的 SKILL.md、Case/Domain/命名指引，并服从 ADR-006 的项目适配：
单 Maven 工程，trigger/api/cases/domain/infrastructure/types；Domain 纯 Java；所有入口经过 Case；
Task 聚合维护规则，Cases 编排事务/跨模块/后台执行，Infrastructure 实现输出契约。
不运行 archetype，不复制技能中的 Redis/MQ、默认密码、跳过测试的部署命令或模式框架。

本轮没有发现需要修改已确认需求的冲突。需求文档保留其分析阶段的“待设计”表述，实施选择以详细设计和 ADR-007～010 为准；
现行实现遵循详细设计，不因为早期文档的历史表述而重新选择已确认的契约。

| 规划时基线（实施更新见 V-01 / V-02） | 实施安排 / 完成证据要求 |
| --- | --- |
| 代码只有 scaffolding；后端仅 1 个不连接 JDBC 的上下文测试 | 业务、SQL、身份、多任务行为全部仍待实现和验证 |
| 现有 CI 有 push/PR、Maven verify、npm ci/lint/typecheck/build、Compose config | IP-01 优先补强测试入口及失败门禁，沿用现有工作流，不重新生成工程 |
| 前端尚无测试脚本/依赖 | IP-01 配置 Vitest + React Testing Library，锁定与现有栈兼容的版本；IP-12～14 补实际业务测试 |
| 当前无业务表、Mapper、账号/模板种子或上传卷 | IP-03 尽早启用真实 MySQL 验证；IP-04/05/06 分别提供真实初始化与资源 |
| Docker 和远程 CI 只有历史“未运行”记录 | 未来执行时重新检查环境；本轮没有重跑，不能当作当前故障或通过证据 |
| 用户确认预置账号登录/退出，不注册 | 保持 demo-a/demo-b、Cookie/Session/CSRF；不恢复访客选择器或 Bearer 提案 |

### 1.2 当前范围与未来边界

当前覆盖 R-01～R-13 强制要求，并继续采用现有 Compose 方向落实 R-14 推荐交付。
预置账号登录是用户确认的 MVP 方案；“完整身份认证系统”延期不包含这项登录/退出和本人数据隔离。

不排入真实 AI Provider、视频、对象存储、MQ/独立 Worker、Outbox、自动续跑/重试、注册/改密/角色、模板编辑/版本平台、
工作流引擎、独立 Generation/Result/History 模块、结果下载/删除/重新制作、分页或自动发布。
保留 MediaGenerator、AssetStorage、TaskDispatcher 的已选最小边界，不为未来增加空接口/表/字段。

任务编号使用 IP-01～IP-18，避免与历史 S/R/T 及 Assessment 的 R 编号混淆。
Verification 使用 V-01～V-18，细化并对应原 VT；这些都是**待执行验证**，不是测试已通过的声明。

## 2. Development Milestones

| 里程碑 | 任务 | 可观察退出条件 |
| --- | --- | --- |
| M0 工程验证入口 | IP-01 | 既有构建门禁保留，前端测试能真实运行并在断言失败时使 CI 失败；远程结果单列 |
| M1 规则与数据基础 | IP-02～05、IP-07 | 状态机受约束；真实 MySQL schema/条件写可验证；登录和只读目录可用 |
| M2 核心后台纵切 | IP-06、IP-08～10 | 合法素材受理后异步完成三阶段；三个失败点均中断；个人持久记录与图片可读 |
| M3 浏览器多任务闭环 | IP-11～14 | 登录、上传、连续制作、独立跟踪、异常恢复、刷新及 A/B 隔离有实际 UI 和行为测试 |
| M4 可复现交付 | IP-15～18 | 容器初始化/业务闭环及材料可复核，用户最终验收；未验证与已接受限制单列 |

M1 不要求先做完整界面；核心异步模型在 IP-02 开始、真实执行在 IP-08 落地。
各任务同步写测试；IP-16 是联合复核，不是把核心测试拖到最后。

## 3. Vertical Slices

| 纵切 | 端到端行为与组成任务 | 验收观察 |
| --- | --- | --- |
| VS-01 登录与只读浏览 | IP-03/04/05/11，加 IP-12 的目录页面 | Java 数据和合法图片可浏览；A 登录/退出后 B 登录；无注册入口 |
| VS-02 制作与成功结果 | IP-02/03/05/06/07/08/09/10，再 IP-11～14 接入浏览器 | 上传 → 202 ID → 三阶段 → MySQL 成功记录 → 可预览图片；不依赖页面持续打开 |
| VS-03 指定失败和请求故障 | IP-08/09/10/13/14 | 三个阶段分别失败且中断；查询断网仅暂停查询，恢复后读真实状态 |
| VS-04 多任务与个人恢复 | IP-04/07/08/10/11/13/14 | 多项独立执行/跟踪，终态停止；刷新恢复；退出/登录及晚到回调不会串用户 |
| VS-05 隔离环境交付 | IP-01/03/15/16/17/18 | 测试门禁、空库初始化、三服务/上传卷、操作说明及最终验收材料一致 |

这些纵切共享必要的工程基础，并非五套平行实现。后端先通过 API/集成测试证明 VS-02，再接入 UI。

## 4. Detailed Task Breakdown

每项仅创建当前实际需要的类/文件；具体 Java 签名从现有契约落实，不在计划中再定义另一套接口。
任务下的范围、验收和 V 项均属于完成条件；当前状态见[总览](#当前进度)，验收勾选见[第 10.1 节](#101-当前阶段与实施-checklist)。
执行记录按原有标题折叠，点击“展开记录”查看命令、证据和当时的验证范围。

后续持久化实现按用户确认统一使用 **MyBatis XML Mapper** 编写 SQL，不在 DAO 注解中内嵌 SQL。
XML 位于 `backend/src/main/resources/mybatis/mapper/<业务>/`，namespace 与对应 DAO 接口全限定名一致，
statement id 与 DAO 方法对应；值参数使用 `#{...}` 绑定。首个实际 Mapper 在 IP-04 配置 XML 资源加载，
后续任务复用该配置；每项真库集成测试必须通过实际 DAO 调用验证 XML 加载、参数绑定及结果映射。

### IP-01：优先补强 CI 与测试工程入口

**依赖**：已完成的 S-02/03/06；无需业务代码。**来源**：R-01、R-12，工程基线。

**范围**：审查并增量完善 .github/workflows/ci.yml、frontend 测试配置/scripts/lockfile、必要测试说明；保留 Java 17/Node 22、现有版本与构建方式。
配置 Vitest + React Testing Library，建立 test:ci 命令；以当前实际占位页面的最小可渲染检查验证测试环境，明确这不是 R-12 的业务测试。
不创建假业务模块或为满足测试数量写空断言。后端沿用已有 JUnit/Spring 测试入口。

**验收 / V-01**：

- push/PR 工作流包含 Maven verify、npm ci、test:ci、lint、typecheck、build、Compose config；任何失败均非零退出，没有 continue-on-error 或空测试通过开关。
- 本地与 CI 命令一致；临时制造一条测试失败，确认测试命令及工作流步骤不会吞掉错误，随后恢复，不提交故意失败的测试。
- 实际运行现有后端测试和至少一个前端测试，产出可定位的测试报告；不能仅检查 YAML 或声称 runner 已通过。
- 若有权限且已有授权的远程运行可记录其 run/commit；本任务不包含推送或部署授权。远程不可运行时标为未验证，不以本地结果冒充；最终由 IP-17/18复核。
- MySQL CI 门禁明确在 IP-03 引入真实 schema 测试；容器业务门禁在 IP-15 引入。此时不得把尚未存在的业务测试标为完成。

**边界**：不新增业务端点、DDL、认证或任务逻辑；不为了搭建 CI 升级框架、禁用 ESLint 规则或跳过测试。

#### IP-01 实际执行记录（V-01）

<details>
<summary>展开记录</summary>

2026-10-30，Asia/Shanghai；状态：**ACCEPTED，按用户明确授权完成自助验收**。
关联 R-01 工程版本约束、AC-R-12-01 的本地命令/CI 配置；不将本项计为 AC-R-12-02/03 的业务覆盖。
执行前工作区干净，使用 Command Line Tools Git 检查（默认 Git 受 Xcode 许可证阻挡）。

修改 `.github/workflows/ci.yml`、`frontend/package.json` / lockfile、新增
`frontend/vitest.config.mts`、`frontend/vitest.setup.ts`、`frontend/src/app/page.test.tsx`，
同步根/前端 README 和本任务记录。沿用现有后端测试，无业务源码或后端配置改动。
直接新增开发依赖精确锁定为 Vitest 5.0.5、Vite 8.3.5、@vitejs/plugin-react 6.1.2、jsdom 29.1.1、
@testing-library/react 16.3.3、@testing-library/dom 10.4.2、@testing-library/jest-dom 7.0.1。
安装前检查 npm 官方元数据的 engines/peerDependencies；本机 Node 22.21.1、npm 10.9.4 兼容。
jsdom 30 要求更高的 Node 补丁版本，因此保留兼容的 29.1.1；现有框架/工具包版本均未改变。
package engines 同步测试依赖的 Node 22.13.0 最低要求，保留 22 LTS 版本线和 `.nvmrc`。
Vite 自带 tsconfigPaths，未保留额外路径解析插件。没有强制安装或忽略 peer 约束。

| V-01 检查 / 工作目录 | 实际命令或步骤 | 状态与证据 |
| --- | --- | --- |
| Java 17 / `backend/` | `mvn -Dmaven.repo.local=/private/tmp/frameflow-m2 --batch-mode --no-transfer-progress verify`；JAVA_HOME 指向本机 JDK 17.0.12 | PASS；1 test，0 failures/errors/skips，JAR 打包成功；`backend/target/surefire-reports/TEST-com.frameflow.studio.FrameflowStudioApplicationTests.xml` |
| 依赖重装 / `frontend/` | `npm ci --offline --cache /private/tmp/frameflow-npm-cache`；PATH 优先 Node 22 | PASS；从已下载缓存重装 432 packages，审计 0 vulnerabilities；lockfile 可重复安装 |
| 最小可渲染检查 / `frontend/` | `npm run test:ci` | PASS；1 test，0 failures/skips；`src/app/page.test.tsx` 渲染真实 Home，断言 main 中可见的一级标题和占位说明；`frontend/test-results/junit.xml` |
| 失败传播 / `frontend/` | 临时将标题断言替换为不存在的名称，通过 `bash --noprofile --norc -e -o pipefail -c 'npm run test:ci'` 执行；finally 恢复文件，再运行正常测试 | PASS；实际断言失败，npm 与步骤 shell 退出码 1；恢复后 1 test 通过；负向日志 `/private/tmp/frameflow-ip01-assertion-failure.log`、失败报告 `/private/tmp/frameflow-ip01-failed-junit.xml` |
| 空测试保护 / `frontend/` | `npm run test:ci -- ip01-no-matching-test` | PASS；No test files found、退出码 1；日志 `/private/tmp/frameflow-ip01-no-tests.log`；随后正常测试已重跑并恢复通过报告 |
| 静态与构建 / `frontend/` | `npm run lint`、`NEXT_TELEMETRY_DISABLED=1 npm run typecheck`、`NEXT_TELEMETRY_DISABLED=1 npm run build` | PASS；ESLint 0 warnings，strict 类型检查、生产构建及 standalone postbuild 成功；构建日志 `/private/tmp/frameflow-ip01-frontend-build.log` |
| Compose / 根目录 | `docker compose --env-file .env.example config --quiet` | PASS；仅配置解析，未启动容器 |
| 工作流与自审 / 根目录 | 解析 YAML，核对 push/PR、所有必需命令、失败传播和报告路径；检查 Git diff 与锁文件 | PASS；无 continue-on-error、空测试放行或跳过测试；报告在未取消的成功/失败 job 后上传、缺失报错；已有包版本无变化；无故意失败测试残留 |
| 远程 Actions / artifact 上传 | 用户随后授权远程验证；以远程 main 为基线创建独立验证分支，只提交 6 个 CI/测试文件并触发 push 工作流 | PASS；run `41052413658`，backend/frontend/compose 及两份报告上传均成功；下载 JUnit 确认前后端各 1 test、0 failures/errors/skips；版本与链接见下文 |

远程补充验证（2026-10-30 20:02～20:03，Asia/Shanghai）：

- 分支 `ci/ip-01-validation-20261030`；提交 `9975f8bc89d27a9633637de45ebb47be6f10c574`，
  父提交为远程 main 的 `9d514cb686c99ff774d9543a4bbe35416d2fe76f`。
  本地另有设计文档提交 `665ba80` 尚未推送，因此只上传 workflow、package.json / lockfile、Vitest 配置/setup 和页面测试共 6 个文件；
  这些运行文件与本地一致，未包含设计提交，也未合并或改写 main。
- [CI run 41052413658](https://github.com/ShengboYY/frameflow-studio/actions/runs/41052413658)：
  push 触发，Ubuntu 24.04，Temurin 17.0.21、Node 22.23.4、npm 10.9.9；三个 job 和全部检查步骤均 success。
- `backend-test-reports` artifact `12975995093`（4124 bytes）、`frontend-test-reports` artifact `12975670723`（372 bytes）
  均成功上传且未过期；经 GitHub 连接下载 ZIP 并解析 XML，分别确认 1 test、0 failures/errors/skips。
  下载的 ZIP 在 `/private/tmp/frameflow-ip01-remote/`；摘要 `/private/tmp/frameflow-ip01-remote-summary.json`，
  job 日志 `/private/tmp/frameflow-ip01-remote-backend.log` / `/private/tmp/frameflow-ip01-remote-frontend.log`。
- 分支保护检查：main 摘要 `protected=false`、required_status_checks 的 enforcement_level 为 `off`、contexts/checks 为空，
  仓库 rulesets 列表为空。当前没有强制要求 CI 通过的合并门禁。
  详细 `/branches/main/protection` 接口返回 403 `Resource not accessible by integration`，该细节读取受连接权限限制；
  本次未修改分支保护、未验证失败 PR 的实际合并操作。

环境说明：普通沙箱首次禁止 npm DNS 与后端随机端口 bind；分别允许联网安装、允许本机监听后成功，
未修改测试来绕过限制。后端日志 `/private/tmp/frameflow-ip01-backend-verify.log`；上下文测试不调用 JDBC。
本地生成报告/日志不提交；远程已实际上传并下载核对报告。临时路径可能被系统清理，远程 artifact 按保留策略到期。
自审由本次实现者完成；无独立审查结论。当前没有阻塞 IP-01 本地验收的缺陷。
ESLint 9 的既有 EOL 限制保持，见 ADR-002；MySQL schema 测试由 IP-03 引入，容器业务测试由 IP-15 引入。
后端三阶段和前端多任务/查询故障行为仍未实现或验证，不据此宣称 R-12 已全部满足。

自助验收与发布授权（2026-10-30）：用户明确要求“对于 CI 请自助验收，可以 commit push”。
执行者复核 V-01 的工作流、失败注入、前后端报告和远程成功记录，确认 6 个 CI/测试运行文件的
Git blob 与已验证远程提交完全一致，全部 IP-01 条件满足，按该授权勾选本任务。
本次新提交仅包含 9 个 CI/测试/说明文件；当前 main 中既有设计文档提交保持原样随分支快进推送。
此验收由实现者自行完成，不表述为独立人工审查；不扩大为业务验收、分支保护变更或部署授权。

</details>

### IP-02：ProductionTask 领域模型及状态机

**依赖**：IP-01。**范围**：domain.task 内聚合、必要值对象/枚举及纯恢复检查；只按 domain-model.md 建模。

**验收 / V-02**：初始输入/owner 固定；9 种合法组合；正常链、三阶段失败、执行前派发失败；跳步、回退、错误阶段失败、缺结果成功及终态修改均被拒绝。
重复素材位置保持；恢复对象不产生副作用。无公开状态 setter、Spring/MyBatis/HTTP 或其他域仓储依赖。
采用参数化 Domain Unit Tests 检查合法边和非法组合，并对已有真实类补依赖规则检查；无需引入架构框架即可完成时使用现有工具。

#### IP-02 实际执行记录（V-02）

<details>
<summary>展开记录</summary>

2026-10-30，Asia/Shanghai；状态：**ACCEPTED，用户明确验收通过并授权 commit / push**。
执行前工作区干净；分支 `feat/ip-02-production-task-domain`，基于 main `d4edff3`。
验收前未提交、推送或自动勾选完成；用户现已明确接受本任务并授权提交及推送当前任务分支。未启动下一任务。

参考 task-development-workflow、ddd-skill 2.2.4 的聚合/值对象/领域设计/命名指引，遵循 ADR-006 的项目适配。
新增源码仅在 `backend/src/main/java/com/frameflow/studio/domain/task/model/`：

| 对象 | 建模结果 |
| --- | --- |
| `aggregate.ProductionTask` | 唯一聚合根；固定 UUID taskId/ownerId、Instant createdAt、TaskInput；私有可变结论，仅通过领域行为推进 |
| `valobj.TaskInput` | 不可变 record：templateId、有序 UUID assetIds；防御性复制、不去重、不可外部修改；数量匹配/归属等外部事实由 Case 校验 |
| `valobj.TaskStatus` / `TaskStage` | 四种整体状态、固定三阶段；无阶段用 null，不增加伪阶段 |
| `valobj.TaskResult` | 不可变 record：非空白稳定结果引用；资源就绪由 Case 确认，不保存 URL/文件内容 |
| `valobj.FailureInfo` / `FailureKind` | 不可变 kind/stage/code/message；STAGE 必须有阶段，DISPATCH 必须无阶段；原因/分类不得空白 |

`accept` 建立 PENDING；`restore` 检查九种合法结论并原样重建，不读取时钟、文件、数据库或派发。
行为为 startChecking、completeChecking、completeGeneration、succeed、failCurrentStage、failBeforeStart。
阶段失败显式提供失败阶段，拒绝将旧阶段失败记到新阶段。失败数据校验完成后才修改状态；被拒绝操作保留原对象。
身份/输入无 setter，聚合不嵌套其他域对象；revision、updatedAt 和生成候选不进入聚合。
恢复入口是可信持久化边界，不能作为外部任意状态修改 API。

新增测试：`TaskDomainBoundaryTest`、`model/aggregate/ProductionTaskTest`、`model/valobj/TaskValuesTest`，
均在 `backend/src/test/java/com/frameflow/studio/domain/task/` 下；首轮未新增依赖或修改 POM/CI，后续按用户 review 引入编译期 Lombok，见下文。
领域测试不启动 Spring，沿用现有 JUnit 参数化测试和 AssertJ；依赖规则用 JDK 自带 jdeps 检查真实编译产物，禁止空匹配通过。

| V-02 条件 / 检查 | 实际证据 | 状态 |
| --- | --- | --- |
| 初始身份/输入固定，重复素材位置保持 | 正常链、各阶段失败和完整操作矩阵均检查固定字段；输入原列表修改、返回列表修改及值相等性测试 | PASS |
| 九种合法组合与纯恢复 | 9 种恢复保留全部传入事实，恢复后行为仍受约束；构造入口必需字段测试；源码自审确认无 I/O/时钟/执行调用 | PASS |
| 正常链、三个阶段失败、派发失败 | 从 accept 实际推进各路径并断言阶段、结果/原因；9 种组合 × 8 个操作覆盖 8 条合法边和 64 个拒绝操作 | PASS |
| 非法组合、缺结果成功、错误阶段、终态保护 | 状态 × 可空阶段 × 有/无结果 × 五种失败信息共 160 种组合，9 合法、151 拒绝；无结果成功/无效失败详情拒绝后无部分修改 | PASS |
| 无 setter、无框架/跨域依赖 | 反射检查无公开构造器/setter、固定字段 private final；jdeps 检查 Task 编译类仅依赖 Java 和本域 | PASS |
| 领域定向测试 / backend | JDK 17.0.12：`mvn -o -Dmaven.repo.local=/private/tmp/frameflow-m2 --batch-mode --no-transfer-progress -Dtest=ProductionTaskTest,TaskValuesTest,TaskDomainBoundaryTest test`；259 tests，0 failures/errors/skips | PASS |
| 后端完整检查 / backend | 同一 JDK：`mvn -o -Dmaven.repo.local=/private/tmp/frameflow-m2 --batch-mode --no-transfer-progress verify`；260 tests，0 failures/errors/skips，JAR 打包成功 | PASS |
| 前端回归 / frontend | Node 22.21.1 / npm 10.9.4：`npm ci --offline --cache /private/tmp/frameflow-npm-cache`、`npm run test:ci`（1 test）、`npm run lint`、`npm run typecheck`、`npm run build`（含 postbuild）；后两项设置 NEXT_TELEMETRY_DISABLED=1 | PASS |
| Compose / 根目录 | `docker compose --env-file .env.example config --quiet`；只证明配置解析 | PASS |
| 首轮自审 / 根目录 | 检查全部新增源码/测试和实际文档 diff，`git diff --check`；未发现阻塞缺陷，当时未修改前端源码、DDL、应用配置或依赖；后续 Lombok 调整另行记录 | PASS |
| 本分支远程 CI / 数据库 / 后台执行 | 本次未推送，无远程运行；MySQL/CAS 为 IP-03/07，真实后台调用与失败中断为 IP-08，不能由领域单测代替 | NOT VERIFIED，后续任务范围 |

后端完整检查首次在普通沙箱失败：既有随机端口上下文测试遭遇 `java.net.SocketException: Operation not permitted`，
同时 259 个领域用例通过；允许本机监听后全量 260 个测试通过，没有跳过/弱化测试。
失败日志 `/private/tmp/frameflow-ip02-backend-verify.log`，成功日志 `/private/tmp/frameflow-ip02-backend-verify-unrestricted.log`；
JUnit 在 `backend/target/surefire-reports/`。前端日志 `/private/tmp/frameflow-ip02-npm-ci.log`、
`frameflow-ip02-frontend-{test,lint,typecheck,build}.log`，JUnit 在 `frontend/test-results/junit.xml`。
这些生成文件不提交，临时路径可能被清理。ESLint 既有 EOL 提示保持，见 ADR-002。
实现者已完成自审；用户随后审查建模、要求以 Lombok 简化 getter、了解 FailureInfo 细节，并明确接受本任务。
本记录不将实现者自审表述为独立代码审查，也不将本任务验收扩展为整体业务验收或合并 main 的授权。

IP-02 review 补充（2026-10-30）：用户允许配置依赖并用构造器/getter/setter 等注解简化代码。
采用 Lombok（不是 Spring 注解）提供的 `@Getter`，替代 ProductionTask 的八个手写 getter，读取签名与领域行为不变。
保留包含组合校验的私有构造器；不生成绕过状态机的 setter。现有不可变 record 已由 Java 自动提供值访问器和构造器，无需额外注解。
Java 的类型 import 不由 POM 依赖代替，继续使用明确导入。
`backend/pom.xml` 引入 Boot 4.0.9 BOM 管理的 Lombok 1.18.48（provided），显式配置 annotationProcessorPaths，
并从 Boot JAR 排除；`backend/lombok.config` 停止继承上级配置并关闭生成的 Lombok 字节码注解。
`backend/Dockerfile` 同步复制该配置；ADR-002/006 和 architecture.md 记录这一获用户授权的编译期适配。
补充验证：同一 Java 17 / Maven 缓存下运行前述领域定向 test（首次去掉 -o 下载依赖）和完整 verify，
分别 259 / 260 tests 通过，0 failures/errors/skips；现有 jdeps 与无 setter 检查保持通过，没有放宽断言。
检查最终可执行 JAR 无任何 Lombok 条目；自审配置和 Dockerfile 的 COPY，`git diff --check` 通过。
日志 `/private/tmp/frameflow-ip02-lombok-test.log`、`/private/tmp/frameflow-ip02-lombok-verify.log`；
前端未改动，沿用本轮此前通过的回归证据；未构建 Docker 镜像或运行远程 CI。

</details>

### IP-03：初始数据库结构与真实 MySQL 测试门禁

当前实施状态：初始结构、集成测试与 CI 配置已实现，本地及远程四个 job 均通过，PR #2 待用户审查/验收，见 V-03。

**依赖**：IP-01。**范围**：四表有版本标识的初始 schema、索引/CHECK/聚合内外键、隔离测试库配置、Maven 集成测试入口、CI MySQL 8.4 service。
本任务只准备账号表，实际账号初始化归 IP-04；模板/媒体不进 SQL。不启用启动自动建表，不新增迁移框架。

**验收 / V-03**：全新隔离库可按脚本初始化；实际 JDBC 检查四表、索引、唯一性及 FK；非法状态和 NULL/结果/失败组合被拒绝，UTC/字节边界明确。
使用真实 MySQL，不以 H2/上下文测试替代；以 *IT 和 Maven Failsafe 接入 integration-tests profile：
CI 执行 mvn --batch-mode --no-transfer-progress -Pintegration-tests verify，必须发现并执行真实集成测试，测试库不可用时失败。
普通 Maven verify 保留不要求数据库的快速测试；两种证明范围分开。CI 临时测试凭据不复用用户数据/密码；输出不泄漏凭据。
初始化脚本仅用于初始结构；已有卷显式初始化方式与“首次空卷脚本不会重跑”均说明，不删除用户卷。

#### IP-03 实际执行记录（V-03）

<details>
<summary>展开记录</summary>

2026-10-30，Asia/Shanghai；状态：**IMPLEMENTED / LOCAL & REMOTE VERIFIED，待用户验收**。
用户授权按 docs、task-development-workflow 和 ddd-skill 执行第三个任务。执行前工作区干净，
依赖 IP-01 已验收；采用 ADR-006/008 的单 Maven 工程及现有四表设计，没有改变领域模型或公共 API。
首次实施与自审阶段未提交、推送或运行远程工作流；用户随后授权在 `codex/ip-03-database` 分支提交、推送并创建 PR。
发布包含用户确认的后续 XML Mapper 任务约定；远程 CI 结果另行记录，不自动开始 IP-04，正式验收状态保持待用户确认。

新增 `backend/src/main/resources/db/schema/V001__initial_schema.sql` 和
`backend/src/test/java/com/frameflow/studio/infrastructure/dao/SchemaIT.java`；
修改 `backend/pom.xml`、`.github/workflows/ci.yml`、README、database-design.md、ADR-008 和本记录。
Failsafe 3.5.6 沿用 Boot 父 POM 管理，仅在 `integration-tests` profile 启用并要求实际发现测试。
CI 增加独立 MySQL 8.4 service job、SQL 健康检查、动态端口、每 run 的临时凭据及测试报告上传，保留所有原有 job。

V001 的 SHA-256：`11aee8420548d195a912b5e348d472262cc23be4a0a54143b9f529822b793f12`。
四表使用 InnoDB/utf8mb4，机器值 ascii_bin、UTC DATETIME(6)；只有任务根/输入有 RESTRICT 外键。
显式保护可空列的 CHECK 防止 UNKNOWN 放过非法结论；状态/阶段/失败类型/MIME 采用二进制比较，拒绝尾部空格；
revision 仅要求非负，不将流程步数写死。
没有账号种子、模板/媒体 SQL、Mapper、迁移框架、启动自动建表或新的业务类。

本机原 Docker 未启动，现有 conda MySQL 5.7 不符合验证要求。随后启动 Docker Desktop，
使用已有 mysql:8.4 镜像启动专用临时实例（实际 8.4.10、随机测试密码、loopback 动态端口、tmpfs 数据目录）。
未连接业务库、未读取真实 .env、未删除用户数据卷；另一个临时容器用于原样核对 CI service 的健康检查参数。

| V-03 条件 / 检查 | 实际证据 | 状态 |
| --- | --- | --- |
| 空库初始化、四表/字段/索引/唯一性/FK | SchemaIT 元数据及真实插入检查；主键、用户名、content_key 唯一；历史组合索引含两个 DESC 列；仅聚合内 FK，拒绝孤儿输入及删除/修改有输入的任务根 | PASS |
| 九种合法结论与非法 NULL/结果/失败组合 | 遍历 3240 种状态/阶段/结果/失败字段组合，恰好 9 种接受；3231 种以 CHECK/NOT NULL 错误拒绝；另查非法更新、大小写、空白值及机器枚举尾部空格 | PASS |
| 数值、字符和 UTC 边界 | 素材 1/8388608 字节接受，0/负数/8388609 拒绝；position/revision 负数拒绝，revision 99 可存；倒退时间拒绝；UTF-8 中文/emoji 字符数与字节数分开、超长/非 ASCII 拒绝；UTC 微秒在 +08/+00 会话间明确转换与回读 | PASS |
| 输入及事务能力 | 相同素材可以占两个位置；输入主键冲突后显式回滚，根和输入全部消失；这是 JDBC/InnoDB 能力验证，真实 Repository/Case 原子创建仍归 IP-07 | PASS |
| Maven 完整集成入口 | Java 17.0.12：`mvn -Dmaven.repo.local=/private/tmp/frameflow-m2 --batch-mode --no-transfer-progress -Pintegration-tests verify`；260 个快速测试 + 13 个真实 MySQL 测试通过，0 failures/errors/skips，JAR 打包成功 | PASS |
| 快速入口不依赖数据库 | 同一 JDK/缓存：`mvn -Dmaven.repo.local=/private/tmp/frameflow-m2 --batch-mode --no-transfer-progress verify`；260 tests 通过，未执行 Failsafe | PASS |
| 门禁拒绝假通过 | 使用 `-Pintegration-tests -Dit.test=SchemaIT failsafe:integration-test failsafe:verify`，分别缺密码、端口不可达、业务库名、非空测试库，全部 BUILD FAILURE/exit 1；哨兵 id=7 保留且未创建额外表 | PASS |
| 没发现 IT 必须失败 | 指定不存在的 IT 时 exit 1；另临时移走再恢复 target 中 SchemaIT 编译产物，默认发现零个 IT 时也 exit 1（No tests were executed），源码不改、不关闭门禁 | PASS |
| README 初始化路径及首次/已有卷说明 | 按容器 mysql 客户端重定向执行 V001，SHOW TABLES 为四表、总行数 0；SchemaIT 验证重放报 1050；README 明确当前 Compose 未挂载、首次空目录规则、已有卷显式执行、DDL 非原子与禁止删用户卷 | PASS |
| CI service 健康检查配置 | 首次远程运行发现 runner 参数解析与 shell 不同；改为双引号分组后，run 41066158015 的 MySQL 8.4 服务初始化、JDBC 测试和报告上传全部成功 | PASS（远程） |
| 前端与 Compose 回归 | Node 22.21.1：`npm ci --offline --cache /private/tmp/frameflow-npm-cache`、`npm run test:ci`（1 test）、lint、typecheck、build/postbuild 全通过；后两项设置 NEXT_TELEMETRY_DISABLED=1；根 Compose config --quiet 通过 | PASS |
| 实现者自审 | 审查新增脚本/测试及全部实际 diff；发现并定向复现 ascii_bin 的 PAD SPACE 使 `PENDING ` 通过，改用二进制比较并加入状态/阶段/失败类型/MIME 回归；修复后无阻塞缺陷，没有独立审查者 | PASS |
| 远程 CI | 修复提交 5d1097a 的 PR run 41066158015：backend、frontend、compose、mysql-integration 四个 job 全部成功；日志核对 260 个快速测试、13 个真库测试和 1 个前端测试，报告均上传 | PASS；首次失败与修复证据保留在下文 |
| 应用容器/登录/媒体/Repository 条件更新 | 未构建/启动三服务业务环境；这些不能由 schema 测试证明 | NOT VERIFIED，IP-04～15 范围 |

日志为 `/private/tmp/frameflow-ip03-{schema-it,integration-verify,backend-verify}.log`、
`frameflow-ip03-negative-{unavailable,missing-config,unsafe-name,no-tests,nonempty,empty-discovery}.log`，
以及 `frameflow-ip03-npm-ci.log` / `frameflow-ip03-frontend-{test,lint,typecheck,build}.log`。
测试报告在 `backend/target/{surefire-reports,failsafe-reports}/` 及 `frontend/test-results/junit.xml`，
这些生成文件不提交，临时路径可被清理。ESLint 9 既有 EOL 提示保持，未更改依赖版本。
失败门禁检查后恢复编译产物与空测试库，并再次运行完整集成入口，使最终报告保留成功回归结果。
尾部空格缺陷的失败复现日志为 `/private/tmp/frameflow-ip03-padding-reproduction.log`（1 test，预期失败），
修复后的全量结果使用最终 `integration-verify` 日志；不将失败复现当作仍未解决的门禁失败。
初始化仅对临时实例执行，未替用户的业务库建表；账号与媒体初始化尚未实现。
结束时已删除本次两个临时容器及其匿名卷，并移除临时密码环境文件；校验源码/本次日志未包含这些随机密码。

发布记录：实现提交 `eb8acc72610a8dd6ba9c46447f01d283baaf7f9a`，分支 `codex/ip-03-database`，
[PR #2](https://github.com/ShengboYY/frameflow-studio/pull/2)，目标 main。首次
[远程 CI 41065816700](https://github.com/ShengboYY/frameflow-studio/actions/runs/41065816700)
暴露 `--health-cmd` 外层单引号在 runner 参数解析中未正确分组，Docker 误将命令中的 mysql 作为镜像名，
后续环境变量未作用于服务。将外层改为 runner 支持的双引号，内部 SELECT 使用单引号保留 SQL 参数，
仍以真实 SQL 校验服务就绪，不跳过测试或降低报告门禁。

修复提交 `5d1097a865e9844bffa5cc48e55ab2c7898c95c1` 的
[远程 CI 41066158015](https://github.com/ShengboYY/frameflow-studio/actions/runs/41066158015) 全部成功，
实际 runner 为 Ubuntu 24.04 / Java 17 / MySQL 8.4.13；核对三个测试 job 的执行日志，
backend 为 260 tests，mysql-integration 为 260 个快速测试 + 13 个 SchemaIT，前端为 1 test，均无失败/错误/跳过。
三份报告均成功上传：backend-test-reports（12983410817）、mysql-integration-test-reports（12983170075）、
frontend-test-reports（12982143863）。这些证据对应上述明确提交；随后仅补充本文和 README 的验证记录，
不将 schema 验证扩展为登录、业务 Mapper 或三服务业务闭环验收。PR 保持开放，不自动合并或勾选用户验收。

</details>

### IP-04：预置账号、Java 登录与会话保护

**依赖**：IP-03。**范围**：Identity 本域查询/PasswordVerifier、认证和当前账号 Case、显式账号初始化、安全过滤器/配置及 JSON 错误出口。
账号查询和初始化 SQL 使用 `mybatis/mapper/identity/user_account_mapper.xml`，配套 Identity DAO/PO；
首次加入 XML 时配置 `mybatis.mapper-locations`，覆盖各业务子目录，沿用 DAO 上的 `@Mapper` 注册方式。
使用 Boot 管理的 Security；从 DEMO_A_PASSWORD/DEMO_B_PASSWORD 初始化两个账号，无注册/API 创建/管理员。
公共错误映射在本任务建立最小基础，后续模块只增量补充对应错误。

**验收 / V-04**：真实 MySQL 中仅保存带盐编码；重复初始化不重置密码/account_id；缺失首次密码和不合格字节长度明确失败。
通过实际账号 DAO 执行 XML 中的查询/插入，验证 XML 可加载、参数绑定及账号字段映射正确。
Case 测试与 API Tests 检查统一登录失败、表单字段/编码边界、已登录 409、会话恢复、Session ID 更新、退出失效、空闲/绝对过期及 Cookie 属性。
CSRF 覆盖登录/退出及后续所有 POST；旧 token 不可在新会话写入；401/403/503 为契约 JSON，DB 故障不能当成未登录。
用可控时间验证到期，不真实等待数小时；Domain 不持有 Session/SecurityContext。账号身份稳定，Worker 不依赖登录存续。

#### IP-04 实际执行记录（V-04）

<details>
<summary>展开记录</summary>

**执行范围与分支**：2026-10-30 用户授权从 main 新建分支执行第四个任务，使用 task-development-workflow、
ddd-skill、mybatis-patterns、springboot-patterns。工作区起始干净；同步 origin/main 后从 `2d5865f`（含 IP-03 合并）
创建 `codex/ip-04-identity-session`。先完成以下本地验证；2026-10-31 用户授权 commit/push 并创建 PR，
发布授权见下文，本地证据不代表用户最终验收。

**实现**：Identity 只读 Account、Repository/PasswordVerifier、认证/当前账号/显式初始化 Case；
`IUserAccountDao` + `UserAccountPO` + `mybatis/mapper/identity/user_account_mapper.xml`，DAO 使用 @Mapper，
统一 `classpath*:mybatis/mapper/**/*.xml` 加载，值参数全用绑定，无注解 SQL。
BCrypt cost 12 + 随机盐 + 算法标识；缺失账号先验证密码、事务外生成编码，再以 REQUIRES_NEW/READ_COMMITTED
短事务原子插入两个账号。重复/竞争插入为 no-op，已有账号永不重置；拒绝在已有事务内初始化。
显式初始化命令、环境变量与本地 Cookie 配置见 README；保持 SQL 自动初始化关闭，V001 未改动。

Spring Security 7.0.8 由 Boot 4.0.9 BOM 管理，标准表单认证过滤器调用 Provider → Case，保存 Session、
登录更换 ID、退出失效并清除同范围 Cookie。传输过滤器严格校验字段/百分号/UTF-8 字节边界；
CSRF 使用 HttpSession repository 和框架掩码处理，只接收 X-CSRF-TOKEN。
可注入 Clock 的过滤器在读取 SecurityContext/CSRF 前检查 30 分钟空闲与 8 小时绝对期限。
API-01/02/13/14 已提供；JSON 错误/请求 ID/no-store 一致；数据库故障保留 503，不当成认证失败。
Cookie 默认 Secure=true；仅本地 `.env.example` 显式 false，Compose 透传该选项，清除 Cookie 同为 Path=/api。
未创建注册/账号管理、前端登录、任务/上传业务，不实现后续任务。

**验收证据**：

| V-04 条件 | 实际检查 | 状态 |
| --- | --- | --- |
| 真实账号 SQL / XML namespace、绑定、字段映射、UTC | IdentityIT 实际 DAO 查询/插入、注入式字符串无匹配、null 查找、UTC 微秒往返 | PASS |
| 带盐编码、字节限制、缺失密码明确失败、重复初始化稳定 | IdentityIT + IdentityCasesTest：相同密码不同 cost 12 编码、72/75 UTF-8 字节、无密码无写入、已有 ID/hash 不变 | PASS |
| 原子初始化及事务边界 | 第二次真实 Mapper 插入后故意抛错，两条记录均回滚；散列先于事务；已有外部事务被拒绝 | PASS |
| 登录失败/输入/409/会话恢复 | AuthApiTest：未知账号与错密同 401，重复/未知/畸形字段 400，编码 415，过长请求 413，已登录 409 | PASS |
| Session ID 轮换、退出、Cookie、过期 | AuthApiTest / SecureCookieTest：真实 HTTP 轮换/清 Cookie、Secure 默认与 loopback 选项；Clock 控制空闲/绝对期限，无小时级等待 | PASS |
| CSRF 与 JSON 错误 / no-store | 登录/退出/未来 assets/tasks POST 缺 token 均 403；参数 token 被拒；登录前/注销旧 token 无效；401/403/503 JSON 与 header/body requestId 一致 | PASS |
| DB 故障与账号不存在不同 | 故障注入返回 503 且保留有效会话；账号移除返回 401 并使会话失效 | PASS |
| 稳定账号身份 | IdentityIT 经真实 HTTP/数据库 A 登录→退出→B 登录，两个 ownerId 正确且不同；重复初始化 ownerId 不变 | PASS |
| 分层依赖 | IdentityBoundaryTest 用 jdeps 检查 Domain/API/Case/Trigger；2026-10-31 补充 Infrastructure 限制并修正配置对 Controller 的依赖，见下方审查修正记录 | PASS（修正后） |
| 显式可执行初始化入口 | 临时空库执行 V001；JAR non-web 初始化缺失密码非零退出/零行；成功创建两账号；清空密码后重跑 ID/hash 相同，全局 DEBUG 日志也无测试密码或编码 | PASS |
| 远程 CI / Next 代理 / 业务资源 owner 隔离 / 三服务容器 | 远程 CI 结果另行核对；其余分别属于后续 IP-11、06/09/10、15，不从认证测试推断通过 | NOT VERIFIED |

**实际命令**（Java 17.0.12、Maven 3.9.10、Node 22.21.1）：

- `mvn --batch-mode --no-transfer-progress verify`：302 个快速测试，0 failures/errors/skips，可执行 JAR 打包通过。
- `mvn --batch-mode --no-transfer-progress -Pintegration-tests verify`：302 个快速测试 + 19 个真库测试（IdentityIT 6、SchemaIT 13），0 failures/errors/skips。
  本任务独立临时 MySQL 8.4 容器、loopback 随机端口、随机凭据，与业务库/卷无关；结束清理本次容器。
- 初始化命令：`java -jar target/frameflow-studio-0.0.1-SNAPSHOT.jar --spring.main.web-application-type=none --app.accounts.initialize=true`，
  连接上述隔离库，按缺密/首次/重复三个分支检查；未记录或提交可用密码。
- `npm ci`、`npm run test:ci`（1 test）、`npm run lint`、`npm run typecheck`、`npm run build`：全部通过；沿用既有 ESLint 9 EOL 限制。
- `docker compose --env-file .env.example config --quiet`：通过，仅证明配置解析；`git diff --check`：通过。

**自审与修正**（同一执行者，非独立审查）：检查实际 diff 与所有新增文件，补上仅 header CSRF 验证，
清 Cookie 显式指定 Path=/api / Secure / HttpOnly / SameSite；注册唯一认证 Provider，避免生成默认开发账号。Identity DAO 固定 INFO，避免全局 APP_LOG_LEVEL=DEBUG 时记录密码编码参数。
最初 MockMvc 请求未模拟有效原会话 ID，导致轮换断言失败；修正模拟请求后与真实 HTTP 检查均通过。
初始化命令验证脚本曾将空字符串误判为凭据出现在日志，修正脚本后缺密/首次/重复分支全部通过，非应用密码泄漏。
初始编译和测试调试失败均在本地修复，最终检查未弱化测试或屏蔽错误。

**2026-10-31 架构审查修正**：用户要求检查并修正 ddd-skill 的代码位置与分层。
自审确认原 SecurityConfiguration 的登录成功回调直接调用 AuthController.response，且边界测试未覆盖 Infrastructure；
此前仅凭原边界测试通过概括全部架构约束成立不够准确。新增依赖断言在旧实现上先失败，修正后通过。

- HTTP 登录成功/失败、401/403、退出状态与清 Cookie 回调集中到 `trigger.http.identity.AuthenticationHttpHandlers`；
  仅 header 的掩码 CSRF 处理移至同包 `HeaderCsrfTokenRequestHandler`。SecurityConfiguration 只注册处理器/过滤器与安全策略，
  不再引用 Controller、API DTO 或 Case 模型。AccountAuthenticationProvider 在 Trigger 按 Servlet 条件注册为唯一认证组件。
- 显式账号初始化入口移至 `trigger.cli.identity.PresetAccountsInitializer`，继续仅在 `app.accounts.initialize=true` 启用；
  IdentityConfiguration 只保留 Clock/PasswordEncoder 装配，命令、密码政策和初始化 Case 保持不变。
- IdentityBoundaryTest 检查 Infrastructure 不依赖 API/Controller/Case 业务契约或模型；仅技术 Case Port 允许跨入 Cases，
  只有 config 可引用用于装配的 Trigger 技术适配器。`jdeps` 确认配置已不再依赖 Controller、API 或 Cases。
- Java 17 执行 `mvn --offline --batch-mode --no-transfer-progress -Pintegration-tests clean verify`：
  302 个快速测试 + 19 个真 MySQL 测试全部通过，0 failures/errors/skips；真实 HTTP Cookie/登录/退出、CSRF、过期、DB 故障回归通过。
  非 Web JAR 初始化入口另在本任务临时空库复验缺密失败、首次写入、无密码重复执行、ID/hash 稳定及 DEBUG 日志脱敏。
- 本次只修改 IP-04 后端职责和架构说明；前端与 Compose 文件未变，不重复运行已记录的前端检查或三服务容器验收。
  `git diff --check` 和新增文件空白检查通过；本轮仍为同一执行者自审，修正完成后等待用户验收。

**发布授权（2026-10-31）**：用户明确要求 commit、push 并提交 PR；仅发布当前 IP-04 实现、测试、配置与相关文档，
目标为 main，不合并 PR，不自动更改最终验收状态。远程运行与 PR 链接以发布后实际结果为准。

**交接**：READY FOR ACCEPTANCE，IP-04 checklist 保持 `[ ]`，待用户明确验收；不会自动开始 IP-05。
会话仍为单实例内存，重启需重新登录，初始化不自动改密；Next Cookie 透传和业务 owner 授权由后续任务验证。

</details>

### IP-05：只读模板目录与合法示例资源

**依赖**：IP-01。**范围**：两项打包 JSON 模板、分类、封面和 Mock 图片资源及来源说明；Template 只读能力/Asset 媒体能力，规则值对象。

**验收 / V-05**：Java 返回不同分类及 1/2 张图片要求，5/8 MiB 和 JPEG/PNG 与现有契约一致；未知分类空列表、未知模板/封面 404。
素材要求的数量/大小边界有单元测试，单张上传验证和完整提交验证各有适用语义；种子重复 ID/无效规则/不可读资源使启动明确失败。
API/资源测试确认图片字节可解码、真实 MIME、公开 key 白名单，不能借路径访问私有文件；不建立模板表或管理后台。

#### IP-05 实际执行记录（V-05）

<details>
<summary>展开记录</summary>

2026-10-31，Asia/Shanghai；状态：**READY FOR ACCEPTANCE，实现与本地验证完成，待用户审查/验收**。
执行前工作区干净；实现阶段未提交、推送、创建 PR、部署或启动 IP-06。用户随后授权发布，见下方记录。
使用 ddd-skill 2.2.4 的值对象、Case、命名及领域设计指引，并遵循 ADR-006 的项目适配。

**实现范围**：

- `domain.template`：只读 Template / Category、AssetRequirements；类型/大小和完整提交数量校验为纯 Java。
  `ITemplateCatalogRepository` 仅提供真实需要的目录读取，无模板表、DAO、写服务、策略或责任链框架。
- `cases.template`：ITemplateCatalogCase 和不可变输入/结果；分类/筛选/详情，单张及完整提交验证均使用同一份模板规则。
  单张上传不检查总数；完整提交按位置计数，不去重；不存在模板返回明确的空结果。
- `infrastructure.adapter.repository.template`：启动时严格读取 `templates/catalog.json`；两项种子保持 API 约定的分类、1/2 张、5/8 MiB、JPEG/PNG。
  无效字段/规则、空目录、重复 ID、分类名称冲突及未登记封面使 Bean 创建失败。
- `domain.asset.adapter.port.IDemoMediaPort`、PackagedDemoMediaPort、IMediaResourceCase：打包图片在启动时真实解码并检测 MIME，
  内存保存不可变内容。请求 key 只查询已加载白名单，不拼接文件路径；Mock fixture 仅供后端 Case 使用。
  CatalogConfiguration 负责资源装配与封面登记检查，Template 与 Asset 不访问彼此内部仓储/模型。
- `api.template.dto`、`trigger.http.template/asset`：API-03～06 已实现；未知分类空列表，未知模板/封面 JSON 404，
  非法 ID、未知/重复查询参数 JSON 400。错误响应显式声明 JSON，兼容只接受图片的客户端。
- 三张原创 Java2D 几何图片、生成器及 `demo-media/SOURCES.md` 来源/CC0 说明已加入仓库资源目录；构建无需下载素材或运行绘图。PR 审查后统一为 JPEG，见下方修正记录。
  README、API、架构、AGENTS 与本任务记录同步实现状态；既有 AuthApiTest 的模板列表预期从尚无端点的 404 更新为 200。

**首次验收证据（统一 JPEG 前）**（对应 R-02 的 Java 目录/资源、R-03 的模板说明及规则、R-13 的来源/初始化材料；不代替前端与业务闭环验收）：

| V-05 条件 | 实际测试 / 检查 | 结果 |
| --- | --- | --- |
| 分类/不同图片数/字段/筛选 | TemplateApiTest 使用真实装配目录校验分类、摘要、详情和 1/2 张、5/8 MiB；未知分类空列表 | PASS |
| 数量、大小、类型边界与两种校验语义 | AssetRequirementsTest / TemplateCatalogCaseTest：1/2 张精确数量、重复位置、大小 0/负值/1/上限前后/Long.MAX_VALUE、JPEG/PNG/不支持类型 | PASS |
| 无效种子拒绝启动 | TemplateCatalogRepositoryTest：实际 Spring context refresh 失败，覆盖重复 ID、无效数量/类型/上限、浮点/字符串/null 数量、未登记封面和分类冲突；正常种子加载且不可修改 | PASS |
| 图片可用/真实 MIME/不可读资源拒绝启动 | PackagedDemoMediaPortTest：两封面和内部 fixture 解码、PNG/JPEG 字节检测、不信任文件名、返回字节防修改、缺失/损坏/截断资源失败 | PASS |
| 公开 key 白名单及 HTTP 行为 | TemplateApiTest：真实 HTTP 两封面均为可解码 640×400 PNG；内部 fixture 404；编码路径穿越及任意路径无图片返回；400/404 JSON/requestId 一致 | PASS |
| 分层 | CatalogBoundaryTest + 既有 IdentityBoundaryTest：jdeps 检查真实编译类；新 Domain 仅依赖 Java/本域，Case 不依赖 API/基础设施，Controller 只调 Case | PASS |
| 可执行 JAR 的资源独立性 | 从临时空工作目录启动 JAR，核对 catalog/三 PNG/来源文件均已打包；故意使用不可达 DB 端口，公开分类/列表/两详情/两封面正常，未建立连接池；图片 Accept 下内部 fixture 返回 JSON 404 | PASS；仅证明公开目录不依赖 JDBC，不证明数据库连通 |

**实际命令与报告**（Java 17.0.12、Maven 3.9.10）：

- `backend/`：`mvn --offline --batch-mode --no-transfer-progress verify`，**332 tests，0 failures/errors/skips**，可执行 JAR 打包成功。
  新增 30 项测试，全部自动进入现有 Maven/CI 入口；未新增依赖或修改 CI/POM。
  报告 `backend/target/surefire-reports/TEST-*.xml`；日志 `/private/tmp/frameflow-ip05-verify.log`。
- 新增六个测试类：AssetRequirementsTest、CatalogBoundaryTest、TemplateCatalogCaseTest、TemplateCatalogRepositoryTest、PackagedDemoMediaPortTest、TemplateApiTest；
  路径分别位于 `domain/template`、`cases/template`、`infrastructure/adapter/{repository/template,port/asset}`、`trigger/http/template`。
- JAR smoke 摘要 `/private/tmp/frameflow-ip05-smoke-summary.json`，启动日志 `/private/tmp/frameflow-ip05-jar-smoke.log`；临时进程已停止、工作目录已清理。
- 根目录：`docker compose --env-file .env.example config --quiet` 通过，仅配置解析；diff/新增文件空白与文档链接已检查。

**调试与自审**：首次误用旧临时 Maven 缓存，缺少 IP-04 依赖且 DNS 受沙箱限制，改用现有完整缓存离线运行。
普通沙箱禁止测试监听端口，获工具允许后运行真实 HTTP 测试。补充图片专用 Accept 测试先复现四个 404 被协商为 406 的失败，
随后给新端点的错误响应明确设置 JSON，完整回归通过；负向复现日志 `/private/tmp/frameflow-ip05-accept-reproduction.log`。
同一执行者检查新增源码、资源和 diff，无独立审查结论；未弱化测试或跳过失败检查。

**边界与未验证项**：前端、数据库结构/SQL、Compose 和 CI 配置未改；本次不重复前端构建或 MySQL *IT。
本地实现阶段未运行远程 CI；随后发布所触发的 CI 结果以关联 PR 为准。Next 转发、浏览器页面及完整三服务容器未验证，分别由后续任务落实。
目录运行时只读；已有模板 ID 的语义不得随意变更。上传/任务提交端点尚未接入这些规则，Mock 资源不是实际生成能力。
IP-05 checklist 保留 `[ ]`，等待用户明确验收。

**发布授权（2026-10-31）**：用户明确要求 commit、push 并提交 PR；本次仅发布 IP-05 的实现、测试、打包资源和相关文档。
发布分支为 `codex/ip-05-template-catalog`，基于已包含 IP-04 的 main `2629ef1`；保留现有 Case 结构，不将发布授权等同于业务验收，也不合并 PR 或启动后续任务。

**PR #4 审查修正（2026-10-31）**：审查发现原 PNG Mock fixture 与既定 `image/jpeg` 结果契约不一致；
用户确认两张封面和 Mock 结果统一为 JPEG。生成器改用真实 JPEG 编码，三张资源均为 640 × 400 的 `.jpg`，
删除旧 PNG 并同步加载路径、来源说明及 README。公开封面 key 与内部 `demo:mock-result` 稳定引用保持原值。

先将媒体回归断言改为 JPEG，定向运行复现 `expected image/jpeg but was image/png`（1 test，预期失败），
日志 `/private/tmp/frameflow-pr4-jpeg-reproduction.log`。修正后使用 Java 17 执行
`mvn --offline --batch-mode --no-transfer-progress clean verify`，332 tests 全部通过，0 failures/errors/skips，
日志 `/private/tmp/frameflow-pr4-jpeg-verify.log`。媒体测试检查三张资源的 MIME、实际 JPEG 解码格式、尺寸和字节防修改；
真实 HTTP 封面响应为 `image/jpeg`，并保留 PNG/JPEG 检测及私有资源白名单负向测试。
核对干净构建的 JAR 只包含三张 `.jpg` 和来源文件，没有旧 `.png`；独立 fixture 契约检查已通过。
本修正的远程 CI 结果以 [PR #4](https://github.com/ShengboYY/frameflow-studio/pull/4) 对应提交检查为准。

</details>

### IP-06：上传、素材解析及本地存储

**依赖**：IP-03、IP-04、IP-05。**范围**：AssetStorage 本地实现、元数据仓储/DAO/PO、上传/解析/后台检查/资源 Case 和上传入口。
素材元数据 SQL 使用 `mybatis/mapper/asset/uploaded_asset_mapper.xml`，复用 IP-04 的 XML 加载配置。

**验收 / V-06**：实际图片类型/可读性/字节数按模板校验；8 MiB 文件、9 MiB multipart 及模板更小上限正确处理；声明 MIME/文件名不可绕过。
内容稳定保存后才写元数据并返回 ID；文件成功/DB 失败时不返回可用素材、尽力清理，清理失败按既定窗口记录。
用临时目录 + Application Service Tests、真实 MySQL 集成及 multipart API Tests 检查错误状态/结构、本人读取、越权统一 404、路径越界和文件丢失。
真库测试通过素材 DAO 验证 XML 中的元数据写入、带 owner 条件的读取及字段映射。
解析保持输入顺序及重复位置；后台检查有真实 I/O 边界；无删除/消费语义，无模板永久绑定，Asset 不反向调用 Task。

#### IP-06 实际执行记录（V-06）

<details>
<summary>展开记录</summary>

2026-10-31，Asia/Shanghai；状态：**IMPLEMENTED / LOCAL VERIFIED，READY FOR ACCEPTANCE，待用户验收**。
用户要求执行下一个 task，依既有顺序实施 IP-06；执行前通过 Command Line Tools Git 确认工作区干净。
沿用 IP-03 四表、IP-04 Cookie/CSRF 与 XML 加载、IP-05 模板规则和打包媒体；无新依赖、DDL 或公共契约变更。

**实现**：

- UploadedAsset 是不可变轻量实体，保存服务端 UUID、owner、实际类型/字节数、不透明 contentKey 和 UTC 时间。
  元数据 Repository → IUploadedAssetDao / UploadedAssetPO → `mybatis/mapper/asset/uploaded_asset_mapper.xml`；
  查询同时绑定 assetId 与 ownerId，API 不返回 PO、内部 key 或本地路径。
- IUploadAssetCase 先查模板，通过 IAssetStoragePort 有界接收/解码，使用 Template Case 的单张规则检查；
  同目录 `.part` 校验后移动为不可替换的 `.image`，再以 REQUIRES_NEW / READ_COMMITTED 短事务写库。
  提交成功才返回描述；异常时尽力清理，清理失败只记录素材 ID。输入读取/图片解码不持有 DB 事务。
- IResolveTaskAssetsCase 保留顺序与重复位置，ICheckTaskAssetsCase 每次重新查询并实际读取内容；
  IMediaResourceCase 扩展本人输入读取，缺失/其他 owner 统一未找到，内容缺失/字节数不符为不可用。
  素材不绑定模板、不消费、不删除；Asset 不访问 Task。
- API-07/08 实现上传 201 和本人图片 200；multipart 恰好一个 file 和一个 templateId，拒绝未知/重复部分及输入 owner。
  实际 JPEG/PNG 检测与 ImageIO 解码不信任文件名/声明 MIME；输出采样限制解码图像内存，原始文件字节保持。
  配置单文件 8 MiB、请求 9 MiB、模板单张上限，框架/业务错误统一 JSON/requestId/no-store；图片保留 nosniff。
- ASSET_STORAGE_ROOT 默认 `./data/assets`（相对于 Java 工作目录）；内部 key 严格限制为服务器 UUID，读取拒绝符号链接。
  README、API、架构/ADR 状态及根 AGENTS 已同步；Compose 素材卷仍按计划归 IP-15。

**验收证据**：

| V-06 条件 | 实际检查 / 证据 | 结果 |
| --- | --- | --- |
| 实际类型/可读性/大小 | LocalAssetStoragePortTest 验证 JPEG/PNG、原始字节跨适配器重建读取、空/伪造/截断图片、8 MiB 含等号及超限、输入 I/O 异常清理 | PASS |
| multipart 全局及模板限额、错误结构 | AssetApiTest 用真实随机端口 Tomcat + HttpClient 发 multipart：8 MiB / 8 MiB+1、5 MiB / 5 MiB+1、超过 9 MiB 请求；缺失/重复/未知部分、伪装 MIME/路径文件名、损坏图片、未知模板、错误编码、无 boundary | PASS |
| 身份及资源隔离 | HTTP Cookie/CSRF 真实过滤链、本人图片、其他 owner 与未知 ID 同为 JSON 404、非法 ID/查询串 400、文件丢失 503；检查 no-store、nosniff、requestId 及无内部路径/SQL | PASS；HTTP 测试替换认证 Case、素材 Repository 和事务管理器，真实会话/CSRF 及存储仍执行 |
| 文件就绪后提交、失败不返回 ID | AssetCasesTest 检查稳定内容先于事务、事务参数、提交顺序、插入/提交失败清理、清理失败保留原异常、未知模板不做 I/O、模板超限不写库 | PASS |
| XML DAO 与真实 MySQL | AssetIT 实际 DAO/Repository 检查全部字段、UTC 微秒、owner 条件/参数绑定、防 SQL 注入、重复主键；真实上传可读、重复素材顺序、后台再次检查磁盘 | PASS |
| 真事务回滚和 I/O 边界 | AssetIT 在实际 Mapper 插入后注入异常，确认行回滚且文件清理；输入读取不在事务中、插入在事务中；拒绝带现有事务启动上传 | PASS |
| 路径及分层 | LocalAssetStoragePortTest 拒绝路径/URL/越界 key、丢失内容和符号链接；既有 CatalogBoundaryTest / IdentityBoundaryTest / TaskDomainBoundaryTest 使用 jdeps 检查真实编译类 | PASS |
| 后端完整集成入口 | Java 17.0.12：`mvn --offline --batch-mode --no-transfer-progress -Pintegration-tests verify` | PASS；345 个快速测试 + 22 个 MySQL 测试（13 SchemaIT、6 IdentityIT、3 AssetIT），无失败/错误/跳过，JAR 打包成功 |
| 普通快速入口 / 最终回归 | 同一 JDK：`mvn --offline --batch-mode --no-transfer-progress verify` | PASS；345 tests，无失败/错误/跳过，不执行 Failsafe |
| Compose | 根目录 `docker compose --env-file .env.example config --quiet` | PASS；只证明配置解析 |

真实 MySQL 使用本任务专用的 mysql:8.4 临时容器（实际 8.4.10）、随机凭据、loopback 动态端口与 tmpfs 数据目录；
不读取真实 `.env`，不连接业务库，不删用户卷。三个 IT 套件都先检查隔离库为空，各自结束仅清理本次创建的四张表。
结束时确认测试库表数为 0，已删除本任务临时容器及凭据文件，并核对日志没有随机数据库密码。
测试报告位于 `backend/target/{surefire-reports,failsafe-reports}/`；
日志 `/private/tmp/frameflow-ip06-{focused-unrestricted,api,integration-verify,verify}.log`。
这些构建/临时文件不提交，可能被后续运行覆盖或系统清理。

**调试与自审**：初次普通沙箱运行的 Mockito attach 被环境限制，存储测试通过、Case 测试无法初始化；
在允许 JVM attach 与本机随机端口的环境运行后通过，未跳过或弱化测试。
真实 HTTP 测试先复现超过 9 MiB 请求时连接中断（日志 `frameflow-ip06-multipart-reset-reproduction.log`），
定位为 Tomcat 默认剩余请求体吞咽上限；配置 `max-swallow-size: 10MB` 后，原边界测试返回完整 JSON 413。
实现者自审源码、实际 diff、新文件、错误/事务边界和文档，无独立审查者；最终检查无阻塞发现或无关修改。

**限制**：文件与 DB 无 XA，进程崩溃/提交结果未知/响应丢失仍可能留下孤立文件或元数据；清理失败只脱敏记录，
没有自动清理/重传/恢复。素材目录重启需保持，Compose 持久卷和三服务容器闭环仍归 IP-15。
前端实现未改，本轮未重复前端构建；未运行远程 CI、未提交/推送。IP-07 未开始；IP-06 checklist 保留 `[ ]` 待用户验收。

</details>

#### IP-06 审查修正：上传解码资源限制（2026-10-31）

<details>
<summary>展开记录</summary>

三个技能审查发现原 `setSourceSubsampling` 只限制输出栅格，不能限制 PNG 解码器按原图宽度分配的扫描行。
已用独立 JVM 复现：145866 字节、50000000 × 1 的真实 RGB PNG，在 512 MiB 堆下通过原存储适配器后占用约 435 MiB 堆，
128 MiB 堆下触发 PNG 解码器内部 OOM。用户随后明确授权修改；此前“无阻塞发现”是首轮自审的历史结论，本项补充并修正该结论。

修复在 LocalAssetStoragePort 的 `reader.read` 前检查原始宽高各 <=8192、总像素 <=16000000、
预计工作内存 <=128 MiB，任一超限为 TOO_LARGE → 413 PAYLOAD_TOO_LARGE。
预算按位深/通道估算两份完整栅格和三行原始扫描缓冲，最低按 4 字节/像素、未知原始类型按 8 字节；
像素与预算使用 long 运算，尺寸先检查再查询原始图像类型。该预算是单图片工作内存估算，不代表 JVM 总内存或并发上限。
图片读取忽略无需使用的描述性元数据，保留采样和实际解码检查，保存内容仍是原始字节。
超限沿用失败清理流程，不移动为稳定内容、不插入元数据/开启事务。

本次收紧上传接受范围，README、api-contract、detailed-architecture 和 ADR-008 已明确限值、公式及兼容性；
未新增 API 字段、错误码、数据库结构、依赖或通用校验框架，前序未提交的 IP-06 改动完整保留。

| 修正检查 | 实际结果 |
| --- | --- |
| 先复现回归失败 | 新增存储回归断言先在旧代码运行：8193 × 1 PNG 被接受，测试失败；`frameflow-ip06-image-limits-red.log` |
| 存储拒绝/接受边界 | 8192/8193 两个方向、16000000/16004000 像素、16-bit RGBA 工作预算两侧、极大整数宽高、缺少栅格的超限头部；合法 JPEG/PNG、合法 16-bit PNG、原始字节和清理全部通过 |
| 真实 HTTP | AssetApiTest 新增高压缩尺寸/像素/预算超限请求，均返回标准 JSON 413；验证未写 Repository、未开启事务且没有临时/稳定文件 |
| 原问题限堆复验 | 同一 145866 字节真实 PNG，`-Xmx128m` 下被 TOO_LARGE 拒绝、临时文件数 0，拒绝后堆占用约 3 MiB；`frameflow-ip06-image-limits-probe.log` |
| 后端最终验证 | Java 17.0.12，`backend/`：`mvn --offline --batch-mode --no-transfer-progress verify`，348 tests，0 failures/errors/skips，JAR 打包成功；`frameflow-ip06-image-limits-final-verify.log` |
| Compose / 自审 | 根 `docker compose --env-file .env.example config --quiet` 与 diff/new-file 空白检查通过；实现者自审无剩余阻塞发现，不作为独立审查 |

以上日志均在 `/private/tmp/`；Surefire 报告在 `backend/target/surefire-reports/`。
本修正不修改 SQL/仓储/事务代码，真实 MySQL 22 项沿用本节此前证据，本轮未重跑；前端与远程 CI 未运行。
原审查发现的尺寸解码内存问题已修复，当前 **READY FOR ACCEPTANCE，待用户验收**；不启动 IP-07，不提交/推送。

</details>

#### IP-06 最终联合复核与发布授权（2026-10-31）

<details>
<summary>展开记录</summary>

用户在解码限制修复后要求最终 review / 测试。同一实现者自审，无新增阻塞发现；
检查前后 158 个已跟踪及非忽略新增源码/文档文件 SHA-256 完全一致，没有测试过程造成的源文件改动。

- Java 17.0.12，`backend/`：`mvn --offline --batch-mode --no-transfer-progress clean -Pintegration-tests verify`，
  **348 个快速测试 + 22 个真实 MySQL 测试**通过，0 failures/errors/skips，JAR 打包成功。
  使用新建的 mysql:8.4 临时容器（实际 8.4.10）、随机凭据、tmpfs 和 loopback 动态端口；
  结束确认表数 0，临时容器已删除，没有操作业务库/用户数据卷。
- Node 22.21.1，`frontend/`：`npm ci --offline --cache /private/tmp/frameflow-npm-cache`、`npm run test:ci`（1 test）、
  `npm run lint`、`npm run typecheck`、`npm run build` / standalone postbuild 全通过；设置 NEXT_TELEMETRY_DISABLED=1。
  该测试仍是现有占位页检查，不代表浏览器业务闭环。
- 根目录 Compose config、diff 空白检查、JAR 内 XML/catalog/JPEG fixture 条目检查通过。
  同一 145866 字节问题 PNG 在独立 `-Xmx128m` JVM 中解码前拒绝、临时文件 0；
  另以畸形 PNG 块长度验证受控 INVALID_IMAGE 拒绝。

日志 `/private/tmp/frameflow-ip06-final-{backend,npm-ci,frontend-test,frontend-lint,frontend-typecheck,frontend-build}.log`，
审查摘要 `/private/tmp/frameflow-ip06-final-review.md`；报告为 Surefire/Failsafe 与前端 JUnit 标准目录。
未运行完整三服务业务容器或远程 CI；发布后的远程结果以 PR 对应提交检查为准。

用户随后明确授权 commit、push 并创建 PR。发布分支 `codex/ip-06-asset-upload` 基于已合并 IP-05 的 main `612189a`，
只包含 IP-06 实现/测试、脱敏配置和相关文档；不将发布授权视为正式业务验收，不合并 PR 或启动 IP-07。

</details>

### IP-07：Task 仓储、查询投影与条件保存

**依赖**：IP-02、IP-03。**范围**：本域 Repository/查询契约与 adapter.repository.task、dao.task/dao.po.task；根和有序绑定的原子创建/恢复。
任务根与输入绑定 SQL 分别使用 `mybatis/mapper/task/production_task_mapper.xml` 和
`mybatis/mapper/task/task_input_asset_mapper.xml`；XML 资源加载配置与 IP-04 保持一致，独立执行本任务时按同一约定补齐。

**验收 / V-07**：根/绑定任一失败全部回滚；顺序、重复素材位置、UTC、本人历史与查询映射正确；PO 不越界。
真库测试通过实际 Task DAO 加载并执行上述 XML，覆盖可空阶段/结果/失败字段的读写映射与条件更新。
每次迁移同时匹配 taskId/revision/旧状态/旧阶段，完整结论和 revision+1 原子写入；owner/input/createdAt 不在更新集合。
真实 MySQL 用两个独立连接/事务和 barrier 控制竞争：仅一个首次抢占成功，旧 revision/旧阶段/终态写影响 0 行，成功/失败竞争不会产生混合结论。
不使用睡眠猜测顺序；检查影响行数。技术并发包络与聚合行为分离，无无条件整行覆盖。

#### IP-07 实际执行记录（V-07）

<details>
<summary>展开记录</summary>

2026-10-31，用户明确授权实现 IP-07 并解释实现方式。本轮仅实现本域持久化和查询基础，
不启动 IP-08、不新增 HTTP 端点、执行器或生成器，不修改 V001、依赖、前端及 CI 配置。

实现：

- `domain.task.adapter.repository.IProductionTaskRepository` 定义创建、可信 Worker 加载、条件保存及本人查询。
  `TaskSnapshot` 保留可变工作聚合，同时冻结加载时的 revision/status/stage/updatedAt；
  并发标记不进入 ProductionTask 的业务状态。`TaskRecord` 是不可变根投影，不向上层返回 PO。
- `ProductionTaskRepository` 通过 `IProductionTaskDao` / `ITaskInputAssetDao` 与两份 task XML 完成映射。
  创建根和按位置写入的全部绑定复用调用方事务；写入口拒绝无事务或只读事务。
  事务的创建、提交及后续副作用仍归未来 Task Cases，本任务由真实测试调用方使用
  REQUIRES_NEW / READ_COMMITTED / 5 秒 TransactionTemplate 验证，仓储不自行提交。
- 输入不去重，按 position 升序恢复并检查连续性；通过 ProductionTask.restore 恢复领域结论，不重放行为。
  输入自创建后不可变，因此加载根后读取绑定不会与任务阶段变化产生不一致；当前无删除/修改输入接口。
- 一条 UPDATE 同时核对 taskId/revision/旧 status/旧 stage（空阶段用 IS NULL），只更新完整状态结论、
  updatedAt 与 revision+1；额外限制旧 status 只能 PENDING/RUNNING，阻止终态出边。
  0 行返回 false，1 行返回 true，其他行数报错；true 仍须调用方提交成功才可执行后续工作。
  每个短事务重新加载，调用方只应用一个领域行为，不重用已保存快照；无自动重试。
- 本人历史和单条查询均从 production_task 根记录获得完整投影；WHERE owner_id 限定范围，
  历史按 created_at DESC/task_id DESC 排序；查询不加载全部素材、不写 revision、不触发制作。
  Instant/LocalDateTime 在适配器中显式按 UTC 转换，保留 DATETIME(6) 微秒精度。

| V-07 条件 | 实际证据 | 结果 |
| --- | --- | --- |
| 原子创建与恢复 | TaskIT：重复根插入失败不改旧记录；第二个绑定真实 CHECK 3819 失败，根和先前绑定全部回滚；顺序/重复素材/UTC 微秒恢复 | PASS |
| 状态/可空字段 | TaskIT：PENDING、三个 RUNNING、SUCCEEDED、三个阶段 FAILED、无阶段 DISPATCH FAILED，快照与根投影一致 | PASS |
| 条件谓词/写集合 | TaskIT：错误 ID/revision/status/stage 均 0 行；修改候选 PO 的 owner/template/createdAt/revision 不进入更新集合；输入保持不变 | PASS |
| 抢占与终态竞争 | TaskIT：两个独立连接/事务，以 CyclicBarrier 在加载后同步，不用 sleep；首次抢占只有一个 true，成功/失败竞争只有一个结论，旧写及终态写均 0 行 | PASS |
| 事务边界 | TaskIT：无事务/只读事务在 SQL 前拒绝；条件更新成功后调用方异常仍完整回滚，不能将影响 1 行当成已提交 | PASS |
| 本人查询与无副作用 | TaskIT：A/B、空列表/不存在、同时间 ID 倒序、绑定参数注入字符串、重复查询不改变 revision | PASS |
| 模块依赖 | 现有 jdeps 规则涵盖新增 Task 领域契约/包络/投影；Domain 仅依赖 Java/本域，PO 只出现在 Infrastructure | PASS |

验证环境和入口：

- Java 17.0.12 / Maven 3.9.10：`mvn --offline --batch-mode --no-transfer-progress -Pintegration-tests clean verify`，
  **348 个快速测试 + 36 个真实 MySQL 测试（新增 TaskIT 14 个）**，0 failures/errors/skips，JAR 打包成功。
  初次定向测试有一条异常分类断言失败：驱动对 CHECK 3819 返回 UncategorizedSQLException；
  已改为核对 DataAccessException 的根 SQLException/errorCode=3819，并保留全部回滚断言，完整重跑通过。
- 新建 mysql:8.4 临时容器，实际 **8.4.10**，随机临时凭据、tmpfs、loopback 动态端口，未使用业务库/用户卷。
  测试后确认剩余表数 0，已删除本轮容器和临时凭据文件。
- Node 22.21.1：`npm ci --offline --cache /private/tmp/frameflow-npm-cache`、`npm run test:ci`（1 test）、
  `npm run lint`、`npm run typecheck`、`npm run build` / standalone postbuild 全通过；设置 NEXT_TELEMETRY_DISABLED=1。
- 根目录 `docker compose --env-file .env.example config --quiet`、diff 空白及改动范围检查通过。
  同一实现者自审；未提交/推送、未运行远程 CI 或完整三服务业务容器。

后端日志 `/private/tmp/frameflow-ip07-integration-verify.log`，前端日志
`/private/tmp/frameflow-ip07-final-{npm-ci,frontend-test,frontend-lint,frontend-typecheck,frontend-build}.log`；
Surefire/Failsafe 报告仍在标准 target 目录，新增 TaskIT 自动进入现有 integration-tests/CI 门禁。

状态：**READY FOR REVIEW，待用户审查/验收**，checklist 保留 `[ ]`。
本轮证明仓储/SQL/事务与并发能力，不代表后台三阶段、公开任务 API 或浏览器闭环已实现；它们分别归 IP-08～14。

</details>

#### IP-07 三技能审查与发布授权（2026-10-31）

<details>
<summary>展开记录</summary>

用户要求按 ddd-skill 2.2.4、mybatis-patterns、springboot-patterns 检查，通过后 commit / push / 创建 PR。
同一实现者完成复核，采用 AGENTS/ADR-006/009 的项目适配：Domain 保持无框架，事务由 Case 控制，
本任务不添加 JPA/缓存/异步框架或新 API；本人全量历史保留既定小规模演示契约，不擅自分页/截断。

- DDD：契约在本域 adapter.repository，实现只通过本域 DAO/PO 读写，技术快照不改变聚合状态规则，
  只读投影不返回 PO，未引入跨域仓储调用、框架依赖或不需要的领域服务。
- MyBatis：仅 @Mapper 注册，namespace/statement ID 与接口逐一对应；全部值用 #{} 绑定，
  nullable 参数显式 jdbcType、空旧阶段用 IS NULL。查询无嵌套 select/N+1，排序匹配已有组合索引。
  两个 Mapper 共享 Spring 事务；条件写不手动提交/重试，不修改不可变归属/输入。
- Spring：构造器注入、事务检查和异常传播符合调用方短事务协议。复核时补齐 TaskIT 中
  DataSourceUtils.getConnection/releaseConnection 配对；释放走 Spring 方法，不直接关闭事务连接。
- 本轮后端完整 clean integration verify 再次通过：348 个快速测试 + 36 个真实 MySQL 测试，
  0 failures/errors/skips。临时 MySQL 8.4.10 使用随机凭据/tmpfs/loopback，结束表数 0 并删除容器。
  日志 `/private/tmp/frameflow-ip07-final-backend.log`；前端及 Compose 结果沿用本轮 V-07 已完成的同版本验证。
- 静态映射检查、新增文件空白、diff/改动范围复核通过，无剩余阻塞发现。

远程 main 已包含 IP-06（合并提交 719c20d），本轮从该提交建立 `codex/ip-07-task-persistence`。
提交范围限定 IP-07 源码/测试及对应实施文档。发布授权不等于用户正式验收，不合并 PR 或启动 IP-08；
远程 CI 以发布后的提交检查为准。

</details>

### IP-08：后台执行 Case、Mock 与有界派发器

**依赖**：IP-05、IP-06、IP-07。**范围**：内部 IExecuteProductionTaskCase、MediaGenerator Mock、TaskDispatcher 及有界 executor/装配。
本任务以已创建测试任务进入执行，无公开受理 API；入口仅可信后台回调。

**验收 / V-08**：首次抢占提交后才检查素材，随后 GENERATE、FINALIZE；每次阶段进入先持久化，再在事务外 I/O/延迟。
素材检查、生成、整理是三个可单独观察/失败的边界；只在资源就绪后保存成功，候选不等于结果。
JUnit Case 测试用受控协作者/事件记录与 latch 检查调用顺序、每个失败点后无后续调用、终态重入无生成、各任务模拟设置互不影响。
结合真实 MySQL 验证重复派发仅一个获准执行，状态可在独立连接读取；0 行或 DB/commit 未知后停止，无重试生成。
默认 2 worker/64 队列，可用小容量注入确定性拒绝；不 caller-runs、不静默丢弃；退出/关页不取消执行。
模拟延迟/失败按受理捕获的不可变参数传入，等待在事务外；停机最多等 30 秒的限制如实记录，不增加恢复扫描。

#### IP-08 实际执行记录（V-08）

<details>
<summary>展开记录</summary>

2026-10-31，用户授权创建新分支并实现 IP-08；从干净的 `codex/ip-07-task-persistence`
（`6e883eb`）创建 `codex/ip-08-task-execution`。本轮不提交、推送或创建 PR。
复用 IP-05/06/07 的资源、素材 Case 和条件仓储，没有新增依赖、DDL、公开 HTTP API 或前端业务。

**实现与边界**：

- `IExecuteProductionTaskCase` / `ExecuteProductionTaskCaseImpl` 从持久 Task 取 owner/有序素材，
  首次仅允许 PENDING/revision=0 抢占；RUNNING、终态、缺失任务均不续跑。
- 每次重新加载并检查 revision/状态/阶段，通过聚合行为后条件保存；短事务为
  REQUIRES_NEW / READ_COMMITTED / 5 秒。事务成功返回后才执行相应阶段，入口拒绝外层活动事务。
- CHECK_ASSETS 调用已有真实素材检查；GENERATE 通过 `IMediaGeneratorPort` 返回不可变候选；
  FINALIZE 经 Asset 的 `prepareResult` 确认登记的打包 JPEG，之后才保存 TaskResult/SUCCEEDED。
  Mock 不根据上传内容真实生成图片，不能用任意路径或封面引用冒充结果。
- `ExecutionOptions` 不可变，包含指定失败阶段及 0～30000 ms 的逐阶段延迟；延迟后在选定阶段注入失败，
  不放入 Domain 聚合、数据库或全局当前任务。`IStageDelay` 用真实 Thread.sleep，测试可控等待。
- 阶段异常保存脱敏原因并停止；条件写 0 行、读取/保存/commit 异常立即停止，不再写另一失败、不重复生成。
  commit 已提交但确认丢失通过替身注入，不能将此测试称为真实数据库断网实验。
- `ITaskDispatcher` 明确返回接受/拒绝；`ExecutorTaskDispatcherPort` 固定 worker、有界队列、AbortPolicy，
  默认 2/64，由 TASK_WORKER_COUNT / TASK_QUEUE_CAPACITY 配置。Callback 经 `ProductionTaskWorker` 调 Case，
  适配器不引用 Case 实现。只有该适配器可依赖技术派发契约使用的 ExecutionOptions，其他 Case 模型仍禁止进入 Infrastructure。
- 正常关闭拒绝新任务，最多等 30 秒后中断剩余工作；中断阶段尽力保存失败并恢复线程中断标记。
  daemon worker 不阻止 JVM 最终退出，不能强制终止不响应中断的 I/O；排队任务可能仍 PENDING。

**验证（JDK 17.0.12 / Maven 3.9.10 / MySQL 8.4.10 / Node 22）**：

| 检查 | 命令 / 证据 | 结果 |
| --- | --- | --- |
| 定向 Case/派发/素材/边界 | `mvn --offline --batch-mode --no-transfer-progress -Dtest=ExecuteProductionTaskCaseTest,ExecutorTaskDispatcherPortTest,AssetCasesTest,IdentityBoundaryTest,TaskDomainBoundaryTest test` | 34 项通过；随后增加的 Task 边界测试另见下行 |
| 完整后端与真库 | `mvn --offline --batch-mode --no-transfer-progress clean -Pintegration-tests verify` | 374 项快速测试、42 项集成测试通过；日志 `/private/tmp/frameflow-ip08-final-backend.log` |
| 补充并发与分层 | `mvn --offline --batch-mode --no-transfer-progress -Dtest=TaskDomainBoundaryTest -Pintegration-tests -Dit.test=ExecutionIT verify` | 3 项边界、7 项执行集成通过；日志 `/private/tmp/frameflow-ip08-followup-backend.log` |
| 最终源码复核 | `mvn --offline --batch-mode --no-transfer-progress -Dtest=ExecutorTaskDispatcherPortTest,ExecuteProductionTaskCaseTest,TaskDomainBoundaryTest,IdentityBoundaryTest test` | 29 项通过；补充停机中断/未运行队列计数日志后复核；日志 `/private/tmp/frameflow-ip08-review-tests.log` |
| 前端回归 | `npm ci --offline --cache /private/tmp/frameflow-npm-cache`、`npm run test:ci`、`npm run lint`、`npm run typecheck`、`npm run build` | 全部通过；沿用 1 项占位测试，不当作业务 UI 验证 |
| Compose / 差异 | `docker compose --env-file .env.example config --quiet`、`git diff --check` | 通过；未运行完整三服务 |

Case 测试以提交事件、受控协作者和模拟事务确认每阶段先提交后 I/O、三个失败点中断、全部四次条件写失效、
commit 结果未知、失败保存失败、外层事务拒绝、终态重入、输入顺序/owner、异常脱敏和中断标记。
派发器以 latch 阻塞一个 worker 填满单槽队列，确认第三项拒绝、调用线程不执行任务、排队选项独立、关闭排空及中断。
真实 MySQL 用 barrier 让重复派发同时读 PENDING，确认仅一人调用检查/生成，独立物理连接看到每阶段已提交，
成功结果可解码；另让一项阻塞而另一项在 GENERATE 失败，确认两任务设置及状态独立。
配置装配后的后台入口无需 HTTP 登录上下文也可完成已保存任务；素材不存在真实落为 CHECK_ASSETS/ASSET_UNAVAILABLE。

使用两个独立、随机命名、tmpfs 数据目录的 MySQL 容器分别执行两轮验证；仅绑定 loopback 随机端口，
随机密码通过环境传入且未出现在日志。每轮结束检查测试库零表并删除本轮容器/临时卷；上传测试目录由套件清理。
初次沙箱运行 Mockito 动态附加受限，授权本机测试后通过；系统 Git 遇 Xcode 许可提示，使用已有独立 Git 完成分支操作。
未修改 Xcode 许可或系统设置。

**状态与剩余范围**：本地实现/验证完成，等待用户审查/验收；未推送，未运行本分支远程 CI。
首次提交后的派发、DISPATCH_REJECTED 持久化和默认模拟配置捕获归 IP-09；任务查询/结果 HTTP 入口归 IP-10。
停机 30 秒超时分支没有真实等待测试，已测正常排空及关闭被中断；硬退出、DB 不可用、内存排队丢失均无恢复保证。
创建→HTTP→查询→浏览器的业务闭环、Compose 三服务及人工验收仍归后续任务，不能由本轮执行测试推断通过。

</details>

#### IP-08 三技能审查与发布授权（2026-10-31）

<details>
<summary>展开记录</summary>

用户明确要求按 `ddd-skill`、`mybatis-patterns`、`springboot-patterns` 检查，无问题后 commit / push / 创建 PR。
三项技能结合 ADR-006/009 和现有详细设计复核，未发现阻塞问题，无需修改业务代码：

- DDD：Domain 仍仅依赖 Java/本域，状态转换由聚合维护；Task Case 仅通过 Asset 公开 Case 协作，
  Worker 只调执行契约；派发器不持有聚合/仓储/Case 实现，不引入空服务、工作流或假 Gateway。
  ExecutionOptions 是技术派发签名的不可变载荷，其唯一适配器依赖已由精确 jdeps 规则约束。
- MyBatis：复用 IP-07 两份 XML 和 Spring 管理的 mapper/session，没有新增 SQL；namespace、显式映射、
  `#{}` 绑定、NULL 阶段分支与本人查询谓词一致。每次条件写检查 revision/状态/阶段及影响行数，
  Case 独立短事务提交成功才允许后续 I/O；不手动提交/复用 SqlSession，不跨阶段重用并发快照。
- Spring Boot：构造器注入和显式 Bean 装配，无事务自调用或依赖异步代理；线程池有界、明确拒绝、
  有关闭回调和中断标记恢复。数据库结果未知停止且不重试，不记录原始异常/素材内容；
  Worker 使用持久 owner，不依赖登录请求的线程上下文。未引入题外缓存、JPA、MQ 或监控依赖。

最终源码在验证期间未变化；提交前仅补充本审查记录，其他原有改动均属于 IP-08。
JDK 17 的 `mvn --offline --batch-mode --no-transfer-progress clean -Pintegration-tests verify`
通过 **375 项快速测试 + 43 项真实 MySQL 测试**，0 failures/errors/skips，可执行 JAR 打包成功；
日志 `/private/tmp/frameflow-ip08-publish-backend.log`。独立 MySQL 8.4.10 容器使用随机凭据/tmpfs/loopback 端口，
结束后确认测试库零表并清理本轮容器。Node 22 的 npm ci/test:ci/lint/typecheck/build 再次全部通过，
Compose config 和 diff 检查通过。远程 CI 结果以实际推送后运行状态为准。

确认 PR #6（IP-07）已合入 main，远端基线为 `53f1cbf`，与本分支开发起点内容相同。
提交/推送范围仅为 IP-08 源码、测试、配置和配套文档；创建面向 main 的 PR。
发布授权不等于用户正式验收，不合并 PR，也不启动 IP-09。停机/进程中断及后续 HTTP/浏览器验证限制仍见 V-08。

</details>

### IP-09：制作受理 Case 与提交 API

**依赖**：IP-04、IP-08。**范围**：创建 Case、输入/模拟配置映射、POST tasks、受理错误及明确派发拒绝处理。

**验收 / V-09**：通过 Template/Asset 公开 Case 校验可信输入及归属；根和绑定独立短事务提交成功后才派发，202 含 ID/owner/createdAt/Location，不伪造 PENDING 快照。
以 latch 阻止后台完成来证明提交已返回；输入或事务明确失败不派发、不假称受理。顶层已有事务在副作用前拒绝，生成/等待不占长事务。
每项 simulation 独立冻结；非法/禁用控制、未知/重复 JSON 字段、64 KiB 边界按 API 返回错误，无任务失败卡片语义。
明确 executor 拒绝尽力保存 FAILED/空阶段/DISPATCH_REJECTED，已有记录仍返回 202；拒绝写竞争失败不覆盖新状态。
用事务/派发替身确定性注入 commit 未知和拒绝后写失败，确认不重复创建/派发、保留可追查 ID/真实窗口；不能声称每次断网已确认回滚。

#### IP-09 实际执行记录（V-09）

<details>
<summary>展开记录</summary>

日期：2026-10-31（Asia/Shanghai）。用户授权新开分支实施 Task 9、10，随后明确应从 main 创建。
已 fetch 并以最新 main `2c9b149`（IP-08 合并提交）为基点建立 `codex/ip-09-10-task-api`，保留当前实施修改；没有提交或推送。
IP-04/06/07/08 的实现可用，本次没有把它们的发布授权视为 IP-09/10 的验收或发布授权。

**实现与边界**：

- 新增 `ICreateProductionTaskCase` / `CreateProductionTaskCaseImpl`、`TaskAccepted` 和任务用例异常；
  仅通过 Template/Asset 公开 Case 获取模板规则和本人可读素材，保留有序重复位置。
- 根与绑定复用 IP-07 仓储，在 REQUIRES_NEW / READ_COMMITTED / 5 秒事务中创建；顶层已有事务在任何协作者调用前拒绝。
  事务成功返回后派发，不等待生成；202 只有 taskId、ownerId、createdAt 和 Location，不构造状态快照。
- 明确拒绝派发时另开短事务，只有 PENDING/revision=0 才应用 DISPATCH_REJECTED；条件保存失效后读现状并停止，
  读写/commit 故障不重复创建或重派，已提交任务仍返回 202。日志区分派发结果未知与拒绝结论未确认，只记 ID/故障类别。
- 受理事务异常保守映射 503 SUBMISSION_OUTCOME_UNKNOWN 和已分配 taskId；不宣称已提交或已回滚，不自动重试。
- 新增 `TaskController`、严格有界 JSON 读取与纯 Java API DTO。所有 POST 沿用会话/CSRF；不接受 owner/status/result 或查询参数。
  未知/重复字段、标量类型、枚举、显式 simulation=null、延迟范围和 65536 字节边界均校验，已知长度/chunked 同等处理。
- `MOCK_CONTROLS_ENABLED` 默认 false；启用时可按任务冻结 failAtStage 和 stageDelayMs，省略使用无失败/1000ms。
  `.env.example` 和 README 补本机 Java 配置、正常提交、三个指定失败及查询命令；不新增调试执行入口。

**V-09/V-10 共同验证记录**：JDK 17.0.12、Maven 默认本地缓存、Node 22、独立临时 MySQL 8.4.10；没有使用业务 DB 或读取 `.env`。

| 工作目录 / 实际命令 | 结果与证据 |
| --- | --- |
| backend：`mvn --offline --batch-mode --no-transfer-progress clean -Pintegration-tests verify`，配置专用 IT_DB_* | PASS；399 个快速测试、46 个真库测试，0 failures/errors/skips；JAR 打包成功。日志 `/private/tmp/frameflow-ip09-10-final-backend.log`，报告 `backend/target/{surefire,failsafe}-reports/` |
| backend：自审后 `mvn -o --batch-mode --no-transfer-progress -Dtest=CreateProductionTaskCaseTest,TaskApiTest,TaskDomainBoundaryTest test` | PASS；最终细分日志异常分支后重跑 21 项，0 failures/errors/skips；`/private/tmp/frameflow-ip09-10-self-review.log` |
| frontend：`npm ci --offline --cache /private/tmp/frameflow-npm-cache`、`npm run test:ci`、`npm run lint`、`npm run typecheck`、`npm run build` | PASS；占位页仍为 1 test，0 failures/errors/skips，静态检查和生产构建通过；`/private/tmp/frameflow-ip09-10-final-{npm-ci,frontend-test,frontend-lint,frontend-typecheck,frontend-build}.log` |
| 根：`docker compose --env-file .env.example config --quiet`、`git diff --check` | PASS；只证明配置解析/改动空白，不代表完整三服务业务容器就绪 |
| 专用容器生命周期 | 动态 loopback 端口、随机临时凭据、tmpfs 数据库；测试后表数为 0，已删除本次容器。未修改用户数据库/卷 |

新增行为测试：

| 测试源码 / 用例 | 断言 / AC 关联 | 结果 |
| --- | --- | --- |
| `cases/task/CreateProductionTaskCaseTest`（11 项） | AC-R-03-03、AC-R-04-01/03：可信素材规则、重复位置、微秒 UTC、提交后派发、独立设置、外层事务前置拒绝；明确写失败回滚不派发；commit 确认丢失分别模拟实际已提交/未提交，保留 ID 且不重试 | PASS |
| 同套件拒绝测试 | AC-R-04-01：拒绝正常落库、竞争保存 0 行、已有 RUNNING、拒绝后写失败/commit 未知、非明确派发异常均不覆盖/重派 | PASS |
| `trigger/http/task/TaskApiTest`（7 项，与 V-10 共用） | AC-R-03-03、AC-R-04-01、AC-R-06-01/03、AC-R-10-02：真实 Servlet/Cookie/CSRF、受理形状和 Location、模拟默认/禁用/非法、严格 JSON、已知与 chunked 64 KiB 含等号及 +1 字节、错误结构/requestId | PASS |
| `infrastructure/dao/task/TaskApiIT`（3 项，与 V-10 共用） | AC-R-04-01/02、AC-R-05-*、AC-R-06-*：真实登录和上传后受理；latch 阻塞后台时已返回 202；释放后无 HTTP/Repository 轮询也完成；三个指定失败独立持久化；真实有序重复绑定 | PASS |
| `domain/task/TaskDomainBoundaryTest` 与既有全局依赖检查 | 新增 HTTP Trigger 不依赖 Domain/Infrastructure/Case 实现，Task Cases 只使用公开 Asset/Template 能力，API 纯 Java，Domain 无框架 | PASS |

首次尝试历史 `/private/tmp/frameflow-m2` 缓存缺依赖，改用已有默认 Maven 缓存后可离线构建；
普通沙箱限制 Mockito attach/本机监听，随后在允许这些能力的本机执行环境重跑。
初轮完整检查发现错误 DTO 的 Jackson 注解违反 API 纯 Java 约束，已移除并使用只针对结果未知的纯 Java DTO，完整检查通过；
测试替身重设和 Accept 请求头问题也已修正，没有删弱断言或改检查规则换取通过。

**自审与状态**：本次为同一执行者自审，检查实际改动、身份/事务/派发/条件保存/异常语义及依赖边界，无剩余阻塞发现。
IP-09 **READY FOR ACCEPTANCE**，用户最终审查/验收未执行，保留 [ ]。
提交后派发前崩溃、未知结果、拒绝结论保存失败和本机内存队列限制保持既定保证；没有恢复扫描、创建幂等或重试。
远程 CI、Next/浏览器与完整 Compose 三服务未验证，分别归发布后检查及 IP-11～16，不据本地结果声称通过。

</details>

### IP-10：个人任务查询、历史与成功图片

**依赖**：IP-09。**范围**：Task 本域只读投影、IQueryProductionTaskCase、IReadTaskResultCase 及 HTTP 映射。
本人历史和单条查询复用或补充 IP-07 的 Task XML Mapper，查询投影在真库测试中验证，不在 DAO 注解中另写 SQL。

**验收 / V-10**：查询/历史读取同一持久事实，不派发、不推进、不修改 revision；无查询也可执行完。
A/B 各自历史和单任务/结果隔离，跨 owner/不存在统一 404，客户端 ownerId 不能授权；Worker 从 DB 使用原 owner。
所有 TaskView 字段/组合、revision、UTC 时间、失败阶段/原因和排序符合 API；FAILED 记录为 GET 200。
结果先检查归属及 SUCCEEDED，再经 Asset 媒体能力读取；未成功 409、资源故障 503，均不回退任务结论。
API Tests + MySQL/图片集成完成后，已有最小后端纵切：登录→上传→202→三步→个人历史→真实预览；页面关闭期间无查询仍能完成。

#### IP-10 实际执行记录（V-10）

<details>
<summary>展开记录</summary>

日期/分支/命令与全量结果同 V-09；新增 `IQueryProductionTaskCase` / `QueryProductionTaskCaseImpl`、
`IReadTaskResultCase` / `ReadTaskResultCaseImpl` 和不可变 TaskView，接入 API-10～12。
本人历史与单条复用 IP-07 的 XML owner 查询和同一根记录，未增加 SQL、第二套历史状态或执行副作用。
结果先查询归属/成功结论，再经 Asset 媒体能力解析稳定引用；URL 在 HTTP 边界派生，内部引用不返回。

| 验收 / 实际测试源码和观察 | 结果 / AC 关联 |
| --- | --- |
| `cases/task/QueryProductionTaskCaseTest`（6 项）：单查/历史相同事实、重复读取无额外仓储调用、授权失败零媒体调用，非成功结果拒绝，资源异常无状态写入 | PASS；AC-R-05-02/03/04、AC-R-09-01/03、AC-R-10-02 |
| `TaskApiTest.mapsAllTaskViewCombinationsAndFailedReadsAre200WithoutChangingFacts`：PENDING、三阶段 RUNNING、SUCCEEDED、STAGE/DISPATCH FAILED，全量字段/null/revision/UTC、空列表、成功 URL，FAILED=200 | PASS；AC-R-05-02/03、AC-R-06-02、AC-R-07-02、AC-R-09-01/03 |
| `TaskApiTest.submissionErrorsAndImagesUseStructuredSanitizedErrorsEvenWhenAcceptIsImage`：404/409/503 标准 JSON 即使 Accept=image，requestId/no-store、真实 JPEG 字节和 nosniff，无 attachment；成功任务资源异常不写失败 | PASS；AC-R-05-03、AC-R-09-01、AC-R-10-02 |
| `TaskApiIT.loginUploadAcceptThenCompleteWithNoPollingAndRecoverHistoryAndRealImage`：真实 MySQL 登录→HTTP 上传→202→三阶段→本人记录→解码 JPEG；latch 证明异步返回；A/B 单条/历史/结果和素材引用隔离；后台完成前不依赖轮询；新会话登录恢复同一记录 | PASS；AC-R-04-01/02、AC-R-05-*、AC-R-09-01/02/03、AC-R-10-02 |
| `TaskApiIT.eachSpecifiedFailureCompletesWithoutQueriesAndHistoryPreservesOrderingAndFailureFacts`：三个指定失败完整落库、GET 200、阶段/原因一致、结果 409、本人历史排序及单条同源；重复读取不改版本/终态 | PASS；AC-R-06-01/02/03、AC-R-07-02、AC-R-09-01/03 |
| 既有 `TaskIT`（14 项）和全部集成入口 | PASS；真实 XML 查询字段、UTC、owner、createdAt/taskId 降序及并发保护继续有效；全部 46 项真库测试无失败/跳过 |

**状态**：最小 Java 后端纵切已通过本地 HTTP/数据库/图片集成；IP-10 **READY FOR ACCEPTANCE**，待用户审查/验收，保留 [ ]。
自审未发现剩余阻塞缺陷；前端会话恢复、真实浏览器关页/刷新和容器业务验收仍归后续任务，不能用本次 Java 客户端测试代替。
没有提交、推送、创建 PR 或运行远程 CI。

</details>

#### IP-09/10 三技能审查与发布授权（2026-10-31）

<details>
<summary>展开记录</summary>

用户明确要求按 `ddd-skill`、`mybatis-patterns`、`springboot-patterns` 检查，通过后 commit / push 并创建面向 main 的 PR。
本次为同一执行者的自审；按 ADR-006/009 的项目适配检查，不复制技能示例中的 JPA、缓存、MQ、框架 Domain、固定行数拆分或默认分页。
发布授权不等于用户正式验收，IP-09/10 继续保留 [ ]，不合并 PR。

| 审查依据 | 实际核对 | 结论 |
| --- | --- | --- |
| ddd-skill 2.2.4 | Trigger → API DTO / Case，Cases 经 Asset/Template 公开能力协作；聚合负责状态迁移，Domain 纯 Java；没有跨域内部仓储/DAO 访问，没有新增无实际需要的模式或包 | 符合 ADR 适配；依赖边界测试验证 |
| mybatis-patterns | 复用 IP-07 XML，namespace/statement ID、明确列/resultMap、`#{}` 参数绑定、nullable jdbcType、owner 谓词、稳定排序、revision/status/stage 条件更新；事务由 Case 管理，同一 DataSource 的 Spring Mapper/session，不手动 commit/close | 未修改 schema/Mapper；没有历史查询逐条加载输入的 N+1；现行不分页由已选 API 明确约定 |
| springboot-patterns | 构造注入、薄 Controller、集中脱敏错误、会话/CSRF、严格 JSON/有界请求、短事务和提交后派发；外部 I/O/等待在事务外，拒绝/未知结果不重试，查询/图片故障不改任务结论 | 保持既定 HTTP 与异步保证；不引入新依赖或替代错误格式 |

**发现并修复的 P2 问题**：`CreateProductionTaskCaseImpl` 原来先解析全部素材再验证数量；
64 KiB 以内可携带 1600 个重复 ID，无效请求仍可能逐项触发数据库和文件读取。
现在先使用 Template 公开 Case 返回的只读数量要求拒绝不匹配输入，沿用 422 / COUNT_MISMATCH；
合法数量仍经 Asset 做归属/内容检查、经 Template 做完整提交校验。没有硬编码模板数量，没有新增校验框架或公共 API。

`CreateProductionTaskCaseTest.wrongImageCountIsRejectedBeforeResolvingOrReadingAnyAssets` 使用 0、3、1600 个素材：
修复前 3 项均因意外调用 Asset 解析而失败（`/private/tmp/frameflow-ip0910-review-count-red.log`）；
修复后断言 422 对应的用例异常、IMAGE_COUNT，且 Asset/任务仓储/派发器均零调用。
既有负向测试同时确认合法数量但完整规则拒绝时仍不创建或派发；该套件现为 14 项。

本次定向命令为 backend 下 `mvn -o --batch-mode --no-transfer-progress -Dtest=CreateProductionTaskCaseTest,TaskApiTest,TaskDomainBoundaryTest test`，
24 项通过，0 failures/errors/skips，见 `/private/tmp/frameflow-ip0910-review-count-green.log`。
完整复验：backend 下 `mvn --offline --batch-mode --no-transfer-progress clean -Pintegration-tests verify` 通过，
402 个快速测试 + 46 个真实 MySQL 8.4.10 测试，0 failures/errors/skips，JAR 打包成功；
日志 `/private/tmp/frameflow-ip0910-review-backend.log`，报告 `backend/target/{surefire,failsafe}-reports/`。
专用测试库结束后表数为 0，本次临时容器已删除；未操作业务库或用户卷。
根目录 Compose config 和 diff/new-file whitespace 检查通过；审查/发布范围为本次 32 个任务相关文件，基点为 main `2c9b149`。
上一轮前端文件未改，安装/测试/lint/typecheck/build 通过证据仍见 V-09。
三技能自审结论：上述 P2 已修复并复验，无剩余阻塞发现，可按本轮授权提交分支并创建 PR；远程 CI 在此记录时尚未验证，以关联 PR 的检查结果为准。
既定提交后派发窗口、无重启恢复/创建幂等和完整 Compose/浏览器未验证限制继续保留。

</details>

### IP-11：Next 同源代理、登录 UI 与身份上下文

**依赖**：IP-04、IP-06、IP-10。**范围**：Node Route Handler 白名单转发、共用传输/错误识别、AuthProvider、/login 与用户名/退出入口。
先读 frontend/AGENTS 及安装版本 Next 文档；不引入 Next 业务执行或 DB 访问。

**验收 / V-11**：运行时 JAVA_API_ORIGIN；14 项 API 方法/路径按契约转发；Cookie/CSRF/multipart boundary/逐条 Set-Cookie/图片/状态码保真，私有 no-store。
以 Route Handler 集成测试/受控上游检查非法目标/路径拒绝、上游不可用/超时映射、写请求不重放；转发各真实 Java 编码至少一次。
组件测试检查用户名密码登录/退出、认证写串行、401 与 503 区分、403 后核对会话而非自动重放；无注册/访客下拉或浏览器凭证持久化。
身份 epoch、跨标签页不含凭证的通知和 focus 会话复核具备行为测试；旧 token 写入拒绝的真实浏览器情形在 V-16 联验。
默认客户端 30 秒、代理响应头预算 60 秒有可控时间测试；Cookie 属性和多个 Set-Cookie 透传另作实际 HTTP 检查，不能只 mock fetch 后认定浏览器会话已通。

#### IP-11 实际执行记录（V-11）

<details>
<summary>展开记录</summary>

实施日期：2026-10-31（Asia/Shanghai）。用户要求按 ddd-skill 实现，承接上一轮建议从 IP-11 开始。
按 ADR-006/010 保持 Java 的业务/持久化职责，未修改后端源码、依赖、SQL 或公共 API；不生成脚手架、不引入新库。
**实现及本地验证完成，待用户审查/验收**，保留 [ ]；没有 commit/push、远程 CI、部署或对外交付。

**实现范围与文件：**

- `frontend/src/app/api/v1/[...path]/route.ts` 与 `src/lib/server/java-proxy.ts`：Node runtime、运行时 JAVA_API_ORIGIN、14 项 API 方法/路径白名单。
  拒绝非法 origin、未知路径/方法、编码路径及不规范 ID；查询保留给 Java 按契约校验。
  原始表单/JSON/multipart 字节保留，分别限登录 4 KiB、任务 64 KiB、multipart 9 MiB；退出不接受请求体。
  仅透传 Cookie、CSRF、Accept、Content-Type，不接受客户端目标地址或 Bearer/转发身份头；上游不重定向、不重放 POST。
  状态、JSON/图片流、Location、requestId、nosniff 和每条 Set-Cookie 保留；全部代理 no-store，不透传解压前的 length/encoding。
- `src/lib/api-client.ts`：同源 Cookie、no-store、30 秒含响应体/解析预算、取消与超时分离，识别结构错误/HTML/断网，所有方法仅尝试一次。
- `src/features/identity/{auth-api,auth-session,auth-provider}`：每个 Provider 独占的同步身份作用域，通过 useSyncExternalStore 订阅。
  session→csrf→session 核对后才提供写上下文；旧 session/CSRF 的 success/error/finally 受 epoch 保护，A→B→A 不复用旧作用域。
  退出立即失效账号/写上下文；认证写同步 gate 覆盖会话复核。未知结果/403 先核对会话，绝不更新 token 后重放旧 POST。
  401 清旧私有上下文，503 保留已知账号信息但暂停私有工作，可手动复核；storage 随机标记不含凭证，focus 也会重新核对。
- `src/features/identity/{login-form,auth-navigation}.tsx`、`src/app/{layout,page,login/page}.tsx` 与 `globals.css`：有标签的登录表单、密码提交后清空、用户名/退出、错误和恢复入口、焦点及窄屏布局。
  **分阶段导航选择：** 当前成功登录返回已有首页；个人记录页面与“我的记录”入口、登录后跳转随 IP-14 接入，避免此阶段链接到未实现路由。没有占位 history 页面或假记录。
- `.env.example`、根/前端 README、AGENTS 及架构/API/前端状态文档同步了运行方式和实际状态。

**自动化与实际断言：**

| 测试 / 入口 | 实际结果与覆盖 |
| --- | --- |
| `src/lib/server/java-proxy.test.ts` | 46 tests；直接调用 Route Handler 接真实 loopback HTTP 上游，覆盖 14 个方法/路径、UTF-8 form、Cookie/CSRF、含 Expires 逗号的多条 Set-Cookie、Secure/HttpOnly/Path/SameSite、8 MiB 文件 multipart、JSON/202/Location、204 清 Cookie、图片字节、4xx/5xx、非法目标/路径/方法/ID、413、重定向拒绝和 60 秒响应头超时/不重放 |
| `src/lib/api-client.test.ts` | 8 tests；同源/no-store、错误结构、非 JSON/截断/断网、30 秒及 body 预算、忽略 abort 的传输仍能超时/取消，写请求一次 |
| `src/features/identity/auth-session.test.ts` | 13 tests；会话恢复、token 核对、503 恢复、退出即时失效/认证串行、未知结果核对、A→B→A 与旧 session/CSRF 成功/错误/finally、401/403/旧 epoch、存储失败、停止/重启 |
| `src/features/identity/auth-ui.test.tsx` | 5 tests；真实客户端编码配受控 fetch、表单标签/密码清理/当前账号/退出、统一凭证错误、storage/focus、503 恢复、Strict Mode 不发 POST、卸载释放订阅 |
| `src/app/page.test.tsx` | 1 test；首页账号入口可用。合计 73 tests，0 failures/errors/skips，JUnit `frontend/test-results/junit.xml` |

命令在 `frontend/` 使用 Node 22.21.1：`npm ci --offline --no-audit --no-fund`、`npm run test:ci`、
`npm run lint`、`npm run typecheck`、`npm run build`，均通过；构建含静态 `/`、`/login` 和动态 `/api/v1/[...path]`，postbuild 成功。
日志分别为 `/private/tmp/frameflow-ip11-{frontend-tests,lint,typecheck,build}.log`。
后端 `JAVA_HOME` 指向 JDK 17 执行 `mvn --offline --batch-mode --no-transfer-progress verify` 通过：402 tests，0 failures/errors/skips，JAR 打包成功；
日志 `/private/tmp/frameflow-ip11-backend-verify.log`，报告 `backend/target/surefire-reports/`。
根目录 `docker compose --env-file .env.example config --quiet` 与 tracked/new-file diff 空白检查通过。
普通沙箱禁止 loopback 监听，首次 HTTP/Maven 测试环境性失败；随后允许本机监听重跑，未 skip/关闭测试或修改应用绕过限制。

**真实 Java + 生产 Next 联调：**

临时脚本 `/private/tmp/frameflow-ip11-live.py` 创建专用无用户卷 MySQL 8.4 容器，loopback 随机端口；
执行 V001 并以临时随机密码显式初始化 demo-a/demo-b，启动本次 JAR 与 Next standalone，运行时注入 JAVA_API_ORIGIN。
同时用直连 Java 与经过 Next 的独立 CookieJar，验证 form-urlencoded 登录、会话、更新后的 CSRF、原始 multipart 上传、本人输入图片、
JSON 202/Location、历史/单任务与成功 JPEG；公开分类/列表/筛选/详情/封面内容一致。
实际检查 Java Cookie 的 Path=/api、HttpOnly、SameSite=Lax 及退出清理；B 的历史为空且 A 的任务/输入/结果均为 404，A 旧 token 在 B Cookie 下为 403；代理未知路径/方法/超限分别为 404/405/413。
生产 `/login` HTML 含用户名/密码表单。全部通过，报告 `/private/tmp/frameflow-ip11-live-report.log`，服务日志 `frameflow-ip11-live-{java,next}.log`。
脚本 finally 停止两个进程并删除专用容器/临时素材目录，未访问业务库或用户卷。

**未验证范围：** 远程 CI 未运行；这是 HTTP 客户端与组件行为证据，不是实际浏览器多标签页 Cookie/旧写请求、小屏或键盘人工验收。
上述浏览器联合检查保留在 IP-16；完整 Compose 三服务及持久卷重建检查保留在 IP-15；模板/上传/任务跟踪/历史页面和刷新恢复仍归 IP-12～14。
本次没有运行新的 MyBatis `*IT`；真实 MySQL 在此用于现有 Java API 联调，不能代替其专门的仓储并发测试或完整容器门禁。

**PR #9 聚焦复核修复（2026-10-31）：** 根据 R-08/10 和用户确认的最小方案，普通 focus 仅查询账号；
同账号不递增身份 epoch、不替换原 CSRF、不清空素材或重启轮询。账号变化/已退出才复用原有失效及完整核对流程；
聚焦查询失败只提示并保留状态，下次聚焦重试；认证通知及 401/403 的立即失效规则不变。
生产代码仅调整 `auth-session.ts` / `auth-provider.tsx`，未修改上传、任务控制器、后端或公共 API。
先用上传位与轮询测试复现两项失败，再修复；新增 8 项测试覆盖素材/预览/在途上传保留、轮询周期保留、
查询失败恢复、连续聚焦的旧响应/收尾隔离及身份 A→B→A 的旧聚焦响应隔离，现有身份切换测试同步等待实际核对结果。
本地 Node 22 下 `npm ci --offline --cache /private/tmp/frameflow-npm-cache`、198 项前端测试、lint、typecheck、build/postbuild 均通过；
JDK 17 下 `mvn --offline --batch-mode --no-transfer-progress verify` 通过（402 tests，零失败/错误/跳过），
Compose 配置解析与 diff 空白检查通过。日志 `/private/tmp/frameflow-focus-{before,tests,lint,typecheck,build,backend}.log`。
本轮没有新增 MySQL 集成或真实浏览器多标签页/文件选择器人工验收，不能把组件测试当作该部分证据。

</details>

### IP-12：模板列表、详情与上传交互

**依赖**：IP-05、IP-06、IP-11。**范围**：/templates、详情制作输入区域，按真实规则的上传位/预览/替换/移除，加载/空态/不存在/请求错误。
本任务不实现任务跟踪 controller；为 IP-14 提供已准备好的有序素材输入。

**验收 / V-12**：数据取自 Java；前端数量/类型/大小校验与模板一致；全部就绪才允许制作，上传中/失败有独立反馈。
组件/Hook Tests 用受控 Promise 测替换/移除/模板切换后的旧上传成功、错误及 finally 均不能复活旧素材或覆盖新请求状态。
选择版本/请求序号/上下文共同保护，释放本地 blob；服务端引用与浏览器预览分开，移除不删除已保存素材。
目录、详情和上传位有语义标签、可见焦点、桌面/小屏基本布局；业务反馈不只靠颜色。

#### IP-12 实际执行记录（V-12）

<details>
<summary>展开记录</summary>

实施日期：2026-10-31（Asia/Shanghai）。用户在 IP-11 完成报告后要求继续，按顺序实施 IP-12，沿用此前指定的 ddd-skill 及 ADR-006/010 适配。
**实现和本地自动化/API 验证完成，实际浏览器人工验收受环境限制，待用户审查/验收**；保留 [ ]。
保留 IP-11 已有未提交工作，未修改后端业务、API、SQL、依赖或 Compose；未 commit/push、部署或运行远程 CI。

**实现与关联：**

- `frontend/src/app/templates/{page,[templateId]/page}.tsx`、`features/template/{template-api,template-catalog,template-detail,template-cover}`：分类导航、封面卡片网格、动态详情/素材要求；数据来自 Java，区分加载、空分类、不存在和服务异常，支持请求手动恢复。来源为 AC-R-02-01～03、AC-R-03-01/05、AC-R-11-01～03。
  参考 requirements.md 已观察的网格/素材操作布局，不增加积分、搜索、会员、语言等题外功能；实际浏览器外观未冒充验收。
- `src/lib/use-resource.ts`：目录、分类及详情共用的组件作用域只读请求；key/重试版本、cleanup active 标记与 AbortController 分别防止旧成功/错误污染新页面。
- `features/asset/{asset-api,use-upload-slots,asset-inputs}`：按真实规则创建精确数量的单图上传位，客户端检查 MIME/非空/大小（包含上限），真实 FormData 保留浏览器 boundary，服务器继续校验图片实际内容。
  本地 File/blob 与服务器 assetId 分开，支持预览、替换、移除及显式重新上传；503 不改账号、不自动重试，401/403 经 IP-11 重新核对会话，旧选择不能转入新账号。
- 上传以 templateId + identityEpoch 挂载，每位置维护 selectionRevision/requestSeq/AbortController/本地 URL/服务端引用；替换/移除先同步失效旧绑定再取消/释放 URL。
  success/catch/finally 检查挂载、身份及请求上下文；身份失效订阅在 React cleanup 前取消私有工作。移除/替换不删除服务器素材，响应丢失或旧上传完成仍可留下未绑定素材。
  `getPreparedInputs()` 仅在全部位置就绪且当前身份有效时返回有序 assetIds，替换/移除后立即为 null；不把 blob 地址作为素材引用。
- `globals.css`、首页和账号导航接入模板入口、响应式网格与详情分栏、上传/错误文本和焦点样式；本地预览失败单独提示，不撤销服务器上传成功。
  **当前 UI 仅展示素材就绪反馈；制作按钮/提交锁、受理和任务卡片仍属于 IP-14，任务轮询仍属于 IP-13。**

**永久自动化证据：**

| 文件 | 结果与行为 |
| --- | --- |
| `features/template/template-api.test.ts` | 7 tests：分类/筛选/规则来自 API，拒绝错误模板 ID、外部封面地址、无效规则、重复/错分类结果与不安全路径 |
| `features/template/template-ui.test.tsx` | 9 tests：分类/卡片/详情链接、加载/空/错误/404、分类与列表独立恢复、旧列表成功/错误和旧详情隔离、封面错误、匿名浏览与登录入口 |
| `features/asset/asset-api.test.ts` | 7 tests：FormData 只有 templateId/file，不手写 Content-Type；拒绝错误 UUID、字节数、类型、时间和外部内容 URL |
| `features/asset/upload-inputs.test.tsx` | 24 tests：精确位置及全部就绪、有序 ID；替换/移除、模板 A→B→A 与身份 A→B→A 后的旧成功/错误/finally；取消/URL 回收、类型/空文件/超限/5 与 8 MiB 含等号、多选拒绝、显式重试、owner 错配、当前 401/403 无重放、文件选择取消、Strict Mode、预览错误与同步绑定失效 |

共新增 47 tests；连同原有测试，`npm run test:ci` 为 120 tests，0 failures/errors/skips，JUnit `frontend/test-results/junit.xml`。
在 frontend 使用 Node 22.21.1 执行 `npm ci --offline --no-audit --no-fund`、`npm run lint`、`npm run typecheck`、`npm run build`，通过；
生产构建含静态 `/templates`、动态 `/templates/[templateId]`，postbuild 成功。
日志 `/private/tmp/frameflow-ip12-{npm-ci,frontend-tests,lint,typecheck,build}.log`。
后端使用 JDK 17 执行 `mvn --offline --batch-mode --no-transfer-progress verify`，402 tests、0 failures/errors/skips，JAR 成功；
日志 `/private/tmp/frameflow-ip12-backend-verify.log`。根 Compose config 及本轮差异/新增文件空白检查通过。

**真实客户端与生产 Next/Java/MySQL：**

`/private/tmp/frameflow-ip12-live.py` 使用专用无用户卷 MySQL，随机 loopback 端口，等待最终 TCP 监听及目标库 SELECT 就绪后初始化 V001 和随机演示密码；启动本次 JAR/Next standalone。
在该环境运行临时 `ip12-live.test.ts`，直接调用本次 templateApi/assetApi 和原 authApi，CookieJar 由测试传输层维护：
验证生产列表/两份详情路由、Java 分类/筛选/数量/类型/大小规则、封面原始字节和 404；真实原生 File/FormData 上传返回合法 owner/assetId/大小/时间/地址，本人内容与原始文件逐字节一致。
同时验证未知内容为 415、截断 JPEG 为 422，登录/退出及会话恢复正常；2 项 live tests 通过。
该运行也复核了既有直连/代理 form、multipart、JSON 202/Location、任务/结果 HTTP 闭环与 A/B 隔离。
报告 `/private/tmp/frameflow-ip12-live-report.log`、`frameflow-ip12-live-client-tests.log`，服务日志 `frameflow-ip12-live-{java,next}.log`。
临时测试未加入永久测试入口；脚本 finally 删除该测试文件并停止本次进程、删除专用容器与素材目录，未操作业务库或用户卷。

**人工验收限制与操作：** 本机 CUA 返回 Mac 锁定且 Work 设置禁用本地浏览器，未能执行真实浏览器文件选择器、桌面/小屏视觉及键盘验收。
环境允许后，打开 `/templates` 切换分类、进入 1/2 图片模板；登录后检查实际选择/替换/移除、上传中/失败/就绪反馈、小屏及 Tab 焦点；离页/切换身份后旧上传不得恢复到新选择。
此限制保留给用户审查和 IP-16 联验；HTTP/组件断言不代替浏览器渲染、共享 Cookie 多标签页或最终布局验收。远程 CI 与完整三服务容器仍未验证。

</details>

#### PR #9 真实 Chrome 补充验收（V-11/12，2026-10-31）

<details>
<summary>展开记录</summary>

用户要求补做此前未执行的多标签页、小屏及原生文件选择器验收。验收源码为 main
`5498715f458d94921a98906b374a0b4239ac8696`；通过 CUA 操作本机 Chrome 原生界面，
不是 jsdom、HTTP 客户端或注入脚本。Mac 解锁后原生操作可用，浏览器连接器仍报告禁用，因此使用原生 Chrome。
复用本次生产 Next standalone 和现有 Java JAR，使用临时 MySQL 8.4 容器、空库执行 V001、
初始化独立演示账号；所有服务仅监听 loopback，未接触既有数据库或用户卷。测试密码未保存到仓库或浏览器密码管理器。

| 检查 | 实际结果 |
| --- | --- |
| 桌面模板浏览 | PASS；两张封面显示正常，旅行分类只保留旅行明信片，详情显示两张图片、JPEG/PNG 和 8 MiB 上限；匿名用户显示登录上传入口 |
| 登录与退出 | PASS；错误密码统一提示且清空密码；正确凭证登录显示对应账号；退出移除账号入口；单标签页退出后首次重新登录成功 |
| 原生上传、预览、替换、移除 | PASS；文件选择器选择项目示例 JPEG，实际上传成功并显示预览；两张上传后为 2/2，移除一张为 1/2 且另一张保留，替换后文件名/预览更新 |
| 同账号切回标签页 | PASS；第二标签页继承 A 会话，切回原详情页两张预览与 2/2 就绪状态均保留 |
| 多标签页退出/身份隔离 | PASS；第二页退出 A 后，原页变为匿名并清除上传区/预览；第二页登录 B 后原页同步为 B，上传位为 0/2；B 可独立上传 |
| 小屏布局与键盘 | PASS；375×812 下目录、登录、详情和上传区可纵向操作，未观察到内容横向溢出；Tab 到达移除按钮且焦点轮廓可见，空格移除后为 0/2；Tab 到达登录提交按钮并可用空格提交 |
| 768 / 1440 像素宽布局 | PASS；实际设备工具宽度分别设置为 768 / 1440，目录分类、卡片及导航布局正常；不是全部页面在所有尺寸的穷举检查 |
| 非图片内容伪装为 JPEG | PASS；选择临时 invalid.jpg 后服务端返回 415，页面分别提示本地预览不可用与图片类型不支持，提供重新上传/移除，保持 0/2 |
| 双标签页退出后首次重新登录 | **FAIL，P2**；正确密码首次提交被 CSRF 拒绝，两次复现，见下文；显式再次输入密码并提交均恢复成功 |

**P2：双标签页注销后的匿名会话/CSRF 竞态。** 两页共享同源 Cookie，均已加载应用：
在一页退出，等待匿名状态，再进入登录页输入正确凭证并提交；首次请求返回 403，
显示“会话保护已变化，请核对账号和表单后重新提交”，密码清空。B→退出→A 和 A→退出→B
各复现一次；关闭第二页后，同样退出/登录流程首次成功。403 后再次手动输入并提交均成功，未自动重放 POST，未观察到跨账号素材泄露。

源码关联：`frontend/src/features/identity/auth-session.ts` 的 `check()`（95～105 行）只比较
CSRF 读取前后的 owner；匿名态两次均为 null，无法识别共享 Cookie 的匿名 Session 已被另一页替换；
`write()`（159～164 行）发出跨页通知并在本页重新核对，两页可能并发创建匿名会话。
这是补测时结合可复现现象与 `HttpSessionCsrfTokenRepository` 的**初步原因推断**；补测未采集原始 Cookie/token 或网络时序。随后受控根因实验已验证该机制，见下节。
建议在后续修复中协调匿名会话初始化或在显式登录前获取与当前会话一致的 CSRF，保留身份隔离与不自动重放写请求的约束，并补真实共享 Cookie 的回归检查。

**补测时结论：NEEDS CHANGES。** 补测解决了此前真实浏览器不可用的证据缺口，同时发现上述可恢复的登录交互缺陷；修复及复测见后续“双标签页 403 修复”记录。
本次仅验证和记录，不修改业务代码、不勾选 IP-11/12/13 整体完成、不提交或推送。
截图及 AX 观察见本次工具记录，Java/Next 日志位于临时 `frameflow-browser-qa-*` 目录；临时服务、容器及专用浏览器窗口已清理。
本次未测慢速上传中跨账号的旧响应、会话过期、离线恢复、屏幕阅读器、axe 或 Core Web Vitals；
没有已提交视觉基线，像素级视觉回归为 NOT VERIFIED。上述通过仅代表已列出的交互/布局检查，不能扩展为完整无障碍或性能验收。
IP-13 仍只有内部 controller/hooks，制作提交、任务卡片、历史/结果页面及其浏览器轮询、刷新恢复需等 IP-14 接入；
完整三服务容器、持久卷重建和整体 MVP 验收仍待后续任务。

</details>

#### 双标签页 403 根因调查（2026-10-31）

<details>
<summary>展开记录</summary>

用户要求研究该问题，本轮只调查和验证修复方向。保留上述验收记录及工作区改动，未修改正式业务源码、API、数据库结构或依赖。
重新创建隔离 MySQL/Java/生产 Next 环境，运行临时 Vitest 研究入口，使用真实 HTTP 响应、共享 Cookie jar 和两个实际 `AuthSession` 实例。
测试只模拟跨页 storage 通知与请求/响应交错；响应中的 Cookie 和 CSRF 来自真实 Spring Security，未 mock 成成功/失败。
这不是第二轮真实 Chrome 操作，也不是此前浏览器现场的网络录制。

**已验证的失败链路：**

1. logout 返回 204 并清除共享 FF_SESSION；通知使两页分别执行 `check()`。
2. 两个 session 查询均返回 401，两页随后在没有 Cookie 时发出 csrf 请求。
3. Java 为两次请求分别创建匿名 Session，返回不同 Set-Cookie；用 S3/S4 代称，未记录原始值。
4. 两份响应应用后，共享 Cookie 最终为 S4；拿到 S3 对应 token 的实例仍通过 owner=null 的检查，发布 anonymous 状态。
5. 该实例显式登录时发送 S4 Cookie 和 S3 对应 token，服务端返回 403 CSRF_INVALID；本次 POST 未重试。
6. 错误后的核对从 S4 获取 token，用户再次显式提交即可成功。因此错误凭证提示、数据库可用性或 Next 折叠 Cookie 都不是这条失败链路的原因。

**对照证据：** 临时入口共 6 项测试，全部按预期通过。其中当前实现返回 403 是缺陷复现断言，不代表缺陷已修复。
直连 Java 与经过 Next 各验证一次“不同匿名 Session 的 token + 当前 Cookie = 403，重新获取对应 token = 200”；
各验证一次“同一已初始化匿名 Session 的两次并发 token 读取均有效”（错误密码到达认证返回 401，正确密码返回 200）。
另两项分别运行当前 AuthSession 与临时实验副本，复现上述失败及验证最小方案。
首次双实例实验错误地固定提交某一页，实际该页恰好持有最后生效的 Cookie 对应 token，因而成功；
研究入口随后按真实 token/Session 归属选择受影响实例，明确验证竞态决定哪一页受影响，而不是固定某个标签页必失败。

**调查时建议的最小修复方向，当时尚未应用：**

- `check()` 确认匿名时直接发布 anonymous，不自动调用 csrf；没有登录动作的其他标签页也就不会争建匿名 Session。
- 用户明确点击登录后，再读取新 CSRF、核对仍为匿名及当前操作 epoch，随后仅发送一次登录 POST。
- 已认证后的 CSRF、上传/退出原上下文、身份变更清理和 403 不自动重放的约束保持；不要把该逻辑扩成所有写请求自动换 token 重试。

仅在临时目录修改的实验副本显示：logout 后两页只 GET session，无后台 csrf；点击登录后才创建一份匿名 Session，
第一次登录 POST 返回 200，后续核对两页均 authenticated。正式实施仍需补永久回归测试并重跑真实 Chrome；
两页同时主动登录、准备登录期间另一页切换账号、旧 epoch/超时/卸载等边界尚未用该实验副本全面验证，不能将其视为可直接发布的修复。
当前无需新增 API、跨标签页锁库、Redis 或改动服务端 CSRF 仓储。

已对照项目安装的 Spring Security 7.0.8 字节码：HttpSessionCsrfTokenRepository 在保存 token 时调用 getSession()，
缺失 token 的延迟加载会生成并保存 token；XOR 掩码导致同 Session 的响应 token 字符串可不同，因此实验以真实校验结果判断有效性。
官方依据：[7.0 CSRF 会话存储与登录/退出刷新](https://docs.spring.io/spring-security/reference/7.0/servlet/exploits/csrf.html)、
[提交表单时获取 token 的建议](https://docs.spring.io/spring-security/reference/features/exploits/csrf.html)。
临时入口/实验副本保存在 `/private/tmp/frameflow-csrf-research/`，脱敏时序日志为 `/private/tmp/frameflow-csrf-research-tests.log`；
仓库内临时测试入口已移除，独立服务和容器已停止，未提交或推送。

</details>

#### 双标签页 403 修复（2026-10-31）

<details>
<summary>展开记录</summary>

用户明确要求修改，按上节已验证的最小方向实施。保留此前浏览器验收和根因调查记录，
正式生产代码仅修改 `frontend/src/features/identity/auth-session.ts`；后端、公共 API 字段/状态码、SQL、依赖及代理均未修改。
`check()` 遇到匿名会话直接结束，不在页面启动、聚焦核对或跨页通知后预取 CSRF；
登录入口同步加 gate、失效旧作用域，再获取新 CSRF 并确认仍匿名；两个读取都校验 epoch，准备中的请求可在失效/卸载时取消。
确认后只发一次认证 POST，POST 已发出时仍保留原有结果核对、不盲目重放的协议；准备失败与写入结果未知分别提示。
已认证时的 CSRF/账号核对、上传/退出原上下文及同账号 focus 保留状态规则不变。
同步 `frontend/README.md`、frontend-state-design 和 API 文档中的客户端 token 获取时机说明，没有改变服务端接口契约。

**永久回归与检查：**

- 新增 `auth-session-cookie.test.ts` 两项共享 Cookie 回归，分别让退出发起页/另一页重新登录。
  测试传输模拟独立 Session/token、登录轮换、退出清 Cookie 和跨页 storage 通知，实际运行 AuthSession/authApi/apiRequest；
  先在修改前复现“退出后两个后台 csrf 请求”，红灯日志 `/private/tmp/frameflow-csrf-fix-before.log`；修复后无后台 csrf，首次登录只有一个 POST 且为 200。
- `auth-session.test.ts` 新增 10 项，覆盖匿名读取、准备期 gate、CSRF/会话核对期间身份失效、取消/卸载后的旧成功与错误、漏收账号通知、503/超时恢复、403 不重放。
  原序列化测试补充等待 POST 确实开始，再注入通知，继续验证已发出写请求的结果核对。
- Node 22.21.1：`npm ci --offline --cache /private/tmp/frameflow-npm-cache --no-audit --no-fund`、`npm run test:ci`（210 tests，0 failures/errors/skips）、`npm run lint`、`npm run typecheck`、`npm run build`/postbuild 均 PASS。
- JDK 17：`mvn --offline --batch-mode --no-transfer-progress verify` PASS，402 tests，0 failures/errors/skips；没有新增 MySQL 仓储集成检查。
- `docker compose --env-file .env.example config --quiet` 和 `git diff --check` PASS；本次实现自审未发现其他阻塞缺陷，不表述为独立审查。
  日志 `/private/tmp/frameflow-csrf-fix-{identity,tests,backend,build}.log`，报告为现有前后端 JUnit 路径。

**修复版真实 Chrome 回归：PASS。** 使用本次构建的生产 Next、Java JAR 和独立空库 MySQL 8.4，全部 loopback；CUA 原生操作两个共享 Cookie 标签页。
先同时打开匿名登录页和模板页，A 首次登录成功；原生文件选择器上传示例 JPEG 成功并显示预览/1/2，切换标签页后仍保留。
第二页退出 A 后素材清除、第一页同步匿名；第一页首次输入 B 凭证即成功，第二页同步 B 且为 0/2。
随后第一页退出 B，第二页首次输入 A 凭证即成功；再在第二页退出 A 并于同页首次输入 B 凭证，也成功。
四次正确密码登录均无须重新输入，三次账号切换未出现此前会话保护提示。测试密码未保存，结束后退出账号、关闭专用窗口、停止 Java/Next 并删除专用容器/上传内容。

**状态：READY FOR ACCEPTANCE（本缺陷修复）。** 未提交、推送或勾选 IP-11/12/13 整体完成；保留人工最终验收。
本次没有在真实浏览器同时提交两页登录、注入离线或准备超时；这些场景不属于已列出的浏览器通过证据，准备失败/失效及不重放由上述自动化覆盖。
任务页面、历史结果、完整 Compose 和整体 MVP 的既有未完成范围保持，不能用本次缺陷回归代替。

</details>

### IP-13：每任务状态合并与轮询 controller/hooks

**依赖**：IP-10、IP-11。**范围**：任务 reducer/作用域 hooks、history 与单查合并、每任务 timer/请求状态，使用已稳定共用传输契约。
本任务用小型测试宿主验证 hooks 行为，不承担 IP-14 的完整页面组合。

**验收 / V-13**：按 taskId 隔离；每任务 settle 后递归 2 秒查询，同一自动查询不重叠；终态只停自己，空集合/全部结束无自动请求。
用 fake timers、受控 Promise 验证乱序 revision、不相邻快照、同版本矛盾、终态保护，晚到历史不删除新受理占位。
queryError 保留 TaskView、仅暂停该查询、可手动恢复；真实 FAILED 与查询异常分字段/行为；401 失效身份，503 不退出。
离页/切换先失效 epoch，再释放 timer/abort；即使 abort 无效，旧 success/catch/finally/timeout 不污染数据/loading/错误或重启 timer，包括 A→B→A。
React Strict Mode setup/cleanup 不重复发出业务 POST；只读历史/跟踪按新 controller 恢复。自动化覆盖 R-12 的两项前端核心行为。

#### IP-13 实际执行记录（V-13）

<details>
<summary>展开记录</summary>

实施日期：2026-10-31（Asia/Shanghai）。用户明确要求执行任务 13；沿用已确认的 API/前端状态设计、DDD 项目适配及 IP-11 身份作用域。
**内部能力实现与本地验证完成，待用户审查/验收**，保留 [ ]；没有提前实现 IP-14 页面或制作 POST，没有 commit/push、部署或远程 CI。
保留实施前全部未提交工作；本轮只新增任务客户端/状态/作用域 hook 及测试并同步文档，没有改变后端、数据库、依赖、代理或上传/身份代码。

**实际文件与协议：**

- `frontend/src/features/task/task-contract.ts`：唯一客户端 TaskView/TaskAccepted 类型及解析，验证 UUID、owner、模板标识、安全整数 revision、必有字段、UTC 时间和五类状态组合；失败阶段/种类/代码匹配，成功结果 URL 必须指向同一任务。
  时间规范为六位小数以比较等价 UTC 表示，同时保留 MySQL 微秒；不把日期作为乱序优先级，不为缺字段补业务默认值。
- `task-api.ts`：仅同源 GET 本人历史/单查，复用 IP-11 的 Cookie、no-store、30 秒预算与错误识别；先完整解析历史并拒绝重复 ID/异主数据，不在 URL/请求体中传 owner 授权参数。
- `task-state.ts`：纯 reducer 按 taskId 合并快照/真实受理占位，历史不整体覆盖任务集合；小版本丢弃，同版本冲突、不可达阶段、不可变事实变化及终态更改均保留已有快照，设置独立查询异常/暂停。
  只校验服务端快照一致性，不在客户端执行或推进阶段；受理占位没有虚构 PENDING。
- `task-controller.ts`：每页面/身份实例维护 generation、每任务请求序号/AbortController/递归 timer。初始非终态在 2 秒后查，后续等待当前请求结束再计 2 秒；自动查询不重叠，终态只停自己，空集合/全部终态无后台历史轮询。
  503/404/断网/超时保留最后快照并只暂停相关查询；显式单查或历史恢复，真实执行 FAILED 仍是成功读取的业务数据。401/403 经 IP-11 核对身份，503 不退出。
  较新有效数据可按 revision 合并；旧请求的 loading/error/finally/timeout 受请求序号约束。新历史推进快照后旧错误不能回写；页面/身份清理先失效 generation 再取消请求、timer 与订阅，旧 A→B→A 回调不能复活资源。
- `use-task-tracking.ts`：Client hook 按 AuthSession 的 owner/identityEpoch 建立页面 controller，通过 useSyncExternalStore 订阅纯 reducer 的同步结果；SSR 使用空快照，根 Provider 不挂全局轮询。
  提供 `trackAccepted`、`refreshTask`、`refreshHistory` 与分离的 tasksById/history 请求状态，供 IP-14 页面组合；本轮只用小型 React 测试宿主验证行为。

**自动化与复现：**

| 文件 | 实际证据 |
| --- | --- |
| `features/task/task-api.test.ts` | 28 tests：PENDING/三阶段 RUNNING/SUCCEEDED/阶段及派发 FAILED；无 owner 查询参数；UUID/归属/版本/时间/必有字段/状态组合/外部地址/失败错配拒绝；历史重复/异主整体拒绝；UTC 等价形式和微秒保留 |
| `features/task/task-controller.test.ts` | 32 tests：空/全部终态不空转、两任务独立与 settle 后调度、503 与执行失败区分/恢复、初始与后续历史错误、晚到历史保留占位、新旧 revision/非相邻快照、同版矛盾/终态/阶段/不可变事实保护、旧错误/finally/超时与新请求 loading 隔离、A→B→A/stop-restart、401 清理、404 暂停和另一任务继续 |
| `features/task/use-task-tracking.test.tsx` | 10 tests：真实 hook 宿主观察多任务/两项受理占位、查询异常与执行失败不同、会话后再读历史、首错非空历史、离页/返回与身份切换后的旧 success/error/timeout 隔离、实际客户端 30 秒超时、Strict Mode 只读和卸载释放 |

共新增 70 tests，永久 `npm run test:ci` 总计 190 tests，0 failures/errors/skips，JUnit 为 `frontend/test-results/junit.xml`，已进入现有 CI 的同一测试入口；不代表远程 runner 已运行。
实施中定向用例先复现“历史在单查失败前发出、同版数据晚到却解除暂停”的竞态，红灯日志 `/private/tmp/frameflow-ip13-race-red.log`；
修正为失败结算同步 release/递增请求序号，再发布 pause/error。该用例和完整回归随后通过；只有新的显式读取才能用同版数据恢复查询。

在 frontend 使用 Node 22.21.1 执行 `npm ci --offline --no-audit --no-fund`、`npm run test:ci`、`npm run lint`、`npm run typecheck`、`npm run build`，通过。
日志 `/private/tmp/frameflow-ip13-{npm-ci,frontend-tests,lint,typecheck,build}.log`；生产路由和 IP-12 保持一致，没有增加业务页面，postbuild 成功。
backend 使用 JDK 17 执行 `mvn --offline --batch-mode --no-transfer-progress verify`，402 tests、0 failures/errors/skips、JAR 成功，日志 `/private/tmp/frameflow-ip13-backend-verify.log`。
根目录 Compose config 与相对本轮开始快照的修改范围、新增/已有文件空白和文档链接检查通过；没有更改失败门禁、skip 测试或增加依赖。

**真实 Java/MySQL + 生产 Next：**

隔离脚本 `/private/tmp/frameflow-ip13-live.py` 使用专用无用户卷 MySQL、随机 loopback 端口及临时随机账号密码，等待最终 TCP/目标库 SQL 就绪，再初始化 V001、启动本次 JAR/Next standalone。
临时 `ip13-live.test.ts` 通过真实 Cookie 传输层登录并上传实际 JPEG，创建两项 stageDelayMs=1200 的任务（一项正常，一项 failAtStage=GENERATE），然后运行本次 AuthSession/TaskController/真实 taskApi。
历史与单查实际读到 A 的 SUCCEEDED/FINALIZE/revision=4 和 B 的 FAILED/GENERATE/revision=3；两项独立且无 queryError，均存在多次单查。
达到终态后再等 2200ms，无新增查询，历史只读取一次。1 项 live test 通过（约 6.7 秒），并复核既有代理编码、任务/结果与 A/B 隔离 HTTP 流程。
报告 `/private/tmp/frameflow-ip13-live-report.log`、`frameflow-ip13-live-client-tests.log`，服务日志 `frameflow-ip13-live-{java,next}.log`。
脚本 finally 删除临时测试、停止本次进程、删除专用容器/素材目录；未访问业务库或用户卷，临时测试不进入永久入口。

**范围与未验证项：** 这是客户端协议、controller 和 React 宿主证据；制作 POST、真实业务页面/历史/媒体整合及刷新 UI 仍归 IP-14。
本次未执行浏览器人工操作、完整三服务 Compose/持久卷验证或远程 CI；分别保留到 IP-14～16 和发布后核对，不能用上述通过替代整体 MVP 验收。

</details>

#### IP-11～13 三技能审查与发布授权（2026-10-31）

<details>
<summary>展开记录</summary>

用户明确要求按 `ddd-skill`、`mybatis-patterns`、`springboot-patterns` 检查这三个 task，通过后从 main 新建分支，分多条 commit，再 push 并创建 PR。
本次为同一执行者自审，不描述为独立多方审查。先 fetch origin/main，确认 IP-09/10 的 PR #8 已合入；
以 main `4cbfbb1` 新建 `codex/ip-11-13-frontend`，保留全部 IP-11～13 未提交产物；该 merge 的文件树与原实现基点相同，无冲突或额外后端变更。
发布授权不等于正式业务验收，IP-11～13 继续保留 [ ]，不合并 PR；IP-14～18 不在本次提交范围。

| 审查依据 | 核对与结论 |
| --- | --- |
| ddd-skill 2.2.4 / ADR-006、010 | Java 仍独占业务执行、持久化及资源授权；Next 只做 14 项白名单传输，客户端 reducer/controller 只维护协议快照和请求生命周期，没有从 Next 访问 DB 或复制制作执行流程；前端按真实 identity/template/asset/task 能力分目录，没有新框架或空业务层。未改纯 Java Domain、Case/Repository/Port 边界。通过。 |
| mybatis-patterns | 未修改 DAO/XML/schema 或查询契约；复核 namespace/statement、明确 resultMap/列、`#{}` 值绑定、nullable jdbcType、本人 owner 谓词、createdAt/taskId 稳定排序和 revision/status/stage 条件写。历史为同一根表投影，无逐条输入加载的 N+1；现行不分页是已选 MVP 契约，不强加技能示例分页。Spring 管理 Mapper/session，事务仍归 Cases；真实 MySQL 测试复验。通过。 |
| springboot-patterns | 薄 Trigger → Case、构造注入、集中脱敏 JSON 错误、Cookie/CSRF、本人归属及短事务保持；查询不推进执行，Java 后台和数据库提交窗口不受页面取消影响；Next 保留编码/状态/图片/多个 Set-Cookie，禁止 POST 自动重放；前端 401/403 与 503、查询错误与执行 FAILED 分开。未照搬 JPA、共享缓存、自动重试或替代错误格式。通过。 |

本轮自审没有新增阻塞发现；IP-13 实施时已复现并修正的同版本晚到历史解除查询暂停问题，其红/绿证据保留在 V-13，不省略失败记录。
范围和秘密核对覆盖全部已修改及新增文件；只有 IP-11～13 的前端/脱敏 origin 示例与相关说明，不包含临时脚本、真实凭证、上传内容、构建产物或其他业务变更。

**本轮最终复验：** frontend 使用 Node 22.21.1，`npm ci --offline --no-audit --no-fund`、`npm run test:ci`（190 tests）、`npm run lint`、`npm run typecheck`、`npm run build` 均通过。
backend 使用 JDK 17，独立临时 MySQL 8.4 测试库执行 `mvn --offline --batch-mode --no-transfer-progress clean -Pintegration-tests verify`：402 个快速测试 + 46 个真实 MySQL 测试，0 failures/errors/skips，JAR 打包成功。
测试库结束后表数为 0，专用容器已清理；未操作业务数据库或用户卷。根 `docker compose --env-file .env.example config --quiet`、差异范围及 tracked/new-file 空白检查通过。
日志 `/private/tmp/frameflow-ip1113-review-{npm-ci,frontend-tests,lint,typecheck,build,backend}.log`，数据库执行记录 `/private/tmp/frameflow-ip1113-review-db-report.log`；报告在 `frontend/test-results/` 与 `backend/target/{surefire,failsafe}-reports/`。

提交按代理/登录、模板/上传、任务查询/轮询、文档/审查证据四个主题划分，面向 main 创建 PR。远程 CI 结果在发布时核对，以 PR checks 为准；本轮本地通过不替代远程通过。
保留 V-12 的浏览器文件选择器、桌面/小屏/键盘及共享 Cookie 的人工检查限制；完整三服务容器和业务页面整合分别仍归 IP-15 / IP-14，没有用本次审查代替整体 MVP 验收。

</details>

### IP-14：制作提交、历史与结果页面整合

**依赖**：IP-09、IP-12、IP-13。**范围**：制作按钮/请求 gate、受理占位、任务卡片、/history、媒体加载/回收、刷新恢复及布局整合。

**验收 / V-14**：同表单同步防双击、提交时固定素材、202 后释放提交状态并立即读取真实快照；可继续创建另一任务。
提交结果未知不自动 POST、不伪造 FAILED，可核对 ID/个人记录；上传/提交/查询/执行/图片异常独立表达。
先恢复 Java 会话再读本人历史，仅未结束任务继续跟踪；初始请求失败不是空历史，成功/失败刷新不回退。
私有图片经 Cookie fetch/blob，旧媒体响应不得挂入新身份，替换/离页/退出时 revoke；预览失败不改 SUCCEEDED。
Component/Hook Tests 覆盖受理瞬间已终态、连续多项、刷新恢复、身份切换与媒体清理；人工检查参考布局、键盘、小屏及可实际预览。

#### IP-14 实际执行记录（V-14）

<details>
<summary>展开记录</summary>

2026-10-31，Asia/Shanghai；按用户明确指令及 task-development-workflow 的 EXECUTE TASK 模式执行。
执行前通过 `DEVELOPER_DIR=/Library/Developer/CommandLineTools git status --short` 确认工作区干净；默认 Git 仍受 Xcode 许可证阻挡，使用已有 Command Line Tools，不更改系统设置。
首次实施状态（历史）：**页面功能实现，自动化及真实 HTTP 验证完成；当时浏览器人工验证受阻，待用户审查/验收**。后续浏览器补验及当前用户验收结论见本节末。
没有 commit/push、远程 CI、部署、正式验收或启动 IP-15。

**实现与边界：**

- `features/task/use-task-submission.ts` 用同步请求 gate 捕获 templateId/有序 assetIds 和当前身份/CSRF；不等待 React disabled 渲染即可拒绝同轮重复提交。
  `features/asset/asset-inputs.tsx` 复用 IP-12 的上传 hook，提交中禁用整个素材 fieldset，选择/重试/移除事件也同步检查 gate；202 后释放按钮，原素材可继续创建独立任务。
- `task-api.ts` 增加单次 POST 和 JPEG 结果读取，沿用 v1 的严格受理元数据解析/owner 校验；请求体不含 owner、Blob URL 或页面模拟控制。
  202 元数据交给已有 `trackAccepted`，占位不伪造 PENDING，立即读取真实快照，首读可以已经终态。
  校验拒绝不建立 FAILED 记录；断网/超时/5xx/异常受理响应保留输入、提示结果未知且不自动重发，可查看记录或手动查询返回的可能 taskId。
  明确提示再次提交可能产生另一任务，单次 404 不将未知提交判为失败。
- `production-workspace.tsx` / `task-list.tsx` 组合同一个页面独占的 IP-13 controller。详情右侧为素材/制作与本人记录，新增 `/history` 页面，导航显示“我的记录”，登录后进入 `/history`。
  先恢复会话再读取本人历史，初始失败与成功空列表分开；记录显示创建时间、实际阶段/状态、执行原因或结果，查询错误/暂停有独立手动恢复，终态不回退。
- `result-preview.tsx` 通过 Cookie/no-store fetch 读取 JPEG Blob，再交 unoptimized Image；身份/任务/结果路径绑定媒体作用域。
  HTTP/解码失败仅显示预览异常，不改 SUCCEEDED，可独立重试；重试替换/离页/身份变化清理请求和 Blob URL，旧 success/catch 与旧图片错误回调不能影响新预览。
  提交订阅在离页立即释放，finally 只清理本次请求，身份变化先失效再取消。
- 同步根/前端 README、AGENTS、架构现状、ADR-010 的实施状态与 frontend-state-design；没有修改后端、公共 API、领域、SQL、依赖、CI 或 Compose。

**自动化证据：**新增 `production-ui.test.tsx` 的 27 项页面行为测试，以及 `task-api.test.ts` 的 8 项提交/媒体传输测试（新增 35 项，全量 245 项）。
覆盖同轮双提交、精确有序/重复素材、受理占位及首读终态、连续两任务不同结果、提交未知/真实 30 秒客户端预算、可能 ID 的 404、输入拒绝、401/403、A→B→A 的晚到成功/错误/finally、离页提交/媒体取消、URL 回收、图片失败独立重试、会话后恢复历史、处理中重新挂载后继续轮询、查询故障暂停/恢复、晚到历史不删除新任务和 Strict Mode 只读挂载。
原 IP-13 的 revision/轮询/生命周期测试继续在同一个入口运行。
首轮定向有 2 项测试在异步卡片出现前使用同步查询而失败，修正为等待卡片挂载；最终全部通过，未弱化业务断言。

| 检查 / 工作目录 | 实际命令与证据 | 结果 |
| --- | --- | --- |
| Node/npm / frontend | PATH 优先 `/opt/homebrew/opt/node@22/bin`，Node 22.21.1 / npm 10.9.4 | PASS |
| 重装依赖 / frontend | `npm ci --offline --cache /private/tmp/frameflow-npm-cache`；`/private/tmp/frameflow-ip14-npm-ci.log` | PASS；432 packages，0 vulnerabilities，现有 ESLint 9 EOL 提示不升级依赖 |
| 全量前端行为 / frontend | `npm run test:ci`；`/private/tmp/frameflow-ip14-tests.log`、`frontend/test-results/junit.xml` | PASS；14 files、245 tests，0 failures/skips |
| 静态/类型 / frontend | `npm run lint`、`npm run typecheck`；`/private/tmp/frameflow-ip14-{lint,typecheck}.log` | PASS；ESLint 0 warnings，strict TypeScript 通过 |
| 生产构建 / frontend | `NEXT_TELEMETRY_DISABLED=1 npm run build`；`/private/tmp/frameflow-ip14-build.log` | PASS；包含 `/history`，standalone postbuild 成功；静态壳不包含本人数据 |
| 后端 / backend | JDK 17：`mvn --offline --batch-mode --no-transfer-progress verify`；`/private/tmp/frameflow-ip14-backend-verify.log`、`backend/target/surefire-reports/` | PASS；402 tests，0 failures/errors/skips，JAR 成功；此命令不单独证明 MySQL 连接 |
| Compose / 根目录 | `docker compose --env-file .env.example config --quiet` | PASS；仅配置解析，不是三服务容器验收 |
| 自审 / 根目录 | Command Line Tools Git diff/status、`git diff --check`，连同新增文件逐一审查 | PASS；没有无关变更/依赖或敏感数据；本次为实现者自审，无独立 review 结论 |

**真实 Java/MySQL/生产 Next HTTP 联调：PASS。**
临时脚本 `/private/tmp/frameflow-ip14-services.py` 创建专用 `mysql:8.4` 容器/空库，显式执行现有 schema、环境密码初始化，JDK 17 JAR + Node 22 standalone 仅监听 loopback；上传目录在系统临时目录。
`python3 /private/tmp/frameflow-ip14-http-smoke.py` 通过 frontend 端口检查 `/login`、详情和 `/history` 页面的实际 HTTP 响应；真实 CSRF/Cookie 登录、JPEG multipart 上传、两个独立 202 ID、首读未结束、SUCCEEDED/JPEG 与 FAILED/GENERATE 分离、B 空历史与 A 资源 404、A 重新登录恢复完全相同终态记录。
结果图片读取验证 JPEG 起始字节、Content-Type/no-store，保存 `/private/tmp/frameflow-ip14-result.jpg`；脱敏检查摘要 `/private/tmp/frameflow-ip14-http-smoke.json`，服务日志在摘要对应临时目录中。
首轮 smoke 因临时环境未开启模拟控制而收到正确的 400 SIMULATION_CONTROLS_DISABLED，未将其记作业务缺陷；只对验证环境显式设 `MOCK_CONTROLS_ENABLED=true` 后重新创建专用数据库并通过。
验证结束已停止 Java/Next 进程并移除本次专用 MySQL 容器，保留脱敏日志/临时图片，未清理任何用户卷。
这是真实 HTTP/API 与生产页面响应证据，不是浏览器渲染/文件选择/图片解码证据；也不是 IP-15 的三服务容器/卷/CI smoke，未新增其配置。

| IP-14 验收项 / AC | 状态与对应证据 |
| --- | --- |
| 防双击、素材固定、202 后释放并继续制作；AC-R-03-02、AC-R-04-01/03、AC-R-07-01 | PASS；表单行为测试 + 真实双任务受理/首读未结束 |
| 受理未知不自动 POST、不伪造 FAILED、可核对记录/可能 ID；AC-R-07-01/03 | PASS；网络/超时/503/异常响应/404 测试 |
| 状态/阶段/执行原因、查询故障与图片故障分离；AC-R-07-02/03、AC-R-08-01、AC-R-09-01 | PASS（自动化/HTTP 部分）；图片实际浏览器显示见下项 |
| 会话后恢复本人历史，处理中继续跟踪，成功/失败不回退；AC-R-09-02/03、AC-R-10-02 | PASS；页面卸载/重新挂载、查询恢复测试；真实 A/B 重新登录持久记录 |
| 私有媒体作用域、晚到回调及资源清理；AC-R-08-03/04、AC-R-10-02 | PASS；受控 Promise、身份 A→B→A、离页/URL 清理和 JPEG 会话传输测试 |
| 参考布局、桌面/小屏、实际文件选择、键盘焦点/操作和浏览器图片预览；AC-R-11-01/02/03、AC-R-09-01 人工部分 | NOT VERIFIED；Work 设置禁用本地浏览器使用，工具返回 `Local browser use is disabled in Work settings` |

**自审与剩余事项：**没有发现未修复的阻塞性实现缺陷。自审补上提交订阅的立即清理与旧图片错误事件的 URL/身份检查，并保留媒体离页和晚到历史的行为测试。
浏览器人工检查仍需在启用浏览器访问后执行：登录后进入历史 → 模板上传/制作 → 连续创建两任务 → 查看真实预览 → 刷新处理中及终态记录 → A/B 切换；分别检查桌面、小屏及 Tab/Enter 操作。
远程 CI 未运行；未执行完整容器持久卷验收（IP-15）或跨标签页联合验收（IP-16）。不据本地通过勾选 IP-14 或宣称整体 MVP 验收完成。

</details>

#### 模板结果错配与示例图片更新（2026-10-31）

<details>
<summary>展开记录</summary>

用户反馈肖像卡片生成后显示另一模板，并要求重做模板、原片与效果图。
根因为 `MockMediaGeneratorPort` 忽略 templateId，所有任务都返回 `demo:mock-result`。
先新增定向测试复现：期望 `demo:portrait-result`，实际得到 `demo:mock-result`。

- Mock 改为按肖像/旅行模板选择不同候选，未知模板明确失败；Asset 按登记的结果白名单解析，不拼接路径。
  原 `demo:mock-result` 与对应 JPEG 保留，已有成功记录仍读原图，不重写历史数据库。
- 使用内置 image_gen 生成两张空白模板、三张 AI 原片，再用模板及对应原片作为引用编辑生成两张效果图。
  肖像保留同一人物，旅行两张原片分别进入主图/副图；替换封面并加入各自 JPEG 结果。
  来源、完整提示词和重打包方法见 `backend/src/main/resources/demo-media/SOURCES.md` / `PROMPTS.md`。
- 详情加入可展开的原片/效果对照、原片下载及明确的模拟说明。公开样例打包在
  `frontend/public/demo-examples/`，不包含用户数据。公共 API 字段、上传规则、任务状态和数据库结构不变。
  当前仍为按模板选择固定示例，不是根据本次上传内容生成图片。

验证：后端 `mvn --offline --batch-mode --no-transfer-progress verify` 404 项通过；前端
`npm ci --offline --cache /private/tmp/frameflow-npm-cache`、250 项测试、lint、typecheck、build 全通过；
Compose 配置解析和 diff 空白检查通过。普通沙箱阻止 Mockito attach/测试 HTTP 监听，允许本机测试后通过。
另用全新 tmpfs MySQL 8.4 容器执行 `TaskApiIT,ExecutionIT`：11 项通过；新增真实 HTTP 测试验证两模板
各自持久化正确引用、返回对应 JPEG 且图片字节不同。测试后确认零表并删除本次临时容器，未操作业务数据库。
证据日志为 `/private/tmp/frameflow-template-{repro,backend-verify,frontend-tests,integration}.log`。

使用临时生产 Next `127.0.0.1:13002` 与 Java `127.0.0.1:18082` 检查两模板真实页面、原片/效果对照和图片显示通过；
预览服务无业务库配置，未执行该浏览器下的上传/制作人工验收。两项真实受理/结果链路由上述独立真库 HTTP 测试验证。
原运行中的 3000/8080 服务保留；后端重启后新任务使用新结果，旧任务保留原图。未提交、推送、部署或启动 IP-15。

**肖像素材调整（2026-10-31）**：用户要求不使用真人外观，改用原创卡通形象。
使用内置 image_gen 重新绘制戴青绿色圆框眼镜、穿薄荷绿衬衫和黄色背带裤的小狐狸原图，
再以空白拱形模板和该原图为引用生成卡片；替换前后端肖像效果图与公开原图，保持各自原路径和尺寸。
对照标签由“原始照片”改为“原始图片”，来源及提示词同步更新。两张封面与全部旅行图片 SHA-256 与调整前一致。
调整后后端 verify 404 项、前端模板相关 13 项测试及 lint/typecheck/build 全通过；
前后端肖像效果图字节一致，standalone 打包原图与新版公开原图一致。日志见 `/private/tmp/frameflow-cartoon-*.log`。

**内置浏览器现场演示补充（2026-10-31）**：按用户要求打开可见内置浏览器，以原本地 `.env` 配置重启 8080 后端加载最新代码和素材，保留原 MySQL/上传目录及 3000 前端。
浏览器重新登录 demo-a，通过文件选择器上传卡通原图并提交肖像任务 `cd389818-feaa-4976-bee4-d232a539aa53`，
SUCCEEDED 且实际预览为小狐狸卡片；随后上传两张旅行原图，提交旅行任务 `8da9f3b7-d27e-4b80-9454-96222e1595fc`，
SUCCEEDED 且实际预览为对应双图明信片。进入 `/history` 并整页刷新后，两项任务及各自图片均恢复；旧任务仍保留修复前结果。
已观察模板对照、实际文件选择、上传就绪/提交反馈、两模板任务成功及终态刷新恢复。
现场截图 `/private/tmp/frameflow-browser-demo-history.png`；浏览器停留在历史页供用户继续查看，本地后端保持运行。
本次没有覆盖处理中刷新、A/B 切换、多标签页、键盘和全部小屏检查，不据此勾选 IP-14 整体验收或 IP-16。

**模板详情历史范围调整（2026-10-31）**：用户要求模板详情仅显示当前模板记录，通过“我的记录”进入时显示全部。
`ProductionWorkspace` 向 `TaskList` 传当前 templateId，列表、计数和空态基于匹配快照；`TaskHistory` 不传筛选条件，继续显示本人全部记录。
本页新受理/核对但尚无快照的占位保留反馈，读到实际快照后按模板归类；没有修改 API、任务事实或查询/轮询协议。
新增四项页面测试，先复现混入其他模板及空态错误，再验证两模板筛选、刷新、全历史重新挂载；沿用的受理占位/未知提交回归通过。
前端全量 254 项测试、lint/typecheck/build、Compose 配置和 diff 空白检查均通过，日志 `/private/tmp/frameflow-template-scope-*.log`。
内置浏览器实查：旅行详情 1 项（刷新仍 1 项）、肖像详情 2 项、“我的记录”全部 3 项；已保留旅行详情页面。
截图 `/private/tmp/frameflow-scoped-travel-history.png`。本次仅前端及说明调整，未重复后端或数据库测试，未提交/推送。

**模板与记录展示精简（2026-10-31）**：按用户五项浏览器批注统一应用于所有模板及个人历史页。
刷新入口仅保留图标，并以 aria-label/title 保留含义；卡片可见内容、无障碍名称及图片替代文本不再包含任务 UUID。
进行中的实际三个步骤分别显示“正在检查上传图片 / 正在制作图片 / 正在准备预览”；成功记录只展示成功结论，失败记录保留失败环节和原因。
两份模板 description 删除模拟说明的第二句，模板标题区移除“图片创作”；展开的示例对照及文档继续说明 Mock 边界。
公共 API 字段、阶段枚举、任务事实及历史筛选规则不变；清除废弃 CSS，并更新相关组件测试。
后端 verify 404 项、前端全量 259 项测试、lint/typecheck/build、Compose 配置和 diff 检查通过；日志 `/private/tmp/frameflow-record-copy-*.log`。
内置浏览器核对两模板说明、卡片及图标刷新操作，个人历史成功/失败记录沿用一致展示；截图 `/private/tmp/frameflow-record-copy-preview.png`。
本地后端已按原 `.env` 重载、演示账号重新登录；保留原数据库和素材，页面停留在旅行模板供用户核对。

**记录卡片对齐修正（2026-10-31）**：用户截图反馈同排卡片底部不齐；真实历史页复现成功卡片 402px、失败卡片 476px。
仅调整共用网格/卡片 CSS：网格项拉伸，卡片采用纵向 flex、内容区填充剩余空间，预览区不收缩。
修正后前三张同排卡片均为 476px、底边一致，预览区均为 327px；模板详情与个人历史共用该样式。
390×844 视口检查单列正常、内容宽度不超过可见宽度，检查后恢复原视口；lint/build 与 diff 检查通过。
截图 `/private/tmp/frameflow-card-alignment.png`，本次未改业务逻辑或重复后端测试。

**记录阶段信息隐藏（2026-10-31）**：用户进一步明确不展示当前阶段，所有记录卡片统一移除阶段、进行中步骤和失败环节行，保留整体状态、模板、创建时间与失败原因。
仅调整共用展示组件与相关行为测试；API 阶段数据及内部状态合并/轮询规则不变，覆盖前述步骤文案方案。
前端全量 259 项测试、lint/typecheck/build 与 diff 检查通过；日志 `/private/tmp/frameflow-hide-stages-*.log`。
内置浏览器核对肖像成功/失败记录、全部历史及旅行详情均不再显示阶段行，截图 `/private/tmp/frameflow-hide-stages.png`。

**模板封面展示调整（2026-10-31）**：用户反馈目录封面观感，原 3:5 容器裁切 8:5 模板并叠加深色渐变，导致标题和版式不完整。
仅调整目录 CSS：按原始比例完整预览，移除渐变遮罩与悬停缩放，名称/创作入口置于下方白色区域；网格加宽，小屏改为单列。
内置浏览器确认两张封面均为 contain、640×400 原比例；390×844 下单列且页面无横向溢出，检查后恢复原视口。
lint/build 与 diff 检查通过，日志 `/private/tmp/frameflow-covers-{lint,build}.log`，截图 `/private/tmp/frameflow-template-cover-layout.png`。

**模板制作区与历史滚动修正（2026-10-31）**：用户反馈多条记录将左侧制作表单拉长，浏览器复现 8 条肖像记录使两栏均达到约 1962px。
工作区改为顶部对齐，制作面板按自身内容高度显示；详情历史区按视口限定高度，仅列表内部滚动，标题与刷新入口保持可见。
滚动列表提供名称和键盘焦点，小屏采用单列并限制历史高度；独立“我的记录”仍按页面自然滚动。
浏览器验证肖像左栏约 813px、历史区 778px，列表可滚至底部且左栏高度不变；旅行两上传位完整显示；390×844 下无横向溢出。
36 项制作页面测试、lint/build 和 diff 检查通过，日志 `/private/tmp/frameflow-history-scroll-*.log`，截图 `/private/tmp/frameflow-history-scroll.png`。

</details>

#### IP-14 补充：浏览器演示设置与真实阶段展示（2026-10-31）

<details>
<summary>展开记录</summary>

**范围归属**：R-06 的指定失败/耗时控制和每任务独立参数已在 IP-08/09 实现；R-07 的当前阶段展示属于 IP-14 原验收范围。
用户本轮明确授权增加可选的页面演示设置；这是 IP-14 的小范围补充，原计划并未要求必须提供 UI，故不新增任务编号或升级为 Assessment 必需功能。
IP-15 的容器配置及 IP-16 的完整联合验收保持原范围，本轮不启动它们。

**实现**：

- Java 和 Next 启动时均可显式注入既有 `MOCK_CONTROLS_ENABLED=true`。Next 模板详情 Server Component 调用 `connection()` 后在请求时读取开关，
  仅向客户端传非敏感布尔值；同一构建支持普通/演示环境。普通模式不显示控件且 POST 省略 simulation，Java 开关仍作最终校验。
- 演示环境的制作表单提供默认折叠的“演示设置”，失败阶段为不触发/素材检查/模拟生成/结果整理；每阶段耗时默认 1 秒，可填 0～30 整数秒。
  设置随本次素材同步复制，提交 gate 同时保护修改事件；202 后释放，可以改设置创建另一任务，不改变已受理任务。
  身份或模板变化时随表单重置，不写浏览器存储；失败/耗时仍由 Java 执行，不由前端预测或推进。
- 卡片恢复 RUNNING 的真实 currentStage 和阶段失败的 failure.stage/原因，成功后不显示阶段行，覆盖上文“记录阶段信息隐藏”的展示方案。
  API 字段、领域、SQL、后端实现、线程/轮询协议、依赖和 Compose 均未改变。
- 更新根/前端 README、`.env.example`、API 说明和前端状态设计；新增页面入口开关测试及提交/展示行为测试。

**验证**：

- 先更新/新增测试，未实现时 14 项按预期失败（缺少控件/阶段及传输字段）；实现后定向 83 项通过。
  补充耗时 0/30 边界、后端开关拒绝及请求时开关检查后，全量 `npm run test:ci` 为 **16 files / 274 tests PASS**，比上轮增加 15 项。
  覆盖默认隐藏/省略字段、连续任务参数冻结、同轮防双击、提交期间锁定、三个失败选项、不预测终态、非法耗时、身份重置和真实传输。
- Node 22.21.1：`npm ci --offline --cache /private/tmp/frameflow-npm-cache`、`npm run lint`、`npm run typecheck`、
  `NEXT_TELEMETRY_DISABLED=1 npm run build` 全通过；Compose config 和 diff 空白检查通过。
  日志 `/private/tmp/frameflow-demo-controls-{npm-ci,red,focused,tests,lint,typecheck,build}.log`。
  全量测试首轮因沙箱禁止 loopback 监听导致现有 46 项代理测试失败；允许本机测试监听后重跑全通过，未跳过或削弱测试。
- 用专用 tmpfs MySQL 8.4 空库、现有 Java 17 JAR 和本轮生产 standalone 联调：同一构建分别以 true/false 启动 Next，HTTP 响应开关正确；
  经 Next 实际登录/CSRF、上传 JPEG、四个独立 202 任务分别成功及在三个指定阶段失败，终态历史稳定，成功 JPEG 可访问。
  脚本 `/private/tmp/frameflow-demo-controls-{services,http-smoke}.py`，脱敏结果 `/private/tmp/frameflow-demo-controls-http-smoke.json`。
  本轮无后端源码修改，未重复 Maven verify；以上是真实应用/数据库联调，未冒充后端全量重跑或三服务容器验收。
- Chrome 原生界面实际检查：演示入口默认折叠；通过文件选择器上传项目卡通原图；创建正常任务，观察素材检查/模拟生成/结果整理。
  再创建生成失败/10 秒与正常成功/10 秒两个任务，后者在前者结束前被受理；前者失败后后者继续运行。
  处理中整页刷新恢复 7 条真实记录、失败环节及未完成阶段，后续成功图片可见；普通模式相同页面不显示演示入口。
  操作证据见本轮 CUA 的 Chrome accessibility 状态记录；本轮未保存截图，未覆盖完整小屏/纯键盘/跨标签页回归，不替代 IP-16。

**自审及状态**：实现者自审未发现剩余阻塞缺陷；仅当前补充范围 **READY FOR ACCEPTANCE**，不勾选 IP-14 整体验收。
验证使用独立临时服务/数据库，已停止并清理本轮专用容器；原业务库、上传目录及 `.env` 保持不变。
未提交、推送、远程 CI 或部署。

</details>

#### IP-14 用户验收记录（2026-10-31）

<details>
<summary>展开记录</summary>

状态：**ACCEPTED，用户明确验收通过**。在已说明实现、测试、浏览器演示结果及剩余验证范围后，
用户确认：“通过 task14可以算是完成了”。据此更新本任务及第 10 节 checklist；此前“待验收”描述保留为历史执行记录。

验收覆盖制作提交、任务/历史/结果整合、刷新恢复，以及本轮授权补充的演示设置与真实阶段展示。
现有证据包括 274 项前端测试及静态/类型/生产构建检查、真实 Java/MySQL/Next HTTP 联调和 Chrome 主要流程实操。
按用户要求另补了展开演示设置的实际截图，临时文件 `/private/tmp/frameflow-demo-settings-ui.png`。

验收确认不将未执行检查改写为 PASS：新增演示入口加入后的完整小屏、纯键盘及跨标签页回归仍未验证；
完整三服务容器/持久卷验证及整体 MVP 最终验收仍属后续 IP-15/16/18。
本次仅更新验收状态与说明，未改业务代码或重复运行业务测试，未提交、推送、部署或启动 IP-15。

</details>

#### IP-14 补充发布前检查（2026-10-31）

<details>
<summary>展开记录</summary>

用户明确授权将剩余 19 个 IP-14 补充文件单独提交、推送并核对远程 CI。
发布前复核范围为演示设置、真实阶段展示、相应测试及文档；没有新增依赖或修改后端业务实现。
Node 22 下重新运行 npm ci（离线缓存）、test:ci（16 files / 274 tests）、lint、typecheck、build 均通过；
Compose config 与 git diff --check 通过，日志 `/private/tmp/frameflow-ip14-publish-*.log`。
本节此前“未提交、推送、远程 CI”的描述保留为当时的历史状态；远程检查以实际发布提交的运行结果为准。

</details>

### IP-15：Compose 初始化、上传卷与容器业务门禁

**依赖**：IP-14。**范围**：复用 Dockerfile/Compose，加入 schema 初始化挂载、账号初始化环境、JAVA_API_ORIGIN、backend 上传卷及实际需要配置；隔离 smoke 脚本和 CI job。

**验收 / V-15**：准备脱敏配置后从空的专用验证卷启动三服务，实际 SQL/账号/模板就绪；通过 frontend 端口完成 CSRF→登录→上传→提交→查询→结果，无测试依赖宿主机应用进程。
镜像构建、服务就绪、初始化和业务 smoke 均单列；私有接口/素材真实可读。Compose down/up 保留数据库/上传内容，重新登录同账号仍有历史/素材；不要求重启续跑未结束任务。
CI 在此接入隔离容器 smoke，失败提供脱敏日志并非零退出，临时资源在结束后清理；只清理专用验证项目/卷，不能删除用户卷。
保留 config --quiet 门禁；已有卷不自动重放 schema/重置密码，文档与实际行为一致；镜像非 root 用户可写上传卷。
本任务补完历史 T-04 的三服务/真实 DB 验证，IP-03 已提前完成 SQL 层集成，不把 DB 风险留到这里才发现。

#### IP-15 实际执行记录（V-15）

<details>
<summary>展开记录</summary>

2026-10-31，Asia/Shanghai；用户明确要求实施 IP-15。
状态：**实现、本地容器验证及远程 CI 均通过，待用户审查/验收**。
使用 docker-patterns 技能核对容器/卷配置；未更改业务 API、Java/Next 业务源码或依赖版本。
工作区已有 IP-14 前端与文档改动，执行前保存差异快照；沿用 Command Line Tools Git，未修改 Xcode 设置。
本地前端 274 项测试含未提交的 IP-14 改动；本次仅发布 IP-15，远程 CI 按提交源码运行，前端 259 项测试通过。

**实现**：

- `compose.yaml` 将 V001 只读挂载到 MySQL 首次初始化目录；保留 SQL 健康检查和 Spring 自动建表关闭。
  新增复用后端镜像的非 root 一次性 `accounts-init`，调用已有 CLI，成功退出后启动 backend。
  初始化密码只传给初始化容器；缺失账号需要配置密码，已有账号不重置密码/ID。
- backend 上传挂载 `asset-data:/data/frameflow/assets`；镜像预建 app 用户拥有的目录，实际验证新卷可写。
  Docker 构建上下文排除本地 data/storage/uploads/results，避免把开发上传内容带进镜像。
  frontend 运行时固定使用 `http://backend:8080`，Java/Next 均转发演示开关，backend 接入现有 worker/queue 配置。
- `scripts/compose-smoke.py` 使用 Python 标准库，生成随机专用项目、凭据和 loopback 端口，显式使用 `.env.example`，不读取开发 `.env`。
  所有业务 HTTP 经 frontend；独立 SQL 检查在专用 mysql 容器内执行。
  成功/失败在 finally 中仅清理本次容器、网络、卷与镜像标签，保留共享基础镜像/缓存。
  失败诊断先脱敏后输出，并返回非零状态；日志在 Git 忽略的 `test-results/compose-smoke.log`。
- CI compose job 保留 config 检查，新增脚本边界测试、实际容器 smoke 和脱敏 artifact；30 分钟超时。
  actionlint 同时发现原 MySQL job 的数字端口键类型错误，改为字符串键 `ports['3306']`，不改变端口含义。

**分层验证证据**（Docker Engine 29.0.1 / Compose 2.40.3，macOS arm64；生产镜像中的 Java 17 / Node 22）：

| 检查 | 实际结果 / 证据 |
| --- | --- |
| 配置解析 | `docker compose --env-file .env.example config --quiet` PASS |
| 镜像构建 | smoke 中真实多阶段构建 backend/accounts-init/frontend PASS；没有用宿主机 JAR/standalone 替代镜像构建 |
| 空卷 SQL / 初始化 / 服务就绪 | 四张表、恰好两个演示账号、Java CLI 退出 0；前后端 UID 均非 0，素材目录可写；frontend `/login` 及模板 API 真实响应 PASS |
| 经 frontend 的业务 smoke | CSRF → demo-a/demo-b 登录与 session → 目录/详情/真实封面 → multipart 上传及字节读取 → 202/Location → 后台 SUCCEEDED/FINALIZE → 历史/结果 JPEG 字节读取 PASS；SQL 确认素材、成功任务、有序绑定各一条 |
| 私有接口与写保护 | 缺 CSRF 提交 403；匿名素材/结果 401；B 读取 A 素材/任务/结果 404，B 历史为空；私有响应 no-store、图片 nosniff PASS |
| down/up 持久化 | 先保留两个卷 down，再改变初始化密码环境重建；schema 未重放；账号 ID/原密码散列逐字不变，原密码仍可登录；旧 session 401；历史/任务快照及输入/结果图片字节不变 PASS |
| 清理隔离 | 成功项目 `frameflow-smoke-0996d1c7d8acd768` 的容器/卷/网络/镜像标签均清理；按项目标签复查为空，原 `frameflow-studio-mysql-1` 仍 healthy、13316 端口保留 |
| 脚本边界测试 | `python3 -m unittest discover -s scripts -p 'test_*.py' -v` PASS，5 tests；覆盖 GET/POST、非预期状态、预期 401、私有缓存策略、空 Cookie/凭据/BCrypt 脱敏和失败诊断/清理/非零返回 |
| GitHub Actions 静态检查 | 使用已有 `rhysd/actionlint:1.7.12` 镜像只读检查 PASS；这不代表远程 job 已执行 |
| 后端 | Java 17：`mvn -Dmaven.repo.local=/private/tmp/frameflow-m2 --batch-mode --no-transfer-progress verify` PASS，404 tests，0 failures/errors/skips，JAR 打包；日志 `/private/tmp/frameflow-ip15-backend.log` |
| 前端 | Node 22：`npm ci --offline --cache /private/tmp/frameflow-npm-cache`、test:ci、lint、typecheck、build PASS；16 files / 274 tests，日志 `/private/tmp/frameflow-ip15-{npm-ci,frontend-tests,lint,typecheck,frontend-build}.log` |
| 差异检查 | `git diff --check` PASS；原前端业务代码改动完整保留，本轮未提交、推送或部署 |

成功 smoke 完整脱敏日志：`test-results/compose-smoke.log`，控制台副本 `/private/tmp/frameflow-ip15-smoke-console.log`。
首次脚本在 HTTP 请求方法的 Python 访问方式上出错，进程返回 1 并收集诊断、清理专用项目；
修正为 `get_method()` 并补边界测试后，完整重跑成功。首次失败证据 `/private/tmp/frameflow-ip15-first-failure.log`。
普通沙箱首次阻止 Maven 外网访问和前端代理测试的 loopback 监听；获得所需执行权限后重跑通过，未跳过测试或绕过业务校验。

**未验证 / 限制**：远程 GitHub Actions 已通过，见下方发布记录；未做 IP-16 浏览器小屏、键盘、共享 Cookie 跨标签页及完整故障联合回归。
新建本地命名卷权限已验证，不承诺任意外部卷或手工改变属主的卷自动修复。
已有卷不自动迁移结构/更改数据库密码；内存 session 重建丢失，未结束任务无自动续跑。
强制杀进程/宿主机崩溃可能留下专用资源；按日志准确项目名和标签核对后清理，禁止全局 prune 或删除用户卷。
本地运行完成历史 T-04 三服务业务闭环验证；不自动勾选 IP-15 用户验收或整体 MVP 最终验收。

</details>

#### IP-15 发布与远程 CI 补充记录

<details>
<summary>展开记录</summary>

2026-10-31（Asia/Shanghai），按用户明确授权仅提交、推送 IP-15，并核对远程 CI。
功能提交 `aa661ec973b379b6b3d9643c6f45b2c6b2861e72` 已推送到 `origin/main`。
使用实施前快照拆分 README、AGENTS、任务记录和环境示例的重叠片段；IP-14 的前端代码、测试及说明保持未提交，工作文件内容完整保留。

[CI run 41195446245](https://github.com/ShengboYY/frameflow-studio/actions/runs/41195446245)
由该提交的 push 触发，2026-10-31 19:06～19:09 执行，workflow 总结论 success：

| Job | 实际结果 |
| --- | --- |
| backend | success；404 tests，0 failures/errors/skips，打包通过 |
| mysql-integration | success；404 项快速测试和 47 项真实 MySQL 集成测试，0 failures/errors/skips |
| frontend | success；15 files / 259 tests，安装、lint、typecheck 和生产 build 均通过 |
| compose | success；配置解析、5 项脚本测试及真实镜像构建、空卷 SQL/账号初始化、frontend 业务/私有媒体、down/up 持久化检查全部通过；日志出现 `[6/6] PASS`，专用项目容器、网络、卷和镜像标签清理成功 |

四份 artifact 均已成功上传：backend-test-reports、mysql-integration-test-reports、frontend-test-reports、compose-smoke-evidence。
远程运行与提交 SHA 已逐一核对；以上结果不代表 IP-16 浏览器联合回归或用户最终验收。
本次后续文档提交仅登记已核实的上述功能提交运行证据。

</details>

### IP-16：联合故障、多任务和验收回归

**依赖**：IP-15。**范围**：复用每项已有测试，补剩余跨边界回归与人工验收；不另建一套工作流/大型 E2E 平台。

**验收 / V-16**：容器中执行成功、三个指定失败、耗时控制、多个任务、关页继续、处理/终态刷新、A/B 隔离、查询中断/恢复、旧请求/图片晚到情形。
同浏览器退出/登录及共享 Cookie 跨标签页场景确认旧 token 写入被拒绝，旧读取不污染；不同浏览器可分别演示 A/B。
验证本机队列拒绝、文件/元数据失败、状态写失败的自动化证据与限制说明；硬崩溃/派发窗口不承诺自动续跑。
按真实对象审查层依赖、跨模块契约、事务边界和全部 14 个 API 的覆盖，补缺陷测试并修复后重跑相关项；人工桌面/小屏/键盘检查单列。
结果按 AC 登记通过/失败/未验证及证据，不以整体 build 成功代替业务验收，也不把故障演示中资源不可读标作预览通过。

#### IP-16 实际执行记录（V-16）

<details>
<summary>展开记录</summary>

2026-10-31（Asia/Shanghai），用户要求执行 Task 16；基线 `a6214a5`，实施前工作区干净。
使用 browser-qa 与 verification-loop 技能；本地联合回归和缺陷修复完成，**待用户审查/验收**，保留 [ ]。
IP-16 本地执行结束时未提交、推送、触发远程 CI、部署或对外发送，IP-17/18 当时未开始；后续发布 CI 与 IP-17 材料记录见本文最新摘要及 V-17。
逐项操作、故障证据、架构审查及 48 条 AC 的通过/部分通过/未验证登记见 [ip16-regression.md](ip16-regression.md)。

**变更：**

- 新增 `scripts/acceptance-regression.py`，复用 IP-15 Smoke 的真实生产镜像、空卷初始化、权限与持久化检查，
  增加三阶段失败/成功、多任务独立设置、无任务 HTTP 查询也完成、双模板结果、A/B 双向隔离、旧 CSRF 403 和输入拒绝。
  实际记录方法/路径后校验 API-01～14 覆盖。无新增依赖、工作流平台、业务 API 或数据库改动。
- 可选 `--browser-review` 将同一专用容器环境保留给原生浏览器，最长 45 分钟；一次性连接文件权限 0600，
  仅含本项目所需配置，结束后删除；人工结果独立登记，不因等待结束自动通过。
- 新增 7 项脚本边界测试，拒绝错阶段、失败后额外迁移、终态回退、结果不可读假通过，并验证连接文件权限、禁止覆盖既有连接以及仅清理本次交接文件；
  与既有 5 项测试一起由 CI 发现。Smoke 增加可选报告名，避免扩展回归覆盖 IP-15 报告。
- 实测复现 `docker compose build` 并发构建 backend/accounts-init 同标签时的 image already exists 竞态；
  `compose.yaml` 现仅由 backend 构建共享标签，accounts-init 引用镜像。初始化/运行依赖与非 root 规则保持；初始化服务显式禁止拉取本地构建标签。
- CI compose job 改跑扩展回归（包含全部 IP-15 基线），上传 `test-results/ip16-regression.log`，保留原失败门禁。

**验证：**

- Node 22.21.1：npm ci（现有离线缓存）、274 项前端测试、lint、typecheck、生产 build/postbuild PASS。
- JDK 17.0.12 / Maven 3.9.10：完整 verify + integration-tests PASS，404 项快速测试和 47 项真实 MySQL 集成测试，
  零失败/错误/跳过。使用本次专用容器中的独立 `frameflow_it_ip16` 空库；没有操作用户库。
- Python 脚本测试 12/12 PASS；Compose 配置解析、已有 actionlint 镜像只读 CI 检查、导入边界检查、diff 空白检查 PASS。
- 最终 `python3 scripts/acceptance-regression.py` 退出 0，默认 Compose build 成功、IP-15 六步和 R1～R5 全部通过，
  项目 `frameflow-smoke-2c760c6cf9ddba07` 的专用容器/卷/网络/镜像标签清理完成。
  成功任务配置三阶段各 1200ms，受理实际耗时 0.036s，返回时任务未完成；三种失败分别保存 revision 2/3/4，成功 revision 4。
  另在无应用镜像标签的新项目 `frameflow-smoke-4db55e4a2f8d3052` 验证默认 `docker compose up --build` 的初始化和 frontend 就绪，PASS 并清理。
- 同容器环境的原生 Chrome：真实文件选择/上传、同账号切页保留、30秒阶段任务处理中刷新、Offline 查询中断/暂停/保留/手动恢复，
  共享 Cookie 双标签页 A→B→A（首次登录均成功、素材/记录同步清除）、390×844 小屏纯键盘双图上传与连续不同结果提交、
  关闭制作页后历史继续跟踪/终态刷新均 PASS。390/768/1440 布局目视检查、账户框 Esc 焦点返回、最终 Console 0 messages；
  全部终态后 Network 无新任务自动查询。检查后退出账号并关闭专用隐身窗口，环境项目 `frameflow-smoke-e172f70d00a09955` 已清理。
- 晚到的旧 success/error/finally/timeout、图片成功/403、Blob 回收及旧回调不重启轮询由本轮重跑的受控组件/controller 测试证明；
  不声称在真实浏览器强制投递了不可取消的旧包。旧 token 拒绝是实际共享 Cookie jar 的 HTTP 403 证据，浏览器身份切换另列。

日志和报告索引见专项文档第 1 节；初次脚本交接 API 使用错误、Compose 构建竞态、Maven 启动器环境串入导致的失败及修正均保留记录，
没有跳过测试或用重跑隐藏失败。仍接受派发崩溃窗口、未知 DB 结果停止、无重启续跑、内存会话及文件/元数据非原子的既定限制。
完整 WCAG/屏幕阅读器审计未执行；本轮桌面、小屏和键盘检查仅证明已记录的基本布局与操作。
本轮结束时，新 CI 配置的远程运行与材料核对尚未完成；后续 CI 结果见本文最新摘要，材料/回滚说明见 V-17。实际投入总量、最终版本及用户验收仍待确认，不把本地或远程通过当作整体交付完成。

</details>

### IP-17：README、材料与验收版本准备

**依赖**：IP-16。**范围**：按实际实现同步 README/相关设计状态、运行测试命令、初始化/资源来源、API/模拟控制/身份/刷新操作、部署回滚基本步骤与证据索引。

**验收 / V-17**：从干净源码副本按说明执行安装/构建/测试及容器启动，源码包不依赖工作区缓存/个人文件；源码/lockfile/schema/初始化/素材/CI/README 齐全，无真实秘密。
成功、三阶段失败、耗时调整、多任务、身份切换与刷新演示命令可复制；登录需密码环境配置，没有通用默认密码。
如实写 AI 工作流及修正实例、复用来源、开发/验证/整理实际投入、未完成与重启限制；投入记录由用户核实，不推算虚构时长。
回滚说明覆盖代码版本与数据库/文件关联数据，不声称 schema 可自动无损降级；不执行生产部署。
本地、MySQL、Compose、远程 CI 各有环境/时间/命令/结果或明确未验证；仓库交付的候选 commit 在实际形成后填入，不虚构编号。

#### IP-17 本轮材料整理记录（V-17）

<details>
<summary>展开记录</summary>

2026-10-31（Asia/Shanghai），用户要求结合 Chrome 中的 Assessment 原文重写完整、易读的 README，并将需要的实际截图嵌入文档。
本轮仅修改 README、截图及本节记录，不修改业务、API、SQL、依赖或容器配置。基线 `f70420f2dcfb45104f107a0ce4f0ccfc3ca4c280`，执行前工作区干净。
状态：**本轮材料和本地复现验证完成，待用户审查；IP-17 保留 [ ]**。不代替 IP-16 用户验收，也未执行 IP-18。

**材料：**

- 通过 CUA 原生 Chrome 阅读现有卷面 R-01～14，按评估者阅读顺序重写根 README：Compose 启动、初始化/配置、素材、正常/失败/耗时/多任务/刷新/身份演示、技术/API、容器内测试、部署/回滚、完成度、AI 工作流与来源。
- `docs/images/` 保存 5 张原生 Chrome 隐身窗口 JPEG，覆盖目录、空白登录、详情上传、演示控制/处理中、成功与三阶段失败历史；附截图来源说明。截图仅包含本次临时账号/示例素材，不包含凭据、卷面或个人标签页。
- 用户给出并确认投入占比：需求分析 5%、架构设计 15%、实现计划 10%、开发 50%、验证 15%、整理 5%。README 按回顾估算登记，合计 100%；没有总小时数，不从提交跨度推算。
- 新鲜 `npm ci` 实际报告 16 项 moderate 漏洞，并提示 ESLint 9.39.5 已停止维护；依赖安装成功不代表没有维护风险。未在文档任务中升级依赖、改变 lockfile 或运行 `npm audit fix --force`，后续需逐项评估兼容升级，不能沿用早期记录中的“0 vulnerabilities”。README 的“已有验证结果”仅保留结果摘要，依赖风险摘要仍见其“已知限制”，具体提示保留在本节。

**本轮验证：**

从 Git 跟踪文件复制到临时目录 `/var/folders/8z/9khp9chx7791ktmcv7j5715r0000gn/T/frameflow-readme-krsawmnj`，不带 `.git`、`.env`、依赖或构建产物；没有使用个人应用进程/数据库。依赖允许从公共仓库下载，Docker 镜像构建可使用其正常层缓存，不声称全量 `--no-cache` 冷构建。

| 环境 / 命令 | 结果 / 日志 |
| --- | --- |
| Maven 3.9.11 / Java 17 容器：`mvn --batch-mode --no-transfer-progress verify` | PASS，404 tests，零失败/错误/跳过；`/private/tmp/frameflow-readme-backend.log` |
| Node 22 容器：`npm ci`、`test:ci`、`lint`、`typecheck`、`build` / postbuild | PASS，16 files / 274 tests；`/private/tmp/frameflow-readme-frontend.log` |
| Maven 容器 + 本次 MySQL 独立空库 `frameflow_it_readme`：`mvn --batch-mode --no-transfer-progress -Pintegration-tests verify` | PASS，404 快速 + 47 IT，零失败/错误/跳过；`/private/tmp/frameflow-readme-mysql.log` |
| `python3 scripts/acceptance-regression.py --browser-review` | Compose 配置、镜像、空卷/账号、六项基线及 R1～R5 全部通过；项目 `frameflow-smoke-50af8adb21f328b8`；`/private/tmp/frameflow-readme-container.log` |
| 原生 Chrome | 实际目录、登录、原图选择/上传、30 秒阶段任务受理、后台阶段与最终成功、历史成功/三阶段失败显示；5 张截图已保存；检查结束退出账号并关闭本次窗口/标签 |
| README 备份命令 | 在本次专用环境停止前后端后，mysqldump 得到四表 SQL，非 root backend 的 tar 得到非空素材归档；内容只在验证进程内检查，不保留含账号编码的备份；没有演练备份恢复或生产回滚 |
| API 可复制示例 | 前端容器内执行 README 原样 Node 示例，登录/上传、连续受理四任务、成功图片响应与三个失败阶段检查通过；`/private/tmp/frameflow-readme-api-check-retry.log` |
| Python 脚本边界 | `python3 -m unittest discover -s scripts -p 'test_*.py' -v`，12/12 PASS；`/private/tmp/frameflow-readme-scripts.log` |
| 文档结构 | README 和截图说明的 47 个本地文件/图片/锚点链接检查通过；另核对 JSON、shell 语法、空白及实际 diff |

Python 只在宿主机编排 Docker、HTTP 和脚本边界验证，构建/安装/应用/Java 与前端测试在容器中执行；README 对两种入口的边界分别说明。截图环境正常退出并清理其专用容器、卷、网络、镜像标签和临时连接凭据。API 示例验证也使用独立项目，结束后清理。未将浏览器等待本身视为验收通过。

API 示例首次测试启动器误用了 Smoke 为验证密码不重置而在 down/up 前改写的环境值，登录返回 401；改为保留实际初始化密码后，原样示例全部通过。首次日志 `/private/tmp/frameflow-readme-api-check.log` 保留，不把该次失败计为通过；没有修改认证实现或跳过登录。

**提交与待办：**用户随后授权将本轮全部文档与截图在当前分支本地提交，实际提交号以 Git 记录为准；API 长示例已原样移至 `docs/api-demo.md`，README 保留入口。最终验收版本仍待确认，本次不推送、不触发新远程 CI、不部署、发送或交卷。
旧远程 run `41195446245` 只作为对应历史提交的证据；不得当成当前文档/274 项前端版本的远程结果。
总小时数未单独统计，约定交付日期/渠道和最终验收由用户确认。本节保留材料整理当时的验证范围；后续远程 CI 及文档状态同步见本文最新摘要，不据此声明整体交付完成。

</details>

### IP-18：用户最终审查、验收与约定交付

**依赖**：IP-17。**负责人**：用户；Agent 仅准备清单和材料。

**验收 / V-18**：用户审查核心模型、公共 API、架构边界及实际演示，确认 AC 结果与剩余限制；核对收到任务的沟通渠道、约定日期与最终版本/包。
仅在用户明确授权后执行提交/推送/对外发送；本规划不授权这些动作。不把材料备妥等同已向招聘方交付。
ASMT-R-13-06 的实际交付需要此记录，不能由自动测试或 Agent 自行勾选。

## 5. Task Dependencies

### 5.1 必要依赖表

依赖表示可验证的前置产物，不按需求编号建立串行链；历史任务不作为本表待执行节点。

| 任务 | 必须完成的 IP 前置 |
| --- | --- |
| IP-01 | 无 |
| IP-02 | IP-01 |
| IP-03 | IP-01 |
| IP-04 | IP-03 |
| IP-05 | IP-01 |
| IP-06 | IP-03、IP-04、IP-05 |
| IP-07 | IP-02、IP-03 |
| IP-08 | IP-05、IP-06、IP-07 |
| IP-09 | IP-04、IP-08 |
| IP-10 | IP-09 |
| IP-11 | IP-04、IP-06、IP-10 |
| IP-12 | IP-05、IP-06、IP-11 |
| IP-13 | IP-10、IP-11 |
| IP-14 | IP-09、IP-12、IP-13 |
| IP-15 | IP-14 |
| IP-16 | IP-15 |
| IP-17 | IP-16 |
| IP-18 | IP-17 |

建议默认串行顺序：IP-01 → IP-02 → IP-03 → IP-04 → IP-05 → IP-06 → IP-07 → IP-08 → IP-09 → IP-10 → IP-11 → IP-12 → IP-13 → IP-14 → IP-15 → IP-16 → IP-17 → IP-18。
这是一条便于单 Agent 执行和审查的顺序；表中允许的独立分支不代表必须并行。

结构性关键路径为：IP-01 → IP-03 → IP-04 → IP-06 → IP-08 → IP-09 → IP-10 → IP-11 → {IP-12、IP-13 汇合} → IP-14 → IP-15 → IP-16 → IP-17 → IP-18。
IP-02→IP-07 和 IP-05 也须在 IP-08 前汇合；缺少工时实测，不能声称已经计算出按时长确定的最长路径。

```mermaid
flowchart LR
    C[IP-01 CI] --> D[IP-02 Domain]
    C --> DB[IP-03 MySQL]
    C --> T[IP-05 Template]
    DB --> A[IP-04 Login]
    D --> R[IP-07 Task repository]
    DB --> R
    A --> U[IP-06 Asset]
    DB --> U
    T --> U
    R --> E[IP-08 Execute]
    U --> E
    T --> E
    E --> S[IP-09 Accept]
    A --> S
    S --> Q[IP-10 Query and result]
    Q --> P[IP-11 Proxy and login UI]
    A --> P
    U --> P
    P --> F[IP-12 Upload UI]
    T --> F
    U --> F
    P --> H[IP-13 Task hooks]
    Q --> H
    F --> I[IP-14 UI integration]
    H --> I
    S --> I
    I --> K[IP-15 Compose]
    K --> V[IP-16 Regression]
    V --> M[IP-17 Materials]
    M --> O[IP-18 Human acceptance]
```

### 5.2 并行开发建议

默认先串行，先完成可运行契约及其测试。以下仅是**未来可选安排**，本轮不启动多个 Agent。

| 可选并行 | 必须满足的条件 | 冲突控制 |
| --- | --- | --- |
| IP-02 / IP-03 / IP-05 | IP-01 完成；领域/数据库/模板契约按已有设计锁定，无待变更的公共语义 | IP-02 仅 task Domain；IP-03 负责 POM/CI/schema；IP-05 负责模板/媒体。全局应用配置或依赖由指定单一任务写入，冲突时改串行 |
| IP-04 / IP-07 | IP-02/03 完成，两模块 Repository/装配边界已核对 | Identity 与 Task DAO/测试分目录；共用错误/配置由 IP-04 单一维护，不各自重写 |
| IP-12 / IP-13 | IP-11 的传输/Auth 契约和 IP-10 TaskView 已稳定且测试通过 | template/asset UI 与 task hooks 分目录；共用 lib、类型和路由组合由 IP-11/IP-14 管理 |

IP-08→09→10 的首次真实异步联调、IP-14 的页面整合、CI/lockfile/Compose 的同一文件修改保持串行。
若实现发现必须改 API/领域含义，先说明原因、兼容性和影响并由用户决定，相关任务恢复串行；不能靠各 Agent 私下修改契约消除冲突。
每个并行任务独立验证，汇合后再验证集成；不得拿单分支通过代替合并结果。

## 6. Requirements Traceability

下表保持 Requirement → 原 AC → 实现任务 → Verification；AC 原文以 requirements.md 为准，不重新定义。
V 的具体方法/预期在任务说明及第 7 节；V-01 实际证据见第 4 节，其余仍为计划、未执行。
每条 AC 至少有一项实现/交付任务和验证方式；“部分自动化”不等于已经通过。

| Requirement | Acceptance Criteria | Implementation Task | Verification / 关键观察 |
| --- | --- | --- | --- |
| ASMT-R-01 | AC-R-01-01 | IP-01、IP-17 | V-01/17：依赖、入口、版本及构建 |
| ASMT-R-01 | AC-R-01-02 | IP-03、IP-06～10 | V-03/06/07/08/09/10：Java 执行、MySQL 持久读写 |
| ASMT-R-01 | AC-R-01-03 | IP-01、IP-15、IP-17 | V-01/15/17：按 README 构建/启动 |
| ASMT-R-02 | AC-R-02-01 | IP-05、IP-11、IP-12 | V-05/11/12：Java 分类/列表/卡片/详情 |
| ASMT-R-02 | AC-R-02-02 | IP-05、IP-12 | V-05/12：不同分类、1/2 素材数 |
| ASMT-R-02 | AC-R-02-03 | IP-05、IP-15、IP-17 | V-05/15/17：实际合法资源及来源说明 |
| ASMT-R-03 | AC-R-03-01 | IP-05、IP-06、IP-12 | V-05/06/12：详情要求、预览/替换/移除 |
| ASMT-R-03 | AC-R-03-02 | IP-12、IP-14 | V-12/14：上传反馈、未就绪及提交防双击 |
| ASMT-R-03 | AC-R-03-03 | IP-05、IP-06、IP-09、IP-12 | V-05/06/09/12：前后端数量/类型/大小边界 |
| ASMT-R-03 | AC-R-03-04 | IP-06、IP-08、IP-15 | V-06/08/15：后台实际读文件、卷保留 |
| ASMT-R-03 | AC-R-03-05 | IP-05、IP-12 | V-05/12：不存在、加载/空/错误分别表达 |
| ASMT-R-04 | AC-R-04-01 | IP-07、IP-08、IP-09、IP-14 | V-07/08/09/14：提交后异步返回 ID，无伪快照 |
| ASMT-R-04 | AC-R-04-02 | IP-08、IP-10、IP-16 | V-08/10/16：关闭页面仍执行、重新读取 |
| ASMT-R-04 | AC-R-04-03 | IP-08、IP-09、IP-13、IP-14 | V-08/09/13/14：多项独立 ID/设置/记录 |
| ASMT-R-05 | AC-R-05-01 | IP-02、IP-08 | V-02/08：三个真实执行边界和调用顺序 |
| ASMT-R-05 | AC-R-05-02 | IP-03、IP-07、IP-08、IP-10 | V-03/07/08/10：持久状态/阶段/结论 |
| ASMT-R-05 | AC-R-05-03 | IP-02、IP-07、IP-08、IP-10、IP-14 | V-02/07/08/10/14：整理成功才成功，图片可读、终态保护 |
| ASMT-R-05 | AC-R-05-04 | IP-08、IP-10 | V-08/10：零查询完成，重复读无副作用 |
| ASMT-R-06 | AC-R-06-01 | IP-08、IP-09、IP-17 | V-08/09/17：三个指定失败可重现，后续零调用 |
| ASMT-R-06 | AC-R-06-02 | IP-02、IP-07、IP-08、IP-10、IP-14 | V-02/07/08/10/14：失败阶段/原因一致且持久 |
| ASMT-R-06 | AC-R-06-03 | IP-08、IP-09、IP-16、IP-17 | V-08/09/16/17：耗时调整和正常成功路径 |
| ASMT-R-07 | AC-R-07-01 | IP-12、IP-13、IP-14 | V-12/13/14：上传/提交/受理/执行状态分开 |
| ASMT-R-07 | AC-R-07-02 | IP-10、IP-13、IP-14 | V-10/13/14：展示服务端阶段/终态/原因 |
| ASMT-R-07 | AC-R-07-03 | IP-11、IP-13、IP-14、IP-16 | V-11/13/14/16：保留已知信息，手动恢复，无伪 FAILED |
| ASMT-R-08 | AC-R-08-01 | IP-07、IP-08、IP-13、IP-14 | V-07/08/13/14：后台及前端任务独立、乱序隔离 |
| ASMT-R-08 | AC-R-08-02 | IP-13、IP-14 | V-13/14：按任务停止，首次空/全部结束无空转 |
| ASMT-R-08 | AC-R-08-03 | IP-11、IP-12、IP-13、IP-14 | V-11/12/13/14：离页/身份变化清 timer/abort，不重启 |
| ASMT-R-08 | AC-R-08-04 | IP-11、IP-12、IP-13、IP-14、IP-16 | V-11/12/13/14/16：旧 success/error/finally 均失效，A→B→A |
| ASMT-R-09 | AC-R-09-01 | IP-07、IP-10、IP-14 | V-07/10/14：个人记录、时间、真实图片或失败原因 |
| ASMT-R-09 | AC-R-09-02 | IP-04、IP-10、IP-11、IP-13、IP-14、IP-16 | V-04/10/11/13/14/16：会话后读 Java，刷新恢复跟踪 |
| ASMT-R-09 | AC-R-09-03 | IP-07、IP-10、IP-13 | V-07/10/13：同源记录、自洽快照/版本、终态不退 |
| ASMT-R-10 | AC-R-10-01 | IP-04、IP-11、IP-17 | V-04/11/17：账号/会话/owner/记录关联及演示说明 |
| ASMT-R-10 | AC-R-10-02 | IP-04、IP-06、IP-09、IP-10、IP-11、IP-13、IP-14、IP-16 | V-04/06/09/10/11/13/14/16：A/B 服务端隔离及旧 UI 回调隔离 |
| ASMT-R-11 | AC-R-11-01 | IP-12、IP-14、IP-16 | V-12/14/16：列表/详情/历史主要参考布局和反馈 |
| ASMT-R-11 | AC-R-11-02 | IP-12、IP-14、IP-16 | V-12/14/16：桌面、小屏主流程人工操作 |
| ASMT-R-11 | AC-R-11-03 | IP-11、IP-12、IP-14、IP-16 | V-11/12/14/16：语义、标签、键盘与焦点 |
| ASMT-R-12 | AC-R-12-01 | IP-01、IP-03、IP-15、IP-17 | V-01/03/15/17：push/PR、测试/构建、CI 材料，远程结果单列 |
| ASMT-R-12 | AC-R-12-02 | IP-02、IP-08、IP-09 | V-02/08/09：实际顺序及三个失败中断测试进 CI |
| ASMT-R-12 | AC-R-12-03 | IP-01、IP-13、IP-14 | V-01/13/14：实际多任务、查询故障/执行失败测试进 CI |
| ASMT-R-13 | AC-R-13-01 | IP-03、IP-04、IP-05、IP-15、IP-17、IP-18 | V-03/04/05/15/17/18：源码/锁/结构/初始化/资源/CI，最终版本 |
| ASMT-R-13 | AC-R-13-02 | IP-03、IP-04、IP-05、IP-15、IP-17 | V-03/04/05/15/17：版本/连接/配置/初始化可复现 |
| ASMT-R-13 | AC-R-13-03 | IP-08～11、IP-14、IP-16、IP-17 | V-08/09/10/11/14/16/17：API、阶段和所有演示路径 |
| ASMT-R-13 | AC-R-13-04 | IP-15、IP-16、IP-17、IP-18 | V-15/16/17/18：实际限制/投入、部署与回滚说明 |
| ASMT-R-13 | AC-R-13-05 | IP-05、IP-17 | V-05/17：素材/复用来源、AI 工作流及实际修正说明 |
| ASMT-R-13 | AC-R-13-06 | IP-17、IP-18 | V-17/18：用户核对约定日期/渠道并授权交付 |
| ASMT-R-14 | AC-R-14-01 | IP-03、IP-04、IP-05、IP-15、IP-17 | V-03/04/05/15/17：已选推荐 Compose，初始化后实际业务闭环 |
| ASMT-R-14 | AC-R-14-02 | IP-17 | V-17：本项目已选 Compose，此条件分支不适用；仍交付必要启动/配置/初始化说明 |
| ASMT-R-14 | AC-R-14-03 | IP-15、IP-17 | V-15/17：解析/镜像/就绪/初始化/业务证据分列 |

覆盖审查：48 条 AC 均已分配；R-04～10 在 IP-02/06～14 及 V-16 联验，R-12 的行为测试分别由 IP-08 和 IP-13 提前落实；
R-13 的来源、实际投入、交付渠道没有被 build 代替；R-14 保持推荐与条件分支语义。当前没有未分配的强制需求。
IP-01～17 均服务当前 AC 或已确认的工程约束，IP-18 是用户交付关卡；没有 Track B 实施任务。

## 7. Verification Strategy

### 7.1 分层验证与执行入口

| 类型 | 主要任务 / V | 方法与可观察结果 |
| --- | --- | --- |
| Domain Unit Tests | IP-02/05；V-02/05 | JUnit 参数化边/组合/规则边界，不起 Spring；合法迁移成功、非法迁移拒绝 |
| Application Service Tests | IP-04/06/08/09/10；V-04/06/08/09/10 | Case 的受控协作者、latch/barrier、调用记录和事务观察；验证提交/副作用时序及失败后零调用 |
| Repository / MyBatis Integration Tests | IP-03/04/06/07；V-03/04/06/07 | MySQL 8.4 隔离库，真实 Mapper/SQL/事务；回滚、NULL CHECK、owner 和两执行者竞争 |
| API Tests | IP-04～06/09/10/11；V-04～06/09/10/11 | Spring 测试 + 真实 HTTP/受控上游，校验字段、编码、状态/错误、Cookie/CSRF/图片和代理一致性 |
| Frontend Component / Hook Tests | IP-11～14；V-11～14 | Vitest/RTL、fake timers、可控 Promise 和测试宿主；观察 UI/请求/清理行为，不镜像 reducer 实现 |
| Build and Static Analysis | IP-01 起持续；V-01/17 | Maven verify；npm ci、test:ci、lint、typecheck、build；真实类出现后检查层与模块依赖 |
| Docker Integration Verification | IP-15/16；V-15/16 | Compose config、镜像构建、三服务 SQL/HTTP 就绪、初始化及 frontend 入口业务 smoke 分层验证 |
| Manual Acceptance Testing | IP-12/14/16/18；V-12/14/16/18 | 桌面/小屏、键盘、真实图片、关页/刷新、浏览器共享会话、A/B 演示及用户最终验收 |

后端：普通 mvn --batch-mode --no-transfer-progress verify 跑快速测试/构建；IP-03 起另跑带 integration-tests profile 的 verify，
CI MySQL job 必须实际执行 *IT。前端：IP-01 起 npm run test:ci 为单次运行、非 watch，后续业务测试加入同一入口；
配合 npm ci、npm run lint、npm run typecheck、npm run build。根目录沿用 docker compose --env-file .env.example config --quiet，
它只使用脱敏样例验证配置；真正业务运行使用独立验证配置，不用示例密码冒充安全运行配置。

没有指定覆盖率百分比或 E2E 框架门槛。测试工具版本在 IP-01 安装前验证兼容性并精确锁定；不修改已有框架版本来迁就测试工具。
负向测试从外部边界/业务规则出发，不只断言静态文字或 getter；等待使用确定性同步和有限超时，避免长 sleep 导致脆弱测试。

### 7.2 关键风险场景的验收证据

| 场景 | 自动化重点 | 联合/人工确认 |
| --- | --- | --- |
| 状态机、非法转换、终态不回退 | V-02 穷举边/组合；V-07 旧写及成功/失败竞争 | V-16 查询/刷新终态保持 |
| 三阶段顺序、指定失败中断 | V-08 逐阶段调用记录、每个失败点无后续调用；V-09 模拟参数捕获 | V-16/17 实际命令演示及持久结果 |
| 多任务不串扰 | V-07/08 独立记录与本地执行参数；V-13 受控乱序响应 | V-14/16 连续制作不同结果 |
| 用户身份隔离 | V-04/06/09/10 owner/CSRF/API；V-11/13 epoch 和跨页通知 | V-16 真实 Cookie 的退出/登录及跨标签页 |
| 前端轮询生命周期 | V-13 fake timers 检查单项终态/空集合/首次空/无重叠 | V-16 浏览器观察请求数量 |
| 清理和旧响应隔离 | V-12/13/14 分别测试 timer/abort/revoke 和旧 success/error/finally/timeout | V-16 离页、A→B→A、返回焦点 |
| 查询异常与执行失败 | V-10 FAILED=200；V-13 查询503保留快照/暂停/恢复；V-14 图片失败独立 | V-16 暂停查询后后台完成，再恢复 |
| 刷新后任务恢复 | V-10 MySQL 同一历史；V-14 挂载重建从会话/Java 恢复 | V-15/16 容器环境处理中、成功/失败刷新 |
| 提交/文件/状态写故障 | V-06 文件成功DB失败；V-08/09 拒绝/0行/commit未知后不继续 | V-16/17 限制与实际行为一致，不承诺续跑 |

### 7.3 CI 逐步增加实际门禁

1. IP-01 保留既有构建并启用前端测试；该时刻业务测试覆盖仍为零，明确记录。
2. IP-02/05/08/09 的单元/Case 测试自动进入 Maven verify；IP-03 新增真实 MySQL job，以有意义的 schema 测试开始，IP-04/06/07 的 *IT 持续接入。
3. IP-11～14 的组件/Hook/代理测试进入 test:ci；IP-13 合并时必须已有 R-12-03 的实际核心测试。
4. IP-15 加容器业务 smoke；到交付时四类门禁均需有实际执行结果或明确阻塞，不允许新增空 job 作为通过证据。

不设置空测试放行、不用 skip/关闭规则换绿色、不启用自动部署。
远程 CI 未运行与本地命令失败是不同状态；远程权限/网络限制不能被写成业务验证通过。

### 7.4 证据与验收登记格式

每个任务完成报告至少包含：任务/AC/V ID、修改文件、命令与工作目录、环境/时间、实际断言和结果、报告路径、未验证项/风险。
自动化测试源码与报告定位到实际文件/用例；人工步骤记录输入、预期/实际、可复现证据；不在本计划伪造未来测试文件名或通过记录。
后续在任务下补实际记录，并保留下列最终验收登记字段：

| AC | 实现任务/版本 | 自动测试或人工步骤位置 | 实际结果/证据 | 审查人/日期 |
| --- | --- | --- | --- | --- |
| 各 AC | 对应 IP 任务与版本 | 第 6 节对应 V 及 ip16-regression.md 的 AC 登记 | 按实际记录分别填写通过/部分通过/未验证 | 用户最终验收另记 |

## 8. Definition of Done

### 8.1 单任务 DoD

任务 checklist 仅在以下适用项全部满足后勾选：

- 当前范围实现完整，直接依赖已有可用产物；对应 AC 和任务验收有实际证据，无空壳/假返回代替业务行为。
- 关键/负向行为测试随实现完成并接入指定入口；相关构建、静态和集成检查通过，报告命令/数量/失败/跳过情况。
- 检查真实依赖：Domain 无框架，Trigger 只调 Case，Cases 不跨域访问内部 Repository/DAO，Infrastructure 不拥有状态流程；DTO/PO 不越界。
- 事务、归属和旧写/旧响应约束符合已确认设计；无秘密、无任意路径/身份输入绕过，文档与实际行为一致。
- diff 仅含当前任务必要变化，保留用户工作；API/领域/核心架构变化已说明理由、影响并经过用户决策。
- 提交修改文件、测试/手工结果与剩余风险报告，完成当前需要的代码审查。用户保留架构决定、代码审查和最终验收权。

环境受阻时标为“实现完成，验证受阻/待审查”等具体状态，保留 [ ]，不得说已满足 DoD；修复阻塞后补跑。
文档规划完成不代表业务任务完成；最终验收/对外交付只能由 IP-18 用户确认。

### 8.2 统一 AI Coding 执行流程

1. 阅读当前 IP 任务、前置产物、对应需求/AC、设计及 AGENTS/相关 ddd-skill 指引；前端先读安装版本 Next 指引。
2. 检查相关源码、git 状态和已有测试，明确修改范围与用户已有变化。
3. 给出简短实现计划、可观察完成条件及会影响契约的假设；范围明确则直接推进，重大设计变化交用户决定。
4. 编写/更新必要行为测试；故障修复先重现，避免只照实现写断言。
5. 仅实现当前任务，按已选契约和依赖规则创建真实需要的代码。
6. 运行最小相关验证，再运行该任务要求的构建/集成检查；失败先定位，不隐藏结果。
7. 审查状态机、事务、模块依赖、归属/异步和前端生命周期是否越界，并检查 diff。
8. 报告修改文件、任务/AC/V 关联、命令/结果、未验证事项及剩余风险；材料不能暴露凭据。
9. 满足 DoD 才更新任务和证据状态；当前任务结束后由用户指定后续范围，不一次实现整项目。

禁止未经说明改公共 API/领域模型；禁止删除失败测试、关闭规则、空测试放行或跳过检查来通过；
禁止用未经验证结果宣称完成，或擅自加入新框架/基础设施/未来功能。遇到已确认范围内的普通实现选择可自行落实，
不反复请求已有授权；用户负责评审不等于每次文件编辑都需另行批准。

### 8.3 MVP 交付 DoD

R-01～13 所有强制 AC 逐条有证据，R-14 推荐/不适用分支如实登记；核心行为自动化在 CI 中可执行；
Java/MySQL 和浏览器/代理/文件闭环真实验证，容器证据分层；材料可独立复现，限制/投入/来源真实，用户完成最终验收与约定交付。
缺少远程 CI 运行时明确保留该项未验证，由用户据真实材料决定交付，不能伪造远程通过或自动把任务勾为完成。

## 9. Risks and Blockers

### 9.1 技术风险与处理任务

| 风险 | 处理 / 验证任务 | 当前状态 |
| --- | --- | --- |
| CI 需逐步补齐实际行为测试 | IP-01 测试入口/失败门禁；IP-03 MySQL、IP-15 容器逐步加强 | IP-01 本地及独立分支远程通过，用户授权自助验收完成；不把占位/上下文测试当核心覆盖 |
| MySQL NULL CHECK、Mapper 原子性、首次抢占竞争 | IP-03/07 真库负向与竞争，IP-08 资格后副作用 | schema/NULL、Mapper/事务/CAS 与执行资格后的副作用已验证，见 V-03/07/08/16 |
| 安全过滤器/Session/CSRF/Set-Cookie 跨代理组合 | IP-04/11 自动化 + IP-15/16 真实 HTTP/浏览器 | 已通过接口/代理与真实浏览器回归，见 V-04/11/16；保留相关回归门禁 |
| 受理提交后派发窗口及 DB 结果未知 | IP-08/09 故障注入并停止后续；IP-17 如实说明 | 接受不自动恢复，不能新增 MQ 掩盖范围 |
| 文件与元数据非原子、非 root 卷权限 | IP-06 失败处理，IP-15 卷/重启实际读取 | 孤立文件可存在，无自动清理 |
| 前端 epoch/requestSeq/revision 各有职责，容易只保护成功分支 | IP-12～14 控制 Promise/timer/收尾回调测试，IP-16 联验 | 关键自动化必须保留 |
| 当前模板只读/素材不删除是校验在事务外的前提 | IP-05/06/09 保持不变；新增编辑/删除前重审 | 不把未来需求加入当前任务 |
| Docker、依赖下载、远程 runner 权限/网络 | 按实际版本记录本地与远程运行 | 本地隔离容器、真库和当前已核对版本远程 CI 均通过；未来环境可用性不由历史结果保证 |
| 参考图/素材及 README 投入、渠道/日期须真实 | IP-05 记录合法资源，IP-16 参考已读取布局，IP-17/18 用户核实实际材料 | 不复制个人卷面 URL、不猜日期/工时 |

实施规划已完成，业务与验证已按 IP 任务落实；当前剩余事项主要是材料审查、投入总量、最终验收版本与约定交付。
历史未获首个任务授权或 Docker 未运行的记录不再作为当前阻塞；未来运行环境仍需按实际版本验证。

### 9.2 Senior Engineering Lead Review

| 审查问题 | 结论、取舍与后续要求 |
| --- | --- |
| 是否完整覆盖 Assessment | 第 6 节 48 条 AC 全覆盖，推荐/条件分支不误升格；IP-18 保留人工交付责任 |
| 粒度是否适合 AI Coding | 每项聚焦单一可验收产物；执行、受理、查询和前端状态分别拆分，避免整个 Task 模块一项做完。IP-04/06 边界较多，应按测试子步骤推进；若仍超出一次审查，可增加子任务但保留父编号/追踪 |
| 是否存在不合理依赖 | 必要依赖表无环；领域/SQL/模板可独立，第一次联调默认串行；不把全 UI 或未来设施设为后台前置 |
| 核心异步是否过晚 | IP-02 立即建状态机，IP-03/07 早做真库与 CAS，IP-08 首次执行、IP-09/10 完成后端闭环；UI 完善在之后 |
| 是否过度设计 | 仅四表、最小 Case/Port、现有单体；无额外事件/领域服务/工作流，验证工具服务真实风险；未强制覆盖率或大型 E2E |
| 自动化是否足够 | 状态机、顺序/失败、真库竞争、owner、前端乱序/清理/故障均有自动化，CI 分阶段接入；浏览器会话和可用性保留人工检查，不能仅靠 mock |
| 尚需警惕 | IP-01 的占位测试不算 R-12 业务覆盖；IP-15 容器门禁不能空跑；远程 CI/最终交付不能由本地构建替代；已接受崩溃窗口需贯穿报告 |

规划阶段结论：可进入逐任务实施，**第一项应为 IP-01**。随后已按用户授权执行该项，见 V-01；未自动启动后续任务。

## 10. Implementation Status

### 10.1 当前阶段与实施 checklist

实现进度、验证结果及详细记录入口统一见[当前进度](#当前进度)。本节仅保留勾选登记：T 项表示准备或验证工作完成，IP 项表示用户验收通过（含明确授权的自助验收）；未勾选不等于未实现。

- [x] T-01：需求分析。
- [x] T-02：领域与架构设计。
- [x] T-03：实施规划。
- [x] IP-01：CI 与测试入口。
- [x] IP-02：Task 领域模型与状态机。
- [ ] IP-03：数据库结构与真库测试。
- [ ] IP-04：预置账号、会话与 CSRF。
- [ ] IP-05：模板目录与打包媒体。
- [ ] IP-06：上传与本地素材存储。
- [ ] IP-07：任务仓储、本人查询与条件更新。
- [ ] IP-08：后台三阶段、Mock 与有界派发。
- [ ] IP-09：任务受理 API。
- [ ] IP-10：本人任务、历史与结果 API。
- [ ] IP-11：Next 转发、登录与身份作用域。
- [ ] IP-12：模板页面与上传交互。
- [ ] IP-13：任务快照合并与独立轮询。
- [x] IP-14：制作、历史、结果预览与演示设置。
- [ ] IP-15：Compose 初始化与持久化。
- [ ] IP-16：联合回归与缺陷修复。
- [ ] IP-17：README、截图与交付材料。
- [ ] IP-18：最终验收与约定交付。
- [x] T-04：容器与 MySQL 集成验证。

### 10.2 保留的历史任务：Scaffolding

| ID | 任务 | 依赖 | 验收标准 | 状态 |
| --- | --- | --- | --- | --- |
| S-01 | 检查已有项目并保留用户文件 | 无 | 记录现状，不覆盖已有内容 | 完成 |
| S-02 | 后端入口、依赖、配置和模块边界 | S-01 | Java 17 编译与上下文测试 | 完成；verify 和 JAR HTTP 检查通过 |
| S-03 | 官方 Next.js 空模板与工具链 | S-01 | lint/typecheck/build | 完成；检查与 standalone HTTP 检查通过 |
| S-04 | Dockerfile、Compose、环境样例 | S-02、S-03 | Compose 解析；容器运行单列 | 解析通过，运行未验证 |
| S-05 | 工程文档和 Agent 规范 | S-02、S-03 | 文档与实现一致，标明待定内容 | 完成 |
| S-06 | GitHub Actions 骨架 | S-02、S-03、S-04 | 工作流包含后端、前端、Compose 检查 | 配置已创建，远程未运行 |

### 10.3 保留的历史任务：后端架构重设计（ddd-skill 2.2.4）

下表 R-01～03 是历史任务编号，**不等于 ASMT-R-01～03**。

| ID | 任务 | 依赖 | 验收标准 | 状态 |
| --- | --- | --- | --- | --- |
| R-01 | 对照技能设计分层优先的后端 | S-02、S-05 | Domain 内按业务划分；明确纯领域与基础设施依赖规则 | 完成，见 ADR-006 |
| R-02 | 迁移包说明并同步开发规范 | R-01 | 不遗留平行旧包结构，不添加业务类或依赖 | 完成；包路径和快照改动范围审查通过 |
| R-03 | 验证架构调整后的工程 | R-02 | Java 17 clean verify；只检查有实际对象的边界 | 完成；1 test 通过，JAR 打包成功；业务依赖规则待真实代码验证 |

### 10.4 保留的需求/设计准备任务

| ID | 任务 | 依赖 | 验收标准 | 状态 |
| --- | --- | --- | --- | --- |
| T-01 | 读取完整 Assessment 并逐项整理需求 | Assessment 原文（已取得） | 每项强制要求可追溯，具备 AC、职责依赖及验证计划 | 完成；见 requirements.md，业务验收未执行 |
| T-02 | 确定业务流程、身份隔离、API 与持久化模型 | T-01 | requirements/API/ADR 对齐 | 设计完成，2026-10-30；见详细架构及配套设计文档，实施/验收未开始 |
| T-03 | 切分最小业务纵切并补充测试计划 | T-02 | 任务有依赖、可观察验收标准 | 规划完成，2026-10-30；实施未开始 |
| T-04 | 运行容器并验证 MySQL 集成 | 可用的 Docker daemon | 镜像构建、三个服务启动及真实数据库检查 | 本地验证通过：IP-03 schema、IP-15 三服务/初始化/业务/持久化；远程 CI 与用户验收另记 |

### 10.5 验证记录与状态解释

历史工程命令、结果与环境限制见 [README.md](../README.md)。它们不代表业务行为或本轮重新验证通过。
规划阶段只检查 AC/任务/Verification 覆盖、依赖无环、文档链接/表格/空白以及文件改动范围，未执行业务验证。
随后 IP-01 的实际构建、测试、失败注入、自审及独立分支远程验证结果见第 4 节 V-01，不覆盖其他任务的验收。
