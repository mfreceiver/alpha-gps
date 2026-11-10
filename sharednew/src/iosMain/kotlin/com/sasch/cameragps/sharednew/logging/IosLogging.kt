package com.sasch.cameragps.sharednew.logging

import com.diamondedge.logging.KmLogging
import com.diamondedge.logging.LogLevel
import com.diamondedge.logging.VariableLogLevel
import com.sasch.cameragps.sharednew.database.logging.DatabaseLogger
import com.sasch.cameragps.sharednew.database.logging.LogRepository

/**
 * Installs the iOS logger set.
 *
 * `KmLogging.setLoggers` **replaces** the whole logger list, so every
 * reconfiguration (launch, log-level change) goes through here.
 */
internal object IosLogging {

    /** Logs to the in-app database at [level]; the level is the user's choice. */
    fun install(logRepository: LogRepository, level: LogLevel) {
        val database = DatabaseLogger(logRepository, VariableLogLevel(level))
        KmLogging.setLoggers(database)
    }
}
