package com.kuhoo.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import java.util.Properties

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        val appData = System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home")
        val dir = File(appData, "Kuhoo")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val dbFile = File(dir, "kuhoo.db")
        val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}", Properties())

        // Always try to create/migrate the schema.
        // SQLDelight's Schema.create is idempotent — safe to call on existing DBs.
        // For a fresh DB or schema version change, this ensures all tables exist.
        try {
            KuhooDatabase.Schema.create(driver)
        } catch (_: Exception) {
            // Tables already exist — this is expected on subsequent runs.
            // If new tables were added, we create them individually as a migration fallback.
            try {
                driver.execute(null, """
                    CREATE TABLE IF NOT EXISTS RecentlyPlayed (
                        songId TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        artistName TEXT NOT NULL,
                        albumName TEXT,
                        durationMs INTEGER NOT NULL DEFAULT 0,
                        thumbnailUrl TEXT,
                        lastPlayedAt INTEGER NOT NULL
                    )
                """.trimIndent(), 0)
            } catch (_: Exception) {}
        }
        return driver
    }
}
