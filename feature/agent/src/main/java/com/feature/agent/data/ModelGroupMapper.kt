package com.feature.agent.data

import com.core.ai.model.GatewayModel
import com.core.ai.model.ModelPrice
import com.core.ai.model.PriceTier
import com.ui.agent.data.UIModelGroup
import com.ui.agent.data.ALL_VENDOR_LABEL
import com.ui.agent.data.UIModelOption
import com.ui.agent.data.UIPriceTone

/**
 * 把带价格的模型折成 UI 用的分组结构。
 *
 * 价格文本在这里就格式化好，UI 只负责摆位置 —— 币种（人民币 / 美元）
 * 和数据来源相关，属于业务逻辑，不该散落在 Composable 里。
 *
 * 排序规则整体是「按经济性从便宜到贵」：厂商组之间比各自最低的成本指数，
 * 组内比单个模型的成本指数。没有价格的排在最后 —— 不确定的东西
 * 不该占据列表最显眼的位置。
 *
 * 返回的第一项是跨厂商的「全部」，后面才是各厂商分组；
 * 厂商多起来之后横向翻牌子太慢，有一个按价格摊平的入口更实用。
 */
fun List<GatewayModel>.toUIModelGroups(currency: String): List<UIModelGroup> = this
    .groupBy { it.vendorLabel }
    .map { (vendor, models) ->
        val sorted = models.sortedWith(MODEL_ORDER)
        GroupEntry(
            group = UIModelGroup(
                vendor = vendor,
                models = sorted.map { model ->
                    UIModelOption(
                        id = model.id,
                        // 组标题已经是厂商名了，行内去掉前缀，省出位置给价格
                        label = model.displayName,
                        price = model.price?.format(currency),
                        tone = model.price?.tier(currency)?.toUITone(),
                        bands = model.price?.bandLabels(currency).orEmpty(),
                        sortWeight = model.price?.costIndex,
                        vendorName = vendor
                    )
                },
                from = sorted.fromLabel(currency)
            ),
            cheapest = sorted.cheapestCostIndex()
        )
    }
    .sortedWith(GROUP_ORDER)
    .map { it.group }
    .toExpandedGroups()

/** 分组排序的中间载体：组本身 + 该组最低价。 */
private class GroupEntry(group: UIModelGroup, cheapest: Double?) {
    val group: UIModelGroup = group
    val cheapest: Double = cheapest ?: Double.MAX_VALUE
}

/**
 * 在厂商分组前面加一个跨厂商的「全部」。
 *
 * 用户找模型有两种路径：认牌子（打开 Qwen 看有哪些），
 * 和只认价钱（哪个最便宜）。后者用横向标签一个个翻厂商太慢，
 * 所以给一个把 68 个模型摊平的入口，按价格排序一眼看到底。
 *
 * 只有一家厂商时没有汇总的意义，直接原样返回。
 */
private fun List<UIModelGroup>.toExpandedGroups(): List<UIModelGroup> {
    if (size < 2) return this
    val all = flatMap { group -> group.models.map { it.copy(vendorName = it.vendorName ?: group.vendor) } }
        .sortedWith(ALL_MODEL_ORDER)
    // 进来之前已经按「最低成本」排过序，所以第一组就是最便宜的那家；
    // 直接沿用它算好的起价，不用再从文本里抠数字（美元 / 人民币都适用）。
    return listOf(UIModelGroup(vendor = ALL_VENDOR_LABEL, models = all, from = first().from)) + this
}

/** 「全部」里按价格从便宜到贵，没价格的沉到最底。 */
private val ALL_MODEL_ORDER: Comparator<UIModelOption> = compareBy(
    { it.sortWeight == null },
    { it.sortWeight ?: Double.MAX_VALUE },
    { it.id.lowercase() }
)

/** 厂商组排序：便宜的在前，同价按厂商名保证顺序稳定。 */
private val GROUP_ORDER: Comparator<GroupEntry> = Comparator { a, b ->
    val byPrice = a.cheapest.compareTo(b.cheapest)
    if (byPrice != 0) byPrice else a.group.vendor.compareTo(b.group.vendor, ignoreCase = true)
}

/** 模型排序：有价格的在前，便宜的在前，同价按名字保证稳定。 */
private val MODEL_ORDER: Comparator<GatewayModel> = compareBy(
    { it.price == null },
    { it.price?.costIndex ?: Double.MAX_VALUE },
    { it.id.lowercase() }
)

/** 该组里最低的成本指数，用来把便宜的厂商排前面。 */
private fun List<GatewayModel>.cheapestCostIndex(): Double? =
    mapNotNull { it.price }.minOfOrNull { it.costIndex }

/**
 * 组标题上的「起价」，只返回数字部分（`¥2`），「起」由 UI 拼。
 *
 * 取该组最便宜模型的输入价当门槛 —— 用户判断一家贵不贵，
 * 先看的是「最便宜能多少钱用上」。
 */
private fun List<GatewayModel>.fromLabel(currency: String): String? {
    val prices = mapNotNull { it.price }
    if (prices.isEmpty()) return null
    if (prices.all { it.isFree }) return "免费"
    val cheapest = prices.filterNot { it.isFree }.minByOrNull { it.costIndex } ?: return "免费"
    return cheapest.inputOnly(currency)
}

/** 只要输入价那一半，例如 `¥2`。 */
private fun ModelPrice.inputOnly(currency: String): String =
    format(currency).substringBefore(" 入")

/** 业务档位转成纯展示档位。 */
fun PriceTier.toUITone(): UIPriceTone = when (this) {
    PriceTier.CHEAP -> UIPriceTone.CHEAP
    PriceTier.NORMAL -> UIPriceTone.NORMAL
    PriceTier.EXPENSIVE -> UIPriceTone.EXPENSIVE
}
