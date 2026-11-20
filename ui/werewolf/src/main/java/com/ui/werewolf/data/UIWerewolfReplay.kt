package com.ui.werewolf.data

/**
 * 复盘演出的播放状态。
 *
 * 演出就是把一局已经打完的存档**当成正在发生**重放一遍：座位、昼夜、气泡、朗读
 * 全都按当时的顺序来。所以牌桌需要知道「演到哪了、在不在播」，由这里给。
 *
 * 和 [UIWerewolfLive.running] 的区别：那个是「引擎在不在推进」，这个只关心播放条。
 * 演出时引擎根本不存在，牌桌绝不能因为 `running=false` 就切回「已结束」的样子。
 */
data class UIWerewolfReplay(
    val playing: Boolean,
    /** 当前是不是上帝视角。演出页里可以直接切，切了时间线重摊、从头再放。 */
    val godView: Boolean,
    /** 「1x」「1.5x」「2x」：只影响句与句之间的停顿，朗读本身不加速。 */
    val speedLabel: String,
    /** 「第 12 / 86 条」。 */
    val positionLabel: String,
    /** 0..1，播放条用。 */
    val progress: Float,
    val canPrev: Boolean,
    val canNext: Boolean,
)

/**
 * 演出前要补座位的那种「人已经不在了」。
 *
 * 存档里只记了座位号和当时的名字，角色本身（音色、头像）来自好友列表。
 * 好友被删掉之后，这一格就没有声音、没有头像——牌桌会缺一个人。
 *
 * 补法是**手动指定一个不在这一局里的好友**，而不是随便拿一个顶上：
 * 替身的名字和音色会出现在牌桌上，撞上局内已有的角色会让人分不清谁是谁。
 */
data class UIWerewolfMissingSeat(
    val seat: Int,
    /** 存档里记的名字，用来告诉用户「缺的是谁」。 */
    val name: String,
    /** 已经选好的替身，null = 还没选。 */
    val substituteId: String? = null,
    /** 明确选了「这个座位就静音」。 */
    val muted: Boolean = false,
)
