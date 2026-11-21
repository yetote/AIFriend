package com.ui.werewolf

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 夜间星空不能排成直线。
 *
 * 曾经横向用 1/φ=0.6180、纵向用 1/φ²=0.3820，两者相加恰好为 1：
 * 取模后 fx + fy/0.58 恒等于常数，26 颗星里有 24 颗落在同一条斜线上，
 * 肉眼看起来就是「星星排成一条直线」。换成不互补的 R2 常数后修好。
 *
 * 这里不断言具体坐标（调星空布局时会误伤），只守住「铺开了、没共线」这个属性。
 */
class NightStarLayoutTest {

    @Test
    fun `夜间星星铺满天空且不共线`() {
        assertTrue("星星数量应足够铺开天空", NIGHT_STARS.size >= 20)

        // 1. 网格覆盖：把天空划成 5×3 的格子，星星应覆盖大部分格子。
        //    共线时格子覆盖数会明显偏低（旧公式只覆盖 8/15）。
        val cells = NIGHT_STARS.map { (fx, fy, _) ->
            val col = (fx * 5f).toInt().coerceIn(0, 4)
            val row = ((fy / 0.58f) * 3f).toInt().coerceIn(0, 2)
            col to row
        }.toSet()
        assertTrue("星星应铺开到至少 12 个格子，实际 ${cells.size}", cells.size >= 12)

        // 2. 不共线：任意三颗星构成的三角形面积不能全为 0。
        //    取三组跨度大的星，叉积（三角形面积的 2 倍）必须显著非零。
        var maxTriangle = 0f
        for (a in NIGHT_STARS.indices) {
            for (b in a + 1 until NIGHT_STARS.size) {
                for (c in b + 1 until NIGHT_STARS.size) {
                    val (ax, ay, _) = NIGHT_STARS[a]
                    val (bx, by, _) = NIGHT_STARS[b]
                    val (cx, cy, _) = NIGHT_STARS[c]
                    val cross = (bx - ax) * (cy - ay) - (cx - ax) * (by - ay)
                    maxTriangle = maxOf(maxTriangle, kotlin.math.abs(cross))
                }
            }
        }
        assertTrue("星星应构成有面积的三角形，实测最大叉积 $maxTriangle", maxTriangle > 0.05f)
    }
}
