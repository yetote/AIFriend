package com.ui.werewolf

import com.ui.werewolf.data.UIWerewolfLive
import com.ui.werewolf.data.UIWerewolfStreaming
import com.ui.werewolf.data.UIWerewolfWaiting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 等待卡片微状态与深度思考流式交接单测。
 */
class WaitingMicroStatusTest {

    @Test
    fun `夜间遮罩微状态仅随时间平滑推进，绝不泄露任何角色或夜间阶段词`() {
        val waiting = UIWerewolfWaiting(
            key = "day2-NIGHT-masked",
            actionLabel = "夜色渐深，各角色正在行动",
            maskedNight = true,
            limitSeconds = 60,
        )
        val forbidden = listOf("狼人", "预言家", "女巫", "守卫", "猎人", "查验", "下刀", "毒", "救")
        val samples = listOf(0, 5, 10, 25, 60).map { waitingMicroStatus(waiting, it) }
        samples.forEach { text ->
            assertTrue("微状态文案不能为空", text.isNotBlank())
            forbidden.forEach { word ->
                assertFalse("夜间遮罩文案绝不能包含敏感词「$word」：$text", text.contains(word))
            }
        }
        assertTrue("不同时间段微状态应自然变化", samples.toSet().size >= 3)
    }

    @Test
    fun `白天单人思考微状态随耗时递进，且特定动作有针对性文案`() {
        val speech = UIWerewolfWaiting(
            key = "day1-DAY_DISCUSSION-3--1",
            seat = 3,
            actionLabel = "正在组织语言",
            maskedNight = false,
            limitSeconds = 60,
        )
        val speechStages = listOf(0, 8, 16, 30).map { waitingMicroStatus(speech, it) }
        assertEquals(4, speechStages.toSet().size)

        val wolfChat = speech.copy(actionLabel = "正在狼队密谈")
        assertTrue(waitingMicroStatus(wolfChat, 2).contains("刀口"))
        assertTrue(waitingMicroStatus(wolfChat, 12).contains("狼队"))

        val hunter = speech.copy(actionLabel = "正在抉择是否开枪")
        assertTrue(waitingMicroStatus(hunter, 3).contains("一枪"))

        val direction = speech.copy(actionLabel = "正在决定发言顺序")
        assertTrue(waitingMicroStatus(direction, 3).contains("警左"))
    }

    @Test
    fun `深度思考未吐正文时让位给 WaitingCard，正文开始后切入 StreamingLine`() {
        val waiting = UIWerewolfWaiting(
            key = "day1-DAY_DISCUSSION-3--1",
            seat = 3,
            speaker = "玩家3",
            actionLabel = "正在组织语言",
            maskedNight = false,
            limitSeconds = 60,
        )
        val thinkingOnly = UIWerewolfStreaming(
            seat = 3,
            speaker = "玩家3",
            text = "",
            reasoning = "先分析昨天票型…",
        )
        val liveThinking = UIWerewolfLive(
            day = 1,
            phaseLabel = "白天讨论",
            night = false,
            running = true,
            finished = false,
            winnerLabel = null,
            stageLabel = "发言 1/8 · 3 号",
            streaming = thinkingOnly,
            waiting = waiting,
        )
        // 尚无正文且有 waiting：交由 WaitingCard 持续走秒表与微状态
        assertNull(resolveFeedStreaming(liveThinking, allowed = thinkingOnly, pinned = null))

        // 吐出正文后立刻切换为逐字 StreamingLine
        val speaking = thinkingOnly.copy(text = "我觉得 1 号发言很奇怪")
        val liveSpeaking = liveThinking.copy(streaming = speaking)
        val resolved = resolveFeedStreaming(liveSpeaking, allowed = speaking, pinned = null)
        assertNotNull(resolved)
        assertEquals(speaking.text, resolved!!.text)

        // 若没有 waiting 兜底，则保留原有 StreamingLine
        val noWaiting = liveThinking.copy(waiting = null)
        assertNotNull(resolveFeedStreaming(noWaiting, allowed = thinkingOnly, pinned = null))
    }

    @Test
    fun `等待计时器锚定起始时间戳后重建卡片不会归零且不会产生负数`() {
        val started = 10_000L
        // 卡片划出视野后 18.9 秒重新进入组合，首帧即按锚定时间继续计时而非归零
        assertEquals(18, elapsedSecondsSince(startedAtMs = started, nowMs = started + 18_900L))
        assertEquals(0, elapsedSecondsSince(startedAtMs = 0L, nowMs = started + 5_000L))
        // 系统时钟回调时不出现负秒数
        assertEquals(0, elapsedSecondsSince(startedAtMs = started, nowMs = started - 2_000L))
    }
}
