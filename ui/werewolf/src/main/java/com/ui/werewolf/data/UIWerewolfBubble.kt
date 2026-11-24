package com.ui.werewolf.data

import androidx.compose.ui.graphics.Color

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

    /** 唱票卡：一轮投票结束后的公开票型。 */
    BALLOT("记票"),

    /** 夜间队内沟通：狼人夜聊、神职的夜间行动。 */
    SECRET("密谈"),

    /** 上帝播报。 */
    BROADCAST("播报"),

    /** 内心活动（只有本人知道的心思）。 */
    INNER("内心"),

    /**
     * 预言家的查验结论。
     *
     * 单独一个色相：它不是「心里话」，是**情报**——预言家唯一的价值所在。
     * 跟内心活动（灰蓝）共用排版会被当成闲话划过去。
     * 用青绿：跟记票的青蓝（偏蓝）、投票的琥珀、密谈的紫都拉得开，
     * 也不带「红=狼 / 绿=好人」的暗示，免得玩家把气泡颜色当成验人结论。
     */
    SEER_RESULT("查验"),

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
 * 牌桌气泡样式：一类一个色相，全部半透明。
 *
 * 牌桌背景是插画（白天天空 + 远山、夜里星月），气泡必须能透出背景，
 * 才看得出「浮在牌桌上」而不是糊了一张卡片；可全用一个中性色，
 * 投票、记票、密谈、内心又会长得一模一样——而这几类恰恰是最该一眼分清的。
 *
 * 所以：**色相区分类型，透明度统一压得很低**。不做实底。
 *
 * 和 [bubbleStyle] 的分工：复盘页是静态列表、没有插画，用 [bubbleStyle] 的淡底；
 * 牌桌有背景，用这里的色相版。两边的 accent 和角标保持一致。
 */
fun tableBubbleStyle(kind: UIWerewolfBubbleKind, night: Boolean): UIWerewolfBubbleStyle {
    val base = bubbleStyle(kind, night)
    val fill = when (kind) {
        // 发言：白天柔白玻璃底（通透不发灰），夜里深蓝玻璃底，文字清晰可读
        UIWerewolfBubbleKind.SPEECH ->
            if (night) Color(0x8C17233B) else Color(0xCCFFFFFF)

        // 投票：琥珀。票是唯一会被当场执行的表态
        UIWerewolfBubbleKind.VOTE ->
            if (night) Color(0x33D99B30) else Color(0x22D69A1E)

        // 记票：青蓝。它是一张统计结果，不是谁说的话，和投票的琥珀分开
        UIWerewolfBubbleKind.BALLOT ->
            if (night) Color(0x88142540) else Color(0xD4F2F7FF)

        // 密谈：紫。紫是唯一只出现在夜里的颜色
        UIWerewolfBubbleKind.SECRET ->
            if (night) Color(0x452A1D54) else Color(0x207A5AF7)

        // 内心：冷灰蓝。比发言更淡，色相也偏冷——这些字没人听见，分量最轻
        UIWerewolfBubbleKind.INNER ->
            if (night) Color(0x52172133) else Color(0x78EDF2FA)

        // 查验结论：青绿。情报色，和记票的青蓝差一个色相（那边偏蓝）
        UIWerewolfBubbleKind.SEER_RESULT ->
            if (night) Color(0x4D133336) else Color(0xCCEAFAF7)

        // 思考：靛蓝。和密谈的紫拉开，否则两团紫会混
        UIWerewolfBubbleKind.THOUGHT ->
            if (night) Color(0x335362CC) else Color(0x1C6A78E8)

        // 播报：中性玻璃胶囊
        UIWerewolfBubbleKind.BROADCAST ->
            if (night) Color(0x52182438) else Color(0xA6FFFFFF)
    }
    val border = when (kind) {
        UIWerewolfBubbleKind.SPEECH ->
            if (night) Color(0x2EFFFFFF) else Color(0x1F1B2B45)
        UIWerewolfBubbleKind.VOTE ->
            if (night) Color(0x66E6B046) else Color(0x44C28D18)
        UIWerewolfBubbleKind.BALLOT ->
            if (night) Color(0x5973BAFF) else Color(0x3D2B6BFF)
        UIWerewolfBubbleKind.SECRET ->
            if (night) Color(0x66A88CFF) else Color(0x407A5AF7)
        UIWerewolfBubbleKind.INNER ->
            if (night) Color(0x1FFFFFFF) else Color(0x181B2B45)
        // 查验结论：描边要比内心重一档——它是结论，不是念头
        UIWerewolfBubbleKind.SEER_RESULT ->
            if (night) Color(0x665FD9C6) else Color(0x4D1E8C7F)
        UIWerewolfBubbleKind.THOUGHT ->
            if (night) Color(0x59A0AAFF) else Color(0x3D6A78E8)
        UIWerewolfBubbleKind.BROADCAST ->
            if (night) Color(0x24FFFFFF) else Color(0x181B2B45)
    }
    return base.copy(fill = fill, border = border)
}

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

        // 记票：青蓝。和投票的琥珀分开——一个是「我投了谁」，一个是「全场投了谁」
        UIWerewolfBubbleKind.BALLOT -> if (night) {
            UIWerewolfBubbleStyle(Color(0x0F2FA8FF), Color(0x266FB6FF), Color(0xFF7FC0FF), "记票")
        } else {
            UIWerewolfBubbleStyle(Color(0x0A2B6BFF), Color(0x1F2B6BFF), Color(0xFF2B6BFF), "记票")
        }

        // 内心活动：灰 + 竖线。刻意做得比发言更淡、更小，
        // 没人听见的字写成发言的样子，复盘时会对不上自己听到的内容
        UIWerewolfBubbleKind.INNER -> if (night) {
            UIWerewolfBubbleStyle(Color(0x0DFFFFFF), Color(0x14FFFFFF), Color(0x66FFFFFF), "内心")
        } else {
            UIWerewolfBubbleStyle(Color(0x0A1B2230), Color(0x141B2230), Color(0x667C8496), "内心")
        }

        // 查验结论：青绿。正文用正常字号和强调色——它是这局最该被看清的一条私信
        UIWerewolfBubbleKind.SEER_RESULT -> if (night) {
            UIWerewolfBubbleStyle(Color(0x1F2BB3A3), Color(0x4D2FB3A3), Color(0xFF5FD9C6), "查验")
        } else {
            UIWerewolfBubbleStyle(Color(0x1520A396), Color(0x3320A396), Color(0xFF1E8C7F), "查验")
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
