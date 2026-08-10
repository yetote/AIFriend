package com.feature.friend.route

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.feature.friend.vm.FriendViewModel
import com.ui.friend.UIFriendList

@Composable
fun UIFriendRoute(viewModel: FriendViewModel = viewModel()) {
    val friendList by viewModel.friendList.collectAsStateWithLifecycle()
    UIFriendList(friendList = friendList) {
        viewModel.createFriend("deepseek")
    }
}