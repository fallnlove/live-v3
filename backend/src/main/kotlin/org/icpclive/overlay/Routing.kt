package org.icpclive.overlay

import io.ktor.application.*
import io.ktor.http.cio.websocket.*
import io.ktor.routing.*
import io.ktor.websocket.*
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.icpclive.cds.OptimismLevel
import org.icpclive.data.DataBus

private suspend inline fun <reified T> DefaultWebSocketServerSession.sendFlow(flow: Flow<T>) {
    val sender = async {
        flow.collect {
            val text = Frame.Text(Json.encodeToString(it))
            outgoing.send(text)
        }
    }
    try {
        for (ignored in incoming) {
            ignored.let {}
        }
    } finally {
        sender.cancel()
    }
}

fun Application.configureOverlayRouting() {
    routing {
        route("/overlay") {
            webSocket("/mainScreen") { sendFlow(DataBus.mainScreenEvents()) }
            webSocket("/contestInfo") { sendFlow(DataBus.contestInfoUpdates) }
            webSocket("/queue") { sendFlow(DataBus.queueEvents()) }
            webSocket("/statistics") { sendFlow(DataBus.statisticEvents()) }
            webSocket("/ticker") { sendFlow(DataBus.tickerEvents()) }
            route("/scoreboard") {
                webSocket("/normal") { sendFlow(DataBus.scoreboardEvents(OptimismLevel.NORMAL)) }
                webSocket("/optimistic") { sendFlow(DataBus.scoreboardEvents(OptimismLevel.OPTIMISTIC)) }
                webSocket("/pessimistic") { sendFlow(DataBus.scoreboardEvents(OptimismLevel.PESSIMISTIC)) }
            }
        }
    }
}
