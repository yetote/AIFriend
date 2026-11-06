package com.core.ai.model

/**
 * 硅基流动的模型价格快照（单位：人民币 / 百万 token）。
 *
 * 为什么要内置快照，而不是运行时拉取：
 * 硅基流动的 `GET /v1/models` 只返回 id / owned_by，**没有任何价格字段**；
 * 价格只存在于 https://www.siliconflow.cn/pricing 那张服务端渲染的 HTML 表格里，
 * 没有对外的 JSON 接口。让 App 每次启动去抓一两百 KB 的 HTML 再正则解析，
 * 页面结构一改就会静默失效，代价和风险都远高于收益。
 *
 * 因此这里把官方价格页的内容固化成快照，取值口径：
 * - 阶梯价取最低档（`输入 [0, 128k)` 这类），代表常规上下文的价格；
 * - 有时段折扣的模型取**标准时段**（不打折那档），避免让用户以为随时都这么便宜；
 * - 免费模型记 0。
 *
 * 快照会过期，所以带上抓取日期，UI 上标注为「参考价」以免误导。
 * 表里查不到的模型不显示价格 —— 显示错的价，比如实便宜更糟。
 */
internal object SiliconFlowPriceSnapshot {

    /** 抓取日期，用于在 UI 上标注数据新旧。 */
    const val CAPTURED_AT = "2026-02-14"

    /** 价格页上的标价货币。 */
    const val CURRENCY = "¥"

    val prices: Map<String, ModelPrice> = mapOf(
        "ByteDance-Seed/Seed-OSS-36B-Instruct" to ModelPrice(1.5, 4.0),
        "Pro/deepseek-ai/DeepSeek-V3.2" to ModelPrice(4.0, 6.0),
        "Pro/moonshotai/Kimi-K2.6" to ModelPrice(6.5, 27.0),
        "Pro/zai-org/GLM-5.1" to ModelPrice(
            inputPerMillion = 6.0,
            outputPerMillion = 24.0,
            bands = listOf(
                PriceBand(fromTokens = 32_000L, inputPerMillion = 8.0, outputPerMillion = 28.0)
            )
        ),
        "Qwen/Qwen3.5-122B-A10B" to ModelPrice(
            inputPerMillion = 0.8,
            outputPerMillion = 6.4,
            bands = listOf(
                PriceBand(fromTokens = 128_000L, inputPerMillion = 2.0, outputPerMillion = 16.0)
            )
        ),
        "Qwen/Qwen3.5-27B" to ModelPrice(
            inputPerMillion = 0.6,
            outputPerMillion = 4.8,
            bands = listOf(
                PriceBand(fromTokens = 128_000L, inputPerMillion = 1.8, outputPerMillion = 14.4)
            )
        ),
        "Qwen/Qwen3.5-35B-A3B" to ModelPrice(
            inputPerMillion = 0.4,
            outputPerMillion = 3.2,
            bands = listOf(
                PriceBand(fromTokens = 128_000L, inputPerMillion = 1.6, outputPerMillion = 12.8)
            )
        ),
        "Qwen/Qwen3.5-9B" to ModelPrice(
            inputPerMillion = 0.5,
            outputPerMillion = 4.0,
            bands = listOf(
                PriceBand(fromTokens = 128_000L, inputPerMillion = 1.5, outputPerMillion = 12.0)
            )
        ),
        "Qwen/Qwen3.6-27B" to ModelPrice(3.0, 18.0),
        "Qwen/Qwen3.6-35B-A3B" to ModelPrice(1.8, 10.8),
        "Qwen/Qwen3.8-27B" to ModelPrice(3.0, 12.0),
        "XingChenAGI/Xing4.0-29B" to ModelPrice(0.0, 0.0),
        // 下面这几个在定价页上只有计费明细、没有模型对象，但 /v1/models 会返回它们。
        // 少记一条，离线首屏就会把它们显示成「查不到价格」。
        "Kev-4B" to ModelPrice(0.0, 0.0),
        "SemIf" to ModelPrice(0.0, 0.0),
        "LoRA/Qwen/Qwen2.5-7B-Instruct" to ModelPrice(0.53, 0.53),
        "LoRA/Qwen/Qwen2.5-14B-Instruct" to ModelPrice(1.05, 1.05),
        "LoRA/Qwen/Qwen2.5-32B-Instruct" to ModelPrice(1.89, 1.89),
        "LoRA/Qwen/Qwen2.5-72B-Instruct" to ModelPrice(6.2, 6.2),
        "deepseek-ai/DeepSeek-V3.1-Terminus" to ModelPrice(4.0, 12.0),
        "deepseek-ai/DeepSeek-V3.2" to ModelPrice(4.0, 6.0),
        "deepseek-ai/DeepSeek-V4-Flash" to ModelPrice(3.0, 9.0),
        "deepseek-ai/DeepSeek-V4-Pro" to ModelPrice(12.0, 24.0),
        "deepseek-ai/DeepSeek-V4.1-Flash" to ModelPrice(2.0, 8.0),
        "inclusionAI/Ling-flash-2.0" to ModelPrice(1.0, 4.0),
        "inclusionAI/Ling-mini-2.0" to ModelPrice(0.5, 2.0),
        "meituan-longcat/LongCat-2.0" to ModelPrice(5.0, 20.0),
        "moonshotai/Kimi-K2.7-Code" to ModelPrice(6.5, 27.0),
        "stepfun-ai/Step-3.5-Flash" to ModelPrice(0.7, 2.1),
        "tencent/Hunyuan-A13B-Instruct" to ModelPrice(1.0, 4.0),
        "tencent/Hunyuan-MT-7B" to ModelPrice(0.0, 0.0),
        "tencent/Hy4-preview" to ModelPrice(6.0, 18.0),
        "zai-org/GLM-4.5-Air" to ModelPrice(1.0, 6.0),
        "zai-org/GLM-4.5V" to ModelPrice(1.0, 6.0),
        "zai-org/GLM-5.2" to ModelPrice(8.0, 28.0),
        "zai-org/GLM-5.3" to ModelPrice(8.0, 28.0),
        "zai-org/GLM-5.3-Flash" to ModelPrice(0.8, 2.8),
    )
}
