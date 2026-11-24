package com.ui.agent.data

/**
 * 好友详情页里「服务商额度」这一块的状态。
 *
 * 放在 ui 层自己定义一份，不直接用 core 的余额模型：
 * ui:agent 不依赖 core:repository（那边是 implementation 依赖，拿不到类）。
 * 这一层只负责画，映射在 feature:agent 里做。
 */
data class UIBalanceState(
    /** 服务商名，卡片标题。 */
    val providerName: String = "",
    val status: UIBalanceStatus = UIBalanceStatus.Idle,
    /** 主数字：余额金额或剩余百分比，已经带好单位。 */
    val headline: String = "",
    /** 明细行：赠金 / 充值 / 各窗口剩余 / 重置时间…… */
    val details: List<String> = emptyList(),
    /** 紧张程度，决定主数字的颜色。 */
    val tone: UIBalanceTone = UIBalanceTone.Normal,
    /** 查不到的原因（没配 Key、这家没这个接口、网络失败…）。 */
    val message: String? = null,
    /** 查询时间（毫秒）；0 表示还没查过。 */
    val fetchedAt: Long = 0L,
)

/**
 * 查询状态。
 *
 * 「这家不提供」和「这次没查成」分开：前者是产品事实，
 * 后者换个 Key 或重试可能就好了。混成一句话，界面上的建议就会指错方向。
 */
enum class UIBalanceStatus {
    /** 还没查。 */
    Idle,

    /** 正在查。 */
    Loading,

    /** 查到了。 */
    Ok,

    /** 这家没有公开的余量查询接口。 */
    Unsupported,

    /** 这次没查成。 */
    Failed,
}

/** 余量的紧张程度。 */
enum class UIBalanceTone {
    Normal,
    Low,
    Empty,
    Unlimited,
}
