package com.freepakistancall.app.service

import com.freepakistancall.app.call.CallManager
import com.freepakistancall.app.data.Api
import com.freepakistancall.app.data.Prefs
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

class FcmService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        Prefs.init(this); Prefs.fcm = token
        if (Prefs.token != null) CoroutineScope(Dispatchers.IO).launch {
            runCatching { Api.req("PUT", "/me", JSONObject().put("fcmToken", token)) }
        }
    }

    // Push wakes the app; the actual SDP offer arrives over the signaling WebSocket.
    override fun onMessageReceived(m: RemoteMessage) {
        if (m.data["type"] != "incoming_call") return
        Prefs.init(this); CallManager.init(this)
        Notifs.incoming(this, m.data["fromName"].orEmpty().ifEmpty { m.data["fromPhone"].orEmpty() })
        android.os.Handler(mainLooper).post { CallManager.connect() }
    }
}
