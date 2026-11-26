package com.ui.werewolf.data

import androidx.compose.ui.graphics.Color

enum class UIWerewolfBubbleKind(val label: String) {
    /** 白天公开发言。 */
    SPEECH("发言"),

    /** 投票与表态。 */
    VOTE("投票"),

    /** 唱票卡：一轮投票结束后的公开票型。 */
    BALLOT("记票"),

    /** 上警名单卡：警长竞选的警上 / 警下座位气泡列表。 */
    SHERIFF_ROSTER("上警"),

    /** 夜间队内沟通：狼人夜聊、神职的夜间行动。 */
    SECRET("密谈"),

    /** 上帝播报。 */
    BROADCAST("播报"),

    /** 内心活动（只有本人知道的心思）。 */
    INNER("内心"),

    /** 预言家的查验结论。 */
    SEER_RESULT("查验"),

    /** 模型的思考过程（复盘才有）。 */
    THOUGHT("思考"),
}

data class UIWerewolfBubbleStyle(
    val fill: Color,
    val border: Color,
    val accent: Color,
    val tag: String,
    val fillBottom: Color = fill,
    val highlight: Color = Color.Transparent,
)

/**
 * 牌桌气泡样式：适配「暖羊皮白昼 × 烛影曜石黑夜」，保留全类别色相区分与高对比阅读性。
 */
fun tableBubbleStyle(kind: UIWerewolfBubbleKind, night: Boolean): UIWerewolfBubbleStyle {
    val base = bubbleStyle(kind, night)
    val fill = when (kind) {
        UIWerewolfBubbleKind.SPEECH ->
            if (night) Color(0xD9251D18) else Color(0xF6FFFEFA)

        UIWerewolfBubbleKind.VOTE ->
            if (night) Color(0x4CD49A30) else Color(0xEBFCF5E6)

        UIWerewolfBubbleKind.BALLOT ->
            if (night) Color(0xAA1D2433) else Color(0xEEF4F7FC)

        UIWerewolfBubbleKind.SHERIFF_ROSTER ->
            if (night) Color(0xA62D2217) else Color(0xEEFDF7EC)

        UIWerewolfBubbleKind.SECRET ->
            if (night) Color(0x663A2454) else Color(0xEEF5F0FC)

        UIWerewolfBubbleKind.INNER ->
            if (night) Color(0x6E211B18) else Color(0xB8F5F0E6)

        UIWerewolfBubbleKind.SEER_RESULT ->
            if (night) Color(0x70153630) else Color(0xEEEAF8F3)

        UIWerewolfBubbleKind.THOUGHT ->
            if (night) Color(0x3648559E) else Color(0x224B63B8)

        UIWerewolfBubbleKind.BROADCAST ->
            if (night) Color(0x781E1815) else Color(0xD4FFFDF9)
    }
    val fillBottom = when (kind) {
        UIWerewolfBubbleKind.SPEECH ->
            if (night) Color(0xC71A1411) else Color(0xEDF8F1E4)

        UIWerewolfBubbleKind.VOTE ->
            if (night) Color(0x36B57D1E) else Color(0xE2F6EAD0)

        UIWerewolfBubbleKind.BALLOT ->
            if (night) Color(0x94161B27) else Color(0xE4EAF0FA)

        UIWerewolfBubbleKind.SHERIFF_ROSTER ->
            if (night) Color(0x94221911) else Color(0xE4F8EBD6)

        UIWerewolfBubbleKind.SECRET ->
            if (night) Color(0x5228183D) else Color(0xE4ECE3FA)

        UIWerewolfBubbleKind.INNER ->
            if (night) Color(0x58181310) else Color(0xA8EDE6D8)

        UIWerewolfBubbleKind.SEER_RESULT ->
            if (night) Color(0x5C0E2622) else Color(0xE4DCF0E9)

        UIWerewolfBubbleKind.THOUGHT ->
            if (night) Color(0x2838427C) else Color(0x184B63B8)

        UIWerewolfBubbleKind.BROADCAST ->
            if (night) Color(0x66161210) else Color(0xC2F7F0E4)
    }
    val border = when (kind) {
        UIWerewolfBubbleKind.SPEECH ->
            if (night) Color(0x42E5B869) else Color(0x2B241C14)
        UIWerewolfBubbleKind.VOTE ->
            if (night) Color(0x7CE8BE68) else Color(0x5EB87A1F)
        UIWerewolfBubbleKind.BALLOT ->
            if (night) Color(0x668AB4F8) else Color(0x472F5DA8)
        UIWerewolfBubbleKind.SHERIFF_ROSTER ->
            if (night) Color(0x70E5B869) else Color(0x57D4722A)
        UIWerewolfBubbleKind.SECRET ->
            if (night) Color(0x7AB494FF) else Color(0x527D56D8)
        UIWerewolfBubbleKind.INNER ->
            if (night) Color(0x2CF5E8D8) else Color(0x24241C14)
        UIWerewolfBubbleKind.SEER_RESULT ->
            if (night) Color(0x8060D6BE) else Color(0x5C1E7E6C)
        UIWerewolfBubbleKind.THOUGHT ->
            if (night) Color(0x5995A8F0) else Color(0x3D4B63B8)
        UIWerewolfBubbleKind.BROADCAST ->
            if (night) Color(0x38E5B869) else Color(0x24241C14)
    }
    val accent = when (kind) {
        UIWerewolfBubbleKind.SPEECH ->
            if (night) Color(0xFFEED3A1) else Color(0xFFB85D32)
        else -> base.accent
    }
    val highlight = when (kind) {
        UIWerewolfBubbleKind.INNER,
        UIWerewolfBubbleKind.THOUGHT -> if (night) Color.Transparent else Color(0x66FFFFFF)
        else -> if (night) Color(0x24F5E8D8) else Color(0xC8FFFFFF)
    }
    return base.copy(
        fill = fill,
        fillBottom = fillBottom,
        border = border,
        accent = accent,
        highlight = highlight,
    )
}

