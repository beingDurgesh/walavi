package com.flux.walavi.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flux.walavi.data.Account
import com.flux.walavi.ui.theme.Mono
import com.flux.walavi.ui.theme.WalaviColors

val CopyIcon: ImageVector = ImageVector.Builder(
    name = "Copy",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(16f, 1f)
        horizontalLineTo(4f)
        curveTo(2.9f, 1f, 2f, 1.9f, 2f, 3f)
        verticalLineTo(17f)
        horizontalLineTo(4f)
        verticalLineTo(3f)
        horizontalLineTo(16f)
        verticalLineTo(1f)
        close()
        moveTo(19f, 5f)
        horizontalLineTo(8f)
        curveTo(6.9f, 5f, 6f, 5.9f, 6f, 7f)
        verticalLineTo(21f)
        curveTo(6f, 22.1f, 6.9f, 23f, 8f, 23f)
        horizontalLineTo(19f)
        curveTo(20.1f, 23f, 21f, 22.1f, 21f, 21f)
        verticalLineTo(7f)
        curveTo(21f, 5.9f, 20.1f, 5f, 19f, 5f)
        close()
        moveTo(19f, 21f)
        horizontalLineTo(8f)
        verticalLineTo(7f)
        horizontalLineTo(19f)
        close()
    }
}.build()

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = WalaviColors.Panel,
        border = BorderStroke(1.dp, WalaviColors.Border),
    ) {
        Column(modifier = Modifier.padding(padding), content = content)
    }
}

@Composable
fun Label(text: String, color: Color = WalaviColors.Muted) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        maxLines = 1,
    )
}

@Composable
fun ScreenTitle(title: String, subtitle: String? = null) {
    Column {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = WalaviColors.Text)
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = WalaviColors.Muted)
        }
    }
}

@Composable
fun StatusPill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    dot: Boolean = true,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.10f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot) {
            Box(Modifier.size(5.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.6.sp),
            color = color,
            maxLines = 1,
        )
    }
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = WalaviColors.Panel,
        border = BorderStroke(1.dp, WalaviColors.Border),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = Mono),
                color = WalaviColors.Text,
                maxLines = 1,
            )
            Spacer(Modifier.height(3.dp))
            Label(label)
        }
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        enabled = enabled && !loading,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = WalaviColors.Btn,
            contentColor = WalaviColors.BtnText,
            disabledContainerColor = WalaviColors.Border,
            disabledContentColor = WalaviColors.Muted,
        ),
        contentPadding = PaddingValues(horizontal = 18.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = WalaviColors.BtnText,
            )
        } else {
            Text(text, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium))
        }
    }
}

@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, WalaviColors.Border),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = WalaviColors.Text),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SquareIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(WalaviColors.Sidebar)
            .border(1.dp, WalaviColors.Border, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = WalaviColors.Muted,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
fun SquareTextButton(text: String, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(WalaviColors.Sidebar)
            .border(1.dp, WalaviColors.Border, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.4.sp),
            color = WalaviColors.Muted,
            modifier = Modifier.semantics { this.contentDescription = description },
        )
    }
}

@Composable
fun SectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Label(title)
        Spacer(Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(
                action.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp),
                color = WalaviColors.Text,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
fun SwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = WalaviColors.Text)
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = WalaviColors.Muted)
            }
        }
        Spacer(Modifier.width(14.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = walaviSwitchColors(),
        )
    }
}

@Composable
fun walaviSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = WalaviColors.Bg,
    checkedTrackColor = WalaviColors.Text,
    checkedBorderColor = WalaviColors.Text,
    uncheckedThumbColor = WalaviColors.Muted,
    uncheckedTrackColor = WalaviColors.Sidebar,
    uncheckedBorderColor = WalaviColors.Border,
)

@Composable
fun walaviFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = WalaviColors.Text,
    unfocusedTextColor = WalaviColors.Text,
    focusedContainerColor = WalaviColors.Sidebar,
    unfocusedContainerColor = WalaviColors.Sidebar,
    cursorColor = WalaviColors.Text,
    focusedBorderColor = WalaviColors.Muted.copy(alpha = 0.55f),
    unfocusedBorderColor = WalaviColors.Border,
    focusedLabelColor = WalaviColors.Muted,
    unfocusedLabelColor = WalaviColors.Muted,
    focusedPlaceholderColor = WalaviColors.Muted.copy(alpha = 0.6f),
    unfocusedPlaceholderColor = WalaviColors.Muted.copy(alpha = 0.6f),
)

@Composable
fun MiniStat(label: String, value: String) {
    Column {
        Text(
            value,
            style = MaterialTheme.typography.labelLarge.copy(fontFamily = Mono),
            color = WalaviColors.Text,
            maxLines = 1,
        )
        Spacer(Modifier.height(1.dp))
        Label(label)
    }
}

@Composable
fun Avatar(label: String) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(WalaviColors.Sidebar)
            .border(1.dp, WalaviColors.Border, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label.take(1).uppercase().ifBlank { "?" },
            style = MaterialTheme.typography.titleMedium,
            color = WalaviColors.Text,
        )
    }
}

@Composable
fun EmptyState(title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            ">_",
            style = MaterialTheme.typography.titleLarge.copy(fontFamily = Mono),
            color = WalaviColors.Muted.copy(alpha = 0.45f),
        )
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = WalaviColors.Text)
        Spacer(Modifier.height(4.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodySmall,
            color = WalaviColors.Muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}

@Composable
fun HorizontalRule() {
    HorizontalDivider(color = WalaviColors.Border, thickness = 1.dp)
}

data class AccountStatus(val label: String, val color: Color)

fun accountStatus(account: Account, now: Long = System.currentTimeMillis()): AccountStatus = when {
    !account.enabled -> AccountStatus("Disabled", WalaviColors.Muted)
    account.needsLogin -> AccountStatus("Sign-in needed", WalaviColors.Red)
    account.cooldownUntil > now -> {
        val seconds = ((account.cooldownUntil - now) / 1000L).coerceAtLeast(1L)
        AccountStatus("Cooling ${seconds}s", WalaviColors.Amber)
    }
    else -> AccountStatus("Ready", WalaviColors.Green)
}

fun statusColor(status: Int): Color = when {
    status in 200..299 -> WalaviColors.Green
    status == 429 -> WalaviColors.Amber
    status in 400..499 -> WalaviColors.Amber
    else -> WalaviColors.Red
}
