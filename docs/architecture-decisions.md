# 架构决策记录

## ADR-001：单工程模块化单体与适度 DDD

- 状态：单工程与适度 DDD 原则保留；原 feature-first 包结构由 ADR-006 替代，2026-10-29。
- 背景：Assessment 需要简单可靠的交付，同时未来存在 AI 素材平台扩展方向。
- 决策：一个 Maven 工程、一个 Spring Boot 进程，按 task/template/asset/identity 分包；
  用包说明标识边界，按真实复杂度逐步添加四层代码。
- 替代：全局 controller/service/mapper 目录；多 Maven 子模块；微服务。
- 权衡：包边界目前靠开发规范和代码审查维护，没有构建级强制隔离；减少初始化复杂度。
- 演进：先稳定聚合和协作接口；出现实际依赖泄漏时再考虑架构测试或构建模块拆分。

## ADR-002：选择兼容且仍维护的技术版本线

- 状态：已采纳，2026-10-29。
- 决策：Java 17、Spring Boot 4.0.9、MyBatis Starter 4.0.1、MySQL 8.4 LTS；
  Next.js 16.4.3 官方空模板、模板配套 React 19.3.0、TypeScript 5、ESLint 9、Node.js 22 LTS。
  npm 直接依赖使用精确版本并提交 lockfile；MySQL Connector/J 等由 Boot BOM 统一管理。
- 原因：Boot 4.0 是仍维护的成熟分支，MyBatis 4.0 对应 Boot 4.0，Java 17 满足二者要求；
  Next.js 16 为受支持版本线，Node 22 满足最低版本要求。
- 替代：Boot 3.5 + MyBatis 3.0；更近的 Boot 4.1 / MyBatis 4.1；Next.js 15。
- 权衡：避免新建项目依赖已退出活跃维护的 Boot 3.5，也不必为本次骨架追逐更新的 Boot 主线。
  Boot 4 的模块化 starter 使用 `spring-boot-starter-webmvc`，不照搬旧版测试包位置。
  不引入 Lombok、H2、Actuator、Validation 或 OpenAPI，直到真实实现需要它们。
  IP-02 review 时用户要求用注解简化样板代码，现引入 Boot BOM 管理的 Lombok，仅作编译期代码生成，
  使用 @Getter 保持既有读取接口；不生成绕过领域校验的构造器或状态 setter，不进入运行时 JAR。
- 演进：维护补丁与安全更新；升级时重新检查支持状态及集成测试，不能把当前版本永久冻结。
- 已知工具链限制：脚手架使用 ESLint 9.39.5，该版本线已 EOL；当前配套的
  `eslint-plugin-react@7.37.5`、`eslint-plugin-import@2.32.0`、
  `eslint-plugin-jsx-a11y@6.10.2` peer 范围尚不包含 ESLint 10。
  本次保留脚手架兼容组合，不使用 `--force`/`--legacy-peer-deps` 绕过约束。
  上游插件支持后升级 ESLint 10；当前这是开发工具维护风险，不应描述为全部依赖均在维护。
