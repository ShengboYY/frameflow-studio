# Frontend Multi-Task State Design（身份、上传与任务查询作用域已实现）

日期：2026-10-30（Asia/Shanghai）。依据 [requirements.md](requirements.md) R-03、R-07～R-12、
[领域模型](domain-model.md)、[详细架构](detailed-architecture.md) 和 [API v1](api-contract.md)。
IP-11 已实现身份/会话作用域；IP-12 已实现模板读取、上传选择和本地预览作用域；IP-13 已实现任务快照合并及独立轮询；IP-14 已接入制作表单、详情记录区、个人历史/刷新恢复及私有结果媒体，验证与未验证项见 tasks.md 的 V-11～14。
以下保留整体设计。`/login` 成功后跳转 `/history`，已登录导航提供“我的记录”。

## 1. App Router 边界与页面职责

| 规划路由 / 范围 | 职责 | 跟踪生命周期 |
| --- | --- | --- |
| `/templates` | 分类/卡片、公开目录、身份选择入口 | 不启动任务轮询 |
| `/templates/[templateId]` | 详情、上传位、制作表单、本人当前模板的任务/结果区 | 本页面挂载期间读取历史并跟踪；离开本页销毁旧 controller |
| `/history` | 本人全部任务、时间、整体状态、成功图片或失败原因 | 独立页面上下文；重新读取 Java 后恢复跟踪 |
| `/login` | 用户名/密码登录表单，无注册入口 | 已登录则进入个人记录；认证写请求串行，不放 mount Effect |
| 根 AuthProvider | 查询 Cookie 会话，保存非敏感账号资料/CSRF 内存状态及 identityEpoch | 无凭证持久化、账号切换器或全局任务轮询 |

路由文件只组合页面；任务/模板/素材/身份组件与 hooks 按真实需求位于 `src/features/<feature>`。
共用 API 传输设施放 `src/lib`，共用 UI 按需放 components；不预建通用状态框架或空目录。
静态布局可为 Server Components；身份、文件、预览、订阅和定时器属于 Client Components。
已阅读安装版本的 [Server / Client 指引](../frontend/node_modules/next/dist/docs/01-app/01-getting-started/05-server-and-client-components.md)
和 [数据获取指引](../frontend/node_modules/next/dist/docs/01-app/01-getting-started/06-fetching-data.md)。
不在 localStorage/sessionStorage、RSC、服务器日志或 HTML 中保存/传递密码和 Session ID，不从 Next 直接读 MySQL。

界面反馈保持 R-11：模板空态/不存在/请求失败分别表达；上传位有标签，状态/错误有可理解文本，键盘焦点可见；
历史展示创建时间及实际服务端整体状态；处理中显示后端当前阶段，阶段失败显示失败环节和原因，成功后隐藏阶段行。
布局遵循已有参考图的主要结构，错误不只靠颜色。

## 2. 状态分区

当前选择 React reducer + 有明确作用域的 hooks，不引入 Redux、Zustand 或查询库。
reducer 只处理不可变状态变化，副作用由 scoped controller/hooks 管理；不在 reducer 内 fetch/setTimeout。
IP-11 的身份作用域由每个 AuthProvider 独占的 AuthSession 同步维护，通过 useSyncExternalStore 订阅；
这样退出/复核可在下一次 React 渲染之前失效旧请求。没有模块级共享账号状态；任务 reducer 的计划不变。
IP-13 的纯 taskReducer 由页面 TaskController 同步应用，再由 useSyncExternalStore 订阅到 hook，避免异步回调读取旧的 React 渲染闭包。
controller 只维护请求、timer 与 generation，reducer 不执行副作用；Java 仍是状态事实来源，客户端只拒绝矛盾快照，不自行推进阶段。

