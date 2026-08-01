package com.scaso.drclawapp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.scaso.drclawapp.data.local.dao.MessageDao
import com.scaso.drclawapp.data.local.dao.SessionDao
import com.scaso.drclawapp.data.local.dao.ToolEventDao
import com.scaso.drclawapp.data.local.entity.ChatSessionEntity
import com.scaso.drclawapp.data.local.entity.MessageEntity
import com.scaso.drclawapp.data.local.entity.MessageFtsEntity
import com.scaso.drclawapp.data.local.entity.ToolEventEntity

@Database(
    entities = [
        ChatSessionEntity::class,
        MessageEntity::class,
        MessageFtsEntity::class,
        ToolEventEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class DrClawDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun messageDao(): MessageDao
    abstract fun toolEventDao(): ToolEventDao

    companion object {
        /**
         * Migration 1 → 2: schema is byte-for-byte identical between these two exported
         * versions (see app/schemas/.../1.json and 2.json, same identityHash). No-op body,
         * but the Migration object must still exist so Room has an explicit path instead of
         * falling back to a destructive rebuild.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // No schema changes between v1 and v2.
            }
        }

        internal const val SQL_2_3_ADD_CONTEXT_PCT =
            "ALTER TABLE sessions ADD COLUMN contextPct INTEGER"
        internal const val SQL_2_3_ADD_CONTEXT_WINDOW =
            "ALTER TABLE sessions ADD COLUMN contextWindow INTEGER"

        /** Migration 2 → 3: adds nullable contextPct/contextWindow columns to sessions. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(SQL_2_3_ADD_CONTEXT_PCT)
                db.execSQL(SQL_2_3_ADD_CONTEXT_WINDOW)
            }
        }

        internal const val SQL_3_4_ADD_SYNC_STATE =
            "ALTER TABLE messages ADD COLUMN syncState TEXT NOT NULL DEFAULT 'SYNCED'"

        /**
         * Migration 3 → 4: adds NOT NULL syncState column to messages. Existing rows default
         * to SYNCED, matching MessageEntity's Kotlin-side default for pre-existing data.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(SQL_3_4_ADD_SYNC_STATE)
            }
        }

        /**
         * Phase 17: SQL strings for migration 4 → 5.
         * Exposed as internal constants so pure-JVM unit tests can assert their
         * structure without needing to invoke the Room migration at runtime.
         */
        internal val SQL_4_5_CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS tool_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                message_id TEXT NOT NULL,
                tool_call_id TEXT NOT NULL,
                tool_name TEXT NOT NULL,
                input_json TEXT NOT NULL,
                output_json TEXT,
                error TEXT,
                status TEXT NOT NULL,
                started_at INTEGER NOT NULL,
                duration_ms INTEGER,
                FOREIGN KEY(message_id) REFERENCES messages(id) ON DELETE CASCADE
            )
        """.trimIndent()

        internal const val SQL_4_5_INDEX_MESSAGE_ID =
            "CREATE INDEX IF NOT EXISTS index_tool_events_message_id ON tool_events(message_id)"

        internal const val SQL_4_5_UNIQUE_INDEX_TOOL_CALL_ID =
            "CREATE UNIQUE INDEX IF NOT EXISTS index_tool_events_tool_call_id ON tool_events(tool_call_id)"

        /**
         * Phase 17 migration: adds the tool_events table.
         * Additive only — no existing tables are dropped or altered.
         * MessageEntity.id is TEXT, so message_id is declared TEXT NOT NULL.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(SQL_4_5_CREATE_TABLE)
                db.execSQL(SQL_4_5_INDEX_MESSAGE_ID)
                db.execSQL(SQL_4_5_UNIQUE_INDEX_TOOL_CALL_ID)
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN mode TEXT NOT NULL DEFAULT 'Default'")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN injectedMemoryIds TEXT")
            }
        }
    }
}
