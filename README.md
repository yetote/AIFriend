# AIFriend

Android 端「AI 好友 / AI 智能体团队」应用：用户创建带角色设定的 AI 好友，选择模型，与它多轮对话，并在首页统一管理。

## 当前进度

- ✅ 首页：智能体列表、搜索、创建入口、状态卡（数据已接真实状态）
- ✅ **7 家厂商对话闭环**：DeepSeek、豆包（火山方舟）、智谱清言、Kimi、通义千问、讯飞星火、Gemini
- ✅ 创建页支持切换模型提供方，模型列表按厂商分别拉取并缓存
- ✅ 统一走 OpenAI 兼容的 Chat Completions 协议，角色 system prompt + 多轮历史
- ✅ **关闭深度思考**：创建好友时可切换（默认关闭），按厂商发送 `thinking` / `enable_thinking` / `reasoning_effort`
- ✅ **本地持久化**：好友、绑定模型、思考开关、完整聊天记录（DataStore + JSON 快照），重启后恢复
- ✅ **聊天室 P0**：多好友房间、用户消息广播给全体、每个 AI 独立上下文与并发回答、单 AI 重试、成员增删、房间与消息持久化
- ✅ **自定义头像**：好友和用户都支持从相册选头像，列表 / 1:1 对话 / 聊天室全部显示真实头像，没设置时用首字 + 稳定配色兜底
- ⏳ 流式输出、思考过程展示
- ⏳ `feature:werewolf` / `ui:werewolf` 仍是空模块（AI 狼人杀，规划中）

## 模块

| 模块 | 职责 |
|---|---|
| `app` | 单 Activity 入口，挂载 `FriendRoute` |
| `feature:friend` | 好友列表 / 创建 / 对话的状态机与 ViewModel |
| `ui:friend` | 首页、创建页、对话页等 Compose 组件 |
| `core:ai` | `IAI` 抽象、各厂商实现、对话与模型 DTO |
| `core:repository` | `WebRepository`、各厂商 `ApiConfig`、`FriendStore` 本地存储 |
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

房间把多个好友放在一起，用户发一条消息，房间内所有 AI 各自回答。

**上下文策略（方案 B）**：每个 AI 只看到「房间内所有用户消息 + 它自己的历史回复」，看不到其他 AI 的回复，避免互相污染。实现是 `buildRoomContext(messages, friendId)` 一次过滤，以后要升级成「共享全文（方案 C）」只需改这一处。

- 发送：用户消息先落库并显示，然后对每个成员并发 `chat()`，谁先回谁先显示
- 并发：每个「房间 + AI」一把 `Mutex`，用户连发消息时同一 AI 的请求串行执行，上下文不会串
- 状态：每个 AI 独立的「正在输入」和错误提示，失败只影响它自己，可单独重试
- UI：单列时间流，用户消息右对齐、AI 回复左对齐并署名；顶部「成员」弹窗可随时增删成员（至少保留 1 位）
- 持久化：房间存 `rooms_v1`、消息存 `room_messages_v1`，与 1:1 的 `friends_v1` 分开，互不干扰
- 已有消息在成员被移除后仍保留，界面上会显示「已移除」

## 自定义头像

- 入口：创建好友页选择头像；创建完成后在 1:1 对话页点头像可更换；首页右上角头像用于设置用户自己的头像
- 选图：系统 Photo Picker（`ActivityResultContracts.PickVisualMedia`），不需要存储权限
- 处理：`AvatarStore` 把图片采样解码、居中裁成正方形、缩放到 256×256 JPEG，存到 `files/avatars/<id>_<时间戳>.jpg`，并清理同 id 的旧文件
- 展示：`UIAvatar` 统一组件，有图片显示图片，没有就显示名字首字 + 按名字稳定的配色
- 持久化：好友头像路径存在 `StoredFriend.avatarPath`，用户头像路径存在 `user_avatar_v1`

## 数据持久化

- 存储实现：`core/repository/.../storage/FriendStore.kt`（好友）、`RoomStore.kt`（聊天室），共用同一个 DataStore(Preferences) 文件，按 key 分区，文件在 `files/datastore/aifriend.preferences_pb`
- 存储模型：`StoredFriend` / `StoredMessage`、`StoredRoom` / `StoredRoomMessage`，与业务模型通过 Mapper 互转，避免 UI 字段变化直接污染存储结构
- 写入时机：创建好友、发送消息、收到回复后；写入走 `Channel(CONFLATED)` 单消费者串行落盘，避免一轮对话两次写并发乱序
- 读取时机：ViewModel 初始化时加载，随后再拉模型列表；加载完成前用户已创建的好友不会被覆盖
- 为什么先不用 Room：当前数据量小、只有「整列表」一种访问方式；等消息量变大需要分页/搜索/按会话查询时，再替换为 Room（`StoredFriend` / `StoredRoomMessage` 结构可平移）
- 聊天室的写盘同样走 `Channel(CONFLATED)` 单消费者队列，房间与消息分别持久化

## 已知待办

- `core/network/src/main/java/com/core/network/key.kt` 是本地密钥文件，已被 `.gitignore` 忽略（模板见 `core/network/key.example.kt`）；但它仍会被编译进 APK，正式发布前必须轮换并改为用户自带 Key 或后端代理
- 好友与对话历史已持久化，但暂不支持删除/重命名好友、清空会话，也没有导出功能
- 头像目前只做居中裁切，没有手动调整裁剪框；相机照片的 EXIF 旋转尚未处理
- 聊天室目前是 P0：没有停止生成、@ 指定某个 AI、分栏对比、主持人总结和「共享全文」模式
- 聊天记录无上限，长期使用后 JSON 快照会变大，届时需要迁移 Room 或做分页裁剪
- 豆包模型选择列表包含账号未开通的模型，选中后会提示 404，后续可在控制台开通或按账号能力过滤
- Gemini 需要能访问 Google（本机需走代理），且测试账号返回 `402 预付款额度耗尽`，代码链路已通但无法实际对话
- 讯飞星火只确认了 `x1` 可用，且没有模型列表接口；后续需要按官方文档补齐其它模型
- 智谱 GLM-5 系列不支持完全关闭思考，只能降到 low
