package com.freepakistancall.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.freepakistancall.app.call.CallManager
import com.freepakistancall.app.call.CallPhase
import com.freepakistancall.app.call.CallUi
import com.freepakistancall.app.data.*
import kotlinx.coroutines.delay

enum class Route { Splash, Login, Otp, Main, Language }

@Composable
fun AppRoot(vm: AppVM) {
    val call by CallManager.state.collectAsState()
    CompositionLocalProvider(LocalLayoutDirection provides if (S.lang == "ur") LayoutDirection.Rtl else LayoutDirection.Ltr) {
        FpcTheme {
            Surface(Modifier.fillMaxSize(), color = Color.White) {
                var route by remember { mutableStateOf(Route.Splash) }
                var langBack by remember { mutableStateOf(Route.Login) }
                LaunchedEffect(Unit) { delay(1200); route = if (vm.loggedIn) Route.Main else Route.Login }
                LaunchedEffect(vm.loggedIn) { if (route != Route.Splash) route = if (vm.loggedIn) Route.Main else Route.Login }
                when (route) {
                    Route.Splash -> Splash()
                    Route.Login -> LoginScreen(vm, { route = Route.Otp }, { langBack = Route.Login; route = Route.Language })
                    Route.Otp -> { BackHandler { route = Route.Login }; OtpScreen(vm) }
                    Route.Main -> MainScreen(vm) { langBack = Route.Main; route = Route.Language }
                    Route.Language -> { BackHandler { route = langBack }; LanguageScreen(vm) { route = langBack } }
                }
                if (call.phase != CallPhase.Idle) CallScreen(call)
            }
        }
    }
}

