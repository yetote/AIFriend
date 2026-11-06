package com.core.ai.model

import android.content.Context
import com.core.repository.WebRepository
import com.core.repository.storage.ModelPriceStore
import com.core.repository.storage.StoredBand
import com.core.repository.storage.StoredPrice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 抓取并缓存聚合平台的价格表。
 *
 * 数据流：定价页 HTML → 解析出 chat 模型价格 → 内存 [LivePriceCache] + 磁盘 [ModelPriceStore]。
 *
 * 刷新时机由调用方决定（[refreshIfNeeded] 判断是不是新的一天），
 * 这里只负责「抓一次」和「启动时把磁盘上的价格装进内存」。
 *
 * 设计上的取舍：
 * - **失败不报错、不清空**。后台静默刷新失败是常态（断网、页面改版），
 *   这不是用户主动发起的操作，弹 toast 只会打扰人；价格继续用上次的，
 *   宁可稍旧也不要满屏无价。手动刷新失败由 UI 自己提示。
 * - **同一时刻只抓一次**。用 [mutex] 串行，而不是「正在抓就返回 false」——
 *   后者会让用户在后台刷新还没结束时点「刷新价格」毫无反应。
 */
class ModelPriceRefresher(
    context: Context,
    private val repository: WebRepository = WebRepository.instance
) {

    private val store = ModelPriceStore(context)

    /** 串行化抓取：并发进来的调用排队，而不是被丢掉。 */
    private val mutex = Mutex()

    /**
     * 启动时调用：把磁盘上的价格装进内存。
     *
     * 只读磁盘，不发网络请求。内存里已有可用价格时直接跳过。
     */
    suspend fun restore() {
        if (LivePriceCache.snapshot().isUsable) return
        val stored = runCatching { store.load() }.getOrNull() ?: return
        LivePriceCache.restore(
            prices = stored.prices.toModelPrices(),
            fetchedAt = stored.fetchedAt,
            nonChatModels = stored.nonChatModels
        )
    }

    /**
     * 如果不是今天抓的，就抓一次。
     *
     * 判断放在锁内：首页和设置页可能在启动时几乎同时调进来，
     * 只在锁外判断会让它们都看到「还没抓过」而各抓一次。
     *
     * @return 是否真的发了请求（false 表示今天已经是最新的）。
     */
    suspend fun refreshIfNeeded(now: Long = System.currentTimeMillis()): Boolean {
        restore()
        return mutex.withLock {
            // 等锁期间可能已经被别的调用抓过了
            if (!LivePriceCache.shouldRefresh(now, LivePriceCache.snapshot().fetchedAt)) {
                return@withLock false
            }
            fetchAndStore(now)
        }
    }

    /**
     * 强制抓一次，供「刷新价格」按钮使用。
     *
     * 不看「今天抓过了」，因为按钮的语义就是「现在去确认一下」：
     * 平台一天内调价是可能的，用户点按钮就是想立刻拿到最新价。
     *
     * @return 是否成功拿到数据。false 时调用方应提示失败，但列表里的旧价格不受影响。
     */
    suspend fun refresh(now: Long = System.currentTimeMillis()): Boolean = mutex.withLock {
        fetchAndStore(now)
    }

    private suspend fun fetchAndStore(now: Long): Boolean = withContext(Dispatchers.IO) {
        val html = repository.getPage(SiliconFlowPricingParser.PRICING_URL)
            .getOrElse { return@withContext false }
        if (html.isEmpty()) return@withContext false

        // 页面有 700KB 左右，解析和括号配对都在 IO 线程做完再回主线程，
        // 避免进设置页时卡一下。
        val parsed = SiliconFlowPricingParser.parse(html)
        if (parsed.isEmpty) return@withContext false

        LivePriceCache.update(parsed.prices, now, parsed.nonChatModels)
        runCatching {
            store.save(
                prices = parsed.prices.toStoredPrices(),
                fetchedAt = now,
                nonChatModels = parsed.nonChatModels
            )
        }
        true
    }
}

/** 内存价格 → 落盘格式。 */
internal fun Map<String, ModelPrice>.toStoredPrices(): Map<String, StoredPrice> =
    mapValues { (_, price) ->
        StoredPrice(
            input = price.inputPerMillion,
            output = price.outputPerMillion,
            bands = price.bands.map { band ->
                StoredBand(
                    fromTokens = band.fromTokens,
                    input = band.inputPerMillion,
                    output = band.outputPerMillion
                )
            }
        )
    }

/** 落盘格式 → 内存价格。 */
internal fun Map<String, StoredPrice>.toModelPrices(): Map<String, ModelPrice> =
    mapValues { (_, price) ->
        ModelPrice(
            inputPerMillion = price.input,
            outputPerMillion = price.output,
            bands = price.bands.map { band ->
                PriceBand(
                    fromTokens = band.fromTokens,
                    inputPerMillion = band.input,
                    outputPerMillion = band.output
                )
            }
        )
    }
