package com.feature.friend

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.core.common.GlobalUiEvent
import com.core.common.GlobalUiEventManager
import com.core.common.ext.showToast
import com.feature.friend.ui.theme.AIFriendTheme
import com.feature.friend.vm.FriendViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AIFriendTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                GlobalUiEventManager.eventFlow.collect { event ->
                    when (event) {
                        is GlobalUiEvent.ShowToast -> showToast(event.message)
                    }
                }
            }
        }
    }
}

@Composable
fun Greeting(
    viewModel: FriendViewModel = viewModel(),
    name: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = "Hello $name!",
        modifier = modifier.clickable(true) {
            viewModel.createFriend("deepseek")
        })
}