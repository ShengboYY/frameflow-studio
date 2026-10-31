# 架构

## 当前决策与范围

根据用户要求采用 `ddd-skill` 2.2.4 的领域中心六边形组织方式：顶层按职责分层，
Domain 内按业务能力分包。保持一个 Spring Boot 进程、一个 Maven 工程，不创建空的构建子模块。
Assessment 原文已取得，R-01～R-14 需求分析已完成，见 [requirements.md](requirements.md)。
领域建模已完成，task/template/asset/identity 的职责与深度见 [domain-model.md](domain-model.md)。
Task 采用行为聚合，其他模块保持轻量；目录不等于四个成熟的独立限界上下文。
2026-10-30 的 [detailed-architecture.md](detailed-architecture.md) 进一步确定模块契约、身份与异步并发方案；
[api-contract.md](api-contract.md)、[database-design.md](database-design.md)、
[frontend-state-design.md](frontend-state-design.md) 分别细化传输、持久化及前端协议。IP-02～08 已按其中相关设计落地，实施与验收状态见 tasks.md，其他能力未实现。

IP-02 已实现 domain.task.model 中的 ProductionTask 聚合、不可变值对象、状态机及纯恢复校验，
领域行为和依赖规则已通过本地验证，用户已验收通过，见 tasks.md 的 V-02。
IP-03 已加入初始四表结构；IP-04 已加入 Identity Port/Repository、Case、Controller、安全过滤器与 XML DAO。
IP-05 已加入只读模板/素材要求、目录 Case/JSON 适配器，以及打包媒体 Port/Case/公开封面入口。
IP-06 已加入 UploadedAsset、上传/解析/后台检查、本人媒体读取、XML DAO 和本地存储。
IP-07 已加入本域 IProductionTaskRepository、TaskSnapshot/TaskRecord、Task DAO/PO 和两份 XML。
仓储复用 Case 的写事务；并发包络冻结旧版本/状态/阶段，SQL 条件保存且禁止终态更新。
IP-08 已实现内部执行 Case、Mock 候选、Asset 结果准备、有界派发器和可信 Worker 回调；
任务提交/查询 HTTP 与前端业务仍待实现，证据和剩余范围见 tasks.md 的 V-04～08。
状态机与对象语义以领域模型为准；API v1、预置账号登录/Cookie 会话、四表结构和前端轮询协议已在上述详细设计文档定义。

## 与原四层架构的对应

| 原职责层 | 当前包 | 责任 |
| --- | --- | --- |
| Interfaces | `trigger` + `api` | 入站适配、传输校验/转换、请求响应契约 |
| Application | `cases` | 用例编排、事务、资源归属检查、异步执行协调 |
| Domain | `domain.<业务>` | 聚合/实体/值对象、业务不变量、必要的领域服务和输出契约 |
| Infrastructure | `infrastructure` | Repository/Port 实现、DAO/PO、外部客户端、技术配置 |
| 最小通用类型 | `types` | 确实共用的无框架类型，不是第五个业务领域 |

Java 包使用 `cases`，因为 `case` 是保留关键字；这也是技能参考工程使用的合法包名。
API 是契约集合，不是一次运行时调用的中间服务：Controller 使用 API DTO，转换后调用 Case，
Case 使用自己的输入/结果或纯领域类型，不依赖 HTTP DTO。

## 已落地的最小包骨架

Java 根包：`com.frameflow.studio`，启动类保留在根包。下面保留初始分层导航；IP-02 在 task.model、
IP-04 在各层 identity 及通用错误/装配位置加入实际代码，其余业务包仍按需实现。

```text
com.frameflow.studio
├── FrameflowStudioApplication.java
├── api/package-info.java
├── trigger/package-info.java
├── cases/package-info.java
├── domain/
│   ├── task/
│   │   ├── package-info.java
│   │   └── model/{aggregate,valobj}/
│   ├── template/package-info.java
│   ├── asset/package-info.java
│   └── identity/package-info.java
├── infrastructure/package-info.java
└── types/package-info.java
```

原根包下 task/template/asset/identity 的包说明迁入 domain；原 shared 的说明由 types 与
infrastructure 的明确职责替代。不保留一套平行的 feature-first 分层。

