package com.feature.werewolf.export

import com.ui.werewolf.data.UIWerewolfReview
import com.ui.werewolf.data.UIWerewolfRevealedSeat
import com.ui.werewolf.data.UIWerewolfSpeechLine

/**
 * 复盘 → Markdown 文档。
 *
 * 纯函数：不读时钟、不碰文件。时间只出现在文件名里——正文一旦带时间戳，
 * 同一局导出两次就是两份内容不同的文件，想拿两版对账时反而分不清哪句变了。
 *
 * 排版顺序和复盘页一致：谁赢 → 谁是谁 → 票怎么走的 → 夜里发生了什么 → 当时他们说了什么。
 * 导出来的东西应该和屏幕上是同一份，而不是另写一篇总结——复盘最怕「换个说法」，
 * 一旦改写，玩家就没法拿它跟自己的记忆对账。
 */
object WerewolfMarkdown {

    fun of(review: UIWerewolfReview): String = buildString {
        append("# 狼人杀复盘 · ").append(review.winnerLabel).append("\n\n")
        if (review.reason.isNotBlank()) {
            quote(review.reason)
            append("\n")
        }
        append(review.facts).append("\n")
        review.selfLine?.let {
            append("\n").append(it).append("\n")
        }

        if (review.groups.isNotEmpty()) {
            append("\n## 底牌\n")
            review.groups.forEach { group ->
                append("\n### ").append(group.title).append("\n\n")
                group.members.forEach { seat ->
                    append("- ").append(seatLine(seat)).append("\n")
                }
            }
        }

        if (review.days.isNotEmpty()) {
            append("\n## 逐日票型\n")
            review.days.forEach { day ->
                append("\n### 第 ").append(day.day).append(" 天\n\n")
                append("**").append(day.result).append("**")
                if (day.extraRounds > 0) {
                    append("（加投 ").append(day.extraRounds).append(" 轮）")
                }
                append("\n")
                if (day.ballots.isNotEmpty()) {
                    append("\n")
                    day.ballots.forEach { ballot ->
                        append("- ").append(ballot.voter).append(" 号 ")
                        val target = ballot.target
                        if (target == null) {
                            append("→ 弃票")
                        } else {
                            append("→ ").append(target).append(" 号")
                        }
                        append("\n")
                    }
                }
            }
        }

        if (review.nights.isNotEmpty()) {
            append("\n## 夜间行动\n\n")
            append("| 夜 | 狼刀 | 守卫 | 女巫 | 结果 |\n")
            append("| --- | --- | --- | --- | --- |\n")
            review.nights.forEach { night ->
                val result = if (night.mutualProtection) {
                    night.deathLine + "（守救同归：守卫守到又被女巫救，按规则同归于尽）"
                } else {
                    night.deathLine
                }
                append("| 第 ").append(night.day).append(" 夜 | ")
                    .append(cell(night.wolfLine)).append(" | ")
                    .append(cell(night.guardLine ?: "—")).append(" | ")
                    .append(cell(night.witchLine ?: "—")).append(" | ")
                    .append(cell(result)).append(" |\n")
            }
        }

        if (review.nightDetails.isNotEmpty()) {
            append("\n## 夜间明细\n")
            append("\n> 夜里每个人做了什么、为什么——这些内容在局中只有当事人知道。\n")
            review.nightDetails.forEach { detail ->
                append("\n### 第 ").append(detail.day).append(" 夜\n\n")
                listOfNotNull(
                    detail.wolfPlan?.let { "狼队" to it },
                    detail.guardPlan?.let { "守卫" to it },
                    detail.seerPlan?.let { "预言家" to it },
                    detail.witchPlan?.let { "女巫" to it },
                ).forEach { (who, what) ->
                    append("- **").append(who).append("**：").append(what).append("\n")
                }
                if (detail.lines.isNotEmpty()) {
                    append("\n")
                    detail.lines.forEach { line ->
                        append("**").append(line.speaker).append("**")
                        line.roleLabel?.let { append("（").append(it).append("）") }
                        append(" · ").append(line.label).append("\n")
                        quote(line.text)
                    }
                }
            }
        }

        if (review.checks.isNotEmpty()) {
            append("\n## 预言家查验\n\n")
            review.checks.forEach { check ->
                append("- 第 ").append(check.day).append(" 夜 · 验 ").append(check.target).append(" 号 → ")
                    .append(if (check.isWolf) "狼人" else "好人")
                    .append("\n")
            }
        }

        if (review.dialogues.isNotEmpty()) {
            append("\n## 逐日对话\n")
            // 导出取全量（含夜里只有少数人看得到的部分），但必须先声明一句：
            // 这份文件可能会被转发出去，别人不该在不知情的情况下读到所有人的底牌推理。
            if (review.dialogues.any { day -> day.lines.any { it.secret } }) {
                append("\n> 含狼人夜聊与神职夜间行动——这些内容在局中只有当事人看得到，标记为「密谈」。\n")
            }
            review.dialogues.forEach { day ->
                append("\n### 第 ").append(day.day).append(" 天\n")
                day.lines.forEach { line ->
                    append("\n")
                    append(speakerLine(line))
                    append("\n")
                    quote(line.text.ifBlank { "（空）" })
                    if (line.reasoning.isNotBlank()) {
                        // 用 <details> 折叠：Markdown 没有原生折叠，支持它的渲染器
                        // 默认收起，不支持的也能看到内容，两种情况都不丢信息
                        append("\n<details>\n<summary>思考过程</summary>\n\n")
                        quote(line.reasoning.trim())
                        append("</details>\n")
                    }
                }
            }
        }

        if (review.incomplete) {
            append("\n## 说明\n\n")
            append("这一局是从旧版存档续下来的，当时还没记夜间行动、票型和逐条发言，所以上面少了那几段。\n")
        }
    }

    /** 一张底牌：`**4 号 李四**（你） · 女巫 · 第 2 天被票出 · 共 3 票`。 */
    private fun seatLine(seat: UIWerewolfRevealedSeat): String = buildString {
        append("**").append(seat.seat).append(" 号 ").append(seat.name).append("**")
        if (seat.isSelf) append("（你）")
        append(" · ").append(seat.roleLabel)
        seat.fate?.let { append(" · ").append(it) }
        // 「0 票」不是信息，是挖苦，跟界面上保持一致
        if (seat.votes > 0) append(" · 共 ").append(seat.votes).append(" 票")
    }

    /** 一句话的抬头：`**3 号 张三**（狼人） · 发言 · 密谈`。 */
    private fun speakerLine(line: UIWerewolfSpeechLine): String = buildString {
        append("**").append(line.speaker).append("**")
        line.roleLabel?.let { append("（").append(it).append("）") }
        append(" · ").append(line.label)
        if (line.secret) append(" · 密谈")
    }

    /** 多行正文逐行加 `> `。Markdown 里引用块必须在每一行都带前缀，空行也要。 */
    private fun StringBuilder.quote(text: String) {
        text.lineSequence().forEach { append("> ").append(it).append("\n") }
    }

    /** 表格单元格里的竖线会把一行劈成两列，换行的文案也会让表格断开。 */
    private fun cell(text: String): String = text.replace("|", "\\|").replace("\n", " ")
}
