package com.flux.walavi.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flux.walavi.BuildConfig
import com.flux.walavi.data.Account
import com.flux.walavi.data.LifetimeUsage
import com.flux.walavi.data.Pricing
import com.flux.walavi.data.RequestLog
import com.flux.walavi.gateway.GatewayState
import com.flux.walavi.ui.components.EmptyState
import com.flux.walavi.ui.components.CopyIcon
import com.flux.walavi.ui.components.HorizontalRule
import com.flux.walavi.ui.components.Label
import com.flux.walavi.ui.components.Panel
import com.flux.walavi.ui.components.PrimaryButton
import com.flux.walavi.ui.components.ScreenTitle
import com.flux.walavi.ui.components.SectionHeader
import com.flux.walavi.ui.components.SquareIconButton
import com.flux.walavi.ui.components.SquareTextButton
import com.flux.walavi.ui.components.StatCard
import com.flux.walavi.ui.components.StatusPill
import com.flux.walavi.ui.components.accountStatus
import com.flux.walavi.ui.components.statusColor
import com.flux.walavi.ui.theme.Mono
import com.flux.walavi.ui.theme.WalaviColors
import com.flux.walavi.util.Net
import java.util.Locale

@Composable
fun DashboardScreen(
    state: GatewayState,
    accounts: List<Account>,
    logs: List<RequestLog>,
    usage: LifetimeUsage,
    knownModels: List<String>,
    url: String,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onCopy: (String) -> Unit,
    onQr: () -> Unit,
    onAccounts: () -> Unit,
    onLogs: () -> Unit,
) {
    val now = System.currentTimeMillis()
    val ready = accounts.count { it.enabled && !it.needsLogin && it.cooldownUntil <= now }
    val successRate = if (state.totalRequests == 0L) {
        "—"
    } else {
        String.format(Locale.US, "%.0f%%", state.successRequests * 100.0 / state.totalRequests)
    }
    val totalTokens = usage.totalTokens
    val totalCredits = usage.credits

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, top = 10.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenTitle("Overview", "One endpoint in front of every CodeBuddy account.")
        }

        item {
            Panel(padding = PaddingValues(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Label("GATEWAY")
                    Spacer(Modifier.weight(1f))
                    StatusPill(
                        text = if (state.running) "Running" else "Stopped",
                        color = if (state.running) WalaviColors.Green else WalaviColors.Muted,
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    if (state.running) "OpenAI-compatible endpoint" else "Gateway is offline",
                    style = MaterialTheme.typography.titleMedium,
                    color = WalaviColors.Muted,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        url,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = Mono,
                            fontSize = 15.sp,
                        ),
                        color = WalaviColors.Text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    SquareIconButton(CopyIcon, "Copy endpoint") { onCopy(url) }
                    Spacer(Modifier.width(8.dp))
                    SquareTextButton("QR", "Show connection QR code", onQr)
                }
                Spacer(Modifier.height(7.dp))
                Text(
                    "Paste this as the base URL. The API key lives in Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = WalaviColors.Muted,
                )
                if (!state.lastError.isNullOrBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        state.lastError,
                        style = MaterialTheme.typography.bodySmall,
                        color = WalaviColors.Red,
                    )
                }
                Spacer(Modifier.height(16.dp))
                PrimaryButton(
                    text = if (state.running) "Stop gateway" else "Start gateway",
                    onClick = if (state.running) onStop else onStart,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("Session requests", state.totalRequests.toString(), Modifier.weight(1f))
                StatCard("Success", successRate, Modifier.weight(1f))
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("Accounts", "$ready/${accounts.size}", Modifier.weight(1f))
                StatCard("In flight", state.activeRequests.toString(), Modifier.weight(1f))
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("Tokens all-time", Pricing.formatTokens(totalTokens), Modifier.weight(1f))
                StatCard("Credits all-time", Pricing.format(totalCredits), Modifier.weight(1f))
            }
        }

        item {
            Panel {
                SectionHeader("Account pool", "Manage", onAccounts)
                Spacer(Modifier.height(10.dp))
                if (accounts.isEmpty()) {
                    EmptyState("No accounts", "Add a CodeBuddy account to start serving requests.")
                } else {
                    accounts.take(3).forEachIndexed { index, account ->
                        if (index > 0) {
                            HorizontalRule()
                            Spacer(Modifier.height(10.dp))
                        }
                        PoolRow(account, now)
                        if (index < minOf(accounts.size, 3) - 1) Spacer(Modifier.height(10.dp))
                    }
                }
            }
        }

        item {
            Panel {
                SectionHeader("Recent requests", "All", onLogs)
                Spacer(Modifier.height(8.dp))
                if (logs.isEmpty()) {
                    EmptyState("No traffic yet", "Point an agent at the endpoint and the first request lands here.")
                } else {
                    logs.take(4).forEachIndexed { index, log ->
                        if (index > 0) HorizontalRule()
                        RecentRow(log, knownModels)
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(4.dp))
            Text(
                "Walavi ${BuildConfig.VERSION_NAME} · port ${state.port} · ${accounts.size} accounts",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.4.sp),
                color = WalaviColors.Muted.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PoolRow(account: Account, now: Long) {
    val status = accountStatus(account, now)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(status.color))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                account.label,
                style = MaterialTheme.typography.bodyLarge,
                color = WalaviColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                status.label,
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.3.sp),
                color = status.color,
            )
        }
        Text(
            "${account.successCount} ok",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.3.sp),
            color = WalaviColors.Muted,
        )
    }
}

@Composable
private fun RecentRow(log: RequestLog, knownModels: List<String>) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
    ) {
        Text(
            log.status.toString(),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.2.sp),
            color = statusColor(log.status),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                log.model,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = Mono),
                color = WalaviColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${log.account} · ${Net.relativeTime(log.at)} · ${Pricing.formatTokens(log.totalTokens.toLong())} tok",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.2.sp),
                color = WalaviColors.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                Net.duration(log.ms),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.2.sp),
                color = WalaviColors.Muted,
            )
            Text(
                "${Pricing.format(Pricing.creditsFor(log, knownModels))} cr",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.2.sp),
                color = if (Pricing.creditsFor(log, knownModels) > 0.0) WalaviColors.Amber else WalaviColors.Muted,
            )
        }
    }
}
