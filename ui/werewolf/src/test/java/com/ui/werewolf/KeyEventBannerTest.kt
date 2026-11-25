package com.ui.werewolf

import com.ui.werewolf.data.UIWerewolfLine
import com.ui.werewolf.data.UIWerewolfLineKind
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 关键事件横幅解析测试：只对公开上帝播报（DIVINE && !secret）触发，
 * 绝不对玩家发言、私密夜聊或普通阶段提示误触发。
 */
class KeyEventBannerTest {

    @Test
    fun `公开的关键上帝播报会生成事件横幅`() {
        val cases = listOf(
            "第1天 天亮：平安夜，没有人出局",
            "第2天 天亮：5 号出局",
            "第1天：3 号狼人自爆，直接进入黑夜",
            "第2天：猎人 4 号开枪带走了 9 号",
            "第2天 投票：6 号以 5 票被投出，翻开是白痴，没有出局，此后失去投票权",
            "第1天：2 号当选警长（4 票）",
            "第2天：警长 2 号把警徽交给 7 号",
        )
        cases.forEach { text ->
            val line = UIWerewolfLine(index = 1, kind = UIWerewolfLineKind.DIVINE, text = text, secret = false)
            assertNotNull("应触发横幅：$text", parseKeyEventBanner(line))
        }
    }

    @Test
    fun `玩家发言与私密消息绝不触发事件横幅`() {
        val speech = UIWerewolfLine(
            index = 2,
            kind = UIWerewolfLineKind.SPEECH,
            seat = 3,
            text = "昨天是平安夜，我怀疑 1 号自爆。",
            secret = false,
        )
        val secretDivine = UIWerewolfLine(
            index = 3,
            kind = UIWerewolfLineKind.DIVINE,
            text = "第1天 天亮：平安夜，没有人出局",
            secret = true,
        )
        assertNull("玩家口述不能冒充上帝播报", parseKeyEventBanner(speech))
        assertNull("私密标记消息不能触发横幅", parseKeyEventBanner(secretDivine))
    }
}
