# AIFriend

Android 端「AI 好友 / AI 智能体团队」应用：用户创建带角色设定的 AI 好友，选择模型，与它多轮对话，并在首页统一管理。

## 当前进度

- ✅ 首页：智能体列表、搜索、创建入口、状态卡（数据已接真实状态）
- ✅ **7 家厂商对话闭环**：DeepSeek、豆包（火山方舟）、智谱清言、Kimi、通义千问、讯飞星火、Gemini
- ✅ 创建页支持切换模型提供方，模型列表按厂商分别拉取并缓存
- ✅ 统一走 OpenAI 兼容的 Chat Completions 协议，角色 system prompt + 多轮历史
- ✅ **关闭深度思考**：创建好友时可切换（默认关闭），按厂商发送 `thinking` / `enable_thinking` / `reasoning_effort`
- ✅ **本地持久化**：好友、绑定模型、思考开关、完整聊天记录（Room / SQLite），重启后恢复
- ✅ **聊天室 P0**：多好友房间、@ 指定本轮成员、单个/全部停止生成、房间设置（改名 + 房间设定）、讨论模式（窗口互见 + 顺序发言）、清空房间消息、每个 AI 独立上下文与并发回答、单 AI 重试、成员增删、房间与消息持久化
- ✅ **聊天室 P1**：消息时间戳与日期分割、长按消息复制 / 引用、消息分页加载、成员上限与角色/模型展示、移除确认、创建时搜索、房间置顶与排序
- ✅ **聊天室 P2**：房间名搜索、房间消息搜索、Markdown 导出与系统分享、房间头像、房间模板、Token / 请求数估算
- ✅ **自定义头像**：好友和用户都支持从相册选头像，列表 / 1:1 对话 / 聊天室全部显示真实头像，没设置时用首字 + 稳定配色兜底
- ✅ **删除智能体**：清空它的会话历史、房间成员关系与房间消息、头像文件，之后不会再把这些内容发给模型
- ✅ **编辑智能体**：长按列表项修改昵称、角色、设定、头像、思考开关
- ⏳ 流式输出、思考过程展示
- ✅ **iOS 风格 UI**：全局浅灰蓝渐变底 + 毛玻璃卡片、大圆角、柔和投影，统一到首页 / 1:1 对话 / 聊天室 / 创建与设置 / 全部弹窗
- ⏳ `feature:werewolf` / `ui:werewolf` 仍是空模块（AI 狼人杀，规划中）

## 模块

| 模块 | 职责 |
|---|---|
| `app` | 单 Activity 入口，挂载 `HomeRoute` |
| `feature:home` | 首页状态、搜索、统计、删除/置顶、导航 |
| `feature:agent` | 智能体创建/编辑、1:1 会话、`AgentViewModel` / `AgentRoute` |
| `feature:room` | 聊天室创建/设置、消息与成员、`RoomViewModel` / `RoomRoute` |
| `ui:common` | 头像、裁剪、PageInsets、主题 |
| `ui:agent` | 1:1 对话、创建/编辑、好友列表组件 |
| `ui:room` | 房间对话、创建、设置 |
| `ui:home` | 首页 Compose 组件 |
| `core:ai` | `IAI` 抽象、各厂商实现、对话与模型 DTO |
| `core:repository` | `WebRepository`、各厂商 `ApiConfig`、Room 数据库与 Repository |
| `core:network` | Ktor 客户端、超时、错误映射 |
| `core:common` | 全局 Toast 事件、BaseViewModel、主题工具 |

## 构建

需要 JDK 21（Gradle 会读取 `gradle/gradle-daemon-jvm.properties`），SDK 路径见 `local.properties`。

首次构建前需要准备本地密钥文件（该文件已被 `.gitignore` 忽略，不会提交）：

```bash
cp core/network/key.example.kt core/network/src/main/java/com/core/network/key.kt
# 然后在 key.kt 里填入各厂商的 Key
```

```bash
./gradlew :app:assembleDebug
./gradlew :core:ai:testDebugUnitTest
```

## 对话协议

所有厂商共用 `core:ai/entity/OpenAICompatibleAI.kt`：请求体为 `{ model, messages: [{role, content}], stream }`，回复取 `choices[0].message.content`；差异只在 `ApiConfig` 和模型列表解析。`Friend.systemPrompt` 把「昵称 + 角色 + 详细设定」拼成 `system` 消息放在历史之前。

