package com.ui.werewolf.data

import androidx.compose.ui.graphics.Color
import com.ui.werewolf.DaySurface
import com.ui.werewolf.NightStroke
import com.ui.werewolf.NightSurface

/**
 * 一条对话气泡的类别。
 *
 * 复盘和牌桌上每句话的**性质**都不同：白天的公开发言、投票表态、
 * 夜里只有当事人知道的话、上帝的播报、还有本人心里想的。
 * 混在一列里读，看完三句就分不清哪句是场上说的、哪句是夜里悄悄说的——
 * 而这恰恰是最要紧的区分：夜里的话解释了白天的一切。
 *
 * 所以每条都套一个气泡，颜色和样式都不同。
 */
enum class UIWerewolfBubbleKind(val label: String) {
    /** 白天公开发言。 */
    SPEECH("发言"),

    /** 投票与表态。 */
    VOTE("投票"),

    /** 夜间队内沟通：狼人夜聊、神职的夜间行动。 */
    SECRET("密谈"),

    /** 上帝播报。 */
    BROADCAST("播报"),

    /** 内心活动（只有本人知道的心思）。 */
    INNER("内心"),

    /** 模型的思考过程（复盘才有）。 */
    THOUGHT("思考"),
}

/**
 * 一类气泡的视觉规格。
 *
 * 复盘页和牌桌**共用同一张表**：同一个「密谈」在两处必须是同一种紫，
 * 不然用户在牌桌上建立的印象，到复盘就断了。
 */
data class UIWerewolfBubbleStyle(
    /** 气泡底色。 */
    val fill: Color,
    /** 气泡描边。 */
    val border: Color,
    /** 强调色：左侧竖线、角标、说话人名字。 */
    val accent: Color,
    /** 角标文字。 */
    val tag: String,
)

/**
 * 牌桌纸片底。
 *
 * 牌桌背景是插画（白天天空 + 远山、夜里星月），半透明气泡会跟着背景一起变色 ——
 * 同一句发言躺在暖色的地平线上偏黄、躺到山影里偏灰，一列读下来像两种东西。
 * 牌桌改用牌桌自己的卡片色做不透明纸片底，字永远压在同一块底色上。
 *
 * 复盘页不跟着改：静态列表里那种淡底更容易分清主次，而且复盘没有插画背景。
 */
fun tablePaperFill(night: Boolean): Color = if (night) NightSurface else DaySurface

/** 纸片底的细描边，只负责把气泡从背景上勾出来。 */
fun tablePaperStroke(night: Boolean): Color =
    if (night) NightStroke else Color(0x1A1B2230)

/**
 * 牌桌气泡样式：[bubbleStyle] 只换底色和描边。
 *
 * accent（左侧竖线、角标）和文字色一概不动 —— 类型仍靠它们区分，
 * 否则所有气泡都成了同一张白纸。
 */
fun tableBubbleStyle(kind: UIWerewolfBubbleKind, night: Boolean): UIWerewolfBubbleStyle =
    bubbleStyle(kind, night).copy(
        fill = tablePaperFill(night),
        border = tablePaperStroke(night),
    )

/**
 * 按类别和明暗取气泡样式。
 *
 * 白天和夜晚是两套色：牌桌夜里是深底，同一个底色压到深底上会糊成一片，
 * 分不出气泡和背景。
 */
fun bubbleStyle(kind: UIWerewolfBubbleKind, night: Boolean): UIWerewolfBubbleStyle =
    when (kind) {
        // 公开发言：中性。中性才不抢戏——场上说的话是最普通的那一类
        UIWerewolfBubbleKind.SPEECH -> if (night) {
            UIWerewolfBubbleStyle(Color(0x14FFFFFF), Color(0x1FFFFFFF), Color(0xFFE6EAF3), "发言")
        } else {
            UIWerewolfBubbleStyle(Color(0x0F1B2230), Color(0x141B2230), Color(0xFF3A4152), "发言")
        }

        // 投票：琥珀。票是全场唯一会被当场执行的表态，单独一个颜色
        UIWerewolfBubbleKind.VOTE -> if (night) {
            UIWerewolfBubbleStyle(Color(0x1FB58A00), Color(0x33D8A22E), Color(0xFFE3B25A), "投票")
        } else {
            UIWerewolfBubbleStyle(Color(0x12B58A00), Color(0x2EB58A00), Color(0xFF9A6A12), "投票")
        }

        // 夜间队内沟通：紫。紫是全场唯一只出现在夜里的颜色，一眼就知道「这是夜里的话」
        UIWerewolfBubbleKind.SECRET -> if (night) {
            UIWerewolfBubbleStyle(Color(0x1F7A5AF7), Color(0x3D9A7CFF), Color(0xFFA894FF), "密谈")
        } else {
            UIWerewolfBubbleStyle(Color(0x147A5AF7), Color(0x2E7A5AF7), Color(0xFF6B4BD9), "密谈")
        }

        // 内心活动：灰 + 竖线。刻意做得比发言更淡、更小，
        // 没人听见的字写成发言的样子，复盘时会对不上自己听到的内容
        UIWerewolfBubbleKind.INNER -> if (night) {
            UIWerewolfBubbleStyle(Color(0x0DFFFFFF), Color(0x14FFFFFF), Color(0x66FFFFFF), "内心")
        } else {
            UIWerewolfBubbleStyle(Color(0x0A1B2230), Color(0x141B2230), Color(0x667C8496), "内心")
        }

        // 思考过程：虚线框 + 极淡底。虚线是「这不是他说出口的话」的视觉信号
        UIWerewolfBubbleKind.THOUGHT -> if (night) {
            UIWerewolfBubbleStyle(Color(0x0AFFFFFF), Color(0x26FFFFFF), Color(0x59FFFFFF), "思考")
        } else {
            UIWerewolfBubbleStyle(Color(0x081B2230), Color(0x1A1B2230), Color(0x597C8496), "思考")
        }

        // 播报：居中分隔线，不套气泡
        UIWerewolfBubbleKind.BROADCAST -> if (night) {
            UIWerewolfBubbleStyle(Color(0x14FFFFFF), Color(0x00FFFFFF), Color(0x99FFFFFF), "播报")
        } else {
            UIWerewolfBubbleStyle(Color(0x0D000000), Color(0x00000000), Color(0x997C8496), "播报")
        }
    }