## 后续代码放置规则（不是已创建的类或完整目录）

只在真实实现需要时创建下列子包，不为每个业务复制一整套空目录。
同一业务名称继续出现在各层子包中，以便追踪归属。

```text
api/<业务>/dto/                         外部 RequestDTO / ResponseDTO
trigger/http/<业务>/                    Controller、传输转换与异常映射
trigger/job/<业务>/                     真实存在的定时/后台入站触发器，按需创建
trigger/cli/<业务>/                     显式命令入口，例如预置账号初始化
cases/<业务>/                          I<Xxx>Case 用例契约
cases/<业务>/impl/                     <Xxx>CaseImpl 编排实现
cases/<业务>/model/                    用例输入和输出（不使用 HTTP 类型）
cases/<业务>/port/                     仅应用编排需要的技术输出契约，见技能适配说明

domain/<业务>/
├── adapter/repository/                I<Xxx>Repository 或必要的只读查询契约
├── adapter/port/                      I<Xxx>Port 外部业务能力契约
├── model/aggregate/                   聚合根
├── model/entity/                      真正具有身份的实体
├── model/valobj/                      不可变值对象、领域枚举
└── service/                           确有跨对象业务规则时的领域服务/公开能力

infrastructure/
├── adapter/repository/<业务>/         Repository 实现，不使用通用 CRUD 基类
├── adapter/port/<业务>/               外部能力/执行机制适配器
├── dao/<业务>/                        I<Xxx>Dao，实际添加时使用 @Mapper
├── dao/po/<业务>/                     <Xxx>PO，与领域对象分离
├── gateway/<业务>/                    真正的 HTTP/RPC 客户端
├── gateway/dto/<业务>/                外部系统请求/响应类型
└── config/                            Spring Bean、线程池等技术装配
```

沿用技能的 adapter/repository、adapter/port、dao/po、gateway/dto 位置。
不创建 infrastructure.persistent、根 infrastructure.repository 或 adapter.gateway 等平行结构。
当前无需 Redis/MQ，因此不创建 redis 包或引入相关依赖。
IP-04 首个 XML Mapper 位于 `src/main/resources/mybatis/mapper/identity/`，
已配置 `classpath*:mybatis/mapper/**/*.xml`，后续业务复用。Mapper 在 DAO 上使用 @Mapper，不重复配置扫描或别名。

命名沿用技能的 `I<Xxx>Repository` / `<Xxx>Repository`、`I<Xxx>Port` / `<Xxx>Port`、
`I<Xxx>Case` / `<Xxx>CaseImpl`、`I<Xxx>Dao` / `<Xxx>PO`、`<Xxx>RequestDTO` / `<Xxx>ResponseDTO`。
聚合根不再另建一个同内容的 Entity；`ProductionTask` 可作为聚合根，不为后缀复制模型。
HTTP 请求或 Case 命令不是领域实体，不因技能中的 CommandEntity 示例而放入 domain/model/entity。

## 源码依赖与运行时调用

允许的源码依赖：

| 包 | 可以依赖 | 不允许依赖 |
| --- | --- | --- |
| `types` | Java | Spring、业务模型和其他项目层 |
| `api` | Java、必要的 types | Domain、Cases、Infrastructure、Controller |
| `domain` | Java、本域类型、必要的 types | Spring、MyBatis、HTTP DTO、Cases、Infrastructure |
| `cases` | Domain 契约/模型、types；事务/编排所需的 Spring | Controller、HTTP DTO、DAO/PO、具体适配器/客户端 |
| `trigger` | API、Case 契约、types；Web 等入站框架 | Repository、DAO/PO、具体外部客户端 |
| `infrastructure` | Domain 输出契约/模型、types；必要的 Case 技术 Port | Controller、业务入口实现 |