| 厂商 | 对话地址 | 模型列表 | 过滤规则 / 备注 |
|---|---|---|---|
| DeepSeek | `api.deepseek.com/chat/completions` | `GET /models` | 直接用 |
| 豆包（方舟） | `ark.cn-beijing.volces.com/api/v3/chat/completions` | `GET /api/v3/models` | `api_supported["chat/completions"]`、非 Retiring、`doubao-` 前缀 |
| 智谱清言 | `open.bigmodel.cn/api/paas/v4/chat/completions` | `GET /api/paas/v4/models` | `glm-` 前缀 |
| Kimi | `api.moonshot.cn/v1/chat/completions` | `GET /v1/models` | 直接用 |
| 通义千问 | `dashscope.aliyuncs.com/compatible-mode/v1/chat/completions` | `GET /compatible-mode/v1/models` | 183 个模型中排除向量/重排/语音/图像/翻译等，只留 `qwen` 对话模型 |
| 讯飞星火 | `spark-api-open.xf-yun.com/x2/chat/completions` | 无（404） | 固定 `x1` |
| Gemini | `generativelanguage.googleapis.com/v1beta/openai/chat/completions` | `GET /v1beta/openai/models` | 官方 OpenAI 兼容层，去掉 `models/` 前缀；排除 embedding / live / audio / image / omni 等 |

## 关闭深度思考

创建好友时可以切换「深度思考」，默认关闭。各厂商参数不同，已在 `AI*` 实现里覆盖：

| 厂商 | 关闭方式 | 实测 |
|---|---|---|
| DeepSeek | `thinking: {"type":"disabled"}` | reasoning 归零 |
| 豆包 | `thinking: {"type":"disabled"}` | reasoning 归零 |
| Kimi | `thinking: {"type":"disabled"}` | reasoning 归零 |
| 通义千问 | `enable_thinking: false` | reasoning 归零 |
| 智谱 GLM-4.x | `thinking: {"type":"disabled"}` | reasoning 归零 |
| 智谱 GLM-5.x | 只能降档：`reasoning_effort: "low"`（传 disabled 会 400） | reasoning 明显缩短 |
| 星火 / Gemini | 暂无对应参数，UI 不显示开关 | — |

> 方舟账号需要先在控制台「开通」具体模型，未开通的模型即便出现在 `/models` 里，调用也会返回 `404 ModelNotOpen`。当前测试账号只开通了 `doubao-seed-evolving`，因此它被设为豆包默认模型。

## 聊天室

房间把多个好友放在一起，用户发一条消息，默认由房间内所有 AI 各自回答。

**定向 @**：在输入框里输入 `@` 会弹出成员选择器，选中后插入 `@名字`，并把成员 ID 一起存进这条消息。**被 @ 的成员才回答，这一轮对其他成员不可见**；没选 @ 时全员可见。成员改名/删除不会影响历史消息的可见性。

**停止生成**：每个「正在输入…」右侧有「停止」，顶栏在有人生成时也会出现「停止」按钮，用于一次取消全部；取消只丢弃本次请求，不会写入消息。

**房间设置**：顶栏齿轮进入设置页，可改房间名、填写「房间设定」（会拼在每位成员的 system prompt 前面，例如「你们是一个评审团，先各自给结论」），以及管理成员。

**讨论模式（窗口互见）**：设置里可开启，其他 AI 的发言只保留最近 **1–5 轮**（按用户消息划分轮次），以「（某成员 的发言）」的 user 消息注入。开启讨论模式、以及调大回看窗口时都会弹窗提示 token 会同步上涨。讨论模式下每位 AI 的上下文预算从 6000 降到 **4000**，窗口越大，其他人的发言占用的额度越多。

**顺序发言**：讨论模式下可让成员按顺序依次回答，后一位等前一位结束，因此能看到本轮前面的回复；关闭则并发回答（并发时同一轮内互相看不到）。

**清空聊天记录**：房间设置页底部的「危险操作」，显示当前消息条数，二次确认后删除本房间的全部消息；房间、成员、讨论设置都会保留。清空时同时把成员的 `joinSeq` 重置为 0，避免清空后新消息被旧的加入位置过滤。正在进行的请求会被取消。

> 取舍：窗口互见只解决「其他人发言」的膨胀；自己的历史仍受 4000 token 预算约束。再往后如果要长期讨论，需要上「滚动摘要」（共享纪要 + 最近消息窗口）。

**上下文策略（方案 B）**：每个 AI 只看到「房间内所有用户消息 + 它自己的历史回复」，看不到其他 AI 的回复，避免互相污染。实现是 `buildRoomContext(messages, friendId)` 一次过滤，以后要升级成「共享全文（方案 C）」只需改这一处。

