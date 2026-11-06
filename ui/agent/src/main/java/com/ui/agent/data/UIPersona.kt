package com.ui.agent.data

/**
 * 人格模板的 UI 视图模型。
 *
 * 放在 ui 层是为了让 ui:agent 不用反向依赖 feature:agent；
 * 真正的模板内容在 `PersonaCatalog`，由 AgentRoute 映射过来。
 */
data class UIPersona(
    val id: String,
    val name: String,
    val role: String,
    val prompt: String,
    val categoryKey: String,
    val categoryLabel: String,
    val tags: List<String> = emptyList()
) {
    /** 关键词命中昵称、角色或标签。 */
    fun matches(keyword: String): Boolean {
        val key = keyword.trim().lowercase()
        if (key.isEmpty()) return true
        return name.lowercase().contains(key) ||
            role.lowercase().contains(key) ||
            tags.any { it.lowercase().contains(key) }
    }
}

/** 按分类过滤，`categoryKey` 为 null 表示全部；再叠加关键词。 */
fun List<UIPersona>.filterPersonas(categoryKey: String?, keyword: String): List<UIPersona> =
    filter { persona ->
        (categoryKey == null || persona.categoryKey == categoryKey) && persona.matches(keyword)
    }

/** 分类去重后按出现顺序返回，用于渲染筛选条。 */
fun List<UIPersona>.personaCategories(): List<Pair<String, String>> =
    map { it.categoryKey to it.categoryLabel }.distinct()
