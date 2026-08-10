package com.yetote.aifriend

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.core.common.GlobalUiEvent
import com.core.common.GlobalUiEventManager

@Composable
fun GlobalUiEventObserver() {
    val context = LocalContext.current.applicationContext

    LaunchedEffect(Unit) {
        GlobalUiEventManager.eventFlow.collect { event ->
            when (event) {
                is GlobalUiEvent.ShowToast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
                else -> {
                }
            }
        }
    }
}