- 发送：用户消息先落库并显示，然后对每个成员并发 `chat()`，谁先回谁先显示
- 分页：打开房间只加载最近 50 条，滚到顶部自动补更早的 50 条；分页不影响 AI 上下文，上下文仍按 token 预算截断
- 并发：每个「房间 + AI」一把 `Mutex`，用户连发消息时同一 AI 的请求串行执行，上下文不会串
- 状态：每个 AI 独立的「正在输入」和错误提示，失败只影响它自己，可单独重试
- UI：单列时间流，用户消息右对齐、AI 回复左对齐并署名；每条消息带 HH:mm，跨天自动插入「今天 / 昨天 / 日期」分割；长按气泡可复制或引用（引用会把「发送者 + 摘录」插到输入框开头）；顶部「成员」弹窗显示角色/模型、移除需二次确认、最多 6 位
- 房间列表：置顶优先，其余按创建时间；首页长按房间可置顶/取消置顶/删除；创建房间支持按昵称 / 角色 / 模型搜索
- 搜索：首页搜索框同时过滤聊天室名；房间顶栏放大镜搜索已加载消息，点结果跳到对应消息
- 导出：房间设置页「导出聊天记录」会读取整屋消息生成 Markdown，通过系统分享面板发送
- 房间头像：设置页点头像选图并裁剪，首页房间卡片和房间顶栏都会显示
- 房间模板：新建房间提供「评审团 / 头脑风暴 / 角色扮演」，点一下填入房间设定
- Token 估算：设置页显示「本轮约 N 次请求、单个 AI 上下文约 X tokens」，按字符估算，仅作参考
- 系统消息：创建房间、「X 加入了聊天室」「X 退出了聊天室」会插入灰色居中的系统提示；这些通知会以「（聊天室通知）」前缀作为 user 消息同步给房间内的 AI，保证它们知道人员变化（不用 system 角色是为了兼容只允许首条 system 的厂商，如 Moonshot）
- 新成员：`Room.memberJoinSeq` 记录每个成员的加入位置，新加入的 AI 只看得到加入之后的消息，不会同步历史；加入时会自动发一条自我介绍式问候（创建房间时不自动打招呼）
- 持久化：房间、成员、消息都在 Room 的 `rooms` / `room_members` / `room_messages` 表；数据库 `version = 6`，`rooms` 表保存房间设定 `prompt`、讨论开关 `discussionEnabled`、回看窗口 `discussionWindow`、顺序发言 `sequentialSpeaking`、置顶 `pinned`、头像 `avatarPath`；`room_messages` 保存定向 @ 的成员 `targetIds`（v1 → v2 → v3 → v4 → v5 → v6 都有 Migration）
- 消息 `seq` 在应用启动时会从历史最大值继续（`seqCounter` 种子），避免重启后新消息的 seq 小于成员 `joinSeq`，导致新成员看不到新消息
- 已有消息在成员被移除后仍保留，界面上会显示「已移除」

## 自定义头像

- 入口：创建好友页选择头像；创建完成后在 1:1 对话页点头像可更换；首页右上角头像用于设置用户自己的头像
- 选图：系统 Photo Picker（`ActivityResultContracts.PickVisualMedia`），不需要存储权限
- 裁剪：选图后进入 `UIAvatarCropDialog`，双指缩放、拖动选择范围，带三分线辅助构图，确认后才输出正方形 JPEG
- 处理：`AvatarStore` 把裁剪结果缩放到 256×256 存到 `files/avatars/<id>_<时间戳>.jpg`，并清理同 id 的旧文件
- 展示：`UIAvatar` 统一组件，有图片显示图片，没有就显示名字首字 + 按名字稳定的配色
- 持久化：好友头像路径存在 `StoredFriend.avatarPath`，用户头像路径存在 `user_avatar_v1`

## 编辑智能体

入口：首页好友列表**长按好友** → 「编辑资料」。

可修改：

- 昵称、角色、角色设定
- 头像
- 深度思考开关

厂商和模型在编辑时只读（避免换模型后历史上下文语义不一致），需要换模型时删除后重建。

## 删除智能体

入口：首页好友列表**长按好友** → 「删除好友」→ 二次确认（对话页右上角原来的垃圾桶已移除）。

一次删除会清理：

