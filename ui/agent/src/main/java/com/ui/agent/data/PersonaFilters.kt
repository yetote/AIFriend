package com.ui.agent.data

import com.core.persona.Persona
import com.core.persona.PersonaCategory

/**
 * 模板数据统一放在 `core:persona`，ui 层直接复用 [Persona]，不再维护平行 DTO。
 * 这里只放选择面板需要的两个列表扩展：作用于调用方传进来的子集，
 * 而不是全局目录，方便以后接「最近使用」「收藏」之类的局部列表。
 */

/** 分类 + 关键词过滤，`category` 为 null 表示不限分类。 */
fun List<Persona>.filterPersonas(category: PersonaCategory?, keyword: String): List<Persona> =
    filter { persona ->
        (category == null || persona.category == category) && persona.matches(keyword)
    }

/** 分类去重后按出现顺序返回，用于渲染筛选条。 */
fun List<Persona>.personaCategories(): List<PersonaCategory> =
    map { it.category }.distinct()
