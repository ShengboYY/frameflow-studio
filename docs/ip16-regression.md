# IP-16 联合回归记录（V-16）

日期：2026-10-31，Asia/Shanghai。基线 `a6214a5` 加本次工作区变更。
本次执行本地回归、补充验证脚本并修复一处容器构建竞态；不修改业务 API、领域模型、数据库结构或依赖版本。
最终用户审查及整体交付仍归用户；IP-17 材料准备、IP-18 最终交付不在本次完成范围内。

## 1. 环境、命令与证据

- macOS arm64；Docker Engine 29.0.1、Compose 2.40.3；容器 Java 17 / Node 22 / MySQL 8.4。
- 宿主 Maven 3.9.10 / JDK 17.0.12；前端 Node 22.21.1 / npm 10.9.4。
- 浏览器通过 CUA 原生 Chrome 操作。Work 设置禁用浏览器接口，因此没有用它声称完成自动浏览器测试。
  专用隐身窗口与本次随机 Compose 项目共享环境；没有访问用户数据库、卷或应用进程。
- 浏览器环境项目 `frameflow-smoke-e172f70d00a09955`，仅发布随机 loopback 端口，已退出演示账号并关闭专用窗口。

| 工作目录 / 命令 | 实际结果 | 证据 |
| --- | --- | --- |
| `frontend/`：`npm ci --offline --cache /private/tmp/frameflow-npm-cache --no-audit --no-fund` | PASS | `/private/tmp/frameflow-ip16-npm-ci.log` |
| `frontend/`：`npm run test:ci` | PASS，16 files / 274 tests | `/private/tmp/frameflow-ip16-frontend-tests.log`；`frontend/test-results/junit.xml` |
| `frontend/`：`npm run lint`、`npm run typecheck`、`npm run build` | PASS，含 standalone postbuild | `/private/tmp/frameflow-ip16-{lint,typecheck,build}.log` |
| `backend/`：`mvn --offline -Dmaven.repo.local=/private/tmp/frameflow-m2 --batch-mode --no-transfer-progress -Pintegration-tests verify` | PASS，404 项快速测试 + 47 项 MySQL 集成测试，零失败/错误/跳过 | `/private/tmp/frameflow-ip16-backend.log`；`backend/target/{surefire,failsafe}-reports/` |
| 根目录：`python3 -m unittest discover -s scripts -p 'test_*.py' -v` | PASS，12 tests | `test-results/ip16-script-tests.log`；`scripts/test_compose_smoke.py`、`scripts/test_acceptance_regression.py` |
| 根目录：`docker compose --env-file .env.example config --quiet` | PASS | 仅证明解析，真实运行另列 |
| 根目录：`python3 scripts/acceptance-regression.py --browser-review` | API 回归通过，浏览器检查后正常释放并清理 | `test-results/ip16-browser-environment.log`；`/private/tmp/frameflow-ip16-console.log` |
| 根目录：`python3 scripts/acceptance-regression.py` | PASS，默认 build、IP-15 全基线与 R1～R5，退出码 0；专用资源已清理 | `test-results/ip16-regression.log`；`/private/tmp/frameflow-ip16-final-console.log` |
| 根目录：默认 `docker compose up --build -d --wait`（新专用项目，原先无应用镜像标签） | PASS，无重复标签导出或远程拉取，初始化及 frontend 就绪；清理完成 | `test-results/ip16-up-build.log`；`/private/tmp/frameflow-ip16-up-build-console.log` |
| GitHub Actions 静态检查 | PASS，已有 actionlint 1.7.12 镜像、禁用网络只读检查；非远程运行 | 本轮工具退出码 0 |
| 源码边界与 diff | 122 个 Java 文件导入检查及关键对象人工审查；diff 空白检查通过 | `test-results/ip16-boundaries.txt`；下文第 4 节 |

MySQL 集成测试在同一专用容器中新建独立空库 `frameflow_it_ip16`，不使用 smoke 的业务库；临时启动器为
`/private/tmp/frameflow-ip16-mysql-verify.py`。首次启动器误将 Compose 的 `SESSION_COOKIE_SECURE=false` 带进 Maven，
导致检查默认 Secure 的测试失败；隔离两套环境变量后完整重跑通过，没有修改或跳过该测试。

