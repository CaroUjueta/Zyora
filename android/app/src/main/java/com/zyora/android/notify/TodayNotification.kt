package com.zyora.android.notify

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.zyora.android.MainActivity
import com.zyora.android.R
import com.zyora.android.ZyoraApplication
import com.zyora.android.ui.Fmt
import com.zyora.model.Task
import com.zyora.service.Planner
import java.time.LocalDate
import java.time.ZoneId

/**
 * A notification that stays in the status bar listing the tasks still pending for today (and the
 * overdue ones). It updates on every change, at midnight and after a restart, and comes back if
 * it is swiped away: to get rid of it, finish the tasks or turn it off in Listas.
 */
object TodayNotification {
    private const val CHANNEL = "today"
    private const val ID = 1
    private const val MAX_LINES = 6
    private const val PREFS = "notifications"
    private const val ENABLED = "today"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(ENABLED, enabled).apply()
        update(context)
    }

    /** Whether Android lets Zyora post notifications (asked at runtime from Android 13 on). */
    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Shows, refreshes or removes the notification to match the planner. Call on the main thread. */
    fun update(context: Context) {
        val app = context.applicationContext as ZyoraApplication
        val manager = NotificationManagerCompat.from(app)
        scheduleMidnight(app)
        val today = app.planner.today()
        val tasks = app.planner.upcomingTasks(0).filter { !it.isCompleted }
        if (!isEnabled(app) || !hasPermission(app) || tasks.isEmpty()) {
            manager.cancel(ID)
            return
        }
        createChannel(app)

        val overdue = tasks.count { app.planner.planDay(it).isBefore(today) }
        val title = (if (tasks.size == 1) "Tienes 1 tarea para hoy" else "Tienes ${tasks.size} tareas para hoy") +
            if (overdue > 0) " ($overdue vencida${if (overdue == 1) "" else "s"})" else ""
        val lines = tasks.map { line(app.planner, it, today) }
        val style = NotificationCompat.InboxStyle().setBigContentTitle(title)
        lines.take(MAX_LINES).forEach { style.addLine(it) }
        if (lines.size > MAX_LINES) {
            style.setSummaryText("+${lines.size - MAX_LINES} más")
        }

        val notification = NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF8F7BD6.toInt())
            .setContentTitle(title)
            .setContentText(tasks.joinToString(" · ") { it.title })
            .setStyle(style)
            .setNumber(tasks.size)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(PendingIntent.getActivity(app, 0, MainActivity.openIntent(app, null),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            // From Android 14 on, ongoing notifications can be swiped away; this puts it right back.
            .setDeleteIntent(broadcast(app, TodayReceiver.ACTION_DISMISSED))
            .build()
        try {
            manager.notify(ID, notification)
        } catch (e: SecurityException) {
            // The permission was taken away in the meantime.
        }
    }

    /** "Comprar pan", "10:00  Estudiar" or "Pagar arriendo · venció 14 oct". */
    private fun line(planner: Planner, task: Task, today: LocalDate): String {
        val day = planner.planDay(task)
        return when {
            day.isBefore(today) -> "${task.title} · venció ${Fmt.DAY_MONTH.format(day)}"
            task.isScheduled -> "${Fmt.TIME.format(task.scheduledStart)}  ${task.title}"
            else -> task.title
        }
    }

    private fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, "Tareas de hoy", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Siempre visible con las tareas pendientes del día"
            setShowBadge(true)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Wakes [TodayReceiver] just after midnight so the list moves on to the new day. */
    private fun scheduleMidnight(context: Context) {
        val next = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).plusMinutes(1)
        context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), broadcast(context, TodayReceiver.ACTION_MIDNIGHT))
    }

    private fun broadcast(context: Context, action: String): PendingIntent =
        PendingIntent.getBroadcast(context, action.hashCode(),
            Intent(context, TodayReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}

/** Brings the notification back up to date after midnight, a restart, a clock change or a swipe. */
class TodayReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        TodayNotification.update(context)
    }

    companion object {
        const val ACTION_MIDNIGHT = "com.zyora.android.TODAY_MIDNIGHT"
        const val ACTION_DISMISSED = "com.zyora.android.TODAY_DISMISSED"
    }
}
