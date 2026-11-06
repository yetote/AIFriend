package com.core.ai.model

/**
 * 运行时价格缓存。
 *
 * 三层价格来源，优先级从高到低：
 *
 * 1. **平台接口自带**（OpenRouter 的 `pricing` 字段）—— 最准，每次拉模型列表就有；
 * 2. **页面抓取**（硅基流动定价页）—— 每天首次打开刷新一次，可手动刷新；
 * 3. **内置快照**（[SiliconFlowPriceSnapshot]）—— 没网 / 抓取失败时的兜底。
 *
 * 这里持有第 2 层的内存副本。持久化由 `core:repository` 的
 * `ModelPriceStore` 负责 —— core:ai 拿不到 Context，也不该管磁盘。
 */
object LivePriceCache {

    /** 一次抓取的结果。 */
    data class Snapshot(
        /** 模型 id → 价格。 */
        val prices: Map<String, ModelPrice>,
        /** 抓取成功的时刻（毫秒）。0 表示从没成功过。 */
        val fetchedAt: Long = 0L,
        /**
         * 平台标注为非对话模型的 id。
         *
         * 用来精确剔除向量、重排、语音、图像、视频这些模型 ——
         * 平台自己的分类比按名字猜准，也不会误伤名字里带 `audio` 的对话模型。
         */
        val nonChatModels: Set<String> = emptySet()
    ) {
        val isEmpty: Boolean get() = prices.isEmpty()

        /** 有价格且抓过，才算可用。 */
        val isUsable: Boolean get() = prices.isNotEmpty() && fetchedAt > 0L
    }

    @Volatile
    private var current: Snapshot = Snapshot(emptyMap())

    /** 当前生效的抓取价格。 */
    fun snapshot(): Snapshot = current

    /**
     * 非对话模型名单；没抓过时为 null。
     *
     * 返回 null 而不是空集，让调用方区分「不知道」和「一个非对话模型都没有」：
     * 前者只能退回按名字过滤，后者说明这份数据不能用来筛。
     */
    fun knownNonChatModels(): Set<String>? =
        current.nonChatModels.takeIf { current.isUsable }

    /**
     * 平台明确标成对话模型的 id；没抓过时为 null。
     *
     * 这份名单的价值在于「反向纠错」：有些对话模型名字里带
     * `audio`、`image` 之类字眼，按关键词会被误杀，
     * 平台既然说它是 chat，就该留下。
     */
    fun knownChatModels(): Set<String>? =
        current.prices.keys.takeIf { current.isUsable && it.isNotEmpty() }

    /**
     * 换成新抓到的价格。
     *
     * 空结果不覆盖旧数据：抓取失败时页面可能返回错误页，
     * 这时把价格清空会让用户突然看不到任何价格，比显示稍旧的价格更糟。
     */
    fun update(
        prices: Map<String, ModelPrice>,
        fetchedAt: Long,
        nonChatModels: Set<String> = emptySet()
    ) {
        if (prices.isEmpty()) return
        current = Snapshot(prices, fetchedAt, nonChatModels)
    }

    /** 从磁盘恢复时直接载入，不做非空校验（磁盘上本来就可能是空的）。 */
    fun restore(
        prices: Map<String, ModelPrice>,
        fetchedAt: Long,
        nonChatModels: Set<String> = emptySet()
    ) {
        if (fetchedAt <= 0L) return
        current = Snapshot(prices, fetchedAt, nonChatModels)
    }

    /** 丢掉内存里的价格，回到「只有内置快照」的状态。测试用。 */
    internal fun clear() {
        current = Snapshot(emptyMap())
    }

    /**
     * 该不该刷新了。
     *
     * 用自然日判断而不是「距上次超过 24 小时」：用户想要的
     * 「每天首次打开更新一次」，跨过零点就该刷新，
     * 而不是等到昨天打开的那个钟点。
     */
    fun shouldRefresh(now: Long, lastFetchedAt: Long): Boolean {
        if (lastFetchedAt <= 0L) return true
        return dayStamp(now) != dayStamp(lastFetchedAt)
    }

    /** 本地时区的「第几天」，用来比较是不是同一天。 */
    private fun dayStamp(millis: Long): Long {
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = millis
        return calendar.get(java.util.Calendar.YEAR) * 1000L +
            calendar.get(java.util.Calendar.DAY_OF_YEAR)
    }
}