报告、构建产物与临时浏览器连接文件均被 Git 忽略；连接文件权限为 0600，只包含本次 Compose 变量和一次性凭据，结束后删除。
不在本文、截图或业务日志中保存密码、Cookie、CSRF。

## 2. 可重复的容器回归

`scripts/acceptance-regression.py` 复用 IP-15 的 `Smoke`，先完整运行初始化、成功业务、权限、down/up 持久化检查，随后验证：

1. 三个失败阶段与正常成功任务连续受理；分别持久化 `FAILED/CHECK_ASSETS/revision=2`、
   `FAILED/GENERATE/revision=3`、`FAILED/FINALIZE/revision=4` 和 `SUCCEEDED/FINALIZE/revision=4`。
   失败结果入口为 409；终态重复读取逐字段保持不变。副作用调用顺序及失败后零调用另有真实 Case 测试。
2. 1200ms 每阶段耗时的任务受理时仍未结束，观察三阶段有序推进；修改其他任务设置不改变它。
3. 提交后不再读取任务 HTTP，直接以容器 SQL 观察完成，再新登录并读取 Java 历史，证明查询不驱动后台执行。
4. 单图/双图模板、同素材重复占两个位置、两个模板不同结果 JPEG；A/B 分别创建且无法读取对方输入、任务、结果。
5. 空素材、超范围耗时、他人素材、未知图片类型、截断 JPEG、超过模板大小分别被拒绝；拒绝前后任务行数不变。
6. 同 Cookie jar 退出 A、登录 B 后重用 A 的旧 CSRF 提交为 403，读取 A 的任务为 404；不重放写请求。
7. 自动收集实际方法/路径，核对 API-01～14 全部被覆盖；不是只检查接口名称是否出现在源码中。

脚本默认运行完即清理，适用于 CI；`--browser-review` 可保留同一环境供原生浏览器联合检查，最多 45 分钟。
读取本地 `test-results/ip16-browser.json` 后进行检查，完成时创建 `test-results/browser-review.done`。
脚本在正常退出、异常、SIGTERM 时仅清理本次随机项目及镜像标签；硬杀进程仍可能留下资源，按确切项目名和标签清理，禁止全局 prune。
浏览器等待结束不自动认定人工步骤通过，人工实际结果单独登记。

## 3. 本轮原生 Chrome 记录

以下均连接真实生产 Next / Java / MySQL 容器，没有用拦截的成功响应代替业务。
证据是本轮 CUA accessibility 状态及截图工具记录；截图未另存到仓库，不虚构本地图片路径。

| ID | 操作 / 实际观察 | 结果 |
| --- | --- | --- |
| B-01 | 用户名、密码、Tab、Enter 登录 A；显示本人历史与真实成功图片；账户框初始焦点在关闭按钮，Esc 后返回账户按钮 | PASS |
| B-02 | 第二标签页选择 `portrait-source.jpg`，真实上传至 1/1；切换标签页后素材仍保留 | PASS |
| B-03 | 每阶段 30 秒，键盘提交；20:28:22 的肖像任务进入素材检查；刷新后从 Java 恢复相同记录并继续跟踪 | PASS |
| B-04 | DevTools 将该标签页设为 Offline，Network 显示 `ERR_INTERNET_DISCONNECTED`；页面保留处理中/模拟生成并显示“自动查询已暂停”。后台继续执行；恢复 No throttling，点击“重新查询状态”显示成功及结果图片 | PASS |
| B-05 | 第一标签页退出 A，第二页素材/结果同步清除；第二页首次登录 B 成功，仅 1 条本人记录，第一页面同步 B；退出 B 后第一页面首次登录 A，恢复其 8 条记录 | PASS |
| B-06 | 390×844 分类切到旅行，Tab/Enter 进入双图模板；从返回链接继续 Tab 到上传位，Enter 打开系统文件选择器，用方向键选择 coast/town 两图，显示 1/2、2/2，未齐时提交禁用 | PASS |
| B-07 | 小屏键盘展开演示设置，15 秒/阶段成功任务于 20:35:42 受理；随后设置生成失败、0 秒并于 20:36:01 再次提交，前任务继续模拟生成，后任务独立失败 | PASS |
| B-08 | 关闭制作标签页，刷新另一页历史；恢复前任务处理中与后任务 GENERATE 失败；稍后刷新，前任务成功、后任务失败及创建时间保持，成功 JPEG 可见 | PASS |
| B-09 | 390、768、1440 宽度的真实历史截图，390 小屏目录/上传/提交与上下排列实际操作；未见明显横向溢出。原生桌面制作页左创作、右任务布局已观察 | PASS（目视检查） |
| B-10 | 所有任务终态后，Network 保持记录期间无新的任务自动读取；最终历史页 Console 为 0 messages；最终退出后私有记录清空，关闭专用隐身窗口 | PASS |

