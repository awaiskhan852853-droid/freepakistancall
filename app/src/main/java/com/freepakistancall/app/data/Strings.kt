package com.freepakistancall.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Urdu / English strings. `lang` is Compose state so the UI recomposes on change. */
object S {
    var lang by mutableStateOf("ur")
    private val m = mapOf(
        "app_name" to ("فری پاکستان کال" to "Free Pakistan Call"),
        "tagline" to ("پاکستان بھر میں آسان کالنگ" to "Simple calling across Pakistan"),
        "phone" to ("موبائل نمبر" to "Mobile number"),
        "send_code" to ("کوڈ بھیجیں" to "Send code"),
        "otp_title" to ("تصدیقی کوڈ" to "Verification code"),
        "otp_sent" to ("کوڈ آپ کے نمبر پر بھیج دیا گیا ہے" to "We sent a code to your number"),
        "code" to ("6 ہندسوں کا کوڈ" to "6-digit code"),
        "your_name" to ("آپ کا نام" to "Your name"),
        "verify" to ("تصدیق کریں" to "Verify"),
        "recents" to ("حالیہ" to "Recents"),
        "contacts" to ("رابطے" to "Contacts"),
        "dialpad" to ("ڈائل پیڈ" to "Dial pad"),
        "settings" to ("ترتیبات" to "Settings"),
        "search_hint" to ("فون نمبر سے تلاش کریں" to "Search by phone number"),
        "search_help" to ("رجسٹرڈ صارف تلاش کرنے کے لیے کم از کم 7 ہندسے لکھیں" to "Type at least 7 digits to find registered users"),
        "favorites" to ("پسندیدہ" to "Favorites"),
        "no_results" to ("کوئی نتیجہ نہیں" to "No results"),
        "cancel" to ("منسوخ" to "Cancel"),
        "call" to ("کال کریں" to "Call"),
        "paid_title" to ("ادائیگی والی کال" to "Paid call"),
        "paid_msg" to ("یہ نمبر ایپ پر رجسٹرڈ نہیں۔ عام موبائل نمبر پر کال کے چارجز لاگو ہوں گے۔" to "This number isn't on the app. Standard mobile-call charges apply."),
        "per_min" to ("فی منٹ" to "per minute"),
        "calling" to ("کال ہو رہی ہے…" to "Calling…"),
        "incoming" to ("آنے والی کال" to "Incoming call"),
        "connecting" to ("جڑ رہا ہے…" to "Connecting…"),
        "reconnecting" to ("دوبارہ جڑ رہا ہے…" to "Reconnecting…"),
        "ended" to ("کال ختم ہو گئی" to "Call ended"),
        "mute" to ("میوٹ" to "Mute"),
        "speaker" to ("اسپیکر" to "Speaker"),
        "accept" to ("قبول کریں" to "Accept"),
        "decline" to ("مسترد کریں" to "Decline"),
        "language" to ("زبان" to "Language"),
        "logout" to ("لاگ آؤٹ" to "Log out"),
        "save" to ("محفوظ کریں" to "Save"),
        "profile" to ("پروفائل" to "Profile"),
        "free_badge" to ("ایپ کال — مفت" to "App call — free"),
        "pstn_badge" to ("موبائل نیٹ ورک کال" to "Mobile network call"),
        "err_network" to ("انٹرنیٹ کنکشن میں مسئلہ ہے" to "Network problem. Check your connection."),
        "err_generic" to ("کچھ غلط ہو گیا" to "Something went wrong"),
        "err_phone" to ("درست پاکستانی موبائل نمبر درج کریں" to "Enter a valid Pakistani mobile number"),
        "err_code" to ("کوڈ درست نہیں" to "Invalid code"),
        "err_mic" to ("کال کے لیے مائیکروفون کی اجازت ضروری ہے" to "Microphone permission is required for calls"),
        "err_pstn" to ("عام نمبروں پر کال فی الحال دستیاب نہیں" to "Calling regular numbers is not available right now"),
        "history_empty" to ("ابھی کوئی کال نہیں" to "No calls yet"),
        "busy" to ("نمبر مصروف ہے" to "User is busy"),
        "no_answer" to ("جواب نہیں ملا" to "No answer"),
        "rejected" to ("کال مسترد کر دی گئی" to "Call declined"),
        "limit" to ("آج کی کال حد پوری ہو گئی" to "Daily call limit reached"),
        "unavailable" to ("صارف دستیاب نہیں" to "User unavailable"),
        "missed" to ("مس کال" to "Missed"),
        "privacy" to ("آپ کا نمبر صرف رجسٹرڈ صارفین کو نظر آتا ہے۔ کالز کی آواز سرور پر ریکارڈ نہیں ہوتی۔" to "Your number is visible only to registered users. Call audio is not recorded."),
    )
    fun t(k: String): String = m[k]?.let { if (lang == "ur") it.first else it.second } ?: k
    fun reason(r: String?): String = when (r) {
        "network" -> t("err_network"); "busy" -> t("busy"); "no_answer" -> t("no_answer")
        "rejected" -> t("rejected"); "limit" -> t("limit"); "unavailable" -> t("unavailable")
        "error" -> t("err_generic"); else -> t("ended")
    }
}
