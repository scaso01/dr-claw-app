package com.scaso.drclawapp.data.local

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM validation of the SQL strings used by [DrClawDatabase.MIGRATION_2_3] and
 * [DrClawDatabase.MIGRATION_3_4]. Same rationale as [ToolEventMigrationTest]: no
 * room-testing artifact available, so the SQL constants are asserted directly.
 *
 * MIGRATION_1_2 has no SQL (schemas are identical) and is covered by
 * [DrClawDatabase.MIGRATION_1_2] simply existing and being registered in AppModule.
 */
class EarlyMigrationsTest {

    @Test
    fun `migration_2_3_adds_contextPct_column_to_sessions`() {
        val sql = DrClawDatabase.SQL_2_3_ADD_CONTEXT_PCT.lowercase()
        assertTrue(sql.contains("alter table sessions"))
        assertTrue(sql.contains("add column contextpct integer"))
    }

    @Test
    fun `migration_2_3_adds_contextWindow_column_to_sessions`() {
        val sql = DrClawDatabase.SQL_2_3_ADD_CONTEXT_WINDOW.lowercase()
        assertTrue(sql.contains("alter table sessions"))
        assertTrue(sql.contains("add column contextwindow integer"))
    }

    @Test
    fun `migration_3_4_adds_syncState_column_to_messages_with_synced_default`() {
        val sql = DrClawDatabase.SQL_3_4_ADD_SYNC_STATE.lowercase()
        assertTrue(sql.contains("alter table messages"))
        assertTrue(sql.contains("add column syncstate text not null default 'synced'"))
    }
}
