package com.core.ai.model

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar

/**
 * 定价页解析测试。
 *
 * 样本按真实页面的结构手写：Next.js 把服务端组件数据分片塞进
 * `self.__next_f.push([1,"<转义字符串>"])`，里面有两类对象
 * ——模型对象（带 `modelName` / `subType`）和计费明细对象
 * （带 `playgroundName` / `realTimePriceCnyUnit`）。
 *
 * 真实页面的一次端到端解析另有一次性验证（62 个对话模型）。
 */
class SiliconFlowPricingParserTest {

    /** 拼出 `self.__next_f.push([1,"..."])`，按 JSON 规则转义。 */
    private fun push(raw: String): String {
        val escaped = raw
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
        return """<script>self.__next_f.push([1,"$escaped"])</script>"""
    }

    /** 模型对象：只负责声明「这个模型叫什么、属于哪一类」。 */
    private fun modelLine(
        modelName: String,
        subType: String = "chat",
        desc: String? = null
    ): String {
        val descPart = if (desc == null) "" else """"desc":"$desc","""
        return """3a:{"modelId":"id-$modelName","modelName":"$modelName","subType":"$subType",""" +
            """"type":"text",$descPart"currency":"¥","priceUnit":"/ M Tokens","contextLen":128}"""
    }

    /**
     * 计费明细对象。
     *
     * [perK] 是每 K token 的人民币价（页面上就是这个单位），
     * 解析后应当乘 1000 变成每百万 token。
     */
    private fun record(
        model: String,
        component: String,
        perK: String,
        coord: String = "",
        unit: String = "K tokens"
    ): String =
        """{"playgroundName":"$model","componentCode":"$component",""" +
            """"coordinateDesc":"$coord","realTimePriceCnyUnit":"$perK",""" +
            """"unitZhCnName":"$unit","price_scenario":"兜底价"}"""

    /** 一个完整模型：模型对象 + 输入 + 输出两条明细。 */
    private fun model(
        name: String,
        inputPerK: String,
        outputPerK: String,
        subType: String = "chat",
        coord: String = ""
    ): List<String> = listOf(
        modelLine(name, subType),
        record(name, "input-tokens", inputPerK, coord),
        record(name, "output-tokens", outputPerK, coord)
    )

    /** 把若干片段拼成一张页面。 */
    private fun page(vararg blocks: List<String>): String =
        push(blocks.flatMap { it }.joinToString("\n"))

    private fun parse(html: String) = SiliconFlowPricingParser.parse(html)

    @Before
    fun resetCache() {
        LivePriceCache.clear()
    }

    @After
    fun cleanUp() {
        LivePriceCache.clear()
    }

    @Test
    fun `解析出输入输出价格并换算成每百万 token`() {
        val html = page(
            model("deepseek-ai/DeepSeek-V3.2", "0.004", "0.006"),
            model("Qwen/Qwen3.6-27B", "0.003", "0.018")
        )

        val prices = parse(html).prices

        assertEquals(2, prices.size)
        assertEquals(4.0, prices.getValue("deepseek-ai/DeepSeek-V3.2").inputPerMillion, 1e-9)
        assertEquals(6.0, prices.getValue("deepseek-ai/DeepSeek-V3.2").outputPerMillion, 1e-9)
        assertEquals(3.0, prices.getValue("Qwen/Qwen3.6-27B").inputPerMillion, 1e-9)
        assertEquals(18.0, prices.getValue("Qwen/Qwen3.6-27B").outputPerMillion, 1e-9)
    }

