# HTTP API Contract v1（Java 端点已实现）

日期：2026-10-30（Asia/Shanghai）。依据 [requirements.md](requirements.md)、[domain-model.md](domain-model.md)
及 [detailed-architecture.md](detailed-architecture.md)。本文件取代 API 占位内容；IP-04 已实现 API-01/02/13/14，IP-05 已实现 API-03～06，验证记录见 tasks.md 的 V-04/05；IP-06 已实现 API-07/08，验证见 V-06；IP-09/10 已实现 API-09～12，本地验证见 V-09/10，待用户验收。
DTO 位于 `api.<feature>.dto`，Trigger 转换后调用 Case；不直接序列化聚合、PO 或 Gateway DTO。

## 1. 通用约定

- Java 路径前缀 `/api/v1`；浏览器经 Next 同源路径转发，方法、路径和业务语义相同。
- JSON 字段使用 camelCase；成功返回资源对象或 `{ "items": [...] }`，不再套通用 success/data 外壳。
- UUID 使用标准小写带连字符形式；templateId/categoryId/mediaKey 使用种子白名单标识，最长 64 字符，不含路径片段。
- 时间为 UTC ISO 8601、带 `Z`；DB 微秒精度允许 0～6 位小数，前端本地化显示。
- 状态和阶段为大写字符串。无阶段/结果/失败明确为 null，不以空字符串、NONE 或假默认值代替。
- JSON 未声明字段、重复键、非法枚举/类型、非法路径 ID 均为 400；禁止自动接受 ownerId、status、result 等写字段。
- 私有资源、登录/会话/CSRF 与开发控制响应使用 `Cache-Control: no-store`；所有任务读取及 Next 转发禁止共享缓存。
- 不以查询推进阶段，不返回精确百分比。列表当前不分页；个人历史采用 createdAt、taskId 降序，均为设计选择。
- 不定义删除、取消、重试、重新制作、下载、视频、任意状态更新或模板 CRUD API。

私有请求使用 FF_SESSION Cookie，由 Java 会话解析 owner，客户端 UUID/用户名不能授权。
浏览器同源 fetch 使用 credentials=same-origin，不保存或发送 Bearer。缺少/失效会话为 JSON 401，不自动创建身份。
不存在和其他 owner 的资源统一 404。所有 POST 需 X-CSRF-TOKEN，先从公开的 auth/csrf 获取；失配为 403。
账号、会话、CSRF、私有资源均 no-store，登录和退出后重新获取 CSRF token。
JSON 输入规则适用于 JSON 端点；登录为下面定义的 form-urlencoded 特例。

## 2. 端点总表

| ID | 方法 / 路径 | 身份 | 成功 | 来源 / 所属 Case |
| --- | --- | --- | --- | --- |
| API-01 | POST `/api/v1/auth/login` | 公开，需 CSRF | 200 AccountView + Cookie | R-10 的已选登录方案；identity 认证 |
| API-02 | GET `/api/v1/auth/session` | 必需 | 200 AccountView | R-10；identity 当前账号 |
| API-03 | GET `/api/v1/templates/categories` | 公开 | 200 items: CategoryView | R-02；template 目录 |
| API-04 | GET `/api/v1/templates` | 公开 | 200 items: TemplateSummary | R-02；template 目录 |
| API-05 | GET `/api/v1/templates/{templateId}` | 公开 | 200 TemplateView | R-03；template 详情 |
| API-06 | GET `/api/v1/demo-media/{mediaKey}` | 公开白名单封面 | 200 图片字节 | R-02/03；asset 媒体 |
| API-07 | POST `/api/v1/assets` | 必需 | 201 AssetView | R-03；asset 上传 |
| API-08 | GET `/api/v1/assets/{assetId}/content` | 必需且本人 | 200 图片字节 | R-03；asset 内容读取 |
| API-09 | POST `/api/v1/tasks` | 必需 | 202 TaskAccepted | R-03/04/06/10；task 创建 |
| API-10 | GET `/api/v1/tasks` | 必需且本人 | 200 items: TaskView | R-09/10；task 历史 |
| API-11 | GET `/api/v1/tasks/{taskId}` | 必需且本人 | 200 TaskView | R-05/07/08/09；task 查询 |
| API-12 | GET `/api/v1/tasks/{taskId}/result` | 必需、本人、已成功 | 200 图片字节 | R-05/09；task 授权后调 asset 媒体 |
| API-13 | POST `/api/v1/auth/logout` | 需 CSRF | 204 + 清 Cookie | R-08/10 的退出流程；Session 失效 |
| API-14 | GET `/api/v1/auth/csrf` | 公开 | 200 CsrfView，必要时匿名 Cookie | Cookie 写请求保护，非账号创建 |