基础设施实现 Case 技术 Port 是明确的项目适配，并非所有类都可以依赖 Cases。
IP-08 的 ExecutorTaskDispatcherPort 仅额外使用该 Port 签名中的不可变 ExecutionOptions 派发载荷；
不得据此访问其他 Case 模型或业务契约/实现。配置类只接收技术 ExecutionCallback，由 Trigger Worker 调用执行 Case。
根启动类与 infrastructure.config 作为装配位置，可实例化纯 Java 领域服务并绑定其接口。
配置类可注册 Trigger 的过滤器、认证 Provider 和处理器；不得依赖 Controller、API DTO 或 Case 模型，
也不在配置类中实现请求响应转换或调用业务入口。HTTP 回调归 trigger.http，显式初始化命令入口归 trigger.cli。
Domain 中不使用 @Service/@Component/@Transactional；事务属于 Case，实体不注入执行器或客户端。
按 IP-02 review 的用户要求，允许编译期 Lombok @Getter 简化访问器；不生成状态 setter 或绕过校验的构造器。
依赖版本由 Boot BOM 管理，lombok.config 保证生成的领域字节码无 Lombok 注解依赖，运行时 JAR 排除 Lombok。

运行时通过依赖注入使用适配器，源码不从 Domain 引用 Infrastructure：

```text
HTTP → Trigger（使用 API DTO）→ Case → 聚合 / 必要的 Domain Service
                                      │
                                      └→ Domain 输出接口
                                           ↑ implements
                                Infrastructure adapter
                                  ├→ DAO / PO → MySQL
                                  └→ Gateway → 外部 HTTP/RPC
```

Cases 可以通过公开的领域能力编排多个业务域；直接使用 Repository 时限于所属业务域，
不能用某个域的 Repository 聚合访问所有其他域的数据。
Domain 不直接依赖另一个域的 Repository、聚合实现或 DAO；跨域检查和数据转换交给 Case。
不共享可变聚合，不通过 `types` 传播业务模型，不建立域之间或层之间的循环依赖。

单 Maven 工程不能通过编译器自动强制这些边界。当前由代码审查维护，并已在 IP-02 用 JDK jdeps
检查实际 Task 领域类仅依赖 Java 和本域；其他层的依赖规则随真实类落地补充，边界难以维护后再评估 Maven 子模块。

## 领域边界与一致性原则

| 业务边界 | Domain 所有的规则 | Case / 适配器所有的职责 |
| --- | --- | --- |
| task | 制作任务生命周期、阶段/结果/失败信息的一致性 | 创建/执行用例、生成调用顺序、事务、派发、状态持久化协调 |
| template | 真正存在的模板业务语义与只读契约 | 列表/详情查询，简单读取不强加聚合或领域服务 |
| asset | 素材业务限制及归属规则、必要的存储契约 | HTTP 上传边界、存储 I/O、访问定位和元数据持久化 |
| identity | 按需出现的纯身份概念，不包含认证框架 | Trigger 提取可信调用者；各资源用例检查归属 |

`ProductionTask` 封装业务行为，禁止通过任意 setter 绕开状态约束；Case 不重复实现状态机。
聚合以必须原子保持的规则为边界，不把 User、Template、Asset 的完整对象图一起塞进任务。
跨业务关联优先使用标识，是否保存模板版本/快照由实际历史一致性需求决定。
题面已要求状态至少表达等待、处理中、成功、失败，并按素材检查、模拟生成、结果整理三个阶段顺序执行。
合法状态/阶段组合已由领域模型定义；详细设计采用 revision 条件写和数据库组合约束。
重试/取消不属于当前必需功能，不提前建模。

内存中的合法转换不能防止并发覆盖。首次实现任务写入时，依据实际竞争情形选择乐观锁
或带预期状态的条件更新，并检查更新结果；不盲目重试可能产生外部费用的生成步骤。
当前选定的版本字段和数据库模型见 database-design.md；IP-07 已用真实 MySQL 验证 Mapper/事务/竞争，见 V-07。

只读用例可以经 Case → 本域只读查询契约 → Repository adapter → DAO，直接获得所需投影，
无需逐条加载聚合。查询结果用纯 Java 类型表达，禁止将 PO 返回给上层；也不为此引入独立读库或 CQRS 框架。
领域服务只承载不适合单个聚合的业务规则，不为每个聚合机械增加 Service 接口/实现。

## 外部能力与技能适配

技能使用“Domain 定接口、Infrastructure 实现”的主体结构。本项目按实际职责区分：