    @Test
    fun `阶梯价取最低档`() {
        // 真实页面：Qwen3.5-27B 分两档，输入 [0, 128k) 是 0.6/4.8，
        // 输入 [128k, +∞) 是 1.8/14.4。常规上下文该显示便宜那档。
        val html = page(
            listOf(
                modelLine("Qwen/Qwen3.5-27B"),
                record("Qwen/Qwen3.5-27B", "input-tokens", "0.0006", "输入 [0, 128k)"),
                record("Qwen/Qwen3.5-27B", "output-tokens", "0.0048", "输入 [0, 128k)"),
                record("Qwen/Qwen3.5-27B", "input-tokens", "0.0018", "输入 [128k, +∞)"),
                record("Qwen/Qwen3.5-27B", "output-tokens", "0.0144", "输入 [128k, +∞)")
            )
        )

        val price = parse(html).prices.getValue("Qwen/Qwen3.5-27B")

        assertEquals(0.6, price.inputPerMillion, 1e-9)
        assertEquals(4.8, price.outputPerMillion, 1e-9)
        // 贵的那一档不能丢：UI 要靠它提示「聊长了会变贵」
        assertEquals(1, price.bands.size)
        assertEquals(128_000L, price.bands[0].fromTokens)
        assertEquals(1.8, price.bands[0].inputPerMillion, 1e-9)
        assertEquals(14.4, price.bands[0].outputPerMillion, 1e-9)
    }

    @Test
    fun `三档阶梯按起点升序排列`() {
        // 档位在载荷里的顺序不可靠（真实页面上 GLM-5.1 的输出记录排在输入记录前面），
        // 所以要按区间起点排序，而不是按出现顺序。
        val html = page(
            listOf(
                modelLine("Qwen/Qwen3.5-9B"),
                record("Qwen/Qwen3.5-9B", "input-tokens", "0.003", "输入 [256k, +∞)"),
                record("Qwen/Qwen3.5-9B", "output-tokens", "0.024", "输入 [256k, +∞)"),
                record("Qwen/Qwen3.5-9B", "input-tokens", "0.0005", "输入 [0, 128k)"),
                record("Qwen/Qwen3.5-9B", "output-tokens", "0.004", "输入 [0, 128k)"),
                record("Qwen/Qwen3.5-9B", "input-tokens", "0.0015", "输入 [128k, 256k)"),
                record("Qwen/Qwen3.5-9B", "output-tokens", "0.012", "输入 [128k, 256k)")
            )
        )

        val price = parse(html).prices.getValue("Qwen/Qwen3.5-9B")

        assertEquals(0.5, price.inputPerMillion, 1e-9)
        assertEquals(4.0, price.outputPerMillion, 1e-9)
        assertEquals(listOf(128_000L, 256_000L), price.bands.map { it.fromTokens })
        assertEquals(1.5, price.bands[0].inputPerMillion, 1e-9)
        assertEquals(3.0, price.bands[1].inputPerMillion, 1e-9)
    }

    @Test
    fun `时段价不产生阶梯档`() {
        // 时段折扣不是「聊长了变贵」，不该出现在阶梯说明里。
        val html = page(
            listOf(
                modelLine("tencent/Hunyuan-A13B-Instruct"),
                record("tencent/Hunyuan-A13B-Instruct", "input-tokens", "0.001", "费用发生时段: 9点～18点"),
                record("tencent/Hunyuan-A13B-Instruct", "output-tokens", "0.004", "费用发生时段: 9点～18点"),
                record(
                    "tencent/Hunyuan-A13B-Instruct", "input-tokens", "0.0008",
                    "费用发生时段: 0点～9点/18点～24点"
                ),
                record(
                    "tencent/Hunyuan-A13B-Instruct", "output-tokens", "0.0032",
                    "费用发生时段: 0点～9点/18点～24点"
                )
            )
        )

        val price = parse(html).prices.getValue("tencent/Hunyuan-A13B-Instruct")

        assertTrue(price.bands.isEmpty())
        assertFalse(price.isTiered)
    }

    @Test
    fun `不分档的模型没有阶梯价`() {
        val html = page(model("deepseek-ai/DeepSeek-V3.2", "0.004", "0.006"))

        val price = parse(html).prices.getValue("deepseek-ai/DeepSeek-V3.2")

        assertTrue(price.bands.isEmpty())
        assertEquals(4.0, price.inputPerMillion, 1e-9)
    }

