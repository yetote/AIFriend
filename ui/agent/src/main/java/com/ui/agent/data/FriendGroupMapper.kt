package com.ui.agent.data

/**
 * 好友列表的一个分组：厂商名 + 该组下的好友（保持传入时的相对顺序）。
 */
data class UIFriendGroup(
    val name: String,
    val friends: List<UIFriendListData>
)

/** 厂商名缺失时的兜底分组，避免列表里冒出一个空标题。 */
private const val UNGROUPED_LABEL = "其他"

/**
 * 把好友按厂商归并成分组，用于首页列表按组标题分段展示。
 *
 * 分组依据是列表第三行的来源字段 [UIFriendListData.source]
 * （官方厂商是公司名、聚合平台是平台名），用户看到的和分组依据是同一个值，
 * 不会出现「显示 A、却归到 B 组」的错位。
 *
 * 排序规则：好友多的组在前（最常用的厂商先看到），同数量按厂商名排，保证顺序稳定；
 * 组内保持原列表顺序，不额外排序 —— 列表原本的顺序是用户熟悉的，分组不该打乱它。
 *
 * 厂商名为空的（旧数据 / 自定义渠道没填来源）统一归到「其他」，不单独占一个空标题。
 */
fun List<UIFriendListData>.groupByVendor(): List<UIFriendGroup> {
    if (isEmpty()) return emptyList()
    return groupBy { friend ->
        friend.source.trim().ifBlank { UNGROUPED_LABEL }
    }
    .map { (vendor, friends) -> UIFriendGroup(vendor, friends) }
    .sortedWith(
        compareByDescending<UIFriendGroup> { it.friends.size }
            .thenBy { it.name }
    )
}
