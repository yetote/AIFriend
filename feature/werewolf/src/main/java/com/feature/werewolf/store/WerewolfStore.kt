package com.feature.werewolf.store

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.feature.werewolf.engine.WerewolfTable
import com.feature.werewolf.engine.persistence.WerewolfSnapshot

/**
 * 一局游戏的存档记录。
 *
 * 前面几列是**冗余的**，只为列表页和「有没有未完成的局」这类查询服务——
 * 让列表页不去解析每条完整快照。真正的权威数据在 [snapshot] 里。
 * 冗余列和快照不一致时以快照为准，写的时候一起写，不会分叉。
 */
@Entity(tableName = "werewolf_games")
data class WerewolfGameEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val updatedAt: Long,
    val finished: Boolean,
    val day: Int,
    val phase: String,
    val winner: String?,
    /** 完整快照，见 [WerewolfSnapshot]。 */
    val snapshot: String,
)

/** 列表页用的轻量投影，不含快照正文。 */
data class WerewolfGameSummary(
    val id: String,
    val createdAt: Long,
    val updatedAt: Long,
    val finished: Boolean,
    val day: Int,
    val phase: String,
    val winner: String?,
)

@Dao
interface WerewolfGameDao {

    @Query(
        "SELECT id, createdAt, updatedAt, finished, day, phase, winner " +
            "FROM werewolf_games ORDER BY updatedAt DESC"
    )
    suspend fun summaries(): List<WerewolfGameSummary>

    @Query("SELECT * FROM werewolf_games WHERE id = :id")
    suspend fun byId(id: String): WerewolfGameEntity?

    /** 最近一局还没分出胜负的，用于「继续上一局」。 */
    @Query("SELECT id FROM werewolf_games WHERE finished = 0 ORDER BY updatedAt DESC LIMIT 1")
    suspend fun latestUnfinishedId(): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WerewolfGameEntity)

    @Query("DELETE FROM werewolf_games WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM werewolf_games WHERE finished = 1")
    suspend fun deleteFinished()
}

/**
 * 狼人杀自己的库。
 *
 * 没有并进 `aifriend.db`，理由是两条：
 * 1. 加表就要写 Migration、还要回归验证聊天室没被弄坏，成本远大于收益；
 * 2. 狼人杀本来就要和聊天室解耦，存档是它自己的事，不该让 `core:repository` 知道。
 *
 * 代价是两份库文件，但它们是独立的，任何一边出问题都不会牵连另一个。
 */
@Database(entities = [WerewolfGameEntity::class], version = 1, exportSchema = false)
abstract class WerewolfDatabase : RoomDatabase() {

    abstract fun gameDao(): WerewolfGameDao

    companion object {
        private const val DB_NAME = "werewolf.db"

        @Volatile
        private var instance: WerewolfDatabase? = null

        fun get(context: Context): WerewolfDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    WerewolfDatabase::class.java,
                    DB_NAME,
                ).build().also { instance = it }
            }
    }
}

/**
 * 存档读写。
 *
 * 存的粒度是**整局快照**，不是增量。一局下来几十条消息、一二十个事件，
 * 整存整取简单得多，也不用担心「回放到一半规则改了导致局面分叉」。
 * 快照本身是 JSON，一局几十 KB，对手机存储没有压力。
 */
class WerewolfStore(
    private val dao: WerewolfGameDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    constructor(context: Context) : this(WerewolfDatabase.get(context).gameDao())

    /**
     * 保存（或覆盖）一局。
     *
     * [createdAt] 由调用方传，保证反复保存不会把开局时间刷新成最后一次行动的时间。
     */
    suspend fun save(id: String, table: WerewolfTable, createdAt: Long = clock()) {
        dao.upsert(
            WerewolfGameEntity(
                id = id,
                createdAt = createdAt,
                updatedAt = clock(),
                finished = table.game.isFinished,
                day = table.game.day,
                phase = table.game.phase.name,
                winner = table.game.winner?.name,
                snapshot = WerewolfSnapshot.encode(table),
            )
        )
    }

    /** 读一局。存档损坏或版本不匹配时返回 null，调用方当作没有存档处理。 */
    suspend fun load(id: String): WerewolfTable? =
        dao.byId(id)?.let { WerewolfSnapshot.decode(it.snapshot) }

    /** 最近一局没打完的。没有则返回 null。 */
    suspend fun latestUnfinished(): Pair<String, WerewolfTable>? {
        val id = dao.latestUnfinishedId() ?: return null
        val table = load(id) ?: return null
        return id to table
    }

    suspend fun summaries(): List<WerewolfGameSummary> = dao.summaries()

    suspend fun delete(id: String) = dao.delete(id)

    /** 清理已结束的局，保留未完成的。 */
    suspend fun deleteFinished() = dao.deleteFinished()
}