    @Test
    fun `阶梯说明按每百万 token 的格式排版`() {
        val html = page(
            listOf(
                modelLine("Qwen/Qwen3.5-27B"),
                record("Qwen/Qwen3.5-27B", "input-tokens", "0.0006", "输入 [0, 128k)"),
                record("Qwen/Qwen3.5-27B", "output-tokens", "0.0048", "输入 [0, 128k)"),
                record("Qwen/Qwen3.5-27B", "input-tokens", "0.0018", "输入 [128k, +∞)"),
                record("Qwen/Qwen3.5-27B", "output-tokens", "0.0144", "输入 [128k, +∞)")
            )
        )

        val price = parse(html).prices.getValue("Qwen/Qwen3.5-27B")

        assertEquals("¥0.6 入 / ¥4.8 出 /M", price.format(ModelPrice.CNY))
        assertEquals("超 128k：¥1.8 入 / ¥14.4 出 /M", price.bandLabels(ModelPrice.CNY).single())
    }

    @Test
    fun `时段价取标准时段而不是折扣时段`() {
        // 真实页面：Hunyuan-A13B-Instruct 标准时段 1/4，闲时 0.8/3.2。
        // 取贵的那个，免得让用户以为随时都这么便宜。
        val html = page(
            listOf(
                modelLine("tencent/Hunyuan-A13B-Instruct"),
                record("tencent/Hunyuan-A13B-Instruct", "input-tokens", "0.001", "费用发生时段: 9点～18点"),
                record("tencent/Hunyuan-A13B-Instruct", "output-tokens", "0.004", "费用发生时段: 9点～18点"),
                record("tencent/Hunyuan-A13B-Instruct", "input-tokens", "0.0008", "费用发生时段: 0点～9点/18点～24点"),
                record("tencent/Hunyuan-A13B-Instruct", "output-tokens", "0.0032", "费用发生时段: 0点～9点/18点～24点")
            )
        )

        val price = parse(html).prices.getValue("tencent/Hunyuan-A13B-Instruct")

        assertEquals(1.0, price.inputPerMillion, 1e-9)
        assertEquals(4.0, price.outputPerMillion, 1e-9)
    }

    @Test
    fun `模型对象上的摘要价不参与计算`() {
        // 这是踩过的坑：模型对象里也有 inputPrice / price，但分档模型上
        // 它取的档位不确定（Qwen3.5-27B 会悄悄变成长上下文那档 1.8/14.4）。
        // 以明细里的 realTimePriceCnyUnit 为准。
        val summary = """3a:{"modelId":"x","modelName":"Qwen/Qwen3.5-27B","subType":"chat",""" +
            """"inputPrice":"1.8","price":"14.4"}"""
        val html = page(
            listOf(
                summary,
                record("Qwen/Qwen3.5-27B", "input-tokens", "0.0006", "输入 [0, 128k)"),
                record("Qwen/Qwen3.5-27B", "output-tokens", "0.0048", "输入 [0, 128k)"),
                record("Qwen/Qwen3.5-27B", "input-tokens", "0.0018", "输入 [128k, +∞)"),
                record("Qwen/Qwen3.5-27B", "output-tokens", "0.0144", "输入 [128k, +∞)")
            )
        )

        val price = parse(html).prices.getValue("Qwen/Qwen3.5-27B")

        assertEquals(0.6, price.inputPerMillion, 1e-9)
        assertEquals(4.8, price.outputPerMillion, 1e-9)
    }

    @Test
    fun `只保留对话模型`() {
        val html = page(
            model("Qwen/Qwen3.6-27B", "0.003", "0.018"),
            model("BAAI/bge-m3", "0.0005", "0.0005", subType = "embedding"),
            model("FunAudioLLM/CosyVoice2", "0.01", "0.01", subType = "text-to-speech"),
            model("Kwai-Kolors/Kolors", "0.02", "0.02", subType = "text-to-image")
        )

        val prices = parse(html).prices

        assertEquals(setOf("Qwen/Qwen3.6-27B"), prices.keys)
    }