| 状态区 | 建议字段 | 不能混入的内容 |
| --- | --- | --- |
| 身份 | 当前 ownerId/username/displayName、内存 CSRF token、identityEpoch、会话查询状态 | 当前 TaskStatus、全局当前任务或“是否全部成功” |
| 页面上下文 | pageEpoch、active/disposed、身份上下文引用 | 服务端业务生命周期 |
| 模板请求 | templateId、加载/成功/失败/不存在、requestSeq、数据 | 上传或任务结论 |
| 每个上传位 | selectionRevision、File、本地预览 URL、uploadState、assetId、上传错误、requestSeq | 上传失败不能变 Task FAILED |
| 提交请求 | idle/submitting/accepted/outcomeUnknown/error、requestSeq、待查 taskId | 提交中与任务 RUNNING 分开 |
| 任务记录 | 按 taskId 保存的 TaskView；owner 作用域外层隔离 | 不保存一个会被所有任务覆盖的 currentTask |
| 受理占位 | taskId、ownerId、createdAt、awaitingSnapshot | 不虚构 status=PENDING；待第一次 TaskView 确认 |
| 查询请求 | 每任务 loading/error/paused、requestSeq、最近成功读取信息 | 网络错误不写进 TaskView.failure |
| 媒体请求 | 按资源/任务的加载、预览 URL、错误与 requestSeq | 图片不可用不回退 SUCCEEDED |
| 跟踪资源（ref） | 每任务 timer、AbortController、有效请求序号 | 不作为持久化业务数据，不序列化到 localStorage |

外层身份作用域相当于 `{ownerId, identityEpoch}`，任务内层以 taskId 为键；页面作用域再带 pageEpoch。
即使 A → B → A，ownerId 又相同，identityEpoch 也不同，不能接收第一轮 A 的旧回调。
请求发起时捕获不可变身份/页面上下文；同源 Cookie 由浏览器携带，POST 使用当前会话的 CSRF token。
Cookie 在标签页间共享，不能声称请求携带独立保存的 A/B Bearer；旧会话 token 不得自动替换后重放旧写请求。

TaskView 以 Java/MySQL 为事实来源；不持久化任务快照或认证凭证，localStorage 至多存放不含敏感信息的 auth-change 事件标记。
UI 派生未完成集合为服务端 PENDING/RUNNING；额外受理占位尚待快照，也需要一次读取。
终态由 SUCCEEDED/FAILED 判定，不以百分比、超时或请求次数推断。

## 3. 登录、退出、会话恢复与跨标签页

未登录用户可以浏览模板；制作/查看个人记录时进入 `/login`。页面提供用户名、密码、登录按钮和错误反馈，
没有注册、创建访客或任意选择用户列表。登录后显示用户名、“我的记录”和“退出登录”；切换账号通过退出后重新登录。

1. 页面初始化 GET auth/session：200 建立账号上下文；401 表示未登录，不当作系统故障，也不预取 CSRF，避免多个匿名标签页同时创建不同 Session；503 保留已知信息并提示会话查询异常。
2. 用户明确提交登录后 GET auth/csrf，再 GET auth/session 确认仍为匿名且操作 epoch 有效，随后只发送一次 form-urlencoded POST auth/login。准备阶段也受同步 gate 和取消/失效检查保护；用户名/密码只用于本次请求，不写浏览器持久存储。
3. 登录成功后递增 identityEpoch，清理旧私有页面状态，重新取 CSRF、核对当前会话，再读取本人历史并跟踪未完成任务。
4. 点击退出时立即失效身份/页面上下文，清理 timer、在途请求、上传选择和私有 Blob，再使用原 CSRF token 请求 logout。
5. 退出成功只展示未登录视图；需要 B 时在登录页输入 B 的账号密码。后台 A 的任务继续，重新登录 A 后从 Java 恢复。

登录/退出在同一标签页串行并使用同步 gate；重复点击不产生并发认证写请求。
认证写请求超时或结果未知时不自动重试，先 GET auth/session 核对实际会话；不能仅因忽略旧 JSON 回调就认为旧 Set-Cookie 不会生效。
登录错误统一提示“账号或密码错误”；私有接口 401 会话失效时清理私有视图并引导登录，不改变 TaskStatus。
403 CSRF_INVALID 使旧写上下文失效，重新查询会话/获取 token，用户核对当前表单后才能重新提交，不能自动重放。

