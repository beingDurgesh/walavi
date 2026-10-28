package com.flux.walavi.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flux.walavi.ui.LoginState
import com.flux.walavi.ui.components.GhostButton
import com.flux.walavi.ui.components.Label
import com.flux.walavi.ui.components.PrimaryButton
import com.flux.walavi.ui.components.StatusPill
import com.flux.walavi.ui.components.walaviFieldColors
import com.flux.walavi.ui.theme.Mono
import com.flux.walavi.ui.theme.WalaviColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAccountSheet(
    login: LoginState,
    onDismiss: () -> Unit,
    onBrowserLogin: () -> Unit,
    onCancelLogin: () -> Unit,
    onAddToken: (label: String, token: String, refresh: String) -> Unit,
) {
    var mode by rememberSaveable { mutableStateOf(0) }
    var label by rememberSaveable { mutableStateOf("") }
    var token by rememberSaveable { mutableStateOf("") }
    var refresh by rememberSaveable { mutableStateOf("") }
    var reveal by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = WalaviColors.Panel,
        contentColor = WalaviColors.Text,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 34.dp, height = 4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(WalaviColors.Border),
            )
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
        ) {
            Text("Add account", style = MaterialTheme.typography.titleLarge, color = WalaviColors.Text)
            Spacer(Modifier.height(4.dp))
            Text(
                "Accounts are pooled automatically. Requests fail over when one is rejected.",
                style = MaterialTheme.typography.bodySmall,
                color = WalaviColors.Muted,
            )
            Spacer(Modifier.height(16.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(9.dp))
                    .background(WalaviColors.Sidebar)
                    .padding(3.dp),
            ) {
                Segment("Browser sign-in", mode == 0, Modifier.weight(1f)) { mode = 0 }
                Segment("Paste token", mode == 1, Modifier.weight(1f)) { mode = 1 }
            }
            Spacer(Modifier.height(18.dp))

            if (mode == 0) {
                BrowserLoginContent(
                    login = login,
                    onBrowserLogin = onBrowserLogin,
                    onCancelLogin = onCancelLogin,
                    onDismiss = onDismiss,
                )
            } else {
                Label("ACCESS TOKEN")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it.take(48) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Label (optional)") },
                    placeholder = { Text("Work account") },
                    singleLine = true,
                    colors = walaviFieldColors(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Access token") },
                    placeholder = { Text("Paste the token or the full credential JSON") },
                    minLines = 3,
                    maxLines = 5,
                    visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                    colors = walaviFieldColors(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = refresh,
                    onValueChange = { refresh = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Refresh token (optional)") },
                    singleLine = true,
                    visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                    colors = walaviFieldColors(),
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { reveal = !reveal }) {
                        Text(if (reveal) "Hide" else "Show", color = WalaviColors.Muted)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "Tokens stay encrypted on this device",
                        style = MaterialTheme.typography.bodySmall,
                        color = WalaviColors.Muted,
                    )
                }
                Spacer(Modifier.height(12.dp))
                PrimaryButton(
                    text = "Add to pool",
                    onClick = {
                        onAddToken(label, token, refresh)
                        label = ""
                        token = ""
                        refresh = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = token.isNotBlank(),
                )
            }
        }
    }
}

@Composable
private fun BrowserLoginContent(
    login: LoginState,
    onBrowserLogin: () -> Unit,
    onCancelLogin: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (login) {
        LoginState.Idle -> {
            Text(
                "Walavi opens CodeBuddy's sign-in page. Approve the session in your browser and this sheet finishes automatically.",
                style = MaterialTheme.typography.bodyMedium,
                color = WalaviColors.Muted,
            )
            Spacer(Modifier.height(16.dp))
            PrimaryButton("Open sign-in", onBrowserLogin, Modifier.fillMaxWidth())
        }
        LoginState.Starting -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = WalaviColors.Muted,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "Requesting a sign-in link…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = WalaviColors.Text,
                )
            }
            Spacer(Modifier.height(16.dp))
            GhostButton("Cancel", onCancelLogin, Modifier.fillMaxWidth())
        }
        is LoginState.Waiting -> {
            StatusPill("Waiting for approval", WalaviColors.Amber)
            Spacer(Modifier.height(10.dp))
            Text(
                "Approve the login in your browser. This closes on its own.",
                style = MaterialTheme.typography.bodyMedium,
                color = WalaviColors.Muted,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                login.authUrl,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = Mono,
                    fontSize = 10.sp,
                    letterSpacing = 0.sp,
                ),
                color = WalaviColors.Muted,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(16.dp))
            GhostButton("Cancel", onCancelLogin, Modifier.fillMaxWidth())
        }
        is LoginState.Done -> {
            StatusPill("Connected", WalaviColors.Green)
            Spacer(Modifier.height(10.dp))
            Text(
                "${login.label} is now part of the pool.",
                style = MaterialTheme.typography.bodyMedium,
                color = WalaviColors.Text,
            )
            Spacer(Modifier.height(16.dp))
            PrimaryButton("Done", onDismiss, Modifier.fillMaxWidth())
        }
        is LoginState.Failed -> {
            Text(
                login.message,
                style = MaterialTheme.typography.bodyMedium,
                color = WalaviColors.Red,
            )
            Spacer(Modifier.height(16.dp))
            PrimaryButton("Try again", onBrowserLogin, Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            GhostButton("Close", onDismiss, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Segment(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(if (selected) WalaviColors.Panel else WalaviColors.Sidebar)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            ),
            color = if (selected) WalaviColors.Text else WalaviColors.Muted,
        )
    }
}
