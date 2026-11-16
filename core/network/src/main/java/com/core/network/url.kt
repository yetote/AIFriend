package com.core.network

// 官方厂商的 OpenAI 兼容地址。baseUrl 只在这里维护一份，
// 全 App 通过 com.core.repository.settings.AIProviderCatalog 取用。
// Gemini 走官方 OpenAI 兼容层：/v1beta/openai/chat/completions、/v1beta/openai/models
const val URL_GEMINI="https://generativelanguage.googleapis.com/v1beta/openai"
const val URL_DEEPSEEK="https://api.deepseek.com"
const val URL_DOUBAO="https://ark.cn-beijing.volces.com/api/v3"
const val URL_ZHIPU="https://open.bigmodel.cn/api/paas/v4"
const val URL_KIMI="https://api.moonshot.cn/v1"
const val URL_QIANWEN="https://dashscope.aliyuncs.com/compatible-mode/v1"
// 混元：Hy3 / Hy4 这些新模型只在 TokenHub 上，老的 api.hunyuan.cloud.tencent.com
// 已停止新增模型能力。端点分地域且不能跨地域调用，这里用广州（大陆）。
// 新加坡地域是 https://tokenhub-intl.tencentmaas.com/v1
const val URL_HUNYUAN="https://tokenhub.tencentmaas.com/v1"
// MiniMax 国内版。海外版是 api.minimax.io，国内域名结尾多一个 i，很容易打错。
const val URL_MINIMAX="https://api.minimaxi.com/v1"
// 小米 MiMo 开放平台，按量付费入口（Token Plan 是另一个域名，不走这里）
const val URL_MIMO="https://api.xiaomimimo.com/v1"
const val URL_SPARK="https://spark-api-open.xf-yun.com/x2"