@Composable fun Splash() = Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Green, GreenDark))), Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(96.dp).clip(CircleShape).background(Color.White), Alignment.Center) {
            Icon(Icons.Filled.Call, null, tint = Green, modifier = Modifier.size(52.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(S.t("app_name"), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(S.t("tagline"), color = Color.White.copy(alpha = .85f), fontSize = 15.sp)
    }
}

@Composable
fun LoginScreen(vm: AppVM, onSent: () -> Unit, onLang: () -> Unit) {
    var phone by remember { mutableStateOf("") }
    var showServer by remember { mutableStateOf(false) }
    var serverText by remember { mutableStateOf(Prefs.serverUrl) }
    if (showServer) AlertDialog(onDismissRequest = { showServer = false },
        title = { Text(S.t("server")) },
        text = { OutlinedTextField(serverText, { serverText = it }, singleLine = true, placeholder = { Text("https://...") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)) },
        confirmButton = { TextButton({ Prefs.serverUrl = serverText; vm.error = null; showServer = false }) { Text(S.t("save")) } },
        dismissButton = { TextButton({ showServer = false }) { Text(S.t("cancel")) } })
    Column(Modifier.fillMaxSize().padding(24.dp).statusBarsPadding(), verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(Green), Alignment.Center) { Icon(Icons.Filled.Call, null, tint = Color.White, modifier = Modifier.size(38.dp)) }
        Spacer(Modifier.height(20.dp))
        Text(S.t("app_name"), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Green)
        Text(S.t("tagline"), color = Color.Gray)
        Spacer(Modifier.height(32.dp))
        OutlinedTextField(phone, { phone = it }, Modifier.fillMaxWidth(), label = { Text(S.t("phone")) },
            placeholder = { Text("03XX XXXXXXX") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), leadingIcon = { Text("🇵🇰 +92") })
        vm.error?.let { Text(it, color = Red, modifier = Modifier.padding(top = 8.dp)) }
        Spacer(Modifier.height(16.dp))
        BigButton(S.t("send_code"), vm.busy) { vm.requestOtp(phone, onSent) }
        TextButton(onLang, Modifier.align(Alignment.CenterHorizontally)) { Icon(Icons.Filled.Language, null); Spacer(Modifier.width(6.dp)); Text("اردو / English") }
        TextButton({ serverText = Prefs.serverUrl; showServer = true }, Modifier.align(Alignment.CenterHorizontally)) { Icon(Icons.Filled.Settings, null); Spacer(Modifier.width(6.dp)); Text(S.t("server")) }
        Text(S.t("privacy"), fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun OtpScreen(vm: AppVM) {
    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf(Prefs.name) }
    Column(Modifier.fillMaxSize().padding(24.dp).statusBarsPadding(), verticalArrangement = Arrangement.Center) {
        Text(S.t("otp_title"), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Green)
        Text(S.t("otp_sent") + "\n" + Pk.display(vm.phone), color = Color.Gray)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(code, { if (it.length <= 8) code = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text(S.t("code")) },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(name, { name = it.take(40) }, Modifier.fillMaxWidth(), label = { Text(S.t("your_name")) }, singleLine = true)
        vm.error?.let { Text(it, color = Red, modifier = Modifier.padding(top = 8.dp)) }
        Spacer(Modifier.height(16.dp))
        BigButton(S.t("verify"), vm.busy) { vm.verify(code, name) }
    }
}

@Composable
fun BigButton(text: String, busy: Boolean = false, onClick: () -> Unit) =
    Button(onClick, Modifier.fillMaxWidth().height(54.dp), enabled = !busy, shape = RoundedCornerShape(16.dp)) {
        if (busy) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
        else Text(text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }

/** Returns a function that runs an action only after RECORD_AUDIO is granted. */
@Composable
fun rememberMicGuard(): (() -> Unit) -> Unit {
    val ctx = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) pending?.invoke() else Toast.makeText(ctx, S.t("err_mic"), Toast.LENGTH_LONG).show()
        pending = null
    }
    return { action ->
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) action()
        else { pending = action; launcher.launch(Manifest.permission.RECORD_AUDIO) }
    }
}

@Composable
fun MainScreen(vm: AppVM, onLang: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    val ctx = LocalContext.current
    val guard = rememberMicGuard()
    var pstnTarget by remember { mutableStateOf<Pair<String, Rate?>?>(null) }

    fun callApp(c: Contact) = guard { CallManager.startCall(c.id, c.name, c.phone) }
    fun callNumber(raw: String) {
        val e164 = Pk.normalize(raw) ?: run { Toast.makeText(ctx, S.t("err_phone"), Toast.LENGTH_SHORT).show(); return }
        vm.lookup(e164) { u ->
            if (u != null) callApp(u) else vm.rate { r -> if (r != null) pstnTarget = e164 to r }
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                listOf(Icons.Filled.History to "recents", Icons.Filled.People to "contacts", Icons.Filled.Dialpad to "dialpad", Icons.Filled.Settings to "settings")
                    .forEachIndexed { i, (ic, k) ->
                        NavigationBarItem(tab == i, { tab = i; if (i == 0) vm.refresh() }, { Icon(ic, null) }, label = { Text(S.t(k), fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = Green, selectedTextColor = Green, indicatorColor = GreenLight))
                    }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> HistoryTab(vm) { callNumber(it) }
                1 -> ContactsTab(vm, ::callApp)
                2 -> DialTab(::callNumber)
                else -> SettingsTab(vm, onLang)
            }
        }
    }

    pstnTarget?.let { (num, rate) ->
        AlertDialog(onDismissRequest = { pstnTarget = null },
            title = { Text(S.t("paid_title")) },
            text = {
                Column {
                    Text(Pk.display(num) + (Pk.operator(num)?.let { "  ·  $it" } ?: ""), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    if (rate?.enabled == true) {
                        Text(S.t("paid_msg"))
                        Spacer(Modifier.height(8.dp))
                        Text("${rate.currency} ${"%.2f".format(rate.perMinute)} ${S.t("per_min")}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Green)
                    } else Text(S.t("err_pstn"))
                }
            },
            confirmButton = {
                if (rate?.enabled == true) TextButton({ pstnTarget = null; guard { CallManager.startPstn(num, "") } }) { Text(S.t("call")) }
            },
            dismissButton = { TextButton({ pstnTarget = null }) { Text(S.t("cancel")) } })
    }
}

@Composable
fun Header(title: String) = Box(Modifier.fillMaxWidth().background(Green).statusBarsPadding().padding(20.dp)) {
    Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
}

@Composable
fun Avatar(name: String, size: Int = 44) = Box(Modifier.size(size.dp).clip(CircleShape).background(GreenLight), Alignment.Center) {
    Text(name.trim().take(1).uppercase().ifEmpty { "#" }, color = Green, fontWeight = FontWeight.Bold, fontSize = (size / 2.4).sp)
}

@Composable
fun HistoryTab(vm: AppVM, onRedial: (String) -> Unit) = Column {
    Header(S.t("recents"))
    if (vm.history.isEmpty()) Box(Modifier.fillMaxSize(), Alignment.Center) { Text(S.t("history_empty"), color = Color.Gray) }
    else LazyColumn {
        items(vm.history) { h ->
            val missed = h.status == "missed"
            val ic = if (h.direction == "incoming") (if (missed) Icons.Filled.CallMissed else Icons.Filled.CallReceived) else Icons.Filled.CallMade
            Row(Modifier.fillMaxWidth().clickable { onRedial(h.peerPhone) }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(h.peerName.ifEmpty { h.peerPhone })
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(h.peerName.ifEmpty { Pk.display(h.peerPhone) }, fontWeight = FontWeight.SemiBold, color = if (missed) Red else Color.Unspecified)
                    Text((if (h.type == "pstn") S.t("pstn_badge") else S.t("free_badge")) + "  ·  " +
                        DateUtils.getRelativeTimeSpanString(h.at, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS), fontSize = 12.sp, color = Color.Gray)
                }
                if (h.duration > 0) Text(fmt(h.duration), color = Color.Gray, fontSize = 13.sp)
                Spacer(Modifier.width(8.dp))
                Icon(ic, null, tint = if (missed) Red else Green)
            }
            HorizontalDivider(color = Color(0xFFEFEFEF))
        }
    }
}

@Composable
fun ContactsTab(vm: AppVM, onCall: (Contact) -> Unit) {
    var q by remember { mutableStateOf("") }
    Column {
        Header(S.t("contacts"))
        OutlinedTextField(q, { q = it; vm.search(it) }, Modifier.fillMaxWidth().padding(16.dp), placeholder = { Text(S.t("search_hint")) },
            leadingIcon = { Icon(Icons.Filled.Search, null) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
        val showing = if (q.filter { it.isDigit() }.length >= 7) vm.results else vm.favorites
        Text(if (showing === vm.results) "" else S.t("favorites"), Modifier.padding(horizontal = 16.dp), color = Green, fontWeight = FontWeight.Bold)
        if (showing.isEmpty()) Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text(if (showing === vm.results) S.t("no_results") else S.t("search_help"), color = Color.Gray, textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp))
        } else LazyColumn {
            items(showing) { c ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(c.name); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) { Text(c.name, fontWeight = FontWeight.SemiBold); Text(Pk.display(c.phone), color = Color.Gray, fontSize = 13.sp) }
                    IconButton({ vm.toggleFav(c) }) { Icon(if (c.fav) Icons.Filled.Star else Icons.Filled.StarBorder, null, tint = Color(0xFFF5B301)) }
                    IconButton({ onCall(c) }, colors = IconButtonDefaults.iconButtonColors(containerColor = Green)) { Icon(Icons.Filled.Call, null, tint = Color.White) }
                }
            }
        }
    }
}

@Composable
fun DialTab(onCall: (String) -> Unit) {
    var num by remember { mutableStateOf("") }
    val e164 = Pk.normalize(num)
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Header(S.t("dialpad"))
        Spacer(Modifier.weight(1f))
        Text(num.ifEmpty { " " }, fontSize = 34.sp, fontWeight = FontWeight.Medium, maxLines = 1, modifier = Modifier.padding(horizontal = 16.dp))
        Text(e164?.let { Pk.operator(it) ?: "" } ?: "", color = Green, fontWeight = FontWeight.SemiBold, modifier = Modifier.height(24.dp))
        Spacer(Modifier.height(8.dp))
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#")
        keys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                row.forEach { k ->
                    Box(Modifier.size(70.dp).clip(CircleShape).background(GreenLight).clickable { if (num.length < 16) num += k }, Alignment.Center) {
                        Text(k, fontSize = 28.sp, color = GreenDark)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Spacer(Modifier.size(56.dp))
            Box(Modifier.size(80.dp).clip(CircleShape).background(Green).clickable { onCall(num) }, Alignment.Center) {
                Icon(Icons.Filled.Call, S.t("call"), tint = Color.White, modifier = Modifier.size(38.dp))
            }
            IconButton({ num = num.dropLast(1) }, Modifier.size(56.dp)) { Icon(Icons.Filled.Backspace, null, tint = Color.Gray) }
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
fun SettingsTab(vm: AppVM, onLang: () -> Unit) {
    var name by remember { mutableStateOf(vm.myName) }
    Column(Modifier.fillMaxSize()) {
        Header(S.t("settings"))
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(S.t("profile"), color = Green, fontWeight = FontWeight.Bold)
            Text(Pk.display(Prefs.phone), color = Color.Gray)
            OutlinedTextField(name, { name = it.take(40) }, Modifier.fillMaxWidth(), label = { Text(S.t("your_name")) }, singleLine = true)
            OutlinedButton({ vm.saveName(name) }, Modifier.fillMaxWidth()) { Text(S.t("save")) }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth().clickable(onClick = onLang).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Language, null, tint = Green); Spacer(Modifier.width(12.dp))
                Text(S.t("language"), Modifier.weight(1f)); Text(if (S.lang == "ur") "اردو" else "English", color = Color.Gray)
            }
            Text(S.t("privacy"), fontSize = 12.sp, color = Color.Gray)
            OutlinedButton({ vm.logout() }, Modifier.fillMaxWidth(), border = BorderStroke(1.dp, Red)) { Text(S.t("logout"), color = Red) }
            vm.error?.let { Text(it, color = Red) }
        }
    }
}

@Composable
fun LanguageScreen(vm: AppVM, onDone: () -> Unit) = Column(Modifier.fillMaxSize()) {
    Header(S.t("language"))
    listOf("ur" to "اردو", "en" to "English").forEach { (code, label) ->
        Row(Modifier.fillMaxWidth().clickable { vm.setLang(code); onDone() }.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 20.sp, modifier = Modifier.weight(1f))
            if (S.lang == code) Icon(Icons.Filled.Check, null, tint = Green)
        }
        HorizontalDivider()
    }
}