fun bubbleStyle(kind: UIWerewolfBubbleKind, night: Boolean): UIWerewolfBubbleStyle =
    when (kind) {
        UIWerewolfBubbleKind.SPEECH -> if (night) {
            UIWerewolfBubbleStyle(
                fill = Color(0x1EF5E8D8),
                border = Color(0x2CF5E8D8),
                accent = Color(0xFFEED3A1),
                tag = "发言",
                fillBottom = Color(0x14F5E8D8),
                highlight = Color(0x1CF5E8D8),
            )
        } else {
            UIWerewolfBubbleStyle(
                fill = Color(0xFFFFFDF9),
                border = Color(0x24241C14),
                accent = Color(0xFF7D4E32),
                tag = "发言",
                fillBottom = Color(0xFFF6EFE3),
                highlight = Color(0xB8FFFFFF),
            )
        }

        UIWerewolfBubbleKind.VOTE -> if (night) {
            UIWerewolfBubbleStyle(Color(0x26C48D20), Color(0x42DCA843), Color(0xFFE8BE68), "投票", Color(0x1CC48D20), Color(0x1CFCE1A6))
        } else {
            UIWerewolfBubbleStyle(Color(0xFFFDF5E6), Color(0x47B87A1F), Color(0xFF9C6413), "投票", Color(0xFFF7E9CF), Color(0xA8FFFFFF))
        }

        UIWerewolfBubbleKind.SECRET -> if (night) {
            UIWerewolfBubbleStyle(Color(0x26845EE6), Color(0x4AA88CFF), Color(0xFFB79BFF), "密谈", Color(0x1C845EE6), Color(0x1CDCCFFF))
        } else {
            UIWerewolfBubbleStyle(Color(0xFFF4EFFC), Color(0x3D7D56D8), Color(0xFF6B46C6), "密谈", Color(0xFFEAE1FA), Color(0xA8FFFFFF))
        }

        UIWerewolfBubbleKind.BALLOT -> if (night) {
            UIWerewolfBubbleStyle(Color(0x184B86E8), Color(0x338AB4F8), Color(0xFF8AB4F8), "记票", Color(0x124B86E8), Color(0x1AB3D1FF))
        } else {
            UIWerewolfBubbleStyle(Color(0xFFF2F6FC), Color(0x302F5DA8), Color(0xFF2F5DA8), "记票", Color(0xFFE8EFFA), Color(0xB0FFFFFF))
        }

        UIWerewolfBubbleKind.SHERIFF_ROSTER -> if (night) {
            UIWerewolfBubbleStyle(Color(0x28D4722A), Color(0x52E5B869), Color(0xFFF0B45A), "上警", Color(0x1CD4722A), Color(0x1EFFE2AD))
        } else {
            UIWerewolfBubbleStyle(Color(0xFFFDF5E9), Color(0x45D4722A), Color(0xFFB85B1B), "上警", Color(0xFFF7EAD6), Color(0xB0FFFFFF))
        }

        UIWerewolfBubbleKind.INNER -> if (night) {
            UIWerewolfBubbleStyle(Color(0x14F5E8D8), Color(0x20F5E8D8), Color(0x88F5E8D8), "内心")
        } else {
            UIWerewolfBubbleStyle(Color(0xFFF3EFE6), Color(0x22241C14), Color(0xFF6B6259), "内心", Color(0xFFECE6DA))
        }

        UIWerewolfBubbleKind.SEER_RESULT -> if (night) {
            UIWerewolfBubbleStyle(Color(0x2626A88F), Color(0x5760D6BE), Color(0xFF60D6BE), "查验", Color(0x1A26A88F), Color(0x209AF5E2))
        } else {
            UIWerewolfBubbleStyle(Color(0xFFEAF7F2), Color(0x471E7E6C), Color(0xFF1E7E6C), "查验", Color(0xFFDDF0E9), Color(0xB0FFFFFF))
        }

        UIWerewolfBubbleKind.THOUGHT -> if (night) {
            UIWerewolfBubbleStyle(Color(0x12F5E8D8), Color(0x2CF5E8D8), Color(0x80F5E8D8), "思考")
        } else {
            UIWerewolfBubbleStyle(Color(0x0E241C14), Color(0x22241C14), Color(0x8C6B6259), "思考")
        }

        UIWerewolfBubbleKind.BROADCAST -> if (night) {
            UIWerewolfBubbleStyle(Color(0x1CF5E8D8), Color(0x00FFFFFF), Color(0x99F3ECE2), "播报")
        } else {
            UIWerewolfBubbleStyle(Color(0x12241C14), Color(0x00000000), Color(0x996B6259), "播报")
        }
    }