FF_SESSION 为 HttpOnly Cookie，脚本不读取它；API fetch 使用 credentials=same-origin，Next 保留 Cookie/Set-Cookie。
CSRF token 仅放内存；已认证会话获取并核对 token，退出后匿名状态不取 token，下一次显式登录时再获取。
更新内存 token 前先核对账号/上下文，不能把 B 的 token 注入 A 的旧表单；准备失败不发送 POST，已开始的认证 POST 仍按结果未知协议核对，不自动重放。
同一浏览器配置中的标签页共享登录账号；登录/退出发送不含凭证的 auth-change 通知，其他页立即失效上下文并重新查询会话。
通知可用 localStorage 随机事件标记触发 storage 事件；存储不可用或漏收事件时，重新聚焦只查询当前会话。
账号未变则保留身份 epoch、原 CSRF 写上下文、上传选择/预览及任务跟踪，不暂停或重建页面；账号变化或已退出才失效旧作用域并重新核对。
聚焦查询暂不可用只提示异常并保留状态，下次聚焦重试；过期响应不得覆盖新查询或新身份。401/403 与认证通知仍按上述规则失效旧上下文。
新会话拒绝旧 CSRF token，防止旧页面在 Cookie 变化后把原上传/提交自动归入另一个账号。

刷新/重新进入先查会话，有效则恢复本人历史，过期或 Java 重启后重新登录同一账号。
会话失效不删除账号、素材或任务，不取消已受理执行；本阶段不提供记住密码、注册、找回密码或跨设备凭证同步。

## 4. 上传与表单竞态

上传前按 TemplateView 检查数量上限、候选文件类型/size，后端仍作真实校验。
每个位置状态为 empty、selected/uploading、ready 或 uploadError；只有全部所需位置 ready、模板有效且没有提交请求，才允许正常提交。
File 本地预览可以在上传完成前出现，不能把这个 blob URL 当服务器素材引用提交。

| 用户动作/返回 | 状态规则 |
| --- | --- |
| 新选择或替换 | selectionRevision +1；清空旧 assetId/错误；尽量取消旧上传；新建本地预览并按需要上传 |
| 移除 | selectionRevision +1；解除位置绑定、取消旧请求并释放 URL；不调用素材删除 API |
| 上传成功 | 只有身份/页面/位置/请求序号全匹配才绑定响应 assetId，且 response.ownerId 匹配当前主体 |
| 上传失败 | 同样经过上下文检查后只设置此位置 uploadError，不修改任何任务结论 |
| 旧上传成功晚到 | 丢弃，不重新填充已移除位置或覆盖替换；服务器可能保留未引用素材 |
| 模板切换/离页/身份切换 | 失效页面/表单上下文，取消上传、解除未提交绑定、回收预览资源 |

提交按钮的防重复需要事件入口立即获得本表单同步 gate，不能仅等待下一次 React 渲染的 disabled 状态。
提交进行中禁用本表单素材修改；请求体捕获当时 templateId 与有序 assetIds，不读取未来的可变选择。
这只避免同一表单的重复点击；受理响应后释放 gate，用户可继续创建另一个独立任务，不等待前一任务完成。

IP-12 已实现 `useUploadSlots`：模板和 identityEpoch 变化时重新挂载；每个位置同步递增选择版本/请求序号，清绑定再取消并释放 URL。
旧 success/catch/finally 同时校验挂载、身份与位置/请求版本；`getPreparedInputs()` 同步读取当前有序服务器 ID，缺一项即 null。
IP-14 的制作表单已接入 `useTaskSubmission`：同步 gate 固定提交参数，fieldset 禁用素材修改，事件入口也同步检查 gate；本地预览错误与已上传素材的就绪状态分开。

