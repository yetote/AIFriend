package com.feature.werewolf.prompt.roles

import com.feature.werewolf.game.WerewolfConfig
import com.feature.werewolf.game.WerewolfRole
import kotlin.random.Random

/**
 * 狼队每局随机抽取的「开局战术风向卡」——只在**第 1 夜狼人夜聊**注入，
 * 给头狼提供差异化的破局灵感，让每局都能打出截然不同、更有观赏性的剧本。
 *
 * 明确标注为「启发参考而非死命令」：头狼可结合首夜局势采纳或微调，
 * 既能打破千篇一律的「单跳模板」，又不会把模型锁死在愚蠢操作里。
 */
internal object WolfStyleCards {

    private data class StyleCard(
        val title: String,
        val hint: String,
        val needs: (WerewolfConfig) -> Boolean = { true },
    )

    private val all = listOf(
        StyleCard(
            title = "稳健控场流",
            hint = "标准阵型推进：1 狼单悍跳争警徽、1 狼冷静挑真预硬破绽冲锋、其余狼藏深水或择机倒钩；夜刀优先外置位避开守卫焦点，稳控轮次。",
        ),
        StyleCard(
            title = "双狼悍跳搅局流",
            hint = "可派 2 只狼同时上警跳预言家制造多边混战，打乱真预言家节奏并借警下狼票抢徽或打入 PK，另一只狼隐匿警下控场。",
            needs = { it.hasSheriff && wolfCount(it) >= 3 },
        ),
        StyleCard(
            title = "狼踩狼做身份流",
            hint = "悍跳狼可考虑给一名狼队友发查杀（双方硬刚演对立）或发金水让队友当场反水立好人相，强行把一名狼队友抬进好人核心圈藏到终局。",
            needs = { wolfCount(it) >= 3 },
        ),
        StyleCard(
            title = "花板子骗药/盗银水流",
            hint = "可评估首夜自刀骗女巫解药（防守救同归），或若刀口平安、天亮抢在真女巫前悍跳女巫盗走银水打乱神边节奏，制造高光博弈。",
            needs = { it.has(WerewolfRole.WITCH) },
        ),
        StyleCard(
            title = "强攻控徽/吞徽流",
            hint = "开局围绕警徽做极致压迫：能抢到警徽就借发言顺序压死好人；一旦警上局势明显不利，果断考虑双爆吞警徽，拖入无警长盲打强推屠边。",
            needs = { it.hasSheriff && wolfCount(it) >= 3 },
        ),
    )

    fun pick(config: WerewolfConfig, random: Random = Random.Default): String {
        val candidates = all.filter { it.needs(config) }.ifEmpty { listOf(all.first()) }
        val card = candidates[random.nextInt(candidates.size)]
        return "${card.title}——${card.hint}"
    }

    private fun wolfCount(config: WerewolfConfig): Int =
        config.composition.entries.filter { (role, _) -> role.isWerewolf }.sumOf { it.value }
}