模板分类路径必须先于动态 templateId 匹配；转发白名单也按这些具体语义匹配，不允许任意上游路径。
每个 endpoint 的方法和返回类型只有本表一个定义；前端不自建另一份业务状态枚举。

## 3. 登录、当前账号、退出与 CSRF

用户已确认只做登录，不做注册。账号由服务端预置，普通用户不能通过 API 创建账号；
旧设计的 POST identities、GET identity、VisitorCredential 和 Bearer 不再属于 v1。

AccountView 固定为 ownerId（账号稳定 UUID）、username、displayName；不返回密码、密码散列或 Session ID。
两项预置账号为 demo-a / demo-b；实际密码从环境变量初始化，README 用配置步骤说明，不写可用密码样例。

| 端点 | 请求 | 成功与失败语义 |
| --- | --- | --- |
| GET auth/csrf | 无请求体，无需登录 | 200 `{token, headerName:"X-CSRF-TOKEN"}`；可创建匿名 Session，token 只放前端内存 |
| POST auth/login | application/x-www-form-urlencoded；恰好 username、password，并带 X-CSRF-TOKEN | 核验预置账号后 200 AccountView；设置/更新 FF_SESSION；用户名不存在与密码错误统一 401 INVALID_CREDENTIALS |
| GET auth/session | 无请求体，同源 Cookie | 已登录 200 AccountView；未登录/过期 401 AUTHENTICATION_REQUIRED；DB 故障 503，不当成密码错误 |
| POST auth/logout | 无请求体，Cookie + X-CSRF-TOKEN | 204 无响应体；使服务器会话失效并清 Cookie；无已认证会话但 CSRF 有效时同样 204，不取消任务 |

username 为规范小写 ASCII，长度 3～32；两个演示账号均在此范围内。
password 保留原样、不 trim，1～72 个 UTF-8 字节；非法形状/重复/未知字段为 400，编码不支持为 415；登录不自动重试。
已有认证会话再次调用 login 为 409 ALREADY_AUTHENTICATED，UI 应先退出；不能把它当注册或任意 owner 切换。
所有 POST 的 CSRF 校验先于业务处理，未通过可以先返回 403；取得有效 token 后再判断认证/输入错误。
登录/退出成功后重新 GET auth/csrf，不能复用登录前或已注销会话的 token；403 时不自动重放 POST。

FF_SESSION 为 host-only、Path=/api、HttpOnly、SameSite=Lax；HTTPS 配置 Secure，本地 loopback HTTP 显式关闭。
Cookie 最长 8 小时；Java HttpSession 空闲 30 分钟或绝对 8 小时失效，退出立即失效；登录成功更换 Session ID。
Next 保留每条 Set-Cookie 和请求 Cookie/CSRF header，不能折叠 Cookie 或使用公开缓存。
登录/退出采用 JSON/204，不返回重定向或默认 HTML 登录页；浏览器通过会话查询决定界面状态。
会话存 Java 内存，后端重启后重新登录，账号及历史仍在 MySQL；本地不保存密码、Bearer 或多个账号凭证。
本阶段不提供注册、公开账号创建、改密/找回密码、账号删除或第三方登录端点；两个账号具有相同普通用户能力。

## 4. 模板、分类与示例图片

CategoryView 为 categoryId、name。TemplateSummary 为 templateId、title、categoryId、coverUrl。
GET templates 可选查询 `categoryId`；不传返回全部；未知分类返回空 items，未知模板详情返回 404。
不接受分页、排序、owner 或状态查询参数；未声明参数为 400。

