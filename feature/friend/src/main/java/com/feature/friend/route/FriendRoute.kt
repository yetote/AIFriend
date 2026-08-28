package com.feature.friend.route

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.feature.friend.vm.FriendViewModel
import com.ui.friend.UIFriendPage

@Composable
fun FriendRoute(viewModel: FriendViewModel = viewModel()) {

    val friendList by viewModel.friendList.collectAsStateWithLifecycle()
    val aiList by viewModel.aiBrandList.collectAsStateWithLifecycle()
    val modelList by viewModel.aiModelList.collectAsStateWithLifecycle()
    UIFriendPage(friendList)
}