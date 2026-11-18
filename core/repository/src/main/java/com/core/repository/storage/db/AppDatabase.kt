package com.core.repository.storage.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        FriendEntity::class,
        FriendMessageEntity::class,
        RoomEntity::class,
        RoomMemberEntity::class,
        RoomMessageEntity::class,
        ProfileEntity::class
    ],
    version = 10,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun friendDao(): FriendDao
    abstract fun roomDao(): RoomDao
    abstract fun profileDao(): ProfileDao

    companion object {
        private const val DB_NAME = "aifriend.db"

        /** v2：聊天室增加房间级设定 prompt。 */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE rooms ADD COLUMN prompt TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v3：讨论模式（窗口互见 + 顺序发言）。 */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE rooms ADD COLUMN discussionEnabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE rooms ADD COLUMN discussionWindow INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE rooms ADD COLUMN sequentialSpeaking INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v4：房间置顶。 */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE rooms ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v5：房间头像。 */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE rooms ADD COLUMN avatarPath TEXT")
            }
        }

        /** v6：用户消息 @ 的成员（房间内定向对话）。 */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE room_messages ADD COLUMN targetIds TEXT")
            }
        }

        /** v7：自由对话（用户发言后成员继续互聊）。 */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE rooms ADD COLUMN autoChatEnabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE rooms ADD COLUMN autoChatTurns INTEGER NOT NULL DEFAULT 3")
            }
        }

        /** v8：好友的朗读音色。可空列，老角色读出 null，引擎走默认音色。 */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE friends ADD COLUMN voiceId TEXT")
            }
        }

        /** v9：好友的朗读 TTS 配置。可空列，老角色读出 null，用默认配置。 */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE friends ADD COLUMN ttsConfigId TEXT")
            }
        }

        /**
         * v10：AI 回复的思考过程。两张消息表都加一个可空列。
         *
         * 只加列不搬数据，旧消息读出来是 null，界面就不显示思考区块——
         * 这和「这条消息当时确实没思考」在展示上是一回事，不需要区分。
         */
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE friend_messages ADD COLUMN reasoning TEXT")
                db.execSQL("ALTER TABLE room_messages ADD COLUMN reasoning TEXT")
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                        MIGRATION_9_10
                    )
                    .build()
                    .also { instance = it }
            }
    }
}