TemplateView 包含 Summary 全部字段，加 description 和 requirements：

```json
{
  "templateId": "portrait-card",
  "title": "肖像卡片",
  "categoryId": "creative",
  "coverUrl": "/api/v1/demo-media/portrait-cover",
  "description": "上传一张图片，演示卡片制作流程",
  "requirements": {
    "requiredImageCount": 1,
    "acceptedMimeTypes": ["image/jpeg", "image/png"],
    "maxFileSizeBytes": 5242880
  }
}
```

当前选定的最小种子规格如下，属于本项目数据选择，不是题面对全部模板的固定要求：

| templateId | 分类 ID / 名称 | 图片数 | 类型 | 单文件上限 | 公开封面 key |
| --- | --- | --- | --- | --- | --- |
| portrait-card | creative / 创意图片 | 1 | JPEG、PNG | 5 MiB = 5242880 字节 | portrait-cover |
| travel-postcard | travel / 旅行 | 2 | JPEG、PNG | 8 MiB = 8388608 字节 | postcard-cover |

大小上限含等号；数量精确匹配，输入顺序按 assetIds 数组顺序保存；同一素材可用于两个位置，不增加去重/消耗规则。
种子顺序用于目录展示，前端“全部”分类为本地显示项，不新增服务器模板。
两份可访问封面和一份合法 Mock 结果 fixture 随后端打包，统一使用 JPEG（`image/jpeg`），来源见打包资源的 SOURCES.md。
公开 demo-media 仅接受登记的封面 key；不能通过该端点传文件路径或任意读取上传文件/内部结果引用。

## 5. 上传与输入媒体

POST assets 为 multipart/form-data，恰好一个 `file` 部分和一个 `templateId` 文本部分。
浏览器让 FormData 设置 boundary，不手写 Content-Type。templateId 用于取得该文件的实际模板约束，
不是把素材永久绑定此模板；后续提交其他模板仍重新检查。

服务端顺序：解析身份/模板 → 有界接收 → 检测实际 JPEG/PNG 并验证可读取 → 检查大小/类型 →
稳定保存内容 → 提交元数据 → 返回 AssetView。数量在 POST tasks 验证，单文件上传不要求已经选齐全部图片。
没有 assetId 的在途/失败上传不能用于正常受理；只靠扩展名或客户端 MIME/size 不合格。

全局单文件上限 8 MiB，multipart 请求上限 9 MiB，服务端与转发部署不得设置比合法模板更小的限制。
框架/代理拒绝也映射 413，不仅 Controller 内处理；具体模板的更小上限同样执行。
上传还需在正式解码前满足全局资源限制：宽、高分别不超过 8192 像素，总像素不超过 16000000，
预计解码工作内存不超过 128 MiB，均含等号；任一超限返回 413 PAYLOAD_TOO_LARGE，清理临时文件且不写元数据。
内存估算为 `2 × width × height × bytesPerPixel + 3 × width × bytesPerPixel`，
覆盖两份完整栅格与三行原始扫描行；bytesPerPixel 按实际原始像素位深/通道计算、最低按 4 字节，无法获取时按 8 字节。
这是单图像工作内存估算，不是 JVM 总内存承诺；16 位 RGBA 等高位深图片可能先触发内存限制。
原始尺寸使用 long 参与像素和预算计算，采样仅减少输出分配，不能替代原始尺寸检查。
非法/缺失部分为 400；未知模板 404；大小超限 413；不支持的真实类型 415；无法读取的图片 422。
文件服务不可用/元数据保存失败为 503；失败不返回可用 assetId。

AssetView：assetId、ownerId、mediaType、sizeBytes、createdAt、previewUrl。
previewUrl 为 `/api/v1/assets/{assetId}/content`，通过同源 Cookie fetch 使用，不包含凭证。
不返回文件系统位置、tokenHash 或原始 MultipartFile。上传/内容读取归属相同主体；元数据存在而内容丢失返回 503 RESOURCE_UNAVAILABLE。
当前没有素材删除 API；响应丢失或旧选择上传完成可留未绑定素材，不能因此改动已有任务。

