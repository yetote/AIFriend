package com.feature.agent.data

import com.core.repository.balance.BalanceResult
import com.core.repository.balance.BalanceTone
import com.ui.agent.data.UIBalanceState
import com.ui.agent.data.UIBalanceStatus
import com.ui.agent.data.UIBalanceTone

/**
 * 把 core 的余额结果翻成 UI 层要的那份。
 *
 * 三种结果分得清清楚楚：「查到了」「这家没这个接口」「这次没查成」。
 * 它们的处置方式完全不同，混在一起的话，用户会去刷新十次，
 * 然后发现根本刷新不出来——问题根本不在这边。
 */
fun BalanceResult.toUI(providerName: String): UIBalanceState = when (this) {
    is BalanceResult.Ok -> UIBalanceState(
        providerName = providerName,
        status = UIBalanceStatus.Ok,
        headline = snapshot.headline,
        details = snapshot.details,
        tone = snapshot.tone.toUI(),
        fetchedAt = snapshot.fetchedAt,
    )

    is BalanceResult.Unsupported -> UIBalanceState(
        providerName = providerName,
        status = UIBalanceStatus.Unsupported,
        message = reason,
    )

    is BalanceResult.Failed -> UIBalanceState(
        providerName = providerName,
        status = UIBalanceStatus.Failed,
        message = reason,
    )
}

private fun BalanceTone.toUI(): UIBalanceTone = when (this) {
    BalanceTone.Normal -> UIBalanceTone.Normal
    BalanceTone.Low -> UIBalanceTone.Low
    BalanceTone.Empty -> UIBalanceTone.Empty
    BalanceTone.Unlimited -> UIBalanceTone.Unlimited
}
