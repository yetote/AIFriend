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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.feature.werewolf.engine.WerewolfTable
import com.feature.werewolf.engine.persistence.WerewolfSnapshot
import com.feature.werewolf.game.SELF_PLAYER_ID

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
    /** 几人局。列表上一眼区分「6 人快局」和「12 人守卫局」。 */
    val playerCount: Int,
    /** 这一局我有没有坐进去。false 表示纯观战。 */
    val withHuman: Boolean,
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
    /** 0 表示旧档升级上来的行，列表页不显示人数而不是显示「0 人」。 */
    val playerCount: Int = 0,
    val withHuman: Boolean = false,
)

@Dao
interface WerewolfGameDao {

    @Query(
        "SELECT id, createdAt, updatedAt, finished, day, phase, winner, playerCount, withHuman " +
            "FROM werewolf_games ORDER BY updatedAt DESC"
    )
    suspend fun summaries(): List<WerewolfGameSummary>

    @Query("SELECT * FROM werewolf_games WHERE id = :id")
    suspend fun byId(id: String): WerewolfGameEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WerewolfGameEntity)

    @Query("DELETE FROM werewolf_games WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM werewolf_games WHERE finished = 1")
    suspend fun deleteFinished()
}

/**
 * 存档读写的抽象。
 *
 * 抽出来是为了让对局控制器不直接依赖 Room：控制器要测「开局落档、每阶段落档、
 * 出错时局面不丢」这些行为，用真库就得跑仪器测试，太慢也太重。
 */
interface WerewolfGameStore {

    /**
     * 保存（或覆盖）一局。[createdAt] 传开局时间，不要用当前时间，否则列表排序会乱。
     *
     * [customPrompt] 是这一局生效的玩家设定，写进快照只给复盘看——
     * 续档不会用它覆盖设置页，见 [WerewolfSnapshot.decodePrompt]。
     */
    suspend fun save(
        id: String,
        table: WerewolfTable,
        createdAt: Long,
        customPrompt: String? = null,
    )

    suspend fun load(id: String): WerewolfTable?

    /** 这一局用的玩家设定。旧档（v10 及以前）或没写设定时是 null。 */
    suspend fun customPrompt(id: String): String?

    suspend fun summaries(): List<WerewolfGameSummary>

    suspend fun delete(id: String)
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
@Database(entities = [WerewolfGameEntity::class], version = 2, exportSchema = false)
abstract class WerewolfDatabase : RoomDatabase() {

    abstract fun gameDao(): WerewolfGameDao

    companion object {
        private const val DB_NAME = "werewolf.db"

        /**
         * v1 → v2：列表页要显示「几人局 / 我有没有上桌」。
         *
         * 旧行补 0 和 false 而不是去回解快照：那张表可能有几百行，
         * 为了两列展示字段全量反序列化一遍太贵；0 在界面上表示「不知道」，
         * 列表页据此不画人数，也不影响「接着打」——续档读的始终是快照。
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE werewolf_games ADD COLUMN playerCount INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE werewolf_games ADD COLUMN withHuman INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        @Volatile
        private var instance: WerewolfDatabase? = null

        fun get(context: Context): WerewolfDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    WerewolfDatabase::class.java,
                    DB_NAME,
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
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
) : WerewolfGameStore {

    constructor(context: Context) : this(WerewolfDatabase.get(context).gameDao())

    /**
     * 保存（或覆盖）一局。
     *
     * [createdAt] 由调用方传，保证反复保存不会把开局时间刷新成最后一次行动的时间。
     */
    override suspend fun save(
        id: String,
        table: WerewolfTable,
        createdAt: Long,
        customPrompt: String?,
    ) {
        dao.upsert(
            WerewolfGameEntity(
                id = id,
                createdAt = createdAt,
                updatedAt = clock(),
                finished = table.game.isFinished,
                day = table.game.day,
                phase = table.game.phase.name,
                winner = table.game.winner?.name,
                playerCount = table.game.players.size,
                withHuman = table.game.players.any { it.id == SELF_PLAYER_ID },
                snapshot = WerewolfSnapshot.encode(table, customPrompt),
            )
        )
    }

    /** 读一局。存档损坏或版本不匹配时返回 null，调用方当作没有存档处理。 */
    override suspend fun load(id: String): WerewolfTable? =
        dao.byId(id)?.let { WerewolfSnapshot.decode(it.snapshot) }

    override suspend fun customPrompt(id: String): String? =
        dao.byId(id)?.snapshot?.let { WerewolfSnapshot.decodePrompt(it) }

    override suspend fun summaries(): List<WerewolfGameSummary> = dao.summaries()

    override suspend fun delete(id: String) = dao.delete(id)

    /** 清理已结束的局，保留未完成的。 */
    suspend fun deleteFinished() = dao.deleteFinished()
}
