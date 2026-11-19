package com.ui.werewolf.data

/**
 * 一条**不是错误**的提示（导入成功、导入后另存了一份……）。
 *
 * [error] 为 true 时表示这其实是失败原因。没有拆成两个类型，因为界面只有一条横条：
 * 一个字段决定用红字还是蓝字，比两个可空参数少一种「同时有两个提示」的非法状态。
 */
data class UIWerewolfNotice(val text: String, val error: Boolean = false)