| 能力（未来按需引入） | 接口归属 | 实现归属 |
| --- | --- | --- |
| 聚合持久化、简单本地查询 | `domain.<业务>.adapter.repository` | `infrastructure.adapter.repository.<业务>` → DAO |
| MediaGenerator | `domain.task.adapter.port` | `infrastructure.adapter.port.task`；Mock 无需 Gateway，真实远程实现再调用 Gateway |
| AssetStorage | `domain.asset.adapter.port` | `infrastructure.adapter.port.asset`；本地实现使用文件 I/O，云实现按需使用客户端 |
| TaskDispatcher | `cases.task.port` | `infrastructure.adapter.port.task`；线程池 Bean 在 infrastructure.config |

以上位置约束保留；Task/Asset 等能力的应用契约已在 detailed-architecture.md 细化，仍待对应任务实施。
Identity 的 Repository/PasswordVerifier 已由 IP-04 落地。
IP-05 的 TemplateCatalogRepository 从 classpath JSON 加载只读目录，不创建空 DAO 或模板表；
PackagedDemoMediaPort 使用本地资源 I/O，无需 Gateway。CatalogConfiguration 装配时核对模板封面 key 与已成功解码的公开封面集合，
两个业务域不直接依赖彼此内部模型/仓储。IMediaResourceCase 保留封面和内部 Mock fixture，IP-06 已补齐本人输入读取；任务结果能力按后续任务补齐。
Repository 适配器不调用生成 HTTP 服务；远程生成适配器不直接更新业务表。
本地存储 I/O 和本机 executor 不是 HTTP/RPC，不为套用“Port 只能调用 Gateway”的示例增加假网关。
TaskDispatcher 是应用编排机制，不属于任务聚合规则，故接口放 Cases；具体适配器仍集中在 adapter/port。
这两点是对技能强约束的显式适配，不宣称逐字照搬模板，原因见 ADR-006。

基础设施允许处理路径越界、I/O 错误和远程协议等技术边界；业务权限、状态转换、业务限制仍归领域规则。
领域模型不保存本地绝对路径、云 SDK 对象或外部响应 DTO。API、Case、Domain、PO、Gateway DTO
按边界需要转换，避免仅因层数而复制相同对象。

## 异步与事务原则（实现阶段遵循）

1. 创建并保存任务使用短事务；事务提交后再派发，避免 Worker 读取未提交数据。
2. 执行逻辑进入独立 Case；线程池/调度适配器只承载执行机制，不拥有任务业务流程。
3. 更新任务状态使用独立短事务；生成服务、文件 I/O 不放进长期数据库事务。
4. 详细设计选择 Case 中显式 TransactionTemplate 独立短事务；不依赖同类方法自调用上的事务注解。
   如未来改用声明式事务，必须通过真实代理边界保证生效。
5. 资源操作使用可信调用者并检查归属；Worker 从已持久化任务获取上下文，不依赖请求线程 ThreadLocal。
6. 明确派发拒绝按详细设计记录执行前失败；保存失败/结果未知则停止后续并保留真实限制。
   提交后派发仍有进程崩溃造成任务未执行的窗口。
   当前接受并记录此限制，不实现 MQ、Outbox、分布式调度或重启恢复。

上述基线由领域状态机及详细设计落实，不要求引入 Spring Event、事件总线或完整工作流。

## 技能使用边界

- 参考 `ddd-skill` 2.2.4 的 SKILL.md、architecture、project-structure、case-layer、port-adapter、
  naming 和 domain-design-guide 文档；采用其合法 cases 包名与主文档指定的 dao/po 位置。
- 本次是已知 backend 路径下的现有工程调整，不创建新项目，因此不调用新项目生成脚本。
- 保留单 Maven 工程，便于现有 Dockerfile、CI、产物路径和启动命令继续使用；未来按需拆构建模块。
- 业务入口统一先经过 Case（简单查询可以非常薄），保留最初 Interfaces 通过 Application 访问业务的约束。
  技能允许的 Trigger 直达 Domain 不作为本项目默认路径。
