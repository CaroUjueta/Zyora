package com.zyora.android.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.zyora.android.R
import com.zyora.android.notify.TodayNotification

/** Turns the always-visible notification with today's tasks on or off, and asks for the permission. */
@Composable
fun NotificationRow() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(TodayNotification.isEnabled(context)) }
    var allowed by remember { mutableStateOf(TodayNotification.hasPermission(context)) }
    // The permission can change in the system settings while Zyora is in the background.
    LifecycleResumeEffect(Unit) {
        allowed = TodayNotification.hasPermission(context)
        onPauseOrDispose {}
    }
    val openSettings = {
        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = granted
        TodayNotification.update(context)
        // After two refusals Android stops asking; the setting is the only way left.
        if (!granted) openSettings()
    }
    val needsPermission = enabled && !allowed

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                when {
                    needsPermission && Build.VERSION.SDK_INT >= 33 -> ask.launch(Manifest.permission.POST_NOTIFICATIONS)
                    needsPermission -> openSettings()
                    else -> {
                        enabled = !enabled
                        TodayNotification.setEnabled(context, enabled)
                    }
                }
            }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavIcon(R.drawable.ic_notification, size = 22)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Notificación de tareas de hoy", color = Pastel.Text, fontSize = 15.sp)
            Text(
                when {
                    needsPermission -> "Falta el permiso de notificaciones: toca para darlo"
                    enabled -> "Siempre visible mientras tengas tareas pendientes para hoy"
                    else -> "Desactivada"
                },
                color = if (needsPermission) Pastel.Danger else Pastel.Muted,
                fontSize = 12.sp,
                maxLines = 2,
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = {
                enabled = it
                TodayNotification.setEnabled(context, it)
                if (it && !allowed && Build.VERSION.SDK_INT >= 33) ask.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            colors = SwitchDefaults.colors(checkedTrackColor = Pastel.Accent),
        )
    }
}
