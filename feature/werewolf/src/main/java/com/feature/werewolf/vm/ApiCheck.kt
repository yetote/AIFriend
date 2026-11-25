package com.feature.werewolf.vm

import com.core.ai.model.Friend
import com.core.ai.probe.toProbeTargets as coreToProbeTargets

// 探活状态与去重计划已上提到 core:ai（com.core.ai.probe），首页好友列表与狼人杀共用同一份缓存。
// 这里保留别名与薄封装，确保既有狼人杀代码与单测无需改动调用口径。

typealias ApiCheckState = com.core.ai.probe.ApiCheckState
typealias ApiCheck = com.core.ai.probe.ApiCheck
typealias ProbeTarget = com.core.ai.probe.ProbeTarget
typealias ProbeTask = com.core.ai.probe.ProbeTask

const val API_CHECK_TTL_MS = com.core.ai.probe.API_CHECK_TTL_MS

internal fun planProbes(
    targets: List<ProbeTarget>,
    known: Map<String, ApiCheck>,
    force: Boolean,
    now: Long,
    ttlMs: Long = API_CHECK_TTL_MS,
    webSearch: Boolean = false,
): List<ProbeTask> = com.core.ai.probe.planProbes(
    targets = targets,
    known = known,
    force = force,
    now = now,
    ttlMs = ttlMs,
    webSearch = webSearch,
)

internal fun List<Friend>.toProbeTargets(): List<ProbeTarget> = coreToProbeTargets()
