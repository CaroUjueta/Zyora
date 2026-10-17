package com.zyora.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zyora.android.Profile
import com.zyora.model.Task
import com.zyora.service.Planner
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters

/** How far ahead "Mis tareas" looks, the same as on the computer. */
private const val DAYS_AHEAD = 7

/** The start page, like Inicio on the computer: a greeting, a small month calendar and the week's tasks. */
@Composable
fun HomeScreen(planner: Planner, profile: Profile, onOpenDay: (LocalDate) -> Unit, onOpenTask: (Task) -> Unit) {
    observePlanner()
    var renaming by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Column {
                Text(
                    if (profile.hasName) "Hola, ${profile.name} ✿" else "Hola ✿",
                    fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Pastel.Text,
                    modifier = Modifier.clickable { renaming = true },
                )
                Text("Aquí tienes tu plan para hoy:", fontSize = 15.sp, color = Pastel.Text.copy(alpha = 0.8f))
            }
        }
        item { HomeCard { MiniMonth(planner, onOpenDay) } }
        item { HomeCard { WeekTasks(planner, onAdd = { adding = true }, onOpenTask = onOpenTask) } }
    }

    if (renaming) {
        NameDialog("Tu nombre", profile.name, "Guardar",
            onConfirm = { profile.rename(it); renaming = false }, onDismiss = { renaming = false })
    }
    if (adding) {
        NameDialog("Nueva tarea para hoy", "", "Crear",
            onConfirm = { title ->
                planner.addTask(title, planner.defaultList()) { it.myDay = planner.today() }
                adding = false
            },
            onDismiss = { adding = false })
    }
}

@Composable
private fun HomeCard(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .shadow(3.dp, RoundedCornerShape(20.dp), ambientColor = Pastel.Accent, spotColor = Pastel.Accent)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .border(1.dp, Pastel.Line, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) { content() }
}

@Composable
private fun MiniMonth(planner: Planner, onOpenDay: (LocalDate) -> Unit) {
    val today = planner.today()
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Arrow("‹") { month = month.minusMonths(1) }
            Text(
                Fmt.capitalize(month.month.getDisplayName(TextStyle.FULL_STANDALONE, Fmt.ES)) + " " + month.year,
                fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Pastel.Text, textAlign = TextAlign.Center,
                // Tapping the month name goes back to the current month.
                modifier = Modifier.width(170.dp).clickable { month = YearMonth.from(today) },
            )
            Arrow("›") { month = month.plusMonths(1) }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            for (c in 0 until 7) {
                val name = DayOfWeek.MONDAY.plus(c.toLong()).getDisplayName(TextStyle.SHORT_STANDALONE, Fmt.ES)
                Text(Fmt.capitalize(name.replace(".", "")).take(2), Modifier.weight(1f), textAlign = TextAlign.Center,
                    fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Pastel.Text)
            }
        }
        Spacer(Modifier.height(4.dp))
        val first = month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        for (week in 0 until 6) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val day = first.plusDays((week * 7 + c).toLong())
                    DayCell(day, YearMonth.from(day) == month, day == today,
                        busy = day != today && planner.agendaFor(day).isNotEmpty(),
                        modifier = Modifier.weight(1f)) { onOpenDay(day) }
                }
            }
        }
    }
}

@Composable
private fun Arrow(text: String, onClick: () -> Unit) {
    Text(text, fontSize = 22.sp, color = Pastel.Muted, textAlign = TextAlign.Center,
        modifier = Modifier.clip(CircleShape).clickable(onClick = onClick).size(36.dp).padding(top = 2.dp))
}

@Composable
private fun DayCell(day: LocalDate, inMonth: Boolean, isToday: Boolean, busy: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(if (isToday) Pastel.Accent else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                day.dayOfMonth.toString(),
                fontSize = 14.sp,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    isToday -> Color.White
                    inMonth -> Pastel.Text
                    else -> Pastel.Faint
                },
            )
        }
        // A dot under the days that have something on the calendar.
        Box(Modifier.size(5.dp).clip(CircleShape).background(if (busy) Pastel.Accent else Color.Transparent))
    }
}

@Composable
private fun WeekTasks(planner: Planner, onAdd: () -> Unit, onOpenTask: (Task) -> Unit) {
    val today = planner.today()
    val tasks = planner.upcomingTasks(DAYS_AHEAD)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(24.dp).clip(CircleShape).background(Pastel.Accent), contentAlignment = Alignment.Center) {
                Text("✓", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            Text("Mis tareas", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Pastel.Text, modifier = Modifier.weight(1f))
            Text("+  Nueva tarea", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Pastel.Accent)
                    .clickable(onClick = onAdd)
                    .padding(horizontal = 14.dp, vertical = 7.dp))
        }
        Spacer(Modifier.height(8.dp))
        if (tasks.isEmpty()) {
            Text("Nada pendiente para esta semana.", color = Pastel.Muted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
        }
        tasks.forEach { task ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onOpenTask(task) }
                    .padding(horizontal = 4.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundCheck(task.isCompleted, { planner.setCompleted(task, !task.isCompleted) }, size = 24, ring = Pastel.Accent)
                Spacer(Modifier.width(12.dp))
                Text(
                    task.title,
                    color = if (task.isCompleted) Pastel.Faint else Pastel.Text,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f),
                )
                if (!task.isCompleted) Badge(planner.planDay(task), today)
            }
            HorizontalDivider(color = Pastel.Line)
        }
    }
}

@Composable
private fun Badge(day: LocalDate, today: LocalDate) {
    val (text, fill, ink) = when {
        day.isBefore(today) -> Triple("Venció " + Fmt.DAY_MONTH.format(day), Color(0xFFFBE8EC), Pastel.Danger)
        day == today -> Triple("Hoy", Color(0xFFFDE4EC), Color(0xFFB54D72))
        day == today.plusDays(1) -> Triple("Mañana", Color(0xFFDCF3E7), Color(0xFF3F8A63))
        else -> Triple(Fmt.capitalize(Fmt.SHORT_DATE.format(day)), Pastel.Chip, Pastel.AccentInk)
    }
    Spacer(Modifier.width(8.dp))
    Text(text, color = ink, fontSize = 11.sp, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(7.dp)).background(fill).padding(horizontal = 9.dp, vertical = 3.dp))
}
