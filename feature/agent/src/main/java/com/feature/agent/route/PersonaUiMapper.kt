package com.feature.agent.route

import com.feature.agent.data.PersonaCatalog
import com.ui.agent.data.UIPersona

/**
 * 内置人格模板 → UI 层 DTO。
 *
 * `ui:agent` 不能反向依赖 `feature:agent`，所以模板从 feature 层映射过去，
 * 界面只认 [UIPersona]。
 */
internal val personaPresets: List<UIPersona> = PersonaCatalog.all.map { persona ->
    UIPersona(
        id = persona.id,
        name = persona.name,
        role = persona.role,
        prompt = persona.prompt,
        categoryKey = persona.category.name,
        categoryLabel = persona.category.label,
        tags = persona.tags
    )
}
