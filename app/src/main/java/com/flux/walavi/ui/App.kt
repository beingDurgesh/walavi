package com.flux.walavi.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flux.walavi.data.Account
import com.flux.walavi.ui.components.Label
import com.flux.walavi.ui.components.PrimaryButton
import com.flux.walavi.ui.components.SquareIconButton
import com.flux.walavi.ui.components.StatusPill
import com.flux.walavi.ui.screens.AccountsScreen
import com.flux.walavi.ui.screens.AddAccountSheet
import com.flux.walavi.ui.screens.DashboardScreen
import com.flux.walavi.ui.screens.LogsScreen
import com.flux.walavi.ui.screens.ModelsScreen
import com.flux.walavi.ui.screens.SettingsScreen
import com.flux.walavi.ui.theme.Mono
import com.flux.walavi.ui.theme.WalaviColors
import com.flux.walavi.util.Qr

enum class Tab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Default.Home),
    Accounts("Accounts", Icons.Default.Person),
    Models("Models", Icons.Default.Star),
    Logs("Activity", Icons.Default.List),
}

@Composable
fun WalaviRoot(vm: AppViewModel) {
    val context = LocalContext.current
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val logs by vm.logs.collectAsStateWithLifecycle()
    val usage by vm.usage.collectAsStateWithLifecycle()
    val gateway by vm.gateway.collectAsStateWithLifecycle()
    val login by vm.login.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val refreshing by vm.refreshingModels.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var showQr by rememberSaveable { mutableStateOf(false) }
    var reloginId by remember { mutableStateOf<String?>(null) }

    val snackbar = remember { SnackbarHostState() }
    val clipboard = remember {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    fun copy(text: String) {
        clipboard.setPrimaryClip(ClipData.newPlainText("Walavi", text))
        vm.notifyCopied()
    }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    fun startGateway() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        vm.startGateway(context)
    }

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }
    LaunchedEffect(Unit) {
        vm.refreshEndpoint()
        vm.autoStartIfNeeded(context)
    }

    var openedAuthUrl by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(login) {
        val waiting = login as? LoginState.Waiting
        if (waiting != null && waiting.authUrl != openedAuthUrl) {
            openedAuthUrl = waiting.authUrl
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(waiting.authUrl))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.onFailure { vm.notify("No browser is available for sign-in") }
        }
        if (login !is LoginState.Waiting) openedAuthUrl = null
    }

    val url = gateway.lanUrl.ifBlank { "http://device-ip:${gateway.port}/v1" }

    Scaffold(
        containerColor = WalaviColors.Bg,
        topBar = {
            if (!showSettings) {
                WalaviTopBar(gateway.running) { showSettings = true }
            }
        },
        bottomBar = {
            if (!showSettings) {
                WalaviBottomBar(tab) { tab = it }
            }
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbar) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = WalaviColors.Panel,
                    contentColor = WalaviColors.Text,
                    actionColor = WalaviColors.Text,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.padding(12.dp),
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                showSettings -> SettingsScreen(
                    settings = settings,
                    state = gateway,
                    accountCount = accounts.size,
                    onBack = { showSettings = false },
                    onSavePort = vm::savePort,
                    onRefreshEndpoint = vm::refreshEndpoint,
                    onSaveBaseUrl = vm::saveBaseUrl,
                    onRequireKey = vm::setRequireKey,
                    onRegenerateKey = vm::regenerateKey,
                    onCopy = ::copy,
                    onAutoStart = vm::setAutoStart,
                    onKeepAwake = vm::setKeepAwake,
                    onStartOnBoot = vm::setStartOnBoot,
                    onNotify = vm::notify,
                )
                tab == Tab.Home -> DashboardScreen(
                    state = gateway,
                    accounts = accounts,
                    logs = logs,
                    usage = usage,
                    knownModels = settings.knownModels,
                    url = url,
                    onStart = ::startGateway,
                    onStop = { vm.stopGateway(context) },
                    onCopy = ::copy,
                    onQr = { showQr = true },
                    onAccounts = { tab = Tab.Accounts },
                    onLogs = { tab = Tab.Logs },
                )
                tab == Tab.Accounts -> AccountsScreen(
                    accounts = accounts,
                    busy = busy,
                    onAdd = {
                        reloginId = null
                        vm.resetLogin()
                        showAdd = true
                    },
                    onToggle = vm::toggleAccount,
                    onTest = vm::testAccount,
                    onRelogin = { account: Account ->
                        reloginId = account.id
                        vm.startBrowserLogin(account.id)
                        showAdd = true
                    },
                    onDelete = vm::deleteAccount,
                    onMove = vm::moveAccount,
                )
                tab == Tab.Models -> ModelsScreen(
                    settings = settings,
                    refreshing = refreshing,
                    onSetDefault = vm::setDefaultModel,
                    onForceModel = vm::setForceModel,
                    onExposeAll = vm::setExposeAll,
                    onToggleSelected = vm::toggleSelectedModel,
                    onRefresh = vm::refreshModels,
                )
                tab == Tab.Logs -> LogsScreen(
                    logs = logs,
                    usage = usage,
                    knownModels = settings.knownModels,
                    onClear = {
                        vm.clearLogs()
                        vm.notify("Activity cleared")
                    },
                )
            }
        }
    }

    if (showAdd) {
        AddAccountSheet(
            login = login,
            onDismiss = {
                showAdd = false
                reloginId = null
                vm.resetLogin()
            },
            onBrowserLogin = { vm.startBrowserLogin(reloginId) },
            onCancelLogin = { vm.cancelLogin() },
            onAddToken = { label, token, refresh ->
                vm.addAccountFromToken(label, token, refresh)
                showAdd = false
                reloginId = null
                vm.resetLogin()
            },
        )
    }

    if (showQr) {
        QrDialog(url, settings.apiKey) { showQr = false }
    }
}

