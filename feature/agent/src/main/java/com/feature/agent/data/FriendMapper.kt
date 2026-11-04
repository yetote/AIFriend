package com.feature.agent.data

import com.core.ai.model.ChatMessage
import com.core.repository.storage.StoredFriend
import com.core.repository.storage.StoredMessage
import com.feature.agent.model.Friend

/**
 * 业务模型 <-> 持久化模型 的映射。
 * 分开是为了让存储结构和 UI 模型可以各自演进（比如以后加未读、置顶）。
 */
fun Friend.toStored(): StoredFriend = StoredFriend(
    id = id,
    name = name,
    role = role,
    prompt = prompt,
    brand = brand,
    company = company,
    model = model,
    thinkingEnabled = thinkingEnabled,
    avatarPath = avatarPath,
    messages = messages.map { StoredMessage(role = it.role, content = it.content) }
)

fun StoredFriend.toFriend(): Friend = Friend(
    id = id,
    name = name,
    role = role,
    prompt = prompt,
    brand = brand,
    company = company,
    model = model,
    thinkingEnabled = thinkingEnabled,
    avatarPath = avatarPath,
    messages = messages.map { ChatMessage(role = it.role, content = it.content) }
)