- 依据：[Spring 支持版本](https://github.com/spring-projects/spring-boot/wiki/)、
  [Boot 4.0 系统要求](https://docs.spring.io/spring-boot/4.0/system-requirements.html)、
  [MyBatis 兼容表](https://mybatis.org/spring-boot-starter/mybatis-spring-boot-autoconfigure/)、
  [Next.js 支持政策](https://nextjs.org/support-policy)、
  [Next.js 16 运行环境](https://nextjs.org/docs/app/guides/upgrading/version-16)。
  ESLint 维护状态见[官方版本支持政策](https://eslint.org/version-support/)。

## ADR-003：外部能力随实际用例引入 Port

- 状态：按需引入原则保留；Port 位置规划由 ADR-006 更新，接口签名仍待讨论。
- 决策：记录 MediaGenerator、AssetStorage、TaskDispatcher 的潜在边界，不创建空接口或适配器。
  Repository 抽象归 Domain，编排所需的外部能力 Port 通常归 Application，具体实现归 Infrastructure。
- 替代：现在设计完整通用 Provider 系统、文件存储框架或消息队列抽象。
- 权衡：未来首次业务实现仍需设计接口，但避免对未确认流程固化错误抽象。
- 演进：Mock/本地文件/Spring executor 优先；真实外部服务通过适配器替换。

## ADR-004：数据库配置与无数据库的基础测试

- 状态：已采纳。
- 决策：运行环境通过变量配置 MySQL，test profile 使用明确的测试占位值；
  基础测试验证 Web 上下文和 MyBatis/数据源装配，不调用数据库。
  不排除数据源自动配置，也不添加替代数据库以掩盖 MySQL 差异。
- 替代：测试依赖开发机数据库；用 H2 代替；直接关闭 MyBatis 自动装配。
- 权衡：基础构建无需 Docker，但测试无法验证 SQL、账号或数据库可达性。
- 演进：出现 Mapper/SQL 后增加真正的 MySQL 集成验证；迁移工具及容器测试方案届时确定。

## ADR-005：缺失题面时保留业务决策

- 状态：历史决策；题面缺失的前提已于 2026-10-29 解除，需求分析已完成，见 requirements.md。
- 初始化阶段决策：只交付 scaffolding；当时不制定 API、状态机、身份传递规则、表模型和种子模板。
- 当时原因：初始化规范提到 Assessment，但附件未包含完整题面。
- 当前状态：R-01～R-14 已映射到需求、AC 和验证计划；本记录不再作为等待题面或阻止业务设计的依据。
  代码仍为骨架，业务实现和业务验收尚未开始，不能宣称已满足业务验收。
- 后续待设计：身份机制、任务状态表示、素材校验/存储细节、API、前端路由、持久化及模块协作契约。

## ADR-006：基于 ddd-skill 改为分层优先的六边形包结构

- 状态：已采纳，2026-10-29；用户明确要求重新设计现有后端。
- 来源：`ddd-skill` 2.2.4 的 SKILL.md、references/architecture.md、project-structure.md、
  case-layer.md、port-adapter.md、naming.md 与 domain-design-guide.md。
- 背景：用户希望按照技能的 Domain/Case/Infrastructure 模式重新组织后端，并在 Domain
  内聚合 task/template/asset 等业务概念。当前无业务实现，调整不涉及公共 API 或数据迁移。

### 决策

1. 保持一个 Maven 工程和一个可执行 Spring Boot 应用，Java 根包、依赖版本和产物路径不变。
   顶层使用 `trigger`、`api`、`cases`、`domain`、`infrastructure`、`types`。
2. 将原 task/template/asset/identity 包说明迁到 `domain.<业务>`，具体领域模型和接口随用例添加。
   各层实际代码继续按业务名归属；这些候选边界尚未证明是独立限界上下文。
3. 原 Interfaces 对应 Trigger + API，原 Application 对应 Cases。API 是数据契约，不是服务调用链
   的执行节点。Trigger 转换 HTTP DTO 后调用 Case；所有业务入口统一经过 Case。
4. 领域 Repository/查询契约位于 `domain.<业务>.adapter.repository`；生成/素材存储等业务能力
   契约位于 `domain.<业务>.adapter.port`。Case 可以通过这些纯接口协调用例，不直接接触 DAO。
5. 按技能主文档固定实现位置：`infrastructure.adapter.repository`、`adapter.port`、
   `dao`、`dao.po`、`gateway`、`gateway.dto`、`config`，内部按业务分组。
   不使用 persistent 等平行包，不创建未使用的 redis/MQ 子包。
6. 原 shared 不再作为杂项入口；最小公共纯类型归 types，技术装配归 infrastructure.config，
   业务对象归所属域。根启动类保留，默认扫描无需额外配置。

### 对技能的显式适配及原因

- **不运行新项目脚本**：本次修改已知路径中的现有工程，不是创建新 DDD 项目；避免覆盖已有内容。
- **不直接拆七个 Maven 子模块**：技能提供的是参考布局，当前只有骨架。空子模块增加 POM、
  Docker 和 CI 维护成本；现阶段包级边界足够，尚无编译级强制隔离。
- **使用 cases 包**：Java 的 case 是关键字；采用技能 project-structure/case-layer 的合法写法。
- **Domain 保持无框架**：原项目明确要求 Domain 不依赖 Spring/MyBatis/HTTP，故不照搬部分示例
  中的 @Service、Lombok @Data、线程池注入和通用 setter；领域服务按需由外层装配。
  IP-02 review 后按用户要求允许 Lombok 编译期 @Getter；构造校验和行为方法保留，
  通过 lombok.config 禁止生成 Lombok 字节码注解，编译后领域类仍仅依赖 Java 与本域。
- **应用技术 Port 仍归 Cases**：TaskDispatcher 属于执行编排机制，不是领域业务能力。
  其契约按需放 `cases.task.port`，实现仍在 infrastructure.adapter.port。
  仅为实现此类输出契约时，允许 Infrastructure 依赖相应 Case Port，不允许依赖 Case 实现。
- **本地 Port 不造 Gateway**：AssetStorage 的本地文件实现、Mock Generator、executor 不涉及
  HTTP/RPC，可在 adapter.port 内使用本地机制；真实远程调用才增加 Gateway/外部 DTO。
  这明确放宽技能“adapter/port 只能调用 gateway”的限制，保留可替换能力且减少无意义包装。
- **不按数量强制引入模式**：原要求强调避免过度设计；三个校验不自动意味着责任链，
  if 分支不自动意味着策略框架，长方法先按真实职责拆分。复杂度出现后再评估。
- **聚合维持行为和一致性**：不将技能中的聚合数据容器、CommandEntity 命名机械应用于用例请求；
  聚合根不与同内容 Entity 重复建模。
- **入口保持一致**：技能允许简单场景 Trigger 直达 Domain，本项目保留最初 Interfaces 经过
  Application 的约束；薄 Case 即可，不为简单查询强制增加领域服务或聚合。

### 替代方案与权衡

- 继续 feature-first：单个业务所有层更集中，但与用户此次要求的技能布局不一致。
- 逐字生成技能完整脚手架：可以建立构建模块边界，但将引入本次不需要的模块、代码和配置。
- 当前方案保持依赖倒置和业务所有权，同时接受同一业务代码分布在多个顶层职责包的导航成本。
  通过各层一致的业务子包名、明确契约和后续有实际对象的依赖测试控制耦合。

### 迁移范围与验证

- 仅迁移包说明、增加层职责说明，更新 architecture、AGENTS、README、API 文档归属和任务追踪。
- 不实现业务类、端点、表、生成/存储/调度能力，不更改数据库连接配置、前端、Dockerfile 或 CI。
- 使用 Java 17 执行 `mvn clean verify`，检查旧路径/旧规范引用与文件改动范围。
  编译和现有上下文测试不证明将来业务代码自动遵守包依赖，也不验证真实 MySQL 或容器。

### 后续演进

依据已完成的需求分析确认聚合与限界上下文、接口签名和事务语义；首次真实业务落地后补领域测试、
MySQL 集成验证与必要的依赖规则测试。只有实际边界维护成本需要时才拆分 Maven 模块。

## ADR-007：预置账号登录与 Cookie 会话

- 状态：用户确认，2026-10-30；IP-04 已实现 Java 端并完成本地验证，待用户验收；Next/界面归 IP-11。
- 来源：用户明确“先不用注册，能够用登录表示就行”；采用预置演示账号＋登录/退出＋Cookie 会话。
- 决策：预置 demo-a / demo-b，密码由环境变量初始化，DB 保存带盐 BCrypt 编码；Account 保持轻量只读模型。
  Spring Security 表单认证通过 Trigger 调用认证 Case，保存 Java HttpSession；浏览器使用 HttpOnly/SameSite Cookie，HTTPS 设置 Secure。
  会话在单实例内存中，重启需重新登录；owner 为稳定账号 ID，历史不会因会话重建而改变。
- 请求保护：全部 POST 保留 CSRF，登录/退出更换或清除会话并刷新 token；Next 透传 Cookie、CSRF header 和逐条 Set-Cookie。
  所属资源 Case 分别维护本人范围，客户端 ownerId 不能授权；同一浏览器配置下标签页共享会话。
- 界面：未登录可浏览模板；制作/记录需登录；登录后显示用户名/退出；切换通过退出再登录，没有访客创建/账号切换器。
- 被替代方案：此前 Bearer＋本机访客列表只是未实现提案；其签发 API、localStorage 凭证保存和 visitor_identity 表设计已取消。
- 权衡：新增最小账号查询/密码校验及安全适配需求，不实现注册、改密、找回、第三方登录、角色或完整账号管理；不引入 Redis/会话表。
- 详情：[detailed-architecture.md](detailed-architecture.md) 第 5 节、[api-contract.md](api-contract.md) 与 [frontend-state-design.md](frontend-state-design.md)。

## ADR-008：四表持久化、只读模板与本地素材

- 状态：详细设计选定，2026-10-30；IP-03 已提供初始四表 SQL 与真库测试入口，实施证据见 V-03（待验收）；IP-05 已提供目录/打包媒体，IP-06 已实现本地上传与元数据仓储，素材卷仍归 IP-15。
- 决策：user_account / uploaded_asset / production_task / task_input_asset 四表，任务根与全部绑定原子创建。
  模板目录与封面/Mock fixture 随后端打包；上传内容位于 backend 命名卷，元数据与稳定引用分开保存。
  Task 结果只保存稳定引用，没有独立历史、结果、步骤、尝试或事件表；Template 无 CRUD/版本模型。
- 约束：只对聚合内部 Task 绑定建物理外键；跨域事实通过公开 Case 能力检查。
  MySQL CHECK 防止非法状态/阶段/结果/失败组合，必须显式处理 NULL；Domain 规则和 owner 检查仍保留。
- 替代：全 JSON 任务输入减少表数但不利于明确有序绑定约束；所有模板入库/版本化暂无写用例；
  云存储和分布式事务增加当前不需要的依赖。
- IP-06 审查修正（2026-10-31，用户授权）：上传在解码前增加单边 8192、总像素 16000000、预计解码工作内存 128 MiB 的全局上限。
  这是对上传接受范围的收紧，HTTP 字段和错误结构不变，超限沿用 413 PAYLOAD_TOO_LARGE；估算公式见 api-contract.md。
  资源保护属于本地存储适配器的技术边界，不向 Template 复制业务规则，不增加可配置策略或图片处理依赖。
- 权衡：接受文件/DB 非原子与未引用文件窗口；只读模板语义必须稳定，不允许静默更改旧 ID 的含义。
  首次实施采用显式初始化脚本，不引入迁移框架或改变当前 SQL 自动初始化关闭状态。
- 详情：[database-design.md](database-design.md)；未来素材删除、模板编辑或长期历史增长前重新分析约束与迁移。

## ADR-009：短事务、提交后派发与 revision 条件更新

- 状态：详细设计选定，2026-10-30；IP-07 已实现并验证并发条件 SQL 和调用方短事务协议，见 V-07；IP-08 已实现线程池和执行 Case 并完成本地验证，见 V-08；创建 Case/提交后派发仍归 IP-09。
- 决策：Case 显式 TransactionTemplate 独立短事务，REQUIRES_NEW / READ_COMMITTED；
  创建提交后派发至有界本机 executor，执行 Case 连续编排固定三阶段，文件 I/O、生成和模拟等待在事务外。
  这将 ADR-006 的真实事务边界细化为显式程序式事务，避免同类事务注解自调用问题；纯 Domain 规则不变。
- 并发：revision 从 0 开始；保存包含任务 ID、预期 revision、状态/阶段，原子更新完整结论并 +1。
  首次 PENDING 抢占只有一个执行者获准；影响行数 0 则停止副作用，终态没有更新出边。
- 失败：明确 executor 拒绝写 FAILED/无阶段；阶段失败写真实阶段；DB 结果未知时停止后续，不盲目重复生成。
  提交后派发前及进程中断窗口保留，不用查询触发续跑，不实现 MQ/Outbox/自动恢复。
- 替代：大事务覆盖整个制作会长期占连接/锁；仅内存校验不能保护旧写；
  事务事件监听可实现提交后触发，但当前无业务订阅者，直接可见的提交后派发更简单。
- 权衡：不提供通用 exactly-once、执行租约或重试恢复；未来外部费用/回调/MQ 需要新幂等与执行身份设计。
- 详情：[detailed-architecture.md](detailed-architecture.md) 第 6～7 节。

## ADR-010：同源转发与前端任务/请求双状态

- 状态：详细设计选定，2026-10-30；未创建 Route Handler、组件、hooks 或测试依赖。
- 决策：Next Node Route Handler 按白名单转发 `/api/v1` 到运行时 JAVA_API_ORIGIN；Java 保有全部业务与持久化责任。
  私有请求/响应 no-store，会话使用 Cookie，POST 带 CSRF header，Next 逐条透传 Set-Cookie；不使用共享缓存或 Next 数据库读取。
  采用 React reducer + scoped hooks；按 taskId 独立递归 setTimeout 查询，终态/空闲停止，查询故障可手动恢复。
- 隔离：身份/页面 epoch、每请求序号和 TaskView revision 各管不同问题；success/catch/finally/定时回调均检查有效上下文。
  清理资源与拒绝旧响应分别满足 R-08，上传/提交/查询异常不转换成 Task FAILED。
- 替代：浏览器直连 Java 需要跨域配置；构建期 rewrite 不作为本轮运行时目标配置方式；
  查询库/SSE/WebSocket 不是当前必要依赖；setInterval 更易制造同任务请求重叠。
- 权衡：多一道转发和受控媒体 fetch/blob 管理；全量历史适用于当前小规模演示，未来按实际增长再设计分页/增量。
- 详情：[frontend-state-design.md](frontend-state-design.md) 与 [api-contract.md](api-contract.md)。
