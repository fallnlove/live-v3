package org.icpclive.background

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.icpclive.DataBus
import org.slf4j.LoggerFactory

class LoggerEventListener() {
    private val logger = LoggerFactory.getLogger(LoggerEventListener::class.java)
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun run() {
        DataBus.allEvents.collect {
            logger.info(Json.encodeToString(it))
        }
    }
}