旧请求不可取消但仍晚到的 success/error/finally/timeout，以及旧图片成功/403、离页后图片返回与 Blob 回收，
由本轮重跑的 `task-controller.test.ts`、`use-task-tracking.test.tsx`、`production-ui.test.tsx`、
`upload-inputs.test.tsx` 和身份测试中的受控 Promise 验证。
没有声称在真实浏览器中强制投递不可取消的旧响应，也没有把共享 Cookie 的 HTTP 校验冒充浏览器手工重放旧 token。
该分工保留“清理资源”与“拒绝旧响应”两个独立断言。

没有执行屏幕阅读器、完整 WCAG 审计或 Core Web Vitals 测量；这些不属于本次 ASMT-R-11 的新增门槛。
没有提交的像素基线，视觉基线比较为 INCONCLUSIVE；以上 PASS 仅指已观察的布局和实际操作。

## 4. 故障证据与架构审查

| 对象 / 边界 | 本轮核对与自动化证据 |
| --- | --- |
| 队列拒绝 | `ExecutorTaskDispatcherPortTest.boundedQueueRejectsWithoutCallerRunsAndRetainsEachEnvelopeAfterCallerReturns` 用实际有界执行器/latch 证明拒绝不在调用线程执行；`CreateProductionTaskCaseTest` 验证提交后派发、拒绝保存及未知结果不重派发 |
| 文件 / 元数据失败 | `AssetCasesTest.insertOrCommitFailureDoesNotReturnAssetAndCompensatesStableFile`、`failedCleanupPreservesOriginalFailureAndUnknownTemplateDoesNoIo`；`LocalAssetStoragePortTest`、`AssetIT` 验证存储边界。使用隔离协作者故障注入，没有破坏容器业务库 |
| 状态写失败 / 提交未知 | `ExecuteProductionTaskCaseTest` 的 0 行竞争、读写异常、未知 commit、失败保存异常测试；真实 `ExecutionIT` / `TaskIT` 验证 SQL 竞争和执行资格；失败后不继续外部副作用 |
| Domain / Trigger / Cases | 122 个 Java 文件导入扫描未发现 Domain 依赖框架/外层、Trigger 直访 Domain/Infrastructure、Cases 依赖 HTTP DTO/Infrastructure 或跨域内部 Domain；并核对当前 Controller、Case 和适配器中的实际调用 |
| 跨模块能力 | 创建任务通过 template/asset Case，执行通过 asset Case，读取结果先查询本人的成功任务，再调用媒体能力；不穿透另一域仓储/DAO |
| 事务与副作用 | `CreateProductionTaskCaseImpl`、`ExecuteProductionTaskCaseImpl`、`UploadAssetCaseImpl` 保持短事务；创建提交成功后派发，阶段状态提交后才执行 I/O；等待/文件/生成不在长事务中 |
| 条件更新 / 只读查询 | `ProductionTaskRepository` 与 `production_task_mapper.xml` 核对 revision、预期状态/阶段和非终态条件；任务/结果 owner 查询由 Java 限定，查询入口不推进任务 |
| Next / 前端作用域 | 14 项代理白名单、运行时上游、逐条 Cookie/no-store；每页 controller、身份 epoch、请求序号、任务 revision 与结果 Blob 各有独立职责；组件/代理测试完整重跑 |

