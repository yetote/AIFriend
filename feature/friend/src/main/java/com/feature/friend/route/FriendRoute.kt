package com.feature.friend.route

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.feature.friend.vm.FriendViewModel
import com.ui.friend.UIFriendList

@Composable
fun FriendRoute(viewModel: FriendViewModel = viewModel()) {

    val friendList by viewModel.friendList.collectAsStateWithLifecycle()
    val aiList by viewModel.aiList.collectAsStateWithLifecycle()
    val modelList by viewModel.aiModelList.collectAsStateWithLifecycle()

    UIFriendList(aiList = aiList, friendList = friendList, aiModelList = modelList, onBrandListSelected = { brand ->
        viewModel.requestModel(brand)
    }) { data ->
        viewModel.createFriend(data)
    }
}