    @Test
    fun `非对话模型被单独记下来供过滤用`() {
        val html = page(
            model("Qwen/Qwen3.6-27B", "0.003", "0.018"),
            model("BAAI/bge-m3", "0.0005", "0.0005", subType = "embedding"),
            model("Kwai-Kolors/Kolors", "0.02", "0.02", subType = "text-to-image"),
            model("Qwen/Qwen3-Reranker", "0.0001", "0.0001", subType = "rerank")
        )

        val result = parse(html)

        assertEquals(
            setOf("BAAI/bge-m3", "Kwai-Kolors/Kolors", "Qwen/Qwen3-Reranker"),
            result.nonChatModels
        )
        assertFalse(result.nonChatModels.contains("Qwen/Qwen3.6-27B"))
    }

    @Test
    fun `平台名单比按名字猜更准`() {
        // 名字里带 audio 的对话模型：按关键词会被误杀，平台标了 chat 就该留下
        val chat = parse(page(model("Acme/audio-chat-9b", "0.001", "0.002")))
        LivePriceCache.update(chat.prices, 1L, chat.nonChatModels)
        assertTrue(isLikelyChatModel("Acme/audio-chat-9b"))

        // 名字看不出问题的向量模型：按关键词会漏掉，平台标了 embedding 就该剔除。
        // 一起放个对话模型，否则价格为空，update 会（按设计）忽略这次结果。
        val embedding = parse(
            page(
                model("Acme/vector-9b", "0.001", "0.002", subType = "embedding"),
                model("Acme/plain-chat", "0.001", "0.002")
            )
        )
        LivePriceCache.update(embedding.prices, 1L, embedding.nonChatModels)
        assertFalse(isLikelyChatModel("Acme/vector-9b"))
    }

    @Test
    fun `同一模型既标 chat 又标别类时以 chat 为准`() {
        // 命名有歧义时，留下用户能用的那个，别把它藏起来
        val html = push(
            listOf(
                modelLine("Acme/dual-9b", "chat"),
                modelLine("Acme/dual-9b", "embedding"),
                record("Acme/dual-9b", "input-tokens", "0.001"),
                record("Acme/dual-9b", "output-tokens", "0.002")
            ).joinToString("\n")
        )

        val result = parse(html)

        assertTrue(result.prices.containsKey("Acme/dual-9b"))
        assertFalse(result.nonChatModels.contains("Acme/dual-9b"))
    }

    @Test
    fun `没有实时名单时退回按名字判断`() {
        // 冷启动还没抓到价，只能靠名字
        assertFalse(isLikelyChatModel("BAAI/bge-m3"))
        assertTrue(isLikelyChatModel("deepseek-ai/DeepSeek-V3.2"))
    }

    @Test
    fun `免费模型解析为零价而不是丢掉`() {
        val html = page(model("zai-org/GLM-5.3-Flash", "0", "0"))

        val price = parse(html).prices["zai-org/GLM-5.3-Flash"]

        assertTrue(price!!.isFree)
    }

    @Test
    fun `缺少输出价时跳过该模型`() {
        // 只有输入价就推输出价属于瞎猜，宁可这个模型不显示价格
        val html = page(
            listOf(
                modelLine("a/partial"),
                record("a/partial", "input-tokens", "0.001")
            )
        )

        assertTrue(parse(html).prices.isEmpty())
    }

    @Test
    fun `价格不是数字时跳过`() {
        val html = page(model("a/weird", "面议", "面议"))

        assertTrue(parse(html).prices.isEmpty())
    }

    @Test
    fun `单位不是每 K token 的明细不参与计算`() {
        // 图片、视频、字符按件计价，乘 1000 会得出荒唐的数
        val html = page(
            listOf(
                modelLine("a/image-model"),
                record("a/image-model", "input-tokens", "0.5", unit = "图片"),
                record("a/image-model", "output-tokens", "0.5", unit = "图片")
            )
        )

        assertTrue(parse(html).prices.isEmpty())
    }

    @Test
    fun `对话模型没有计价明细时不显示价格`() {
        val result = parse(page(listOf(modelLine("a/no-price"))))

        assertTrue(result.prices.isEmpty())
        // 没价也不能被误判成非对话模型
        assertFalse(result.nonChatModels.contains("a/no-price"))
    }