仍接受已确认限制：提交后派发前崩溃窗口、DB 提交结果未知时停止、不自动重试/重启续跑；内存会话重启失效；
上传文件与元数据非原子，清理失败可能遗留文件；模板只读、素材无删除；不新增恢复队列或取消 API。

## 5. 本轮缺陷与修复

默认 `docker compose build` 曾在 backend 与 accounts-init 并发导出相同镜像标签时失败：
`image ...-backend:latest: already exists`。复现日志 `/private/tmp/frameflow-ip16-build-race.log`。
`compose.yaml` 现仅由 backend 构建该标签，accounts-init 仅引用同一 image，并显式禁止拉取该本地构建标签；初始化命令、环境和依赖顺序不变。
最终回归使用默认 build 路径验证修复，不依赖手工串行构建掩盖竞态。另用新项目 `frameflow-smoke-4db55e4a2f8d3052`，在原先不存在应用镜像标签时执行 README 的 `up --build`，初始化与服务就绪通过，专用资源已清理。

新增脚本首先遇到 `Path.open(opener=...)` API 使用错误；改为内置 `open` 后完整容器重跑通过。
另补 owner-only 文件、拒绝覆盖既有连接及仅清理本次交接文件的测试，最终脚本测试 12/12 通过。
该错误发生在全部 API 回归完成后的浏览器交接阶段，原失败进程非零退出并清理了专用资源；
记录 `/private/tmp/frameflow-ip16-first-console.log`。没有将首次失败记为整体验证通过。

## 6. AC 登记

下表的“通过”表示本轮列出的本地证据，不代替用户验收。材料、远程 CI 和最终交付的未验证项明确保留。

