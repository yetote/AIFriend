package com.core.common

import com.core.network.getReadableMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object GlobalUiEventManager {
    private val _eventFlow = MutableSharedFlow<GlobalUiEvent>(
        extraBufferCapacity = 64
    )
    val eventFlow = _eventFlow.asSharedFlow()

    fun sendEventSync(event: GlobalUiEvent) {
        _eventFlow.tryEmit(event)
    }
}

sealed interface GlobalUiEvent {
    data class ShowToast(val message: String) : GlobalUiEvent
}

object GlobalErrorHandler {
    fun handleCoreError(throwable: Throwable) {
        val message = getReadableMessage(throwable)

        // 纯同步调用，零协程开销，无泄露风险
        GlobalUiEventManager.sendEventSync(GlobalUiEvent.ShowToast(message))
    }
}