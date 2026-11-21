package com.feature.werewolf.vm

/**
 * 从列表点「接着打」时，判断该不该直接接回那张还在后台跑的牌桌。
 *
 * 返回 true = 这一局从没停过，引擎还在跑，直接把界面贴回去即可——不重读档、
 * 不重建座位、不发任何新请求。返回 false = 走常规续档流程。
 *
 * 抽成纯函数是为了把这套判定钉住：漏判一次「还在跑」就会让 AI 正在想的那一步
 * 被打断重来，用户看到的正是「思考进度被重置」。
 *
 * @param detached 人是否在列表页（局在后台）
 * @param running 引擎的 playJob 是否还活着
 * @param currentGameId 引擎手上这局的 id
 * @param requestedGameId 用户点的那局 id
 * @param finished 这局是否已经打完
 */
fun shouldAttachDetachedGame(
    detached: Boolean,
    running: Boolean,
    currentGameId: String?,
    requestedGameId: String,
    finished: Boolean,
): Boolean = detached && running && !finished &&
    currentGameId != null && currentGameId == requestedGameId