- 保留原先的纯 Java Domain 和适度充血模型，不照搬示例中的 Spring 注解、Lombok @Data 或任意 setter。
- 策略/责任链/模板方法根据独立变化和复用需求选用，不仅凭 if 分支数量、三个校验或方法行数引入框架。
- 不复制 Redis、RabbitMQ、真实 AI SDK、默认密码、第三方镜像源或部署脚本；本次不部署服务。

## 前端组织

使用官方 `create-next-app` 的 App Router / TypeScript / ESLint 空模板。
当前 `src/app` 只包含布局和一个工程占位入口，没有业务页面，也不调用后端。
保留默认 Turbopack；关闭本阶段不需要的缓存组件/预取选项，不引入 UI 库或 React Compiler。
显式将 Turbopack 根目录设置为 frontend，避免上级目录中的无关 lockfile 干扰根目录推断。
生产构建使用 standalone 输出，postbuild 将静态资源一并打包，供本地与容器复用。
未来按真实代码需要建立：

```text
src/app/             路由与页面组合；具体模板/制作/历史路由待 API 和需求确认
src/features/        按业务组织组件、hooks、类型；不承担路由定义
src/components/      真正跨功能复用的 UI
src/lib/             有实际用途的客户端公共设施
```

这些预期目录在需要时创建，不提交空的业务页面、客户端服务或类型。
布局使用标准 `ReactNode`，避免首次类型检查依赖已生成的 Next.js 全局路由类型。
详细设计选择同源 Next Route Handler 转发与服务端运行时 JAVA_API_ORIGIN；不启用跨域凭据，
个人任务数据通过同源 Cookie 会话读取，POST 校验 CSRF；登录/退出和任务轮询/上下文失效协议见 frontend-state-design.md。

## 配置、持久化和运行

- Spring Boot 从环境读取 MySQL 连接信息；用户名/密码无运行时默认值。
  根 `.env` 由 Compose 读取，独立运行 Java 时需要显式导出；不引入 dotenv 库。
- 本设计阶段不创建表、种子数据或迁移脚本，SQL 自动初始化关闭。
  详细设计选定首次实施使用显式初始脚本、打包模板/图片，不引入迁移框架，见 database-design.md。
- MyBatis 只配置下划线字段映射，Connector/J 版本交给 Spring Boot BOM 管理。
- 默认 INFO 控制台日志；可通过 `APP_LOG_LEVEL` 调整本项目日志等级，运行端口可配置。
- Compose 提供 frontend/backend/mysql 和数据库命名卷；本机端口仅绑定 loopback。
  后端等待数据库通过真实 SQL 健康检查；前端目前独立，不把容器启动顺序当作业务就绪证明。
- Dockerfile 使用多阶段构建和非 root 运行用户。容器和 CI 使用 Java 17、Node.js 22。
  基础镜像固定兼容版本线，未锁 digest；正式发布时应复核补丁与供应链策略。
- IP-06 的 IAssetStoragePort / LocalAssetStoragePort 以 ASSET_STORAGE_ROOT 隔离上传内容，临时文件与稳定文件同目录；
  元数据写入使用 Case 的 REQUIRES_NEW / READ_COMMITTED 短事务。Compose 素材卷归 IP-15，当前未接入。
- IP-04 已实现 Java 预置账号登录/Cookie 会话；IP-08 已实现本机 executor，Next 登录 UI 尚未实现。仍未实现部署环境 profile、健康端点、重试或恢复。

## 验证边界

基础测试启动真实随机端口的 Web ApplicationContext，检查数据源与 MyBatis 装配。
使用 test profile 的占位数据库配置，不打开 JDBC 连接，不创建表，不证明 MySQL 可用。
数据库健康检查只在未来实际运行 Compose 时执行。
Identity API 的真实 HTTP/会话/CSRF 及 MySQL 证据见 V-04；未登录访问后端 `/` 现返回 JSON 401。

## 未来演进（本次不实现）

Mock Generator → 真实 AI 适配器、本地文件 → 对象存储、executor → MQ/Worker、
Assessment 身份方案 → 完整认证授权，均通过实际用例再设计契约与适配器。
不提前建立 generation/result 模块、多 Provider 管理、分布式事务、事件溯源或通用工作流。
依据已完成的需求分析验证业务边界，再按实际需要决定是否拆分限界上下文和 Maven 模块。
