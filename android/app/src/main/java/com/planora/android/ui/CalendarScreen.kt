package com.planora.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.planora.model.CalendarEvent
import com.planora.model.Task
import com.planora.service.AgendaItem
import com.planora.service.Planner
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters

/** Month grid (Monday first) with colored dots, and the agenda of the selected day below it. */
@Composable
fun CalendarScreen(planner: Planner, selected: LocalDate, onSelect: (LocalDate) -> Unit, onOpenTask: (Task) -> Unit) {
    observePlanner()
    var month by remember { mutableStateOf(YearMonth.from(selected)) }
    // Follow the selected day, e.g. when the widget opens a day of another month.
    LaunchedEffect(selected) { month = YearMonth.from(selected) }
    var editing by remember { mutableStateOf<CalendarEvent?>(null) }
    var isNew by remember { mutableStateOf(false) }
    val today = planner.today()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 18.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(Fmt.capitalize(Fmt.MONTH_YEAR.format(month)), fontSize = 22.sp, fontWeight = FontWeight.Bold,
                    color = Pastel.Text, modifier = Modifier.weight(1f))
                Pill("Hoy", false) { month = YearMonth.from(today); onSelect(today) }
                RoundButton("‹") { month = month.minusMonths(1) }
                RoundButton("›") { month = month.plusMonths(1) }
            }
            MonthGrid(planner, month, selected, today, onSelect)
            Text(Fmt.capitalize(Fmt.LONG_DATE.format(selected)), fontWeight = FontWeight.Bold, color = Pastel.Text,
                modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 6.dp))
            val items = planner.agendaFor(selected)
            LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (items.isEmpty()) {
                    item { Text("Nada en el calendario este día.", color = Pastel.Muted, fontSize = 13.sp, modifier = Modifier.padding(4.dp)) }
                }
                items(items) { item ->
                    AgendaRow(item, selected == today) {
                        if (item.isTask) onOpenTask(item.task()) else { isNew = false; editing = item.event() }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = {
                val start = selected.atTime(LocalTime.of(minOf(planner.now().hour + 1, 23), 0))
                editing = CalendarEvent().apply { this.start = start; end = start.plusHours(1) }
                isNew = true
            },
            containerColor = Pastel.Accent,
            contentColor = Color.White,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        ) { Text("+", fontSize = 26.sp) }
    }

    editing?.let { event ->
        EventDialog(event, isNew,
            onSave = {
                if (isNew) planner.addEvent(event) else planner.changed()
                editing = null
            },
            onDelete = { planner.deleteEvent(event); editing = null },
            onDismiss = { editing = null })
    }
}

@Composable
private fun MonthGrid(planner: Planner, month: YearMonth, selected: LocalDate, today: LocalDate, onSelect: (LocalDate) -> Unit) {
    val first = month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    Column(Modifier.padding(horizontal = 10.dp)) {
        Row {
            for (i in 0 until 7) {
                Text(Fmt.capitalize(DayOfWeek.MONDAY.plus(i.toLong()).getDisplayName(TextStyle.SHORT, Fmt.ES)).take(3),
                    color = Pastel.Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).padding(vertical = 6.dp))
            }
        }
        for (week in 0 until 6) {
            Row {
                for (d in 0 until 7) {
                    val day = first.plusDays((week * 7 + d).toLong())
                    DayCell(day, YearMonth.from(day) == month, day == today, day == selected,
                        planner.agendaFor(day).map { Pastel.bar(it.color()) }, Modifier.weight(1f)) { onSelect(day) }
                }
            }
        }
    }
}

@Composable
private fun DayCell(day: LocalDate, inMonth: Boolean, isToday: Boolean, isSelected: Boolean, dots: List<Color>,
                    modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected && !isToday) Pastel.AccentSoft else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(30.dp).clip(CircleShape).background(if (isToday) Pastel.Accent else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Text(day.dayOfMonth.toString(), fontSize = 14.sp,
                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    isToday -> Color.White
                    !inMonth -> Pastel.Faint
                    isSelected -> Pastel.AccentInk
                    else -> Pastel.Text
                })
        }
        Spacer(Modifier.height(3.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            dots.take(3).forEach { Dot(it, size = 6) }
        }
    }
}

