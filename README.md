# AIFriend

Android 端「AI 好友 / AI 智能体团队」应用：用户创建带角色设定的 AI 好友，选择模型，与它多轮对话，并在首页统一管理。

## 当前进度

- ✅ 首页：智能体列表、搜索、创建入口、状态卡（数据已接真实状态）
- ✅ **DeepSeek 闭环**：创建好友 → 拉取 `/models` → 多轮聊天（请求注入角色 system prompt）→ 失败提示 + 重试
- ⏳ 其它厂商（豆包 / 智谱 / Kimi / 通义 / Gemini / 星火）尚未迁移到多轮对话协议
- ⏳ 会话与好友持久化（当前只在内存，重启丢失）
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

```bash
./gradlew :app:assembleDebug
./gradlew :core:ai:testDebugUnitTest
```

## DeepSeek 协议

- 模型列表：`GET https://api.deepseek.com/models`（`Authorization: Bearer <key>`）
- 对话：`POST https://api.deepseek.com/chat/completions`，body 为 `{ model, messages: [{role, content}], stream }`，回复取 `choices[0].message.content`
- 角色人格：`Friend.systemPrompt` 把「昵称 + 角色 + 详细设定」拼成 `system` 消息，放在历史消息之前

## 已知待办

- `core/network/src/main/java/com/core/network/key.kt` 中仍是明文测试 Key，正式使用前必须轮换并改为用户自带 Key 或后端代理
- 好友与对话历史尚未持久化（DataStore / Room 依赖已就绪但未使用）