## 6. 制作受理

POST tasks 的正常请求示例（UUID 仅为示例 ID，不代表已有资源）：

```json
{
  "templateId": "portrait-card",
  "assetIds": ["82dd291e-4673-4500-8fe3-97a71a6cbe40"]
}
```

| 请求字段 | 必需 | 验证 |
| --- | --- | --- |
| templateId | 是 | 种子中的有效模板 |
| assetIds | 是 | 有序 UUID 数组；长度精确匹配模板；每项服务端存在、属于当前主体且内容就绪 |
| simulation | 否 | 只在 MOCK_CONTROLS_ENABLED=true 时允许，见下文 |

重复引用同一 assetId 不合并数组，也不重复上传；按每个位置计数。模板匹配错误为 422，
无效或其他主体的素材引用统一 404 RESOURCE_NOT_FOUND，不能揭示它属于谁。
校验失败不创建 FAILED 任务。全局 JSON 请求体上限选定 64 KiB，超限 413。

simulation 用于测试/开发接口演示，不出现在正式页面或模板配置：

```json
{
  "templateId": "portrait-card",
  "assetIds": ["82dd291e-4673-4500-8fe3-97a71a6cbe40"],
  "simulation": {"failAtStage": "GENERATE", "stageDelayMs": 5000}
}
```

failAtStage 为 null 或 CHECK_ASSETS/GENERATE/FINALIZE，省略默认为 null；stageDelayMs 为整数 0～30000，
省略使用服务端默认值 1000。simulation 省略时整项使用默认；显式 simulation=null 或未知字段为 400。
开关关闭但请求携带 simulation 时为 400 SIMULATION_CONTROLS_DISABLED。
每任务受理时捕获设置，逐阶段延迟后注入指定失败，不能改变其他已排队任务；不支持运行中修改。
分别提交三个 failAtStage 即可演示阶段失败，省略该值演示正常成功；README 后续提供真实命令。

已知创建提交成功返回 202 TaskAccepted，并带 `Location: /api/v1/tasks/{taskId}`：

```json
{
  "taskId": "283def77-032c-4e18-9d95-34b435a0f2f1",
  "ownerId": "b92661af-8e57-4ac4-9aac-56640e063407",
  "createdAt": "2026-10-30T08:00:00Z"
}
```

此响应没有捏造 PENDING 快照；客户端立即查询 taskUrl。派发已知拒绝且创建成功仍返回这个受理 ID，
服务端尽力保存 FAILED/无阶段/DISPATCH_REJECTED；查询结论为准。不会等待整项制作结束。
不支持后端创建幂等，禁止自动重试 POST；提交超时/503 结果未知不表示后台失败，用户可先刷新个人记录。

## 7. TaskView 与个人历史

GET tasks 返回当前主体全部记录 `{ "items": [TaskView] }`，无记录为 `items: []`。
GET tasks/{taskId} 返回同一 TaskView。两种读取来自同一根记录，不能拥有独立结论；不是强制两次 HTTP 读取的瞬间快照完全相同。

```json
{
  "taskId": "283def77-032c-4e18-9d95-34b435a0f2f1",
  "ownerId": "b92661af-8e57-4ac4-9aac-56640e063407",
  "templateId": "portrait-card",
  "status": "RUNNING",
  "currentStage": "GENERATE",
  "revision": 2,
  "createdAt": "2026-10-30T08:00:00Z",
  "updatedAt": "2026-10-30T08:00:01Z",
  "result": null,
  "failure": null
}
```

TaskView 字段全部必有；revision 为非负安全整数，对应每次合法状态变更，查询本身不增加它。
日期/版本用于显示和合并，不让客户端自行推进阶段。

| 组合 | currentStage | result | failure |
| --- | --- | --- | --- |
| PENDING | null | null | null |
| RUNNING | 三阶段之一 | null | null |
| SUCCEEDED | FINALIZE | `{previewUrl, mediaType}` | null |
| FAILED，阶段内 | 实际失败阶段 | null | `{kind:"STAGE", stage, code, message}`，stage 与 currentStage 相同 |
| FAILED，执行前 | null | null | `{kind:"DISPATCH", stage:null, code, message}` |

