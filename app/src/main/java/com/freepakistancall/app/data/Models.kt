package com.freepakistancall.app.data

import org.json.JSONObject

data class Contact(val id: Long, val name: String, val phone: String, val fav: Boolean)
data class CallLog(val peerName: String, val peerPhone: String, val direction: String,
                   val type: String, val status: String, val duration: Int, val at: Long)
data class Rate(val enabled: Boolean, val perMinute: Double, val currency: String)

fun JSONObject.toContact() = Contact(getLong("id"), getString("name"), getString("phone"), optBoolean("fav"))
fun JSONObject.toLog() = CallLog(optString("peerName"), optString("peerPhone"), optString("direction"),
    optString("type"), optString("status"), optInt("duration"), optLong("at"))

object Pk {
    /** Returns +923XXXXXXXXX or null. Accepts 03xx..., 3xx..., 92..., +92..., 0092... */
    fun normalize(s: String): String? {
        var d = s.filter { it.isDigit() }
        if (d.startsWith("0092")) d = d.drop(2)
        d = when {
            d.startsWith("92") && d.length == 12 -> d.drop(2)
            d.startsWith("0") && d.length == 11 -> d.drop(1)
            else -> d
        }
        return if (Regex("^3\\d{9}$").matches(d)) "+92$d" else null
    }
    fun operator(e164: String): String? {
        val p = e164.removePrefix("+92").take(3).toIntOrNull() ?: return null
        return when (p) {
            in 300..309, in 320..329 -> "Jazz"
            in 310..319 -> "Zong"
            in 330..337 -> "Ufone"
            in 340..349 -> "Telenor"
            355 -> "SCOM"
            else -> null
        }
    }
    fun display(e164: String) = if (e164.startsWith("+92") && e164.length == 13) "0" + e164.drop(3).let { "${it.take(3)} ${it.drop(3)}" } else e164
}
