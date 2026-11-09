package com.core.repository.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 编辑资料丢聊天记录的回归测试。
 *
 * 根因：friend_messages 对 friends 声明了 ON DELETE CASCADE，
 * 而 FriendDao 原来用 @Insert(onConflict = REPLACE) 保存好友。
 * SQLite 的 REPLACE 语义是「先 DELETE 再 INSERT」，
 * 那条 DELETE 会顺着外键把该好友的消息全部级联删掉 ——
 * 于是「改个名字」等于「把聊天记录清空」。
 *
 * 这里没有 Robolectric，跑不了真实 Room，所以退一步锁住两件能静态验证的事：
 * 1. DAO 里不能再出现针对 friends 的 REPLACE 插入；
 * 2. 更新资料必须是显式 UPDATE，且不碰 friend_messages。
 */
class FriendUpsertKeepsMessagesTest {

    private val daosSource: String by lazy {
        // 单测工作目录通常是模块根，两种相对路径都试一下
        val candidates = listOf(
            "src/main/java/com/core/repository/storage/db/Daos.kt",
            "core/repository/src/main/java/com/core/repository/storage/db/Daos.kt"
        )
        candidates.map { java.io.File(it) }.firstOrNull { it.exists() }?.readText()
            ?: error("找不到 Daos.kt，工作目录=" + java.io.File(".").absolutePath)
    }

    @Test
    fun `friends 表不使用 REPLACE 插入`() {
        val friendDao = daosSource.substringAfter("interface FriendDao")
            .substringBefore("@Dao")
        assertTrue(
            "FriendDao 又出现了 REPLACE 插入，会级联删掉好友的消息:\n$friendDao",
            !friendDao.contains("OnConflictStrategy.REPLACE")
        )
    }

    @Test
    fun `更新资料只 UPDATE friends 表`() {
        val friendDao = daosSource.substringAfter("interface FriendDao")
            .substringBefore("@Dao")
        assertTrue(
            "更新好友资料应该是显式 UPDATE",
            friendDao.contains("UPDATE friends SET")
        )
        assertTrue(
            "更新资料不应该再去写 friend_messages",
            !friendDao.contains("INSERT INTO friend_messages")
        )
    }

    @Test
    fun `新建好友仍然可以插入`() {
        val friendDao = daosSource.substringAfter("interface FriendDao")
            .substringBefore("@Dao")
        assertTrue(
            "首次创建好友需要插入路径",
            friendDao.contains("insertFriend")
        )
    }

    @Test
    fun `消息表对好友声明了级联删除`() {
        val entities = listOf(
            "src/main/java/com/core/repository/storage/db/Entities.kt",
            "core/repository/src/main/java/com/core/repository/storage/db/Entities.kt"
        ).map { java.io.File(it) }.firstOrNull { it.exists() }?.readText()
            ?: error("找不到 Entities.kt")

        val messageEntity = entities.substringAfter("data class FriendMessageEntity")
            .substringBefore("data class RoomEntity")
        assertTrue(
            "FriendMessageEntity 必须保持 CASCADE —— 这是删除好友时清理消息所必需的",
            entities.contains("onDelete = ForeignKey.CASCADE")
        )
        assertEquals(
            "级联存在与否直接决定 REPLACE 是否会清空聊天记录，别随手改",
            true,
            entities.contains("entity = FriendEntity::class")
        )
    }
}