IP-14 补充演示入口：模板详情的 Server Component 通过 `connection()` 在请求时读取 `MOCK_CONTROLS_ENABLED`，
仅将非敏感布尔值传给 Client Components；默认 false，不使用构建期 `NEXT_PUBLIC_` 配置或新增 API。
显式启用时，制作表单出现默认折叠的“演示设置”，选择不触发失败/三个失败阶段及 0～30 整数秒的每阶段耗时（默认 1 秒）。
提交同步复制本次 simulation 和素材数组，提交中禁用设置且事件入口检查 gate；202 后可修改设置并创建另一任务，不能改变已受理任务。
身份或模板变化时随上传表单重置，未提交设置不持久化；普通模式省略 simulation。
前端不按设置预测任务阶段或终态，始终读取 Java 真实记录；后端未启用时的 400 只显示提交未受理，不创建伪失败记录。

## 5. 受理、未知结果与记录恢复

202 返回 TaskAccepted 后：结束 submitting → 合并受理占位 → 立即 GET 单任务 → 按真实 TaskView 展示并决定后续跟踪。
受理不代表成功，也不保证第一张快照是 PENDING；Worker 可能已 RUNNING 或终态。
响应 ownerId/taskId 需与请求所属身份和返回规范一致，不能把响应插入现在切换后的另一个身份。

校验错误只展示提交错误，不创建失败 Task 卡片。断网、超时、500/503 等无法判断创建结果时进入 outcomeUnknown：
提示“提交结果暂未确认，可先查看记录”，保留表单输入；不自动 POST 重试，也不声称任务 FAILED。
若错误携带可能的 taskId，可读该 ID；否则手动刷新历史。不以一次 404 证明此前 commit 一定未成功。
用户可在知情后再次提交，当前不承诺后端去重。

页面刷新/重新进入先查询当前登录账号会话，再 GET tasks；初始成功的空 items 才展示“暂无记录”。
首次历史请求失败展示读取异常，不能将其当作空历史，也不能伪造任务完成或删除已有数据。

模板详情的记录列表按当前 templateId 筛选，数量和空态均基于筛选结果；`/history` 展示本人所有模板的记录。
筛选仅作用于展示，不改变 API 或任务事实；没有快照的本页受理/核对占位仍显示即时反馈，读到快照后按实际模板归类。
记录卡片不展示任务 UUID；刷新按钮仅呈现图标并保留无障碍名称。
除整体状态外，RUNNING 展示后端 currentStage，阶段失败展示 failure.stage 与原因；PENDING、成功和派发失败不展示阶段行。
API 的阶段数据和内部状态合并规则保持不变。

历史与单任务响应统一走版本合并，历史读取不整体覆盖 tasksById：
查询发出后可能刚受理新任务，晚到历史未包含新 ID 时不得删除该占位/记录。
当前没有删除 API，历史结果中的“未出现”不被解释为删除。新请求可能补齐旧任务，但不改写较新 revision。

## 6. TaskView 合并与请求结果隔离

### 6.1 成功响应的合并协议

每个成功回调依次检查：

1. controller 未 disposed；捕获的 identityEpoch/pageEpoch 仍有效，且 ownerId 与当前作用域一致。
2. 资源 key/taskId 与发起请求时一致，响应 ownerId 匹配；完整解析字段、枚举、日期、revision 和合法状态组合。
3. 对任务数据比较 revision：小于已知值丢弃；等于时只允许业务内容一致；大于时接受合法可达的更近快照。
4. 合并后若有效记录已终态，停止该任务跟踪；其他任务继续，不重启终态 timer。

轮询可能跳过中间阶段，不能要求客户端逐条观察相邻转换；PENDING 直接读到 SUCCEEDED 是合法快照跃迁。
但已知终态不能被任意更高 revision 的 RUNNING 覆盖；这种服务端矛盾视为契约异常，保留结论并提示请求问题。
同 revision 内容矛盾同样不采用“后到覆盖”；不能把不同任务的 revision 相互比较。

