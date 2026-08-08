package com.core.common

import androidx.lifecycle.ViewModel
import com.core.network.GlobalErrorHandler

open class BaseViewModel(
) : ViewModel() {
    protected fun Throwable.handleError(onShowError: (String) -> Unit) {
        val message = GlobalErrorHandler.getReadableMessage(this)
        onShowError(message)
    }
}
