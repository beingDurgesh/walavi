package com.flux.walavi.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flux.walavi.data.ModelCatalog
import com.flux.walavi.data.ModelInfo
import com.flux.walavi.data.Pricing
import com.flux.walavi.data.WalaviSettings
import com.flux.walavi.ui.components.GhostButton
import com.flux.walavi.ui.components.HorizontalRule
import com.flux.walavi.ui.components.Label
import com.flux.walavi.ui.components.Panel
import com.flux.walavi.ui.components.ScreenTitle
import com.flux.walavi.ui.components.SectionHeader
import com.flux.walavi.ui.components.SwitchRow
import com.flux.walavi.ui.theme.Mono
import com.flux.walavi.ui.theme.WalaviColors

@Composable
fun ModelsScreen(
    settings: WalaviSettings,
    refreshing: Boolean,
    onSetDefault: (String) -> Unit,
    onForceModel: (Boolean) -> Unit,
    onExposeAll: (Boolean) -> Unit,
    onToggleSelected: (String, Boolean) -> Unit,
    onRefresh: () -> Unit,
) {
    val catalog = ModelCatalog.all(settings.knownModels)
    val current = catalog.firstOrNull { it.id == settings.defaultModel }
        ?: ModelInfo(settings.defaultModel, ModelCatalog.prettify(settings.defaultModel), "Other")
    val grouped = catalog.groupBy { it.provider }
    var pickerOpen by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, top = 10.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenTitle("Models", "What agents can request from this pool.")
        }

        item {
            Panel {
                Label("DEFAULT MODEL")
                Spacer(Modifier.height(9.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            current.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = WalaviColors.Text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            current.id,
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.2.sp),
                            color = WalaviColors.Muted,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            current.credits?.let { "Cost ${Pricing.creditLabel(it)} per 1K tokens (est.)" }
                                ?: "Cost unknown for this model",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (current.credits == 0.0) WalaviColors.Green else WalaviColors.Muted,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    GhostButton("Change", onClick = { pickerOpen = true })
                }
            }
        }

        item {
            Panel {
                SwitchRow(
                    title = "Force default model",
                    subtitle = "Rewrite every request to the model above",
                    checked = settings.forceModel,
                    onCheckedChange = onForceModel,
                )
                HorizontalRule()
                SwitchRow(
                    title = "Expose all models",
                    subtitle = "Agents see the whole catalogue in /v1/models",
                    checked = settings.exposeAllModels,
                    onCheckedChange = onExposeAll,
                )
            }
        }

        item {
            Panel {
                SectionHeader("Catalogue", "Refresh", onRefresh)
                Spacer(Modifier.height(6.dp))
                if (refreshing) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = WalaviColors.Muted,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Asking the upstream for its catalogue…",
                            style = MaterialTheme.typography.bodySmall,
                            color = WalaviColors.Muted,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    if (settings.exposeAllModels) {
                        "${catalog.size} models available. Tap one to make it the default."
                    } else {
                        "Only checked models are served. Tap a row to make it the default."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = WalaviColors.Muted,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "× = credits per 1K tokens (estimate).",
                    style = MaterialTheme.typography.bodySmall,
                    color = WalaviColors.Muted.copy(alpha = 0.8f),
                )
                Spacer(Modifier.height(10.dp))

                grouped.forEach { (provider, models) ->
                    Spacer(Modifier.height(8.dp))
                    Label(provider)
                    Spacer(Modifier.height(2.dp))
                    models.forEach { model ->
                        ModelRow(
                            model = model,
                            isDefault = model.id == settings.defaultModel,
                            showCheckbox = !settings.exposeAllModels,
                            selected = model.id in settings.selectedModels,
                            onSetDefault = { onSetDefault(model.id) },
                            onToggle = { onToggleSelected(model.id, it) },
                        )
                    }
                }
            }
        }
    }

    if (pickerOpen) {
        AlertDialog(
            onDismissRequest = { pickerOpen = false },
            containerColor = WalaviColors.Panel,
            titleContentColor = WalaviColors.Text,
            textContentColor = WalaviColors.Muted,
            title = { Text("Default model") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(catalog) { model ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    onSetDefault(model.id)
                                    pickerOpen = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = model.id == settings.defaultModel,
                                onClick = {
                                    onSetDefault(model.id)
                                    pickerOpen = false
                                },
                                colors = radioColors(),
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    model.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = WalaviColors.Text,
                                )
                                Text(
                                    model.id,
                                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.2.sp),
                                    color = WalaviColors.Muted,
                                )
                            }
                            Text(
                                Pricing.creditLabel(model.credits),
                                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.2.sp),
                                color = creditColor(model.credits),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pickerOpen = false }) {
                    Text("Close", color = WalaviColors.Text)
                }
            },
        )
    }
}

@Composable
private fun ModelRow(
    model: ModelInfo,
    isDefault: Boolean,
    showCheckbox: Boolean,
    selected: Boolean,
    onSetDefault: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onSetDefault)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = isDefault,
            onClick = onSetDefault,
            colors = radioColors(),
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                model.name,
                style = MaterialTheme.typography.bodyLarge,
                color = WalaviColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                model.id,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = Mono,
                    letterSpacing = 0.2.sp,
                ),
                color = WalaviColors.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            Pricing.creditLabel(model.credits),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.2.sp),
            color = creditColor(model.credits),
            modifier = Modifier.padding(horizontal = 6.dp),
        )
        if (showCheckbox) {
            Checkbox(
                checked = selected,
                onCheckedChange = onToggle,
                colors = CheckboxDefaults.colors(
                    checkedColor = WalaviColors.Text,
                    uncheckedColor = WalaviColors.Border,
                    checkmarkColor = WalaviColors.Bg,
                ),
            )
        }
    }
}

private fun creditColor(credits: Double?): androidx.compose.ui.graphics.Color = when {
    credits == null -> WalaviColors.Muted
    credits == 0.0 -> WalaviColors.Green
    else -> WalaviColors.Amber
}

@Composable
private fun radioColors() = RadioButtonDefaults.colors(
    selectedColor = WalaviColors.Text,
    unselectedColor = WalaviColors.Muted,
)