- 好友本身和它的完整会话历史（`Friend.messages`），之后不会再作为上下文发送给模型
- 所有聊天室里的成员关系与 `memberJoinSeq`
- 它在聊天室发过的所有消息（避免残留内容和上下文）
- 头像文件 `files/avatars/<id>_*.jpg`
- 正在进行的请求结果会被丢弃（返回时校验好友是否还存在）
- 如果它在某个房间，会追加一条系统提示「X 已被删除并移出聊天室」

## 删除聊天室

入口：首页「聊天室」卡片**长按** → 二次确认。

删除会同时清掉 Room 里的 `rooms` / `room_members`（外键级联）/ `room_messages`，不可恢复。

> 关于「模型侧的上下文」：本项目的调用是**无状态**的——每次请求都把历史消息随请求发送，没有使用厂商的 thread / session / `previous_response_id`。因此删除本地历史后，这些内容不会再发给模型；厂商是否保留请求日志由各厂商政策决定，chat completions 接口没有提供删除接口。

## 上下文预算

每次请求前会用 `trimToBudget()` 截断发给模型的历史（`core` 之外，位于 `feature/agent/data/ContextBudget.kt`）：

- 从最新消息往回保留，默认预算 **6000 估算 token**，至少保留 **6 条**，保证最后一轮完整
- 只影响请求内容，本地展示和持久化的历史不受影响
- 估算规则：CJK 字符按 1 字符 ≈ 1 token，其它字符按 4 字符 ≈ 1 token（不引入各厂商 tokenizer）
- 聊天室先按「加入位置 + 方案 B」构建上下文，再统一截断
- 这样单轮输入成本有上界，不会随对话长度无限增长

## 数据持久化

存储已从 DataStore 迁移到 **Room（SQLite）**，数据库文件 `aifriend.db`。

- 实现位置：`core/repository/.../storage/db/`
  - `friends` / `friend_messages`（外键级联删除）
  - `rooms` / `room_members`（联合主键，记录 `joinSeq`）/ `room_messages`
  - `profile`（用户头像路径）
- 写入：增量写，不再是整包 JSON 快照
  - 加好友 / 改头像：`upsert` 或单字段更新
  - 消息：逐条 `insert`（自增 id 保证顺序）
  - 删除好友：级联删除会话消息，同时清理房间成员和它在房间里的消息
- 读取：ViewModel 初始化时通过 `FriendRepository` / `RoomRepository` 加载
- 迁移：首次启动 `LegacyMigration` 会把旧 DataStore 的 `friends_v1` / `rooms_v1` / `room_messages_v1` / `user_avatar_v1` 一次性导入 Room，成功后清掉旧 key；DataStore 现在只用于这次迁移
- 版本：数据库 `version = 6`，后续加字段需要补 Room `Migration`

> 构建用 KSP 做 Room 注解处理（`ksp = 2.3.6`，匹配 AGP 9 内置 Kotlin）。AGP 9 默认禁止 KSP 通过 `kotlin.sourceSets` 注入生成目录，`gradle.properties` 里用 `android.disallowKotlinSourceSets=false` 放行。

## 已知待办

- `core/network/src/main/java/com/core/network/key.kt` 是本地密钥文件，已被 `.gitignore` 忽略（模板见 `core/network/key.example.kt`）；但它仍会被编译进 APK，正式发布前必须轮换并改为用户自带 Key 或后端代理
- 好友与对话历史已持久化；支持长按编辑/删除好友、长按删除聊天室，但暂不支持重命名、清空单个会话、批量删除和导出功能
- 编辑好友时暂不支持切换厂商 / 模型
- Room 当前是 `version = 1`，加字段时要写 Migration；消息表目前全量加载，没有分页
- 头像裁剪暂不支持旋转/翻转手势；相机照片的 EXIF 旋转也还没处理
- 上下文目前用「滑动窗口 + 字符估算」控制，早期内容会直接丢弃；需要长期记忆时应改成摘要压缩或向量检索
- 聊天室下一步：滚动摘要（共享纪要）、单条消息删除、Markdown 渲染
- 聊天记录已改成 Room 分页读取，长期使用后单房间消息量变大时，重点在滚动性能和索引，而不是整包快照
- 豆包模型选择列表包含账号未开通的模型，选中后会提示 404，后续可在控制台开通或按账号能力过滤
- Gemini 需要能访问 Google（本机需走代理），且测试账号返回 `402 预付款额度耗尽`，代码链路已通但无法实际对话
- 讯飞星火只确认了 `x1` 可用，且没有模型列表接口；后续需要按官方文档补齐其它模型
- 智谱 GLM-5 系列不支持完全关闭思考，只能降到 low