成功结果的 previewUrl 固定为 `/api/v1/tasks/{taskId}/result`，mediaType 当前为 image/jpeg，
由实际 Mock fixture 一致确定。内部结果引用不直接暴露。failure.message 为可理解脱敏文案，
code 区分 MOCK_STAGE_FAILURE、ASSET_UNAVAILABLE、GENERATION_FAILED、RESULT_UNAVAILABLE、STAGE_EXECUTION_FAILED、DISPATCH_REJECTED。
阶段失败是正常任务读取的 200 内容，不返回 HTTP 500。终态结果不能因再次读取而回退。

TaskView 的 revision 与组合取自同一记录；updatedAt 不作为乱序判断依据。前端不接收小版本覆盖大版本；
相同 revision 若出现冲突业务内容，作为契约异常保留已有信息，不能以最后响应为准。

## 8. 图片响应与访问失败

API-06/08/12 成功返回实际图片字节，准确 Content-Type，`X-Content-Type-Options: nosniff`；
不提供 attachment 下载行为。私有图片 no-store，不能重定向到把凭证带给第三方的 URL。
API-12 先校验身份和任务归属，再判断成功状态；非成功为 409 RESULT_NOT_READY，不直接找文件绕过 Task。
成功记录存在但资源不可读为 503 RESOURCE_UNAVAILABLE；不把记录改成 FAILED。
失败时即使客户端 Accept 为图片也返回标准 JSON 错误；前端检查 status/Content-Type 后才能创建 blob URL。

## 9. 错误、请求 ID 与重试语义

错误统一结构：

```json
{
  "error": {
    "code": "TEMPLATE_REQUIREMENTS_MISMATCH",
    "message": "素材数量不符合当前模板要求",
    "details": [{"field": "assetIds", "code": "COUNT_MISMATCH", "message": "需要 1 张图片"}]
  },
  "requestId": "3a6026b7-968f-4980-bc4e-93fa6d5e46a7"
}
```

details 是数组，无字段信息时为空；requestId 为服务端生成的诊断 ID，HTTP `X-Request-Id` 与之相同。
SUBMISSION_OUTCOME_UNKNOWN 可额外包含 error.taskId（如果已分配），只提示可能存在的任务，不等于受理成功。
未知资源错误不返回敏感 owner、存储路径、SQL、堆栈或凭证。

| HTTP | 主要 code / 情形 | 客户端行为 |
| --- | --- | --- |
| 400 | INVALID_REQUEST、INVALID_SIMULATION、SIMULATION_CONTROLS_DISABLED | 修正请求，不自动重试 |
| 401 | INVALID_CREDENTIALS（登录）、AUTHENTICATION_REQUIRED（私有资源） | 登录提示统一错误；会话失效清理私有上下文并引导登录，不改 TaskStatus |
| 403 | CSRF_INVALID | 失效旧写上下文，重新检查会话/获取 token；不自动重放 POST |
| 404 | RESOURCE_NOT_FOUND | 模板/任务/素材缺失或无权；保留已有任务快照并提示资源问题 |
| 405 | METHOD_NOT_ALLOWED | 当前端点不支持该方法 |
| 409 | RESULT_NOT_READY、ALREADY_AUTHENTICATED | 结果未成功时不伪造图片；已登录用户先退出再登录其他账号 |
| 413 | PAYLOAD_TOO_LARGE | 修正图片或请求大小 |
| 415 | UNSUPPORTED_MEDIA_TYPE | 请求编码/实际媒体不支持 |
| 422 | TEMPLATE_REQUIREMENTS_MISMATCH、INVALID_IMAGE | 输入业务校验失败，没有正常受理 |
| 503 | SERVICE_UNAVAILABLE、RESOURCE_UNAVAILABLE、UPSTREAM_UNAVAILABLE、SUBMISSION_OUTCOME_UNKNOWN | 区分请求与后台状态；读可手动恢复，写不自动重试 |
| 504 | UPSTREAM_TIMEOUT | Next 代理上游超时；提交结果可能未知，查询异常不等于执行失败 |
| 500 | INTERNAL_ERROR | 未预期错误，脱敏；不能据此断言任务一定未创建 |

