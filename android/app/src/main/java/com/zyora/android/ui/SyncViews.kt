package com.zyora.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zyora.android.ZyoraApplication
import com.zyora.android.R
import com.zyora.android.sync.PhoneSync
import com.zyora.android.sync.SyncState
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** "Sincronizar con el PC" with the state of the last sync; tapping it opens [SyncDialog]. */
@Composable
fun SyncRow() {
    val sync = (LocalContext.current.applicationContext as ZyoraApplication).sync
    val state by sync.state.collectAsState()
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { open = true }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavIcon(R.drawable.ic_nav_sync, size = 22)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Sincronizar con el PC", color = Pastel.Text, fontSize = 15.sp)
            Text(
                if (!sync.isConfigured) "Toca para conectar con Zyora en tu computador" else describe(state),
                color = if (state is SyncState.Failed) Pastel.Danger else Pastel.Muted,
                fontSize = 12.sp,
                maxLines = 2,
            )
        }
    }
    if (open) SyncDialog(sync, state) { open = false }
}

@Composable
private fun SyncDialog(sync: PhoneSync, state: SyncState, onDismiss: () -> Unit) {
    var host by remember { mutableStateOf(sync.host) }
    var code by remember { mutableStateOf(sync.code) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(22.dp),
        title = { Text("Sincronizar con el PC", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "En el PC abre Zyora y pulsa «Sincronizar con el teléfono». Escribe aquí la dirección y el código que aparecen. " +
                        "Los dos deben estar en la misma red Wi‑Fi.",
                    color = Pastel.Muted, fontSize = 14.sp,
                )
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it.trim() },
                    label = { Text("Dirección del PC") },
                    placeholder = { Text("192.168.1.20") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.filter(Char::isDigit).take(6) },
                    label = { Text("Código") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state != SyncState.Idle) {
                    Text(
                        describe(state),
                        color = when (state) {
                            is SyncState.Failed -> Pastel.Danger
                            is SyncState.Done -> Pastel.AccentInk
                            else -> Pastel.Muted
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    sync.configure(host, code)
                    sync.syncNow()
                },
                enabled = host.isNotBlank() && code.length == 6 && state != SyncState.Running,
            ) { Text("Sincronizar ahora", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

private val TIME = DateTimeFormatter.ofPattern("HH:mm")
private val DAY = DateTimeFormatter.ofPattern("d MMM")

private fun describe(state: SyncState): String = when (state) {
    SyncState.Idle -> "Todavía no se ha sincronizado"
    SyncState.Running -> "Sincronizando…"
    is SyncState.Failed -> state.message
    is SyncState.Done -> {
        val at = state.at.atZone(ZoneId.systemDefault())
        val day = if (at.toLocalDate() == LocalDate.now()) "hoy" else "el " + DAY.format(at)
        "✓ Sincronizado $day a las ${TIME.format(at)}"
    }
}
