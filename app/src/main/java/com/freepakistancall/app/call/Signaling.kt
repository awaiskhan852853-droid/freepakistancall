package com.freepakistancall.app.call

import com.freepakistancall.app.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** WebSocket signaling client. Auth token is sent as a header (never in the URL). */
class Signaling(private val onMsg: (JSONObject) -> Unit, private val onState: (Boolean) -> Unit) {
    private val client = OkHttpClient.Builder().pingInterval(25, TimeUnit.SECONDS).build()
    private var ws: WebSocket? = null
    @Volatile var isOpen = false
        private set

    fun connect(token: String) {
        val url = BuildConfig.SERVER_URL.replaceFirst("http", "ws") + "/ws"
        ws = client.newWebSocket(Request.Builder().url(url).header("Authorization", "Bearer $token").build(),
            object : WebSocketListener() {
                override fun onOpen(w: WebSocket, r: Response) { isOpen = true; onState(true) }
                override fun onMessage(w: WebSocket, text: String) {
                    try { onMsg(JSONObject(text)) } catch (_: Exception) {}
                }
                override fun onClosed(w: WebSocket, code: Int, reason: String) { isOpen = false; if (code != 4000) onState(false) }
                override fun onFailure(w: WebSocket, t: Throwable, r: Response?) { isOpen = false; onState(false) }
            })
    }
    fun send(o: JSONObject): Boolean = ws?.send(o.toString()) ?: false
    fun close() { isOpen = false; ws?.close(1000, null); ws = null }
}
