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
    version = 3,
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

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }
    }
}
