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

- 状态：已采纳。
- 决策：只交付 scaffolding；不制定 API、状态机、身份传递规则、表模型和种子模板。
- 原因：初始化规范提到 Assessment，但本次附件未包含完整题面。
- 权衡：当前不能宣称满足业务验收；需要后续将题面映射到 requirements/tasks。
- 待讨论：用户身份、任务流程/失败语义、文件约束、API、前端路由、持久化迁移、模块调用关系。

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
