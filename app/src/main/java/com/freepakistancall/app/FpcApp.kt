package com.freepakistancall.app

import android.app.Application
import com.freepakistancall.app.call.CallManager
import com.freepakistancall.app.data.Prefs
import com.freepakistancall.app.service.Notifs

class FpcApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        Prefs.init(this)
        Notifs.channels(this)
        CallManager.init(this)
        if (Prefs.token != null) CallManager.connect()
    }
    companion object { lateinit var instance: FpcApp }
}