fun fmt(sec: Int) = "%02d:%02d".format(sec / 60, sec % 60)

/** Outgoing, incoming, connecting and active call states in one full-screen UI. */
@Composable
fun CallScreen(c: CallUi) {
    val guard = rememberMicGuard()
    val status = when (c.phase) {
        CallPhase.Outgoing -> S.t("calling")
        CallPhase.Incoming -> S.t("incoming")
        CallPhase.Connecting -> S.t("connecting")
        CallPhase.Active -> if (c.reconnecting) S.t("reconnecting") else fmt(c.durationSec)
        CallPhase.Ended -> S.reason(c.endReason)
        else -> ""
    }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Green, GreenDark))).systemBarsPadding()) {
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(48.dp))
            Text(if (c.isPstn) S.t("pstn_badge") else S.t("free_badge"), color = Color.White.copy(alpha = .8f), fontSize = 13.sp)
            Spacer(Modifier.height(24.dp))
            Avatar(c.peerName.ifEmpty { c.peerPhone }, 112)
            Spacer(Modifier.height(20.dp))
            Text(c.peerName.ifEmpty { Pk.display(c.peerPhone) }, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            if (c.peerName.isNotEmpty()) Text(Pk.display(c.peerPhone), color = Color.White.copy(alpha = .8f))
            Spacer(Modifier.height(8.dp))
            Text(status, color = Color.White, fontSize = 20.sp)
            Spacer(Modifier.weight(1f))
            when (c.phase) {
                CallPhase.Incoming -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    RoundBtn(Icons.Filled.CallEnd, Red, S.t("decline")) { CallManager.reject() }
                    RoundBtn(Icons.Filled.Call, Color(0xFF2ECC71), S.t("accept")) { guard { CallManager.accept() } }
                }
                CallPhase.Ended -> {}
                else -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    RoundBtn(if (c.muted) Icons.Filled.MicOff else Icons.Filled.Mic, if (c.muted) Color.White else Color.White.copy(alpha = .2f), S.t("mute"), dark = c.muted) { CallManager.toggleMute() }
                    RoundBtn(Icons.Filled.CallEnd, Red, "", big = true) { CallManager.hangup() }
                    RoundBtn(Icons.Filled.VolumeUp, if (c.speaker) Color.White else Color.White.copy(alpha = .2f), S.t("speaker"), dark = c.speaker) { CallManager.toggleSpeaker() }
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
fun RoundBtn(icon: ImageVector, bg: Color, label: String, big: Boolean = false, dark: Boolean = false, onClick: () -> Unit) =
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(if (big) 76.dp else 64.dp).clip(CircleShape).background(bg).clickable(onClick = onClick), Alignment.Center) {
            Icon(icon, label, tint = if (dark) Green else Color.White, modifier = Modifier.size(32.dp))
        }
        if (label.isNotEmpty()) Text(label, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
    }
