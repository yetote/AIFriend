package com.feature.werewolf.engine

import com.core.ai.model.VoiceCatalog

/**
 * 按座位给朗读分配音色。
 *
 * 为什么需要这一层：一局八到十二个人，如果谁都没单独配过音色，就全落在同一个
 * **服务商默认音色**上——牌桌上八个人一个声音，听起来像一个人在自言自语，
 * 而且白天连珠炮式发言时根本分不清谁在说。线下牌桌上每个人的嗓音是天然不同的。
 *
 * 三条规则，顺序不能换：
 * 1. **本人配过的音色永远优先**，游戏不替用户改他的人设；
 * 2. 没配过的座位从该服务商的内置列表里拿一个**还没被占用**的；
 * 3. 用完了（或这家只有一个音色）就回落到默认音色，不去编造 ID——
 *    编出来的 ID 会让合成直接失败，比同声更糟。
 *
 * 只看**同一个服务商内部**是否重复：音色 ID 跨服务商不通用，
 * 拿 MiniMax 的「少女」去给火山用只会报错。
 *
 * 人类座位不朗读，直接跳过，免得白占一个音色。
 */
internal object SeatVoices {

    /**
     * @param seats 需要分配音色的座位号
     * @param humanSeat 人类的座位，null 表示纯观战
     * @param explicit 座位 → 该座位上好友自己配的音色（可空）
     * @param providerOf 座位 → 这套朗读实际会走哪个服务商（和引擎的回落规则一致）
     */
    fun assign(
        seats: List<Int>,
        humanSeat: Int?,
        explicit: Map<Int, String?>,
        providerOf: (Int) -> String,
    ): Map<Int, String?> {
        // 先把「用户自己选的」全部登记为已占用，避免给别人的座位分到同一个。
        // 两个人都明确选了同一个音色也不算冲突——那是用户的选择，这里只是不再主动加剧。
        val used = mutableMapOf<String, MutableSet<String>>()
        seats.forEach { seat ->
            val voice = explicit[seat]?.takeIf { it.isNotBlank() } ?: return@forEach
            used.getOrPut(providerOf(seat)) { mutableSetOf() } += voice
        }

        return seats.associateWith { seat ->
            val chosen = explicit[seat]?.takeIf { it.isNotBlank() }
            when {
                chosen != null -> chosen
                seat == humanSeat -> null
                else -> {
                    val provider = providerOf(seat)
                    val taken = used.getOrPut(provider) { mutableSetOf() }
                    val spare = VoiceCatalog.spareVoice(provider, taken)
                    if (spare != null) taken += spare
                    spare
                }
            }
        }
    }
}
