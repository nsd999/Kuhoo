package com.kuhoo.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.worker.WebWorkerDriver
import org.w3c.dom.Worker

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        val worker = Worker("sqlite.worker.js")
        return WebWorkerDriver(worker)
    }
}
