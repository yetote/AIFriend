package com.core.ai.entity

import com.core.ai.model.ChatCompletionRequest
import com.core.ai.model.DoubaoModelResponse
import com.core.ai.model.GatewayModel
import com.core.ai.model.ThinkingConfig
import com.core.ai.model.toChatModelIds
import com.core.common.GlobalErrorHandler.handleCoreError
import com.core.repository.WebRepository

class AIDoubao : OfficialAI(
    brandName = "豆包",
    company = "字节跳动",
    providerId = "doubao"
) {
    /** 方舟上代理了别家模型，按牌子分组展示；自家豆包排在最前。 */
    override val gatewayModels: List<GatewayModel>
        get() = buildVendorGroups(modelSet.toList())

    override suspend fun loadModels() {
        val response = WebRepository.instance
            .getModel<DoubaoModelResponse>(config)
            .getOrElse {
                handleCoreError(it)
                useFallbackModels()
                return
            }
        val models = response.doubaoModelList.toChatModelIds()
        modelSet.clear()
        if (models.isEmpty()) {
            useFallbackModels()
        } else {
            modelSet.addAll(models)
        }
    }

    override fun ChatCompletionRequest.applyThinking(
        enableThinking: Boolean
    ): ChatCompletionRequest =
        if (enableThinking) this else copy(thinking = ThinkingConfig("disabled"))

    /**
     * 豆包开启联网只要一个**空对象** `web_search_options: {}`，
     * 搜不搜由模型自己判断，代码里没有「强制搜索」这一档。
     *
     * 不要顺手加 `tools`：方舟文档写明两者同时出现时以自定义工具为准，
     * 联网搜索会被静默关掉——看起来把开关都打开了，实际一次都没搜。
     */
    override val supportsWebSearch: Boolean get() = true
}
