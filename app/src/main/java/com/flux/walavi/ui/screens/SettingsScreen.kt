package com.flux.walavi.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flux.walavi.BuildConfig
import com.flux.walavi.data.WalaviSettings
import com.flux.walavi.gateway.GatewayState
import com.flux.walavi.ui.components.GhostButton
import com.flux.walavi.ui.components.CopyIcon
import com.flux.walavi.ui.components.HorizontalRule
import com.flux.walavi.ui.components.Label
import com.flux.walavi.ui.components.Panel
import com.flux.walavi.ui.components.SquareIconButton
import com.flux.walavi.ui.components.SwitchRow
import com.flux.walavi.ui.components.walaviFieldColors
import com.flux.walavi.ui.theme.Mono
import com.flux.walavi.ui.theme.WalaviColors

@Composable
fun SettingsScreen(
    settings: WalaviSettings,
    state: GatewayState,
    accountCount: Int,
    onBack: () -> Unit,
    onSavePort: (Int) -> Unit,
    onRefreshEndpoint: () -> Unit,
    onSaveBaseUrl: (String) -> Unit,
    onRequireKey: (Boolean) -> Unit,
    onRegenerateKey: () -> Unit,
    onCopy: (String) -> Unit,
    onAutoStart: (Boolean) -> Unit,
    onKeepAwake: (Boolean) -> Unit,
    onStartOnBoot: (Boolean) -> Unit,
    onNotify: (String) -> Unit,
) {
    val context = LocalContext.current
    var portText by rememberSaveable(settings.port) { mutableStateOf(settings.port.toString()) }
    var baseText by rememberSaveable(settings.baseUrl) { mutableStateOf(settings.baseUrl) }
    var revealKey by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, top = 10.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SquareIconButton(Icons.Default.ArrowBack, "Back") { onBack() }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Settings", style = MaterialTheme.typography.headlineSmall, color = WalaviColors.Text)
                Text(
                    "Gateway and account behaviour",
                    style = MaterialTheme.typography.bodySmall,
                    color = WalaviColors.Muted,
                )
            }
        }

        Panel {
            Label("SERVER")
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it.filter { ch -> ch.isDigit() }.take(5) },
                    modifier = Modifier.weight(1f),
                    label = { Text("Port") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = walaviFieldColors(),
                )
                Spacer(Modifier.width(10.dp))
                GhostButton("Save", onClick = { onSavePort(portText.toIntOrNull() ?: 0) })
            }
            Spacer(Modifier.height(7.dp))
            Text(
                "Restart the gateway after changing the port. Default 8787.",
                style = MaterialTheme.typography.bodySmall,
                color = WalaviColors.Muted,
            )
            Spacer(Modifier.height(12.dp))
            HorizontalRule()
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Label("ENDPOINT")
                    Spacer(Modifier.height(3.dp))
                    Text(
                        state.lanUrl.ifBlank { "http://<device-ip>:${settings.port}/v1" },
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = Mono),
                        color = WalaviColors.Text,
                    )
                }
                Spacer(Modifier.width(8.dp))
                GhostButton("Refresh", onClick = onRefreshEndpoint)
            }
            Spacer(Modifier.height(5.dp))
            Text(
                "Kept stable across restarts while this address stays assigned to the device.",
                style = MaterialTheme.typography.bodySmall,
                color = WalaviColors.Muted,
            )
        }

        Panel {
            Label("UPSTREAM")
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = baseText,
                onValueChange = { baseText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API base URL") },
                singleLine = true,
                colors = walaviFieldColors(),
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Used for sign-in and inference.",
                    style = MaterialTheme.typography.bodySmall,
                    color = WalaviColors.Muted,
                    modifier = Modifier.weight(1f),
                )
                GhostButton("Save", onClick = { onSaveBaseUrl(baseText) })
            }
        }

        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label("API KEY")
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { revealKey = !revealKey }) {
                    Text(
                        if (revealKey) "HIDE" else "SHOW",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp),
                        color = WalaviColors.Muted,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(WalaviColors.Sidebar)
                    .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (revealKey) settings.apiKey else maskKey(settings.apiKey),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = Mono,
                        fontSize = 12.sp,
                    ),
                    color = WalaviColors.Text,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                SquareIconButton(CopyIcon, "Copy API key") {
                    onCopy(settings.apiKey)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                GhostButton("Regenerate", onClick = { onRegenerateKey() })
                Spacer(Modifier.weight(1f))
                Text(
                    "Clients send Bearer <key>",
                    style = MaterialTheme.typography.bodySmall,
                    color = WalaviColors.Muted,
                )
            }
            Spacer(Modifier.height(4.dp))
            HorizontalRule()
            SwitchRow(
                title = "Require API key",
                subtitle = "Reject requests without the key above",
                checked = settings.requireKey,
                onCheckedChange = onRequireKey,
            )
        }

        Panel {
            Label("BEHAVIOUR")
            Spacer(Modifier.height(4.dp))
            SwitchRow(
                title = "Start with the app",
                subtitle = "Bring the gateway up when Walavi opens",
                checked = settings.autoStart,
                onCheckedChange = onAutoStart,
            )
            HorizontalRule()
            SwitchRow(
                title = "Keep awake",
                subtitle = "Hold a partial wake and Wi-Fi lock while running",
                checked = settings.keepAwake,
                onCheckedChange = onKeepAwake,
            )
            HorizontalRule()
            SwitchRow(
                title = "Start on boot",
                subtitle = "Resume the gateway after a reboot",
                checked = settings.startOnBoot,
                onCheckedChange = onStartOnBoot,
            )
        }

        Panel {
            Label("BATTERY")
            Spacer(Modifier.height(8.dp))
            Text(
                "Android may freeze background sockets unless Walavi is exempt from battery optimisation. Use this only if the gateway drops while the screen is off.",
                style = MaterialTheme.typography.bodySmall,
                color = WalaviColors.Muted,
            )
            Spacer(Modifier.height(10.dp))
            GhostButton("Open battery settings", onClick = {
                val intent = Intent(
                    AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${context.packageName}"),
                )
                runCatching { context.startActivity(intent) }
                    .onFailure { onNotify("This device does not expose battery settings") }
            })
        }

        Panel {
            Label("ABOUT")
            Spacer(Modifier.height(10.dp))
            InfoRow("Version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            InfoRow("Status", if (state.running) "running on ${state.port}" else "stopped")
            InfoRow("Accounts", accountCount.toString())
            InfoRow("Default model", settings.defaultModel)
            InfoRow("Cost basis", "× credits per 1K tokens")
            Spacer(Modifier.height(12.dp))
            Text(
                buildString {
                    appendLine("Walavi ${BuildConfig.VERSION_NAME}")
                    appendLine("Endpoint: ${state.lanUrl.ifBlank { "http://<device-ip>:${settings.port}/v1" }}")
                    appendLine("Port: ${settings.port}")
                    appendLine("Accounts: $accountCount")
                    appendLine("Base URL: ${settings.baseUrl}")
                    appendLine("Default model: ${settings.defaultModel}")
                    append("Exposed: ")
                    append(if (settings.exposeAllModels) "all" else settings.selectedModels.joinToString())
                },
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = Mono,
                    fontSize = 10.sp,
                    letterSpacing = 0.sp,
                ),
                color = WalaviColors.Muted,
            )
            Spacer(Modifier.height(10.dp))
            GhostButton("Copy diagnostics", onClick = {
                onCopy(
                    buildString {
                        appendLine("Walavi ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                        appendLine("Status: ${if (state.running) "running" else "stopped"}")
                        appendLine("Port: ${settings.port}")
                        appendLine("Accounts: $accountCount")
                        appendLine("Base URL: ${settings.baseUrl}")
                        appendLine("Default model: ${settings.defaultModel}")
                    },
                )
            })
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = WalaviColors.Muted,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = Mono, fontSize = 11.sp),
            color = WalaviColors.Text,
        )
    }
}

private fun maskKey(key: String): String {
    if (key.length <= 16) return "••••••••••••"
    return key.take(9) + "••••••••" + key.takeLast(4)
}
