package com.core.common

import androidx.lifecycle.ViewModel
import com.core.network.getReadableMessage

open class BaseViewModel(
) : ViewModel() {
    protected fun Throwable.handleError(onShowError: (String) -> Unit) {
        val message = getReadableMessage(this)
        onShowError(message)
    }
}
