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
)

/**
 * 牌桌气泡样式：适配「暖羊皮白昼 × 烛影曜石黑夜」，保留全类别色相区分与高对比阅读性。
 */
fun tableBubbleStyle(kind: UIWerewolfBubbleKind, night: Boolean): UIWerewolfBubbleStyle {
    val base = bubbleStyle(kind, night)
    val fill = when (kind) {
        UIWerewolfBubbleKind.SPEECH ->
            if (night) Color(0xA61E1815) else Color(0xDCFFFDF9)

        UIWerewolfBubbleKind.VOTE ->
            if (night) Color(0x36D0982E) else Color(0x26C98C1D)

        UIWerewolfBubbleKind.BALLOT ->
            if (night) Color(0x901B212E) else Color(0xDCF3F5FA)

        UIWerewolfBubbleKind.SHERIFF_ROSTER ->
            if (night) Color(0x8C2A2016) else Color(0xE0FCF5E8)

        UIWerewolfBubbleKind.SECRET ->
            if (night) Color(0x4D322048) else Color(0x207D56D8)

        UIWerewolfBubbleKind.INNER ->
            if (night) Color(0x5E1F1A17) else Color(0x88EFEAE0)

        UIWerewolfBubbleKind.SEER_RESULT ->
            if (night) Color(0x52142F2A) else Color(0xD8E8F6F1)

        UIWerewolfBubbleKind.THOUGHT ->
            if (night) Color(0x3648559E) else Color(0x1E4B63B8)

        UIWerewolfBubbleKind.BROADCAST ->
            if (night) Color(0x661F1916) else Color(0xBAFFFDF9)
    }
    val border = when (kind) {
        UIWerewolfBubbleKind.SPEECH ->
            if (night) Color(0x2EF5E8D8) else Color(0x22241C14)
        UIWerewolfBubbleKind.VOTE ->
            if (night) Color(0x6CE5B552) else Color(0x4AB87A1F)
        UIWerewolfBubbleKind.BALLOT ->
            if (night) Color(0x598AB4F8) else Color(0x3D2F5DA8)
        UIWerewolfBubbleKind.SHERIFF_ROSTER ->
            if (night) Color(0x66E5B869) else Color(0x4DD4722A)
        UIWerewolfBubbleKind.SECRET ->
            if (night) Color(0x6CB494FF) else Color(0x427D56D8)
        UIWerewolfBubbleKind.INNER ->
            if (night) Color(0x22F5E8D8) else Color(0x1C241C14)
        UIWerewolfBubbleKind.SEER_RESULT ->
            if (night) Color(0x6B60D6BE) else Color(0x521E7E6C)
        UIWerewolfBubbleKind.THOUGHT ->
            if (night) Color(0x5995A8F0) else Color(0x3D4B63B8)
        UIWerewolfBubbleKind.BROADCAST ->
            if (night) Color(0x28F5E8D8) else Color(0x1C241C14)
    }
    return base.copy(fill = fill, border = border)
}

fun bubbleStyle(kind: UIWerewolfBubbleKind, night: Boolean): UIWerewolfBubbleStyle =
    when (kind) {
        UIWerewolfBubbleKind.SPEECH -> if (night) {
            UIWerewolfBubbleStyle(Color(0x18F5E8D8), Color(0x24F5E8D8), Color(0xFFF3ECE2), "发言")
        } else {
            UIWerewolfBubbleStyle(Color(0x0E241C14), Color(0x1A241C14), Color(0xFF3E362E), "发言")
        }

        UIWerewolfBubbleKind.VOTE -> if (night) {
            UIWerewolfBubbleStyle(Color(0x22C48D20), Color(0x3ADCA843), Color(0xFFE8BE68), "投票")
        } else {
            UIWerewolfBubbleStyle(Color(0x14B87A1F), Color(0x33B87A1F), Color(0xFF9C6413), "投票")
        }

        UIWerewolfBubbleKind.SECRET -> if (night) {
            UIWerewolfBubbleStyle(Color(0x22845EE6), Color(0x42A88CFF), Color(0xFFB79BFF), "密谈")
        } else {
            UIWerewolfBubbleStyle(Color(0x147D56D8), Color(0x307D56D8), Color(0xFF6B46C6), "密谈")
        }

        UIWerewolfBubbleKind.BALLOT -> if (night) {
            UIWerewolfBubbleStyle(Color(0x144B86E8), Color(0x2E8AB4F8), Color(0xFF8AB4F8), "记票")
        } else {
            UIWerewolfBubbleStyle(Color(0x0D2F5DA8), Color(0x242F5DA8), Color(0xFF2F5DA8), "记票")
        }

        UIWerewolfBubbleKind.SHERIFF_ROSTER -> if (night) {
            UIWerewolfBubbleStyle(Color(0x24D4722A), Color(0x4DE5B869), Color(0xFFF0B45A), "上警")
        } else {
            UIWerewolfBubbleStyle(Color(0x18D4722A), Color(0x3DD4722A), Color(0xFFB85B1B), "上警")
        }

        UIWerewolfBubbleKind.INNER -> if (night) {
            UIWerewolfBubbleStyle(Color(0x10F5E8D8), Color(0x1AF5E8D8), Color(0x70F5E8D8), "内心")
        } else {
            UIWerewolfBubbleStyle(Color(0x0B241C14), Color(0x16241C14), Color(0x706B6259), "内心")
        }

        UIWerewolfBubbleKind.SEER_RESULT -> if (night) {
            UIWerewolfBubbleStyle(Color(0x2226A88F), Color(0x5060D6BE), Color(0xFF60D6BE), "查验")
        } else {
            UIWerewolfBubbleStyle(Color(0x161E7E6C), Color(0x381E7E6C), Color(0xFF1E7E6C), "查验")
        }

        UIWerewolfBubbleKind.THOUGHT -> if (night) {
            UIWerewolfBubbleStyle(Color(0x0DF5E8D8), Color(0x29F5E8D8), Color(0x66F5E8D8), "思考")
        } else {
            UIWerewolfBubbleStyle(Color(0x09241C14), Color(0x1E241C14), Color(0x666B6259), "思考")
        }

        UIWerewolfBubbleKind.BROADCAST -> if (night) {
            UIWerewolfBubbleStyle(Color(0x18F5E8D8), Color(0x00FFFFFF), Color(0x99F3ECE2), "播报")
        } else {
            UIWerewolfBubbleStyle(Color(0x0E241C14), Color(0x00000000), Color(0x996B6259), "播报")
        }
    }
