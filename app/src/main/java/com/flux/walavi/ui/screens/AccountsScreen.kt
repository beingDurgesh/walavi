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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import com.flux.walavi.data.Account
import com.flux.walavi.ui.components.Avatar
import com.flux.walavi.ui.components.EmptyState
import com.flux.walavi.ui.components.HorizontalRule
import com.flux.walavi.ui.components.MiniStat
import com.flux.walavi.ui.components.Panel
import com.flux.walavi.ui.components.ScreenTitle
import com.flux.walavi.ui.components.StatusPill
import com.flux.walavi.ui.components.accountStatus
import com.flux.walavi.ui.components.walaviSwitchColors
import com.flux.walavi.ui.theme.WalaviColors
import com.flux.walavi.util.Net

@Composable
fun AccountsScreen(
    accounts: List<Account>,
    busy: Set<String>,
    onAdd: () -> Unit,
    onToggle: (Account, Boolean) -> Unit,
    onTest: (Account) -> Unit,
    onRelogin: (Account) -> Unit,
    onDelete: (Account) -> Unit,
    onMove: (Account, Int) -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<Account?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, top = 10.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScreenTitle("Accounts", "${accounts.size} in the pool")
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = onAdd,
                    modifier = Modifier.height(36.dp),
                    shape = RoundedCornerShape(9.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WalaviColors.Text,
                        contentColor = WalaviColors.Bg,
                    ),
                    contentPadding = PaddingValues(horizontal = 13.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Add", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        if (accounts.isEmpty()) {
            item {
                Panel {
                    EmptyState(
                        "Pool is empty",
                        "Sign in with a CodeBuddy account or paste an access token to start.",
                    )
                    Spacer(Modifier.height(6.dp))
                    com.flux.walavi.ui.components.PrimaryButton(
                        "Add account",
                        onAdd,
                        Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        items(accounts.size) { index ->
            val account = accounts[index]
            AccountCard(
                account = account,
                busy = busy.contains(account.id),
                isFirst = index == 0,
                isLast = index == accounts.lastIndex,
                onToggle = { onToggle(account, it) },
                onTest = { onTest(account) },
                onRelogin = { onRelogin(account) },
                onDelete = { pendingDelete = account },
                onMove = { onMove(account, it) },
            )
        }
    }

    val target = pendingDelete
    if (target != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = WalaviColors.Panel,
            titleContentColor = WalaviColors.Text,
            textContentColor = WalaviColors.Muted,
            title = { Text("Remove account?") },
            text = {
                Text(
                    "${target.label} will leave the pool. The CodeBuddy session itself is not revoked.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(target)
                    pendingDelete = null
                }) {
                    Text("Remove", color = WalaviColors.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Cancel", color = WalaviColors.Muted)
                }
            },
        )
    }
}

@Composable
private fun AccountCard(
    account: Account,
    busy: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onToggle: (Boolean) -> Unit,
    onTest: () -> Unit,
    onRelogin: () -> Unit,
    onDelete: () -> Unit,
    onMove: (Int) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val status = accountStatus(account)

    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(account.label)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        account.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = WalaviColors.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (account.needsLogin) {
                        Spacer(Modifier.width(6.dp))
                        StatusPill("Re-auth", WalaviColors.Red, dot = false)
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    Net.maskToken(account.accessToken) +
                        (account.userId?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.2.sp),
                    color = WalaviColors.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            Switch(
                checked = account.enabled,
                onCheckedChange = onToggle,
                colors = walaviSwitchColors(),
            )
        }

        Spacer(Modifier.height(12.dp))
        HorizontalRule()
        Spacer(Modifier.height(11.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            MiniStat("OK", account.successCount.toString())
            Spacer(Modifier.width(18.dp))
            MiniStat("ERR", account.failureCount.toString())
            Spacer(Modifier.width(18.dp))
            MiniStat("LAST", Net.relativeTime(account.lastUsedAt))
            Spacer(Modifier.weight(1f))
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = WalaviColors.Muted,
                )
                Spacer(Modifier.width(8.dp))
            }
            Box {
                com.flux.walavi.ui.components.SquareIconButton(
                    Icons.Default.MoreVert,
                    "Account actions",
                ) { menuOpen = true }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    containerColor = WalaviColors.Panel,
                ) {
                    DropdownMenuItem(
                        text = { Text("Test connection") },
                        leadingIcon = { Icon(Icons.Default.PlayArrow, null) },
                        onClick = {
                            menuOpen = false
                            onTest()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Re-authenticate") },
                        leadingIcon = { Icon(Icons.Default.Refresh, null) },
                        onClick = {
                            menuOpen = false
                            onRelogin()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Move up") },
                        leadingIcon = { Icon(Icons.Default.KeyboardArrowUp, null) },
                        enabled = !isFirst,
                        onClick = {
                            menuOpen = false
                            onMove(-1)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Move down") },
                        leadingIcon = { Icon(Icons.Default.KeyboardArrowDown, null) },
                        enabled = !isLast,
                        onClick = {
                            menuOpen = false
                            onMove(1)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Remove", color = WalaviColors.Red) },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = WalaviColors.Red) },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
        }

        if (status.label != "Ready") {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(status.color.copy(alpha = 0.08f))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(5.dp).clip(CircleShape).background(status.color))
                Spacer(Modifier.width(8.dp))
                Text(
                    account.lastError ?: when {
                        account.needsLogin -> "Sign-in expired. Re-authenticate to resume."
                        !account.enabled -> "Account is disabled."
                        else -> status.label
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = status.color,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
