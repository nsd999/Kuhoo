package com.kuhoo.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        val appData = System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home")
        val dir = File(appData, "Kuhoo")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val dbFile = File(dir, "kuhoo.db")
        val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")
        if (!dbFile.exists()) {
            KuhooDatabase.Schema.create(driver)
        }
        return driver
    }
}
