package com.flux.walavi.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flux.walavi.data.LifetimeUsage
import com.flux.walavi.data.Pricing
import com.flux.walavi.data.RequestLog
import com.flux.walavi.ui.components.EmptyState
import com.flux.walavi.ui.components.GhostButton
import com.flux.walavi.ui.components.Label
import com.flux.walavi.ui.components.Panel
import com.flux.walavi.ui.components.ScreenTitle
import com.flux.walavi.ui.components.StatusPill
import com.flux.walavi.ui.components.statusColor
import com.flux.walavi.ui.theme.Mono
import com.flux.walavi.ui.theme.WalaviColors
import com.flux.walavi.util.Net

@Composable
fun LogsScreen(
    logs: List<RequestLog>,
    usage: LifetimeUsage,
    knownModels: List<String>,
    onClear: () -> Unit,
) {
    var errorsOnly by rememberSaveable { mutableStateOf(false) }
    val visible = if (errorsOnly) logs.filter { it.status !in 200..299 } else logs
    val totalTokens = usage.totalTokens
    val totalIn = usage.promptTokens
    val totalOut = usage.completionTokens
    val totalCredits = usage.credits

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, top = 10.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScreenTitle("Activity", "All-time usage, with the latest requests listed.")
                Spacer(Modifier.weight(1f))
                GhostButton("Clear", onClear, enabled = logs.isNotEmpty())
            }
        }

        item {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            Pricing.formatTokens(totalTokens),
                            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = Mono),
                            color = WalaviColors.Text,
                        )
                        Label("ALL-TIME TOKENS (IN+OUT)")
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            Pricing.format(totalCredits),
                            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = Mono),
                            color = WalaviColors.Text,
                        )
                        Label("ALL-TIME CREDITS")
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "in ${Pricing.formatTokens(totalIn)} · out ${Pricing.formatTokens(totalOut)} · ${Pricing.formatTokens(usage.requests)} requests all-time",
                    style = MaterialTheme.typography.bodySmall,
                    color = WalaviColors.Muted,
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterPill("All", !errorsOnly) { errorsOnly = false }
                FilterPill("Errors", errorsOnly) { errorsOnly = true }
                Spacer(Modifier.weight(1f))
                Text(
                    "${visible.size}",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.4.sp),
                    color = WalaviColors.Muted,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        if (visible.isEmpty()) {
            item {
                Panel {
                    EmptyState(
                        if (errorsOnly) "No errors" else "No requests yet",
                        if (errorsOnly) {
                            "Every request in the buffer succeeded."
                        } else {
                            "Send a request from your coding agent and it will appear here."
                        },
                    )
                }
            }
        } else {
            items(visible) { log ->
                LogCard(log, knownModels)
            }
        }
    }
}

@Composable
private fun FilterPill(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp),
        color = if (selected) WalaviColors.Bg else WalaviColors.Muted,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) WalaviColors.Text else WalaviColors.Sidebar)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

@Composable
private fun LogCard(log: RequestLog, knownModels: List<String>) {
    val color = statusColor(log.status)
    Panel(padding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusPill(log.status.toString(), color, dot = false)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    log.model,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Mono),
                    color = WalaviColors.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${log.account} · ${Net.relativeTime(log.at)}${if (log.stream) " · stream" else ""}",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.2.sp),
                    color = WalaviColors.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    Net.duration(log.ms),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.2.sp),
                    color = WalaviColors.Text,
                )
                Text(
                    "${Pricing.formatTokens(log.totalTokens.toLong())} tok",
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
        if (!log.error.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                log.error,
                style = MaterialTheme.typography.bodySmall,
                color = WalaviColors.Red,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
