# AIFriend

Android 端「AI 好友 / AI 智能体团队」应用：用户创建带角色设定的 AI 好友，选择模型，与它多轮对话，并在首页统一管理。

## 当前进度

- ✅ 首页：智能体列表、搜索、创建入口、状态卡（数据已接真实状态）
- ✅ **7 家厂商对话闭环**：DeepSeek、豆包（火山方舟）、智谱清言、Kimi、通义千问、讯飞星火、Gemini
- ✅ 创建页支持切换模型提供方，模型列表按厂商分别拉取并缓存
- ✅ 统一走 OpenAI 兼容的 Chat Completions 协议，角色 system prompt + 多轮历史
- ✅ **关闭深度思考**：创建好友时可切换（默认关闭），按厂商发送 `thinking` / `enable_thinking` / `reasoning_effort`
- ⏳ 会话与好友持久化（当前只在内存，重启丢失）
- ⏳ 流式输出、思考过程展示
- ⏳ `feature:werewolf` / `ui:werewolf` 仍是空模块（AI 狼人杀，规划中）

## 模块

| 模块 | 职责 |
|---|---|
| `app` | 单 Activity 入口，挂载 `FriendRoute` |
| `feature:friend` | 好友列表 / 创建 / 对话的状态机与 ViewModel |
| `ui:friend` | 首页、创建页、对话页等 Compose 组件 |
| `core:ai` | `IAI` 抽象、各厂商实现、对话与模型 DTO |
| `core:repository` | `WebRepository` 与各厂商 `ApiConfig` |
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

## 已知待办

- `core/network/src/main/java/com/core/network/key.kt` 是本地密钥文件，已被 `.gitignore` 忽略（模板见 `core/network/key.example.kt`）；但它仍会被编译进 APK，正式发布前必须轮换并改为用户自带 Key 或后端代理
- 好友与对话历史尚未持久化（DataStore / Room 依赖已就绪但未使用）
- 豆包模型选择列表包含账号未开通的模型，选中后会提示 404，后续可在控制台开通或按账号能力过滤
- Gemini 需要能访问 Google（本机需走代理），且测试账号返回 `402 预付款额度耗尽`，代码链路已通但无法实际对话
- 讯飞星火只确认了 `x1` 可用，且没有模型列表接口；后续需要按官方文档补齐其它模型
- 智谱 GLM-5 系列不支持完全关闭思考，只能降到 low
