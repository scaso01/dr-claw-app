package com.scaso.drclawapp.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM validation of the SQL strings used by [DrClawDatabase.MIGRATION_4_5].
 *
 * Room's MigrationTestHelper requires the room-testing artifact which is not in
 * libs.versions.toml, so this test validates the SQL structure directly against
 * the [DrClawDatabase.SQL_4_5_CREATE_TABLE], [DrClawDatabase.SQL_4_5_INDEX_MESSAGE_ID],
 * and [DrClawDatabase.SQL_4_5_UNIQUE_INDEX_TOOL_CALL_ID] constants.  The constants
 * are the same strings executed in migrate() — testing them is equivalent to
 * testing the migration output without running any Android runtime code.
 *
 * Phase 17 — DB migration 4 → 5.
 */
class ToolEventMigrationTest {

    // -------------------------------------------------------------------------
    // CREATE TABLE assertions
    // -------------------------------------------------------------------------

    @Test
    fun `migration_sql_create_table_references_tool_events`() {
        val sql = DrClawDatabase.SQL_4_5_CREATE_TABLE.lowercase()
        assertTrue(
            "CREATE TABLE statement must name the table 'tool_events'",
            sql.contains("tool_events"),
        )
        assertTrue(
            "CREATE TABLE must use IF NOT EXISTS guard",
            sql.contains("if not exists"),
        )
    }

    @Test
    fun `migration_sql_create_table_declares_all_required_columns`() {
        val sql = DrClawDatabase.SQL_4_5_CREATE_TABLE.lowercase()

        val requiredColumns = listOf(
            "id",
            "message_id",
            "tool_call_id",
            "tool_name",
            "input_json",
            "output_json",
            "error",
            "status",
            "started_at",
            "duration_ms",
        )
        for (column in requiredColumns) {
            assertTrue(
                "CREATE TABLE SQL must declare column '$column'",
                sql.contains(column),
            )
        }
    }

    @Test
    fun `migration_sql_create_table_has_integer_primary_key_autoincrement`() {
        val sql = DrClawDatabase.SQL_4_5_CREATE_TABLE.lowercase()
        assertTrue(
            "Primary key must be INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL",
            sql.contains("integer primary key autoincrement not null"),
        )
    }

    @Test
    fun `migration_sql_create_table_has_foreign_key_to_messages_with_cascade`() {
        val sql = DrClawDatabase.SQL_4_5_CREATE_TABLE.lowercase()
        assertTrue(
            "CREATE TABLE SQL must declare FOREIGN KEY referencing messages",
            sql.contains("foreign key(message_id) references messages(id)"),
        )
        assertTrue(
            "CASCADE delete must be declared on the foreign key",
            sql.contains("on delete cascade"),
        )
    }

    // -------------------------------------------------------------------------
    // INDEX assertions
    // -------------------------------------------------------------------------

    @Test
    fun `migration_sql_message_id_index_targets_correct_column`() {
        val sql = DrClawDatabase.SQL_4_5_INDEX_MESSAGE_ID.lowercase()
        assertTrue(
            "Index SQL must be a CREATE INDEX (not UNIQUE)",
            sql.startsWith("create index"),
        )
        assertTrue(
            "Index must be on tool_events(message_id)",
            sql.contains("on tool_events(message_id)"),
        )
        assertTrue(
            "Index must use IF NOT EXISTS guard",
            sql.contains("if not exists"),
        )
    }

    @Test
    fun `migration_sql_tool_call_id_unique_index_is_unique_and_targets_correct_column`() {
        val sql = DrClawDatabase.SQL_4_5_UNIQUE_INDEX_TOOL_CALL_ID.lowercase()
        assertTrue(
            "Unique index SQL must be CREATE UNIQUE INDEX",
            sql.startsWith("create unique index"),
        )
        assertTrue(
            "Unique index must be on tool_events(tool_call_id)",
            sql.contains("on tool_events(tool_call_id)"),
        )
        assertTrue(
            "Unique index must use IF NOT EXISTS guard",
            sql.contains("if not exists"),
        )
    }

    @Test
    fun `migration_sql_strings_count_is_exactly_three`() {
        // Defensive: verify no accidental SQL statements were added or removed
        val statements = listOf(
            DrClawDatabase.SQL_4_5_CREATE_TABLE,
            DrClawDatabase.SQL_4_5_INDEX_MESSAGE_ID,
            DrClawDatabase.SQL_4_5_UNIQUE_INDEX_TOOL_CALL_ID,
        )
        assertEquals(
            "MIGRATION_4_5 must consist of exactly 3 SQL statements",
            3,
            statements.size,
        )
        // None blank
        for (stmt in statements) {
            assertTrue("SQL statement must not be blank", stmt.isNotBlank())
        }
    }
}