    @Test
    fun `只有计费明细没有模型对象的模型也能拿到价格`() {
        // 真实页面上的 LoRA 部署模型就长这样：没有 modelName / subType，
        // 只有 playgroundName 和输入、输出两条明细。
        // `/v1/models` 会返回它们，漏掉就在列表里显示成「查不到价格」。
        val html = page(
            listOf(
                record("LoRA/Qwen/Qwen2.5-7B-Instruct", "input-tokens", "0.00053"),
                record("LoRA/Qwen/Qwen2.5-7B-Instruct", "output-tokens", "0.00053")
            )
        )

        val price = parse(html).prices["LoRA/Qwen/Qwen2.5-7B-Instruct"]

        assertEquals(0.53, price!!.inputPerMillion, 1e-9)
        assertEquals(0.53, price.outputPerMillion, 1e-9)
    }

    @Test
    fun `没有模型对象且只有一个分量时不猜价格`() {
        // 只有输入价就推出价格等于瞎编，宁可这个模型不显示价
        val html = page(listOf(record("a/half", "input-tokens", "0.001")))

        assertTrue(parse(html).prices.isEmpty())
    }

    @Test
    fun `没有模型对象的免费模型记为零价`() {
        // Kev-4B / SemIf 这类免费体验模型：两个分量都是 0
        val html = page(
            listOf(
                record("Kev-4B", "input-tokens", "0.000000"),
                record("Kev-4B", "output-tokens", "0.000000")
            )
        )

        val price = parse(html).prices["Kev-4B"]

        assertEquals(0.0, price!!.inputPerMillion, 1e-9)
        assertEquals(0.0, price.outputPerMillion, 1e-9)
    }

    @Test
    fun `明确标成非对话模型的不会被补价逻辑收进来`() {
        // 补齐只能补「页面上没有类型」的，不能推翻平台已经给出的分类
        val html = page(
            listOf(modelLine("BAAI/bge-m3", subType = "embedding")),
            listOf(
                record("BAAI/bge-m3", "input-tokens", "0.001"),
                record("BAAI/bge-m3", "output-tokens", "0.001")
            )
        )

        val result = parse(html)

        assertTrue(result.prices.isEmpty())
        assertTrue(result.nonChatModels.contains("BAAI/bge-m3"))
    }

    @Test
    fun `同一模型重复出现只取一个`() {
        val html = page(
            model("Pro/moonshotai/Kimi-K2.6", "0.0065", "0.027"),
            model("Pro/moonshotai/Kimi-K2.6", "0.0065", "0.027")
        )

        assertEquals(1, parse(html).prices.size)
    }

    @Test
    fun `转义的中文和引号不会让字段错位`() {
        // desc 里带转义引号和 \n，必须正确还原，否则模型对象的后续字段会被切坏
        val line = modelLine("a/quoted", desc = "他说\\\"好用\\\"\\n然后呢")
        val html = page(
            listOf(
                line,
                record("a/quoted", "input-tokens", "0.001"),
                record("a/quoted", "output-tokens", "0.002")
            )
        )

        val price = parse(html).prices["a/quoted"]

        assertEquals(1.0, price!!.inputPerMillion, 1e-9)
        assertEquals(2.0, price.outputPerMillion, 1e-9)
    }

    @Test
    fun `描述里的花括号不会让对象提前结束`() {
        val line = modelLine("a/brace", desc = "支持 {参数} 与 }")
        val html = page(
            listOf(
                line,
                record("a/brace", "input-tokens", "0.003"),
                record("a/brace", "output-tokens", "0.004")
            )
        )

        val price = parse(html).prices["a/brace"]

        assertEquals(3.0, price!!.inputPerMillion, 1e-9)
        assertEquals(4.0, price.outputPerMillion, 1e-9)
    }