### 6.2 请求错误与收尾回调

task revision 只解决业务快照乱序，不能解决旧错误/旧 finally 的竞态；还需每资源 requestSeq。
成功数据可以按 revision 合并，但 loading/error、清理 controller、恢复 timer 等请求状态更新只允许当前有效请求序号。
请求开始后若任务已经被历史或其他读取更新为较新 revision/终态，旧错误不能覆盖新请求反馈或重新启动跟踪。

所有 success、catch、finally、timeout 和 Promise 后续回调都执行上下文检查，不能只保护 setTasks。
AbortError 因正常清理而出现时不显示业务失败；无法成功 abort 的旧成功/错误也一律隔离。
客户端单次请求预算为 30 秒；区分“上下文清理取消”与“请求超时取消”，后者在上下文仍有效时展示请求异常并按查询恢复规则处理。
生命周期清理与旧响应隔离是“且”的关系，分别满足 AC-R-08-03 和 AC-R-08-04。

请求取消不是 Java 任务取消。即使旧身份的上传/受理已经在服务端完成，也只能忽略其当前 UI 回调；
以后切回该身份从后端恢复，不能为了 UI 一致性撤销已受理任务或误归属给新身份。

IP-13 已落实此协议：历史和单查共用同一个按 ID 的 reducer；单查反馈使用 resource.seq，页面清理使用实例/generation，身份继续核对 identityEpoch。
更高 revision 的有效数据可合并并使旧在途反馈失效；同版本旧历史不能清理后来单查产生的 pause/error。查询失败结算会同步递增请求序号，显式新读取才可恢复同版本信息的跟踪。
初始历史失败保留 loaded=false，后续历史失败保留旧记录与 loaded 标记，不停止已有的独立任务查询。

## 7. 按任务独立自动跟踪

### 7.1 正常调度

选择每任务递归 setTimeout，间隔 2000ms；等待当前查询 settle 后才考虑下一次，不使用可能重叠的 setInterval。
同一任务同一 controller 最多一个自动查询在途；手动刷新与历史读取可以交错，所以仍需要第 6 节协议。
初始历史中的非终态在 2 秒后跟踪；受理占位立即查询；其后仅对仍有效、未暂停且非终态的任务调度。

| 情形 | timer / 请求变化 |
| --- | --- |
| 首次历史为空或全为终态 | 不创建自动 timer，没有隐蔽的持续全量历史轮询 |
| A、B 两任务未完成 | 各有独立 timer/AbortController，A 的数据或错误不覆盖 B |
| A 结束、B 未结束 | 清理 A 的 timer/在途自动请求，B 保持跟踪 |
| 全部结束 | 无自动 timer/自动请求；允许用户手动查看/刷新历史 |
| 页面离开或身份切换 | 先失效上下文，再清理全部 timer、取消请求，回调不得重建旧资源 |
| 再次进入同页面/身份 | 新 controller + 新 epoch；读取 Java 当前记录后重新派生待跟踪集合 |