框架 multipart/JSON 错误、Java 业务错误和 Next 代理错误需使用兼容错误结构；前端仍处理断网、非 JSON、
异常 HTML 等无结构响应，将其归入请求异常，不读取 error.code 来臆造业务终态。
错误结构适用于响应尚未提交的情况；已开始传输后断流可能只有网络或解析异常，不能保证重新发送 JSON/504。
客户端请求预算为 30 秒，代理等待上游响应头为 60 秒；客户端先超时仍不证明上游没有受理或保存。
所有 POST（登录、退出、上传、受理）都不自动重试；GET 恢复方式见前端设计。

## 10. 兼容性与验证状态

此前没有已实现公共 API，本轮首次定义 v1，无需兼容旧业务客户端；既有技术版本及根架构不变。
状态组合沿用领域模型，包括派发前失败；revision、字段、路径、限额均为本阶段方案。
本次按用户确认将未实现的访客提案改为预置账号登录；保留 API-03～12 编号，新增 API-13/14。
IP-04 已用真实 HTTP 与受控故障测试验证认证端点结构、状态码、会话/CSRF 及 Java Cookie；
其他 Java 资源端点及 owner 授权证据见 V-06/09/10；Next 代理透传及真实 Java 联调证据见 V-11。
IP-05 已验证目录分类/筛选/详情、真实 HTTP 封面读取、素材规则边界、资源白名单及无效种子启动失败；
即使请求只接受图片，未知模板/封面也返回标准 JSON 404。Java 资源和字段契约保持不变，Next/UI 尚未接入。

| 日期 | 变更 | 状态 |
| --- | --- | --- |
| 2026-10-29 | API 占位骨架 | 历史，未实现 |
| 2026-10-30 | 首次定义 v1，随后按用户确认改为预置账号登录/退出与 Cookie/CSRF，移除访客签发 | 设计选定，未实现/验收 |
| 2026-10-30 | IP-04 实现 API-01/02/13/14 与统一错误出口；v1 字段/语义不变 | 本地验证通过，待用户验收，见 V-04 |
| 2026-10-31 | IP-05 实现 API-03～06、素材规则与打包图片；v1 字段/语义不变 | 本地验证通过，待用户验收，见 V-05 |
| 2026-10-31 | IP-06 实现 API-07/08、真实类型检查、素材存储及本人读取；v1 字段/语义不变 | 验证见 V-06，待用户验收 |
| 2026-10-31 | IP-09/10 实现 API-09～12、受理后派发、本人查询及结果图片；v1 字段/语义不变 | 本地验证通过，待用户验收，见 V-09/10 |
| 2026-10-31 | IP-06 审查修正：用户授权新增解码前宽高/像素/工作内存限制，收紧上传接受范围，超限沿用 413 | 348 项快速测试通过，见 V-06 补充记录 |

IP-08 的内部执行入口仍不对外公开；IP-09/10 已接入 API-09～12。任务和有序绑定提交成功后才派发；
本人查询复用 IP-07 的 XML 投影，结果由 Task 授权后调用 Asset 能力。严格 JSON/限额、模拟控制、错误/图片和真实 MySQL 闭环见 V-09/10。
本次新增端点沿用既定 v1，不改变字段、状态或数据结构。
IP-11 已实现白名单 Next 转发与登录/身份上下文；真实 Java 和生产 Next 的表单、multipart、JSON、图片与 Cookie/CSRF 联调见 V-11。
IP-12 已接入模板/素材客户端与页面，校验响应并保留 Java 的实际数量/类型/大小规则，见 V-12。
IP-13 已实现 TaskView/TaskAccepted 客户端解析、归属和版本检查、历史/单查合并及独立轮询 hooks，见 V-13；没有新增或修改服务端 v1 字段、枚举或端点。
制作/历史页面整合与浏览器联合人工验收仍待 IP-14～16。