@Composable
private fun AgendaRow(item: AgendaItem, isToday: Boolean, onClick: () -> Unit) {
    val time = when {
        !item.allDay() -> Fmt.TIME.format(item.start()) + " – " + Fmt.TIME.format(item.end())
        item.isTask -> if (isToday) "Vence hoy" else "Vence este día"
        else -> "Todo el día"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Pastel.fill(item.color()))
            .clickable(onClick = onClick)
            .padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(5.dp).height(48.dp).background(Pastel.bar(item.color())))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(time, color = Pastel.ink(item.color()).copy(alpha = 0.7f), fontSize = 12.sp)
            Text((if (item.isTask) (if (item.completed()) "✓ " else "☐ ") else "") + item.title(),
                color = Pastel.ink(item.color()), fontWeight = FontWeight.Bold, fontSize = 14.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun RoundButton(text: String, onClick: () -> Unit) {
    Box(Modifier.size(38.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 22.sp, color = Pastel.Muted)
    }
}

/** Creates or edits an event. The event is only modified when the user saves. */
@Composable
private fun EventDialog(event: CalendarEvent, isNew: Boolean, onSave: () -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf(event.title ?: "") }
    var allDay by remember { mutableStateOf(event.isAllDay) }
    var start by remember { mutableStateOf(if (event.isAllDay) LocalTime.of(9, 0) else event.start.toLocalTime()) }
    var end by remember { mutableStateOf(if (event.isAllDay) LocalTime.of(10, 0) else event.end.toLocalTime()) }
    var color by remember { mutableStateOf(event.color) }
    var location by remember { mutableStateOf(event.location ?: "") }
    var picking by remember { mutableStateOf<String?>(null) } // "start" or "end"
    val day = event.start.toLocalDate()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(24.dp),
        title = { Text(if (isNew) "Nuevo evento" else "Editar evento", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(title, { title = it }, placeholder = { Text("Agregar un título") }, singleLine = true,
                    shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
                Text(Fmt.capitalize(Fmt.LONG_DATE.format(day)), color = Pastel.Muted, fontSize = 13.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Todo el día", modifier = Modifier.weight(1f))
                    Switch(allDay, { allDay = it }, colors = SwitchDefaults.colors(checkedTrackColor = Pastel.Accent))
                }
                if (!allDay) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill(Fmt.TIME.format(start), true) { picking = "start" }
                        Text("–", color = Pastel.Muted)
                        Pill(Fmt.TIME.format(end), true) { picking = "end" }
                    }
                }
                // Eight swatches must fit the narrowest dialog, so they share the width.
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    Planner.COLORS.forEach { c ->
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(CircleShape)
                                .background(Pastel.bar(c))
                                .border(2.dp, if (c.equals(color, ignoreCase = true)) Pastel.Text else Color.Transparent, CircleShape)
                                .clickable { color = c },
                            contentAlignment = Alignment.Center,
                        ) { if (c.equals(color, ignoreCase = true)) Text("✓", color = Color.White, fontWeight = FontWeight.Bold) }
                    }
                }
                OutlinedTextField(location, { location = it }, placeholder = { Text("Ubicación") }, singleLine = true,
                    shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank(), onClick = {
                event.title = title.trim()
                event.isAllDay = allDay
                if (allDay) {
                    event.start = day.atStartOfDay()
                    event.end = day.plusDays(1).atStartOfDay()
                } else {
                    event.start = day.atTime(start)
                    // An end before the start means the event runs past midnight.
                    event.end = if (end.isAfter(start)) day.atTime(end) else day.plusDays(1).atTime(end)
                }
                event.color = color
                event.location = location.trim()
                onSave()
            }) { Text("Guardar", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = onDelete) { Text("Eliminar", color = Pastel.Danger) }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )

    picking?.let { which ->
        TimeDialog(if (which == "start") start else end, onPick = {
            if (which == "start") {
                // Keep the length of the event when moving its start.
                val length = java.time.Duration.between(start, end).let { if (it.isNegative || it.isZero) java.time.Duration.ofHours(1) else it }
                start = it
                end = it.plus(length)
            } else {
                end = it
            }
            picking = null
        }, onDismiss = { picking = null })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(initial: LocalTime, onPick: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initial.hour, initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(24.dp),
        text = { TimePicker(state) },
        confirmButton = { TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)) }) { Text("Listo") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
