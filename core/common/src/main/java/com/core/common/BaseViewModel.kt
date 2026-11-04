package com.core.common

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.core.network.getReadableMessage

open class BaseViewModel(
    application: Application
) : AndroidViewModel(application) {
    protected fun Throwable.handleError(onShowError: (String) -> Unit) {
        val message = getReadableMessage(this)
        onShowError(message)
    }
}
