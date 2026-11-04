# AIFriend

Android 端「AI 好友 / AI 智能体团队」应用：用户创建带角色设定的 AI 好友，选择模型，与它多轮对话，并在首页统一管理。

## 当前进度

- ✅ 首页：智能体列表、搜索、创建入口、状态卡（数据已接真实状态）
- ✅ **7 家厂商对话闭环**：DeepSeek、豆包（火山方舟）、智谱清言、Kimi、通义千问、讯飞星火、Gemini
- ✅ 创建页支持切换模型提供方，模型列表按厂商分别拉取并缓存
- ✅ 统一走 OpenAI 兼容的 Chat Completions 协议，角色 system prompt + 多轮历史
- ✅ **关闭深度思考**：创建好友时可切换（默认关闭），按厂商发送 `thinking` / `enable_thinking` / `reasoning_effort`
- ✅ **本地持久化**：好友、绑定模型、思考开关、完整聊天记录（DataStore + JSON 快照），重启后恢复
- ✅ **聊天室 P0**：多好友房间、@ 指定本轮成员、单个/全部停止生成、房间设置（改名 + 房间设定）、每个 AI 独立上下文与并发回答、单 AI 重试、成员增删、房间与消息持久化
- ✅ **自定义头像**：好友和用户都支持从相册选头像，列表 / 1:1 对话 / 聊天室全部显示真实头像，没设置时用首字 + 稳定配色兜底
- ✅ **删除智能体**：清空它的会话历史、房间成员关系与房间消息、头像文件，之后不会再把这些内容发给模型
- ✅ **编辑智能体**：长按列表项修改昵称、角色、设定、头像、思考开关
- ⏳ 流式输出、思考过程展示
- ⏳ `feature:werewolf` / `ui:werewolf` 仍是空模块（AI 狼人杀，规划中）

## 模块

| 模块 | 职责 |
|---|---|
| `app` | 单 Activity 入口，挂载 `FriendRoute` |
| `feature:friend` | 好友列表 / 创建 / 对话的状态机与 ViewModel |
| `ui:friend` | 首页、创建页、对话页等 Compose 组件 |
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

**本轮成员**：输入框上方有一排「全员 / 成员」chip，可以只勾选本轮要回答的 AI，避免一条消息触发 N 次请求；至少保留一位。

**停止生成**：每个「正在输入…」右侧有「停止」，顶栏在有人生成时也会出现「停止」按钮，用于一次取消全部；取消只丢弃本次请求，不会写入消息。

**房间设置**：顶栏齿轮进入设置页，可改房间名、填写「房间设定」（会拼在每位成员的 system prompt 前面，例如「你们是一个评审团，先各自给结论」），以及管理成员。

**上下文策略（方案 B）**：每个 AI 只看到「房间内所有用户消息 + 它自己的历史回复」，看不到其他 AI 的回复，避免互相污染。实现是 `buildRoomContext(messages, friendId)` 一次过滤，以后要升级成「共享全文（方案 C）」只需改这一处。

- 发送：用户消息先落库并显示，然后对每个成员并发 `chat()`，谁先回谁先显示
- 并发：每个「房间 + AI」一把 `Mutex`，用户连发消息时同一 AI 的请求串行执行，上下文不会串
- 状态：每个 AI 独立的「正在输入」和错误提示，失败只影响它自己，可单独重试
- UI：单列时间流，用户消息右对齐、AI 回复左对齐并署名；顶部「成员」弹窗可随时增删成员（至少保留 1 位）
- 系统消息：创建房间、「X 加入了聊天室」「X 退出了聊天室」会插入灰色居中的系统提示；这些通知会以「（聊天室通知）」前缀作为 user 消息同步给房间内的 AI，保证它们知道人员变化（不用 system 角色是为了兼容只允许首条 system 的厂商，如 Moonshot）
- 新成员：`Room.memberJoinSeq` 记录每个成员的加入位置，新加入的 AI 只看得到加入之后的消息，不会同步历史；加入时会自动发一条自我介绍式问候（创建房间时不自动打招呼）
- 持久化：房间、成员、消息都在 Room 的 `rooms` / `room_members` / `room_messages` 表；数据库 `version = 2` 起 `rooms` 增加房间设定 `prompt` 字段（v1 → v2 有 Migration）
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

每次请求前会用 `trimToBudget()` 截断发给模型的历史（`core` 之外，位于 `feature/friend/data/ContextBudget.kt`）：

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
- 版本：数据库 `version = 1`，后续加字段需要补 Room `Migration`

> 构建用 KSP 做 Room 注解处理（`ksp = 2.3.6`，匹配 AGP 9 内置 Kotlin）。AGP 9 默认禁止 KSP 通过 `kotlin.sourceSets` 注入生成目录，`gradle.properties` 里用 `android.disallowKotlinSourceSets=false` 放行。

## 已知待办

- `core/network/src/main/java/com/core/network/key.kt` 是本地密钥文件，已被 `.gitignore` 忽略（模板见 `core/network/key.example.kt`）；但它仍会被编译进 APK，正式发布前必须轮换并改为用户自带 Key 或后端代理
- 好友与对话历史已持久化；支持长按编辑/删除好友、长按删除聊天室，但暂不支持重命名、清空单个会话、批量删除和导出功能
- 编辑好友时暂不支持切换厂商 / 模型
- Room 当前是 `version = 1`，加字段时要写 Migration；消息表目前全量加载，没有分页
- 头像裁剪暂不支持旋转/翻转手势；相机照片的 EXIF 旋转也还没处理
- 上下文目前用「滑动窗口 + 字符估算」控制，早期内容会直接丢弃；需要长期记忆时应改成摘要压缩或向量检索
- 聊天室下一步：串行发言、AI 互见开关（圆桌模式）、消息时间戳、分页与搜索
- 聊天记录无上限，长期使用后 JSON 快照会变大，届时需要迁移 Room 或做分页裁剪
- 豆包模型选择列表包含账号未开通的模型，选中后会提示 404，后续可在控制台开通或按账号能力过滤
- Gemini 需要能访问 Google（本机需走代理），且测试账号返回 `402 预付款额度耗尽`，代码链路已通但无法实际对话
- 讯飞星火只确认了 `x1` 可用，且没有模型列表接口；后续需要按官方文档补齐其它模型
- 智谱 GLM-5 系列不支持完全关闭思考，只能降到 low
