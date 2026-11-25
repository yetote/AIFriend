package com.feature.werewolf.prompt

import com.feature.werewolf.game.WerewolfConfig
import kotlin.math.abs

/**
 * 牌桌上的「打牌风格画像」——只塑造思考切入点与说话力度，绝不与规则、阵营目标或起跳/投票纪律冲突。
 */
enum class PlayerStyle(
    val id: String,
    val label: String,
    val tagline: String,
    val brief: String,
) {
    CAUTIOUS(
        id = "cautious",
        label = "谨慎求证",
        tagline = "不见实锤不下重断，重交叉核对",
        brief =
            "【你的牌风：谨慎求证】你不凭气势盲从，习惯先核对发言与票型的交叉证据再给出倾向。" +
            "就算当下给不了百分之百的铁断，你也会把当前的倾斜方向和仍待验证的疑点讲清，该落票时果断落票。\n" +
            "你看人时，习惯先问「这话能对着台账核吗」：报了验人就看后续能否兑现、发了金水就看动机与票型，" +
            "说得再漂亮、对不上账的一律先记一笔。",
    ),

    AGGRESSIVE(
        id = "aggressive",
        label = "强攻压迫",
        tagline = "主动带节奏，敢追问破绽",
        brief =
            "【你的牌风：强攻压迫】你喜欢掌握主动权，轮到你时先亮明核心判断、抓住具体矛盾追问，" +
            "不给对手含糊过关的空间。\n" +
            "你看人时，习惯盯着谁在回避问题或前后对不上；但你的压迫必须建立在可核对的事实之上，" +
            "如果新证据推翻了旧判断，你会果断换靶子，绝不死咬空气。",
    ),

    ANALYTICAL(
        id = "analytical",
        label = "逻辑推演",
        tagline = "盘链条、算票型，像解题一样打牌",
        brief =
            "【你的牌风：逻辑推演】你把这局当成一道推理题：起跳顺序、警徽流兑现、双死结构、票型绑定——" +
            "你要把它们串成自洽的逻辑链。\n" +
            "你看人时，习惯做交叉验证：昨天说的话和今天矛不矛盾、站边和投票一不一致、验人和死讯对不对得上。" +
            "不吃情绪和气势那一套，链条对不上就划掉，话糙但逻辑严密就认。",
    ),

    INTUITIVE(
        id = "intuitive",
        label = "直觉洞察",
        tagline = "敏感于心态与措辞变化，再用事实印证",
        brief =
            "【你的牌风：直觉洞察】你对发言里的心态变化很敏感：谁突然过度解释、谁急着撇清关系、谁在关键处异常沉默，" +
            "你都会第一时间记下。\n" +
            "你看人时，习惯先凭心态敏锐捕捉嫌疑，再用票型和台账事实去验证自己的直觉；" +
            "发现直觉和硬证据打架时，以硬证据为准。",
    ),

    DEEP(
        id = "deep",
        label = "深水潜伏",
        tagline = "平时克制记账，关键处一针见血",
        brief =
            "【你的牌风：深水潜伏】你说话简洁克制、不爱扯废话，平时不动声色把全场谁保了谁、谁跟了谁的票默默记账。\n" +
            "你看人时，习惯攒齐矛盾在关键轮次一针见血点破；克制不等于逃避表态或死藏该跳的身份——" +
            "每轮仍给清站边与票向，该亮底牌保轮次时果断出手。",
    ),

    STUBBORN(
        id = "stubborn",
        label = "执拗较真",
        tagline = "盯死逻辑漏洞，逼对方给说法",
        brief =
            "【你的牌风：执拗较真】你讨厌和稀泥，一旦抓到对方的逻辑缺口就会追问到底，直到对方给出合理解释或露怯。\n" +
            "你看人时，习惯把前后矛盾刨深刨透；但你的较真是对事实较真，不是赌气——" +
            "如果出现更硬的新证据（如验人改口、身份拍明），你会干脆利落重盘。",
    ),

    ADAPTIVE(
        id = "adaptive",
        label = "随机应变",
        tagline = "顺风不浪、逆风不慌，随新证据重盘",
        brief =
            "【你的牌风：随机应变】你不死守旧立场。新信息一出现就重新算账，" +
            "前一刻怀疑的人，后一刻证据对上就敢翻案；死撑错判断在你眼里最不可取。\n" +
            "你看人时，习惯看「谁在新信息出来后改了口、谁还在硬撑」，每次改站边都会说清触发改变的具体依据。",
    ),

    WARM(
        id = "warm",
        label = "温和引导",
        tagline = "不撕咬，靠把道理讲明白带票",
        brief =
            "【你的牌风：温和引导】你不靠压人取胜，习惯平和地把推理链摊开讲，让大家自己听明白该信谁、把票拢齐。\n" +
            "你看人时，习惯听谁在认真讲可核依据、谁在情绪拉票或转移焦点；温和不等于没主见，该拍板归票时毫不含糊。",
    ),
    ;

    companion object {
        val DEFAULT = CAUTIOUS

        fun fromId(id: String?): PlayerStyle? = entries.firstOrNull { it.id == id }
    }
}

internal fun assignPlayerStyles(
    friendIds: List<String>,
    pinned: Map<String, String> = emptyMap(),
): Map<String, PlayerStyle> {
    if (friendIds.isEmpty()) return emptyMap()
    val all = PlayerStyle.entries
    val result = LinkedHashMap<String, PlayerStyle>()
    val used = HashSet<String>()
    for (id in friendIds) {
        val style = pinned[id]?.let { PlayerStyle.fromId(it) }
        if (style != null) {
            result[id] = style
            used.add(style.id)
        }
    }
    val auto = friendIds.filter { it !in result }
    for (id in auto) {
        val start = abs(stableHash(id)) % all.size
        var pick = all[start]
        for (offset in 1..all.size) {
            val candidate = all[(start + offset) % all.size]
            if (candidate.id !in used) {
                pick = candidate
                break
            }
        }
        result[id] = pick
        used.add(pick.id)
    }
    return friendIds.associateWith { result.getValue(it) }
}

private fun stableHash(s: String): Int {
    var h = 0
    for (ch in s) h = 31 * h + ch.code
    return h
}

internal fun playerStyleBrief(style: PlayerStyle?, config: WerewolfConfig): String? {
    if (style == null) return null
    val body = if (config.hasSheriff) {
        style.brief.trim()
    } else {
        style.brief.trim().replace("警徽流兑现", "验人兑现")
    }
    return buildString {
        append("【你在牌桌上的性格】\n")
        append(body)
        append("\n\n")
        append("这是你打牌时的思考风格，优先于上面那段日常性格；")
        append("但永远服从你的阵营胜负目标、硬规则、保密红线与团队分工。")
    }
}
