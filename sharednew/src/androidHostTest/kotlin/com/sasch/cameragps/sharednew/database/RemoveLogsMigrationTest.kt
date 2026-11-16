package com.sasch.cameragps.sharednew.database

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RemoveLogsMigrationTest {
    @Test
    fun dropsLogsAndPreservesCameraDevices() {
        BundledSQLiteDriver().open(":memory:").use { connection ->
            connection.execSQL("CREATE TABLE log_entries (id INTEGER NOT NULL PRIMARY KEY, message TEXT NOT NULL)")
            connection.execSQL("CREATE TABLE camera_devices (mac TEXT NOT NULL PRIMARY KEY)")
            connection.execSQL("INSERT INTO log_entries VALUES (1, 'old log')")
            connection.execSQL("INSERT INTO camera_devices VALUES ('camera')")

            LogDatabase_AutoMigration_7_8_Impl().migrate(connection)

            connection.prepare("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'log_entries'").use {
                assertFalse(it.step())
            }
            connection.prepare("SELECT mac FROM camera_devices").use {
                assertTrue(it.step())
                assertEquals("camera", it.getText(0))
            }
        }
    }
}
