package com.feature.werewolf.prompt.roles

import com.feature.werewolf.game.WerewolfConfig
import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfRole

/** 猎人：用最后一枪追回轮次的强神。 */
internal object HunterKit : RoleKit {

    override val roles = setOf(WerewolfRole.HUNTER)

    override fun rules(role: WerewolfRole): List<String> = listOf(
        "猎人被狼人杀死或被投票放逐时可以开枪带走一个人；被女巫毒死时不能开枪。" +
            "这一枪在出局结算前打出，可能直接改写胜负。",
    )

    override fun notes(game: WerewolfGame, seat: Int): List<String> = listOf(
        "你是猎人：被狼人杀死或被投票放逐时可以开枪带走一个人；如果是被女巫毒死的，不能开枪。"
    )

    override fun playbook(role: WerewolfRole, config: WerewolfConfig): List<String> = listOf(
        "你是猎人，平时藏好身份、不要冒充预言家抢警徽；但感觉自己要吃女巫毒或要被抗推时，果断跳明猎人自保（被票出还能开枪，吃毒就什么都没了）。",
        "开枪目标优先级：对跳猎人的 > 查杀你的 > 已经坐实的悍跳/冲锋狼 > 全程话少划水但票型紧跟狼队的深水狼。",
        "开枪前多想一层：**先排除「藏着没跳的神职（尤其可能乱打的女巫/守卫）」和替真预挡枪的好人**，别只凭谁说话怪就一枪误杀队友。",
        "首夜被刀且零信息时，可以选择不开枪（黑枪），用遗言拍明猎人身份防狼穿衣服。",
        "中后期局势紧张时可以适当预告枪口施压，但不要在没有把握的残局瞎开枪——打错好人可能直接送狼绑票。",
    )

    override fun conceal(role: WerewolfRole): String =
        "未面临吃毒威胁或抗推风险前，不要主动说出自己的猎人身份。"
}
