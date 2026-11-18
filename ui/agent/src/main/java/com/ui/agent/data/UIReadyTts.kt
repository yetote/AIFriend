package com.ui.agent.data

/**
 * 已经配好凭证、可以参与批量随机的语音服务。
 *
 * 只传「能用的」：没配 Key 的 TTS 一旦参与随机，建出来的角色朗读全报错，
 * 而用户建的时候看不出来——设置页配没配，创建页没必要再让用户记一遍。
 */
data class UIReadyTts(
    val id: String,
    val label: String
)
