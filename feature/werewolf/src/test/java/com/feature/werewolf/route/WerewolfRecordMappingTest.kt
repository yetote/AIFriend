package com.feature.werewolf.route

import com.feature.werewolf.game.Phase
import com.feature.werewolf.store.WerewolfGameSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 战绩列表的「第 N 夜 / 第 N 天」必须和牌桌顶栏一个口径。
 *
 * 牌桌一直是分开报的（夜里「第 1 夜」、天亮「第 1 天」），列表原来一律写「天」，
 * 于是停在「狼人商议」的局读起来像白天。这个测试盯的就是这处对齐。
 */
class WerewolfRecordMappingTest {

    private fun summary(phase: Phase, day: Int = 2): WerewolfGameSummary = WerewolfGameSummary(
        id = "g1",
        createdAt = 0L,
        updatedAt = 0L,
        finished = false,
        day = day,
        phase = phase.name,
        winner = null,
    )

    @Test
    fun `夜里的阶段记成夜`() {
        val record = summary(Phase.NIGHT_WOLF_CHAT).toRecord()
        assertTrue("狼人商议在夜里", record.night)
        assertEquals("第 2 夜", record.dayLabel)
    }

    @Test
    fun `白天阶段记成天`() {
        val record = summary(Phase.DAY_DISCUSSION).toRecord()
        assertFalse("白天讨论不算夜里", record.night)
        assertEquals("第 2 天", record.dayLabel)
    }

    @Test
    fun `夜和天共用序号，第 1 夜属于第 1 天`() {
        // 引擎只在进入新的一夜时加天，所以第一夜和第一天的 day 都是 1
        assertEquals("第 1 夜", summary(Phase.NIGHT_SEER, day = 1).toRecord().dayLabel)
        assertEquals("第 1 天", summary(Phase.DAY_VOTE, day = 1).toRecord().dayLabel)
        assertEquals("第 2 夜", summary(Phase.NIGHT_WOLF, day = 2).toRecord().dayLabel)
    }

    @Test
    fun `猎人开枪归到白天`() {
        // 被刀的猎人天亮就开枪、被票出的猎人也是白天开枪，两种都在白天播报里
        val record = summary(Phase.HUNTER_SHOOT).toRecord()
        assertFalse("开枪按白天算", record.night)
        assertEquals("第 2 天", record.dayLabel)
    }

    @Test
    fun `认不出的阶段原样显示且算白天`() {
        val record = WerewolfGameSummary(
            id = "old",
            createdAt = 0L,
            updatedAt = 0L,
            finished = true,
            day = 3,
            phase = "SOMETHING_NEW",
            winner = null,
        ).toRecord()
        assertEquals("SOMETHING_NEW", record.phaseLabel)
        assertFalse(record.night)
        assertEquals("第 3 天", record.dayLabel)
    }
}