| AC | 本轮结果 | 实际证据 / 后续边界 |
| --- | --- | --- |
| AC-R-01-01 | 通过 | 依赖/入口审查、前后端构建、真实 MySQL IT |
| AC-R-01-02 | 通过 | R1/R2、容器 SQL、Java 历史与 B-03/08 |
| AC-R-01-03 | 部分通过 | 版本与现有运行方式已实际使用；干净源码按完整 README 复现归 IP-17 |
| AC-R-02-01 | 通过 | R3、B-06 的真实分类与键盘模板入口 |
| AC-R-02-02 | 通过 | 单图/双图、creative/travel 两类及服务端规则 |
| AC-R-02-03 | 通过 | 打包封面/结果真实字节读取、B-01/08 预览 |
| AC-R-03-01 | 通过 | B-02/06、本轮 upload-inputs 预览/替换/移除测试 |
| AC-R-03-02 | 通过 | B-06、production-ui/upload-inputs 的上传/失败/同步提交 gate |
| AC-R-03-03 | 通过 | R4、AssetApiTest / TaskApiTest 及模板/上传组件测试 |
| AC-R-03-04 | 通过 | 容器 multipart→Java 文件→任务受理；B-02/07 |
| AC-R-03-05 | 通过（组件/API） | template-ui.test.tsx 的加载、空列表、未知模板、分类/详情请求故障与恢复；本轮浏览器检查正常目录，未逐项注入目录故障 |
| AC-R-04-01 | 通过 | R1 受理时未完成，B-03/07 受理后按钮可再次提交 |
| AC-R-04-02 | 通过 | R2 无 HTTP 查询完成、B-08 关闭制作页后继续执行 |
| AC-R-04-03 | 通过 | R1 四项独立任务 ID，B-07 连续提交不同设置、B-08 恢复独立结果 |
| AC-R-05-01 | 通过 | R1 三阶段实际观察、ExecuteProductionTaskCaseTest / ExecutionIT |
| AC-R-05-02 | 通过 | MySQL IT、R1 快照/失败/结果，B-01/08 |
| AC-R-05-03 | 通过 | R1 终态重复读取、实际结果字节，B-08 终态刷新 |
| AC-R-05-04 | 通过 | R2 无任务 HTTP 查询推进；只读查询代码与 Case 测试 |
| AC-R-06-01 | 通过 | R1 三个固定失败及 revision、Case 后序零调用 |
| AC-R-06-02 | 通过 | R1 持久失败字段、B-01/07/08 原因和环节 |
| AC-R-06-03 | 通过 | R1 1200ms、B-03 30 秒、B-07 15/0 秒独立设置 |
| AC-R-07-01 | 通过 | B-02/06/07 与 production-ui 提交/任务双状态测试 |
| AC-R-07-02 | 通过 | B-03 阶段、B-01/08 真实成功/失败 |
| AC-R-07-03 | 通过 | B-04 浏览器 Offline→暂停保留→手动恢复；controller 的503/timeout |
| AC-R-08-01 | 通过 | R1、B-07 两项不同设置；controller 乱序和跨任务 revision |
| AC-R-08-02 | 通过 | B-03/08 自动跟踪、B-10 终态停止；空集合/首次空/无重叠自动化 |
| AC-R-08-03 | 通过 | B-05/08、controller.stop / hook 卸载 timer/abort/subscription 测试 |
| AC-R-08-04 | 通过（受控乱序） | controller A→B→A 旧 success/401/503/timeout、上传/图片晚到；本轮浏览器未强制不可取消旧包 |
| AC-R-09-01 | 通过 | B-01/08/09 时间、状态、原因和真实成功图片 |
| AC-R-09-02 | 通过 | B-03 处理中刷新、B-08 关页/终态刷新、容器 down/up |
| AC-R-09-03 | 通过 | R1/R2 历史和单任务一致，终态重复读取及 B-08 |
| AC-R-10-01 | 部分通过 | 按已有配置初始化并登录恢复；完整材料核对归 IP-17 |
| AC-R-10-02 | 通过 | R3/R4 双向 owner/旧CSRF、B-05 共享 Cookie A→B→A；旧回调另有受控证据 |
| AC-R-11-01 | 通过（目视） | 与 ui-layout-review.md 布局约定核对，真实桌面与小屏 CUA 截图 |
| AC-R-11-02 | 通过 | B-06/07 小屏主流程、390/768/1440 布局观察 |
| AC-R-11-03 | 通过（基本操作） | B-01 焦点返回，B-06/07 Tab/Enter/方向键文件选择与提交；非完整 WCAG 审计 |
| AC-R-12-01 | 本地通过；远程未验证 | push/PR 四 job 配置保留，compose 改跑扩展回归；本次未推送/触发新远程运行 |
| AC-R-12-02 | 通过 | 404 项快速测试含真实三阶段顺序/失败后零调用，47 项 MySQL IT |
| AC-R-12-03 | 通过 | 274 项前端测试含多任务隔离、查询故障/执行失败分离与恢复 |
| AC-R-13-01 | 部分通过 | 源码/锁/SQL/资源/CI 文件已检查；干净源码副本及候选交付版本归 IP-17 |
| AC-R-13-02 | 部分通过 | 本轮使用现有命令/配置；材料一致性和可复制说明归 IP-17 |
| AC-R-13-03 | 部分通过 | API-01～14 实测、成功/失败/耗时/刷新/身份已演示；完整复制命令归 IP-17 |
| AC-R-13-04 | 部分通过 | 本文登记真实限制；投入由用户核实，部署/回滚材料归 IP-17 |
| AC-R-13-05 | 部分通过 | 本文记录 AI 拆解、故障重现和修正，现有 SOURCES/PROMPTS 已有来源；完整交付说明归 IP-17 |
| AC-R-13-06 | 未验证 | 约定日期/沟通渠道/实际交付归用户与 IP-18，不自动发布或发送 |
| AC-R-14-01 | 通过（本地容器） | 空卷初始化、生产镜像、frontend 业务、down/up 持久化，全部为本次专用项目 |
| AC-R-14-02 | 不适用 | 已选择 Compose；不走无容器交付分支 |
| AC-R-14-03 | 通过（本地容器） | 解析、构建、就绪、初始化、业务与持久化分列；远程新版本另记 |
