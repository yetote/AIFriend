package com.core.repository.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 磁盘上的一个阶梯档位，超出 [fromTokens] 之后按这组单价计费。 */
@Serializable
data class StoredBand(
    val fromTokens: Long,
    val input: Double,
    val output: Double
)

/** 磁盘上的一个价格条目，单位是「该平台标价货币 / 百万 token」。 */
@Serializable
data class StoredPrice(
    val input: Double,
    val output: Double,
    /**
     * 超出常规上下文后更贵的档位。
     *
     * 有默认值，所以旧版本写下的数据照样能读（读出来就是不分档）。
     */
    val bands: List<StoredBand> = emptyList()
)

/** 整张价格表 + 抓取时间。 */
@Serializable
data class StoredModelPrices(
    /** 抓取成功的时刻（毫秒），0 表示不是有效数据。 */
    val fetchedAt: Long = 0L,
    /** 模型 id → 价格。 */
    val prices: Map<String, StoredPrice> = emptyMap(),
    /**
     * 平台标注为非对话模型的 id。
     *
     * 一并存下来，冷启动时就能精确剔除向量、重排、语音这些模型，
     * 不用等下一次抓取，也不用退回按名字猜。
     * 有默认值，所以旧版本写下的数据照样能读。
     */
    val nonChatModels: Set<String> = emptySet()
)

/**
 * 抓取到的价格表的落盘缓存。
 *
 * 为什么要落盘：定价页有几百 KB，一天只该抓一次。存下来之后，
 * 冷启动、断网、页面改版都能立刻显示上次的价格，而不是一进来满屏没有价。
 *
 * 存在全 App 共用的 Preferences DataStore 里，和好友、凭证同一份文件。
 * 数据本身不敏感（公开标价），不需要加密。
 */
class ModelPriceStore(context: Context) {

    private val store = context.applicationContext.appPreferences

    /** 读上次抓到的价格表；没抓过或数据坏了返回 null。 */
    suspend fun load(): StoredModelPrices? {
        val prefs = runCatching { store.data.first() }.getOrNull() ?: return null
        val fetchedAt = prefs[FETCHED_AT] ?: return null
        val payload = prefs[PAYLOAD] ?: return null
        val parsed = runCatching { json.decodeFromString(StoredModelPrices.serializer(), payload) }
            .getOrNull() ?: return null
        if (parsed.prices.isEmpty()) return null
        // 时间戳以独立键为准：payload 里那份是写入时的副本，
        // 分开存是为了不解析整张表也能判断「今天抓过没有」。
        return parsed.copy(fetchedAt = fetchedAt)
    }

    /** 写入新抓到的价格表。返回是否写成功。 */
    suspend fun save(
        prices: Map<String, StoredPrice>,
        fetchedAt: Long,
        nonChatModels: Set<String> = emptySet()
    ): Boolean {
        if (prices.isEmpty() || fetchedAt <= 0L) return false
        val payload = runCatching {
            json.encodeToString(
                StoredModelPrices.serializer(),
                StoredModelPrices(
                    fetchedAt = fetchedAt,
                    prices = prices,
                    nonChatModels = nonChatModels
                )
            )
        }.getOrNull() ?: return false
        return runCatching {
            store.edit { prefs ->
                prefs[PAYLOAD] = payload
                prefs[FETCHED_AT] = fetchedAt
            }
            true
        }.getOrDefault(false)
    }

    /** 清掉缓存，下次启动会重新抓。 */
    suspend fun clear() {
        runCatching {
            store.edit { prefs ->
                prefs.remove(PAYLOAD)
                prefs.remove(FETCHED_AT)
            }
        }
    }

    private companion object {
        val PAYLOAD = stringPreferencesKey("model_price_payload")
        val FETCHED_AT = longPreferencesKey("model_price_fetched_at")

        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }
}
