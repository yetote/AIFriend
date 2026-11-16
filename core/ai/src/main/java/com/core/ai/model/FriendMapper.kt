package com.core.ai.model

import com.core.repository.storage.StoredFriend
import com.core.repository.storage.StoredMessage

/**
 * 业务模型 <-> 持久化模型 的映射。
 * 分开是为了让存储结构和 UI 模型可以各自演进（比如以后加未读、置顶）。
 *
 * 跟着 [Friend] 一起放在 core:ai：换算的两端是 Friend 与 StoredFriend，
 * 谁定义 Friend 谁就该负责它和存储之间的转换，避免 feature 之间互相借 mapper。
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
