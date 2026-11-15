package com.freepakistancall.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freepakistancall.app.call.CallManager
import com.freepakistancall.app.data.*
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URLEncoder

class AppVM : ViewModel() {
    var loggedIn by mutableStateOf(Prefs.token != null)
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var phone by mutableStateOf("")           // normalized E.164 awaiting OTP
    var myName by mutableStateOf(Prefs.name)
    val favorites = mutableStateListOf<Contact>()
    val results = mutableStateListOf<Contact>()
    val history = mutableStateListOf<CallLog>()

    init { S.lang = Prefs.lang; if (loggedIn) refresh() }

    private fun fail(e: Exception) {
        error = if (e is ApiException) when {
            e.code == 0 -> S.t("err_network")
            e.message == "invalid_code" -> S.t("err_code")
            e.message == "invalid_phone" -> S.t("err_phone")
            else -> S.t("err_generic")
        } else S.t("err_generic")
    }

    fun setLang(l: String) { Prefs.lang = l; S.lang = l }

    fun requestOtp(raw: String, onOk: () -> Unit) {
        val p = Pk.normalize(raw) ?: run { error = S.t("err_phone"); return }
        viewModelScope.launch {
            busy = true; error = null
            try { Api.req("POST", "/auth/request-otp", JSONObject().put("phone", p)); phone = p; onOk() }
            catch (e: Exception) { fail(e) }
            busy = false
        }
    }

    fun verify(code: String, name: String) = viewModelScope.launch {
        busy = true; error = null
        try {
            val r = Api.req("POST", "/auth/verify", JSONObject().put("phone", phone).put("code", code.trim()).put("name", name.trim()))
            val u = r.getJSONObject("user")
            Prefs.token = r.getString("token"); Prefs.name = u.getString("name"); Prefs.phone = u.getString("phone")
            myName = Prefs.name; loggedIn = true
            registerPush(); CallManager.connect(); refresh()
        } catch (e: Exception) { fail(e) }
        busy = false
    }

    fun registerPush() {
        try {
            if (FirebaseApp.getApps(com.freepakistancall.app.FpcApp.instance).isEmpty()) return
            FirebaseMessaging.getInstance().token.addOnSuccessListener { t ->
                Prefs.fcm = t
                viewModelScope.launch { runCatching { Api.req("PUT", "/me", JSONObject().put("fcmToken", t)) } }
            }
        } catch (_: Exception) {}
    }

    fun refresh() = viewModelScope.launch {
        try {
            val f = Api.req("GET", "/favorites").getJSONArray("items")
            favorites.clear(); for (i in 0 until f.length()) favorites.add(f.getJSONObject(i).toContact())
            val h = Api.req("GET", "/calls").getJSONArray("items")
            history.clear(); for (i in 0 until h.length()) history.add(h.getJSONObject(i).toLog())
        } catch (e: Exception) { if (e is ApiException && e.code == 401) logout() }
    }

    fun search(q: String) = viewModelScope.launch {
        if (q.filter { it.isDigit() }.length < 7) { results.clear(); return@launch }
        try {
            val a = Api.req("GET", "/users/search?q=" + URLEncoder.encode(q, "UTF-8")).getJSONArray("items")
            results.clear(); for (i in 0 until a.length()) results.add(a.getJSONObject(i).toContact())
        } catch (e: Exception) { fail(e) }
    }

    fun toggleFav(c: Contact) = viewModelScope.launch {
        try {
            if (c.fav) Api.req("DELETE", "/favorites/${c.id}") else Api.req("POST", "/favorites", JSONObject().put("id", c.id))
            val i = results.indexOfFirst { it.id == c.id }; if (i >= 0) results[i] = c.copy(fav = !c.fav)
            refresh()
        } catch (e: Exception) { fail(e) }
    }

    fun lookup(e164: String, cb: (Contact?) -> Unit) = viewModelScope.launch {
        try {
            val r = Api.req("GET", "/users/lookup?phone=" + URLEncoder.encode(e164, "UTF-8"))
            cb(if (r.isNull("user")) null else r.getJSONObject("user").toContact())
        } catch (e: Exception) { fail(e) }
    }

    fun rate(cb: (Rate?) -> Unit) = viewModelScope.launch {
        try {
            val r = Api.req("GET", "/pstn/rate")
            cb(Rate(r.optBoolean("enabled"), r.optDouble("perMinute"), r.optString("currency", "PKR")))
        } catch (e: Exception) { fail(e); cb(null) }
    }

    fun saveName(n: String) = viewModelScope.launch {
        try { Api.req("PUT", "/me", JSONObject().put("name", n)); Prefs.name = n; myName = n } catch (e: Exception) { fail(e) }
    }

    fun logout() {
        CallManager.disconnect(); Prefs.clearSession(); loggedIn = false
        favorites.clear(); history.clear(); results.clear()
    }
}