@Composable
private fun WalaviTopBar(running: Boolean, onSettings: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(WalaviColors.Bg).statusBarsPadding()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                ">_",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = Mono,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = WalaviColors.Text,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "WALAVI",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontFamily = Mono,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = WalaviColors.Text,
            )
            Spacer(Modifier.weight(1f))
            StatusPill(
                if (running) "Live" else "Idle",
                if (running) WalaviColors.Green else WalaviColors.Muted,
            )
            Spacer(Modifier.width(10.dp))
            SquareIconButton(Icons.Default.Settings, "Settings") { onSettings() }
        }
        HorizontalDivider(color = WalaviColors.Border, thickness = 1.dp)
    }
}

@Composable
private fun WalaviBottomBar(selected: Tab, onSelect: (Tab) -> Unit) {
    Column(Modifier.fillMaxWidth().background(WalaviColors.Sidebar).navigationBarsPadding()) {
        HorizontalDivider(color = WalaviColors.Border, thickness = 1.dp)
        Row(Modifier.fillMaxWidth().height(62.dp), verticalAlignment = Alignment.CenterVertically) {
            for (item in Tab.entries) {
                val active = item == selected
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelect(item) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier
                            .height(2.dp)
                            .width(18.dp)
                            .background(if (active) WalaviColors.Text else Color.Transparent),
                    )
                    Spacer(Modifier.height(7.dp))
                    Icon(
                        item.icon,
                        contentDescription = item.label,
                        tint = if (active) WalaviColors.Text else WalaviColors.Muted,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        item.label,
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.3.sp),
                        color = if (active) WalaviColors.Text else WalaviColors.Muted,
                    )
                }
            }
        }
    }
}

@Composable
private fun QrDialog(url: String, apiKey: String, onDismiss: () -> Unit) {
    val bitmap = remember(url) { Qr.bitmap(url) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = WalaviColors.Panel,
            border = BorderStroke(1.dp, WalaviColors.Border),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Label("SCAN TO CONNECT")
                Spacer(Modifier.height(14.dp))
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Gateway connection QR code",
                    modifier = Modifier.size(230.dp).clip(RoundedCornerShape(10.dp)),
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    url,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = Mono, fontSize = 11.sp),
                    color = WalaviColors.Muted,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    apiKey,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = Mono,
                        fontSize = 10.sp,
                        letterSpacing = 0.sp,
                    ),
                    color = WalaviColors.Text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                PrimaryButton("Done", onDismiss, Modifier.fillMaxWidth())
            }
        }
    }
}