调度前读取当前有效状态，不使用请求开始时闭包里的“仍运行”作为唯一判断。
React 开发模式 Effect 的额外 setup/cleanup 周期也必须保持正确，不重复登录、上传或提交。
Effect 清理语义和数据请求忽略过期结果的依据见 [React useEffect](https://react.dev/reference/react/useEffect)。

### 7.2 查询失败与恢复

当前采用简单手动恢复：单任务网络/503/非结构响应错误后保留最后 TaskView，
将该任务的查询设为 paused/error 并停止该任务的自动 timer，显示“状态查询暂不可用/重试”。
其他未失败任务继续自动跟踪。用户重试时立刻读取，成功且仍非终态再恢复 2 秒调度。
这符合 R-07 的恢复途径，不引入自动指数退避系统。

401 使整个身份上下文失效并取消旧请求、清除私有展示，提示重新登录；503 不直接判定会话失效。
404 保留已知快照并暂停该任务，显示资源问题；图片 409/503 只影响媒体请求。
执行 FAILED 以 TaskView.failure 为准，和 queryError 具有不同文案与数据字段。

## 8. 图片预览与资源回收

私有输入或任务结果使用同源 Cookie fetch，先检查 HTTP 成功和真实图片 Content-Type，再从 Blob 创建显示 URL。
显示 URL 按 owner/page/task/resource key 管理，不持久化；组件卸载、替换、身份切换或源资源改变时 revoke。
若旧请求完成后才创建 URL，须立即检查上下文；已失效则直接 revoke，不挂入新视图。
公开模板封面可以普通图片 URL；不把 Cookie/CSRF token 拼进 URL，不将私有内容交给无会话上下文的图片优化器。
Blob 显示与释放参见 [MDN createObjectURL](https://developer.mozilla.org/en-US/docs/Web/API/URL/createObjectURL_static)。

选择文件时的本地预览和服务端输入引用是两套对象；本地预览销毁不删除服务端素材。
成功任务图片访问失败保留 SUCCEEDED 和文字记录，单独提供预览重试，不能改成执行失败或重新生成。

## 9. 行为验证矩阵

实现阶段使用受控响应/Promise 与 fake timers 检查行为，不测试 reducer 与实现逐行同构的细节。

| 场景 | 必须观察 | 来源 |
| --- | --- | --- |
| 连续创建两项，返回不同阶段/错误/结果 | 两项信息独立，受理后按钮不持续 submitting | R-04、R-07、R-08 |
| 单任务查询失败，其他任务正常 | 保留原信息，只有该查询暂停，真实 TaskStatus 不变；手动恢复有效 | R-07、R-08 |
| 初次无任务/全部终态/一项先终态 | 无空转，先终态停止，其他项继续 | AC-R-08-02 |
| 离页/切换身份 | timer/订阅释放、在途尽量取消，旧回调不重启资源 | AC-R-08-03 |
| 退出/重新登录后旧身份返回成功、错误或 finally | 当前任务/错误/loading/定时器均不被污染，包括 A→B→A | AC-R-08-04 |
| 同任务旧 revision 晚到、历史/单查交错 | 新结论不回退；晚到列表不删除刚受理占位 | R-05、R-08、R-09 |
| 上传替换/移除后旧成功/失败返回 | 不绑定旧素材、不复活位置、不覆盖新错误 | R-03 |
| 处理中刷新、成功/失败刷新 | 从 Java 恢复对应身份记录，只有未完成任务继续跟踪 | R-09、R-10 |
| 受理响应丢失/查询失败 | 不自动再次 POST，不伪造后台失败；可查看个人记录 | R-04、R-07 |
| 私有预览失效、退出/登录切换 | 无凭证 URL，旧 Blob 不挂入新视图，资源回收不改 TaskStatus | R-03、R-09、R-10 |

另外检查登录/退出串行、Cookie/CSRF 透传、会话过期、跨标签页通知/聚焦复核和旧 token 写入拒绝。
R-12 强制的核心测试包含多任务不串扰、查询异常与执行失败区分；其余通过相关测试或明确人工验收覆盖。
可用性另作桌面/小屏、键盘与标签人工检查，不要求指定像素、覆盖率或额外 E2E 框架。
上述表格是整体计划；IP-11 身份/传输证据见 V-11，IP-12 上传/模板与本地预览证据见 V-12，IP-13 任务快照/轮询的 controller 与 React 宿主证据见 V-13。
IP-14 通过 ProductionWorkspace / TaskHistory 组合上述 hooks；ResultPreview 以身份/任务/结果路径为 key，私有 JPEG 经 fetch/blob 显示和回收。
制作/历史/媒体组合的行为测试与真实 HTTP 联调见 V-14；浏览器外观、键盘、小屏和实际解码人工验收尚未通过。