    @Test
    fun `多个 flight 片段会被拼起来`() {
        // 真实页面有上百个片段，对象可能被切在两个片段里
        val html = push("3a:{\"modelId\":\"x\",\"modelName\":\"a/split\",\"subType\":\"chat\",") +
            push("\"contextLen\":128}") +
            push(record("a/split", "input-tokens", "0.001")) +
            push(record("a/split", "output-tokens", "0.002"))

        val price = parse(html).prices["a/split"]

        assertEquals(1.0, price!!.inputPerMillion, 1e-9)
    }

    @Test
    fun `空页面和非定价页返回空结果`() {
        assertTrue(parse("").isEmpty)
        assertTrue(parse("<html><body>404</body></html>").isEmpty)
    }
}

class LivePriceCacheTest {

    /*
     * LivePriceCache 是进程级单例，测试之间会互相污染：
     * 前面某个用例塞了实时价格，后面断言「回落到快照」就会失败。
     * 每个用例前清空，让每个测试都从「只有内置快照」的干净状态开始。
     */
    @Before
    fun resetCache() {
        LivePriceCache.clear()
    }

    @After
    fun cleanUp() {
        LivePriceCache.clear()
    }

    @Test
    fun `没抓过时该刷新`() {
        assertTrue(LivePriceCache.shouldRefresh(System.currentTimeMillis(), 0L))
    }

    @Test
    fun `同一天不重复抓`() {
        val morning = at(2026, 3, 10, 8, 0)
        val evening = at(2026, 3, 10, 23, 30)
        assertFalse(LivePriceCache.shouldRefresh(evening, morning))
    }

    @Test
    fun `跨过零点就刷新`() {
        // 用户要的是「每天首次打开」，不是「距上次满 24 小时」
        val lastNight = at(2026, 3, 10, 23, 50)
        val thisMorning = at(2026, 3, 11, 0, 10)
        assertTrue(LivePriceCache.shouldRefresh(thisMorning, lastNight))
    }

    @Test
    fun `空结果不覆盖已有价格`() {
        val price = mapOf("a/b" to ModelPrice(1.0, 2.0))
        LivePriceCache.update(price, at(2026, 3, 10, 8, 0))

        // 抓取失败时页面可能是错误页，解析结果为空
        LivePriceCache.update(emptyMap(), at(2026, 3, 10, 9, 0))

        assertEquals(price, LivePriceCache.snapshot().prices)
    }

    @Test
    fun `实时价格优先于内置快照`() {
        // 抓到的价格覆盖内置快照，哪怕两者不同
        LivePriceCache.update(
            mapOf("deepseek-ai/DeepSeek-V3.2" to ModelPrice(9.0, 9.0)),
            at(2026, 3, 10, 8, 0)
        )

        val price = ModelPrices.lookup("siliconflow", "deepseek-ai/DeepSeek-V3.2")

        assertEquals(9.0, price!!.inputPerMillion, 1e-9)
        assertEquals(9.0, price.outputPerMillion, 1e-9)
    }

    @Test
    fun `没有实时价格时回落到快照`() {
        val price = ModelPrices.lookup("siliconflow", "deepseek-ai/DeepSeek-V3.2")

        assertEquals(4.0, price!!.inputPerMillion, 1e-9)
        assertEquals(6.0, price.outputPerMillion, 1e-9)
    }

    @Test
    fun `有实时价格时日期显示抓取日而不是快照日`() {
        LivePriceCache.update(
            mapOf("a/b" to ModelPrice(1.0, 2.0)),
            at(2026, 3, 10, 8, 0)
        )

        assertEquals("2026-03-10", ModelPrices.snapshotDate("siliconflow"))
    }

    @Test
    fun `只有硅基流动能手动刷新`() {
        assertTrue(ModelPrices.canRefresh("siliconflow"))
        assertFalse(ModelPrices.canRefresh("openrouter"))
    }

    @Test
    fun `时间戳为零的数据不算可用`() {
        LivePriceCache.restore(mapOf("a/b" to ModelPrice(1.0, 2.0)), 0L)

        assertFalse(LivePriceCache.snapshot().isUsable)
        // 无效数据不该被 lookup 采信
        assertNull(ModelPrices.lookup("siliconflow", "a/b"))
    }

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            set(year, month - 1, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
}
