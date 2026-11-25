package com.ui.agent.data

import androidx.compose.ui.graphics.Color

/** 首页好友列表展示用的模型连通性探活状态。 */
enum class UIFriendProbeState {
    /** 还没检测过（或修改 Key / 更换模型后已失效）。 */
    IDLE,

    /** 正在探活中。 */
    CHECKING,

    /** 已连通，可正常对话或开局。 */
    OK,

    /** 探活失败（Key 异常、模型不可用、超时等）。 */
    FAILED,
}

data class UIFriendListData(
    val name: String,
    val modelTag: String,
    val role: String,
    val source: String,
    val usageCount: String,
    val avatarBgColor: Color,
    val avatarTextColor: Color,
    val isOnline: Boolean = true,
    val id: String = "",
    val avatarPath: String? = null,
    val probeState: UIFriendProbeState = UIFriendProbeState.IDLE,
    /** 已连通时的响应耗时文本（如 `480ms`、`1.3s`）。 */
    val probeLatency: String? = null,
    /** 探活失败时的可读原因。 */
    val probeError: String? = null,
)
