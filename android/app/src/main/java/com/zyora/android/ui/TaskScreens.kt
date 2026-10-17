package com.zyora.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zyora.model.Step
import com.zyora.model.Task
import com.zyora.service.Planner
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** A banner, a field to add tasks, the pending tasks and the completed ones. */
@Composable
fun TaskListScreen(
    planner: Planner,
    title: String,
    subtitle: String,
    banner: Pair<Color, Color>,
    tasks: List<Task>,
    showList: (Task) -> Boolean,
    onAdd: (String) -> Unit,
    onOpen: (Task) -> Unit,
    emptyText: String = "No hay tareas pendientes aquí.",
) {
    val open = tasks.filter { !it.isCompleted }
    val done = tasks.filter { it.isCompleted }.sortedByDescending { it.completedAt }
    var showDone by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Banner(title, subtitle, banner.first, banner.second)
        AddTaskField(onAdd)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (open.isEmpty()) {
                item { Text(emptyText, color = Pastel.Muted, fontSize = 13.sp, modifier = Modifier.padding(8.dp)) }
            }
            items(open, key = { it.id }) { TaskRow(planner, it, showList(it), onOpen) }
            if (done.isNotEmpty()) {
                item {
                    Text(
                        (if (showDone) "▾" else "▸") + "  Completadas (${done.size})",
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Pastel.Chip)
                            .clickable { showDone = !showDone }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        color = Pastel.AccentInk,
                        fontSize = 13.sp,
                    )
                }
                if (showDone) items(done, key = { it.id }) { TaskRow(planner, it, showList(it), onOpen) }
            }
        }
    }
}

@Composable
private fun AddTaskField(onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    fun submit() {
        if (text.isNotBlank()) {
            onAdd(text.trim())
            text = ""
        }
    }
    TextField(
        value = text,
        onValueChange = { text = it },
        placeholder = { Text("+ Agregar una tarea", color = Pastel.Accent) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { submit() }),
        shape = RoundedCornerShape(14.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .shadow(3.dp, RoundedCornerShape(14.dp), ambientColor = Pastel.Accent, spotColor = Pastel.Accent),
    )
}

@Composable
fun TaskRow(planner: Planner, task: Task, showList: Boolean, onOpen: (Task) -> Unit) {
    observePlanner()
    val today = planner.today()
    Row(
        Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(14.dp), ambientColor = Pastel.Accent, spotColor = Pastel.Accent)
            .clip(RoundedCornerShape(14.dp))
            .background(if (task.isCompleted) Color(0xFFFCFBFE) else Color.White)
            .clickable { onOpen(task) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundCheck(task.isCompleted, { planner.setCompleted(task, !task.isCompleted) })
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                task.title,
                color = if (task.isCompleted) Pastel.Faint else Pastel.Text,
                textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                fontSize = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = details(planner, task, today, showList)
            if (meta.isNotEmpty()) {
                Text(meta, color = if (task.isOverdue(today)) Pastel.Danger else Pastel.Muted, fontSize = 12.sp, maxLines = 1)
            }
        }
        Text(
            if (task.isImportant) "★" else "☆",
            color = if (task.isImportant) Pastel.Star else Pastel.Faint,
            fontSize = 20.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable {
                    task.isImportant = !task.isImportant
                    planner.changed()
                }
                .padding(6.dp),
        )
    }
}

private fun details(planner: Planner, task: Task, today: LocalDate, showList: Boolean): String {
    val parts = mutableListOf<String>()
    if (showList) parts += planner.listOf(task).name
    if (task.isInMyDay(today)) parts += "☀ Mi día"
    task.scheduledStart?.let { parts += "◷ " + Fmt.SHORT_DATE.format(it) + ", " + Fmt.TIME.format(it) }
    task.dueDate?.let {
        parts += when {
            task.isOverdue(today) -> "Venció " + Fmt.SHORT_DATE.format(it)
            it == today -> "Vence hoy"
            else -> "Vence " + Fmt.SHORT_DATE.format(it)
        }
    }
    if (task.steps.isNotEmpty()) parts += "${task.steps.count { it.isDone }} de ${task.steps.size}"
    if (!task.notes.isNullOrBlank()) parts += "✎ Nota"
    return parts.joinToString("  ·  ")
}

/** Edits one task; text fields are saved when the sheet closes, everything else right away. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskSheet(planner: Planner, task: Task, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember(task.id) { mutableStateOf(task.title) }
    var notes by remember(task.id) { mutableStateOf(task.notes ?: "") }
    var newStep by remember { mutableStateOf("") }
    var pickDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val today = planner.today()

    fun saveText() {
        if (planner.findTask(task.id).isEmpty) return
        val t = title.replace('\n', ' ').trim()
        if ((t.isNotEmpty() && t != task.title) || notes != (task.notes ?: "")) {
            if (t.isNotEmpty()) task.title = t
            task.notes = notes
            planner.changed()
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            saveText()
            onDismiss()
        },
        sheetState = sheet,
        containerColor = Pastel.SurfaceAlt,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            observePlanner()
            Text(Fmt.path(planner.pathOf(planner.listOf(task))), color = Pastel.AccentInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)

            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RoundCheck(task.isCompleted, { planner.setCompleted(task, !task.isCompleted) }, size = 24)
                    TextField(
                        value = title,
                        onValueChange = { title = it },
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Pastel.Text),
                        colors = transparentField(),
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        if (task.isImportant) "★" else "☆",
                        color = Pastel.Star,
                        fontSize = 22.sp,
                        modifier = Modifier.clickable {
                            task.isImportant = !task.isImportant
                            planner.changed()
                        },
                    )
                }
                task.steps.toList().forEach { step ->
                    HorizontalDivider(color = Pastel.Line)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                        RoundCheck(step.isDone, {
                            step.isDone = !step.isDone
                            planner.changed()
                        }, size = 18)
                        Spacer(Modifier.width(10.dp))
                        Text(step.title, color = if (step.isDone) Pastel.Faint else Pastel.Text, modifier = Modifier.weight(1f))
                        Text("✕", color = Pastel.Muted, modifier = Modifier.clickable {
                            task.steps.remove(step)
                            planner.changed()
                        }.padding(4.dp))
                    }
                }
                TextField(
                    value = newStep,
                    onValueChange = { newStep = it },
                    placeholder = { Text("+ Agregar paso", color = Pastel.Accent) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (newStep.isNotBlank()) {
                            task.steps.add(Step(newStep.trim()))
                            newStep = ""
                            planner.changed()
                        }
                    }),
                    colors = transparentField(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Card {
                val inMyDay = today == task.myDay
                Text(
                    if (inMyDay) "☀  En Mi día" else "☀  Agregar a Mi día",
                    color = if (inMyDay) Pastel.PeachInk else Pastel.Text,
                    fontWeight = if (inMyDay) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (inMyDay) Pastel.Peach else Color.Transparent)
                        .clickable {
                            task.myDay = if (inMyDay) null else today
                            planner.changed()
                        }
                        .padding(10.dp),
                )
                SectionLabel("⚑", "Fecha de vencimiento")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pill(task.dueDate?.let { Fmt.capitalize(Fmt.SHORT_DATE.format(it)) } ?: "Sin fecha", task.dueDate != null) { pickDate = true }
                    if (task.dueDate != null) {
                        Spacer(Modifier.width(8.dp))
                        Text("✕", color = Pastel.Muted, modifier = Modifier.clickable {
                            task.dueDate = null
                            planner.changed()
                        }.padding(6.dp))
                    }
                }
                if (task.isScheduled) {
                    SectionLabel("▦", "Bloque en el calendario")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            Fmt.capitalize(Fmt.SHORT_DATE.format(task.scheduledStart)) + ", " +
                                Fmt.TIME.format(task.scheduledStart) + " – " + Fmt.TIME.format(task.scheduledEnd),
                            color = Pastel.Text,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { planner.unscheduleTask(task) }) { Text("Quitar") }
                    }
                }
            }

            Card {
                SectionLabel("✎", "Notas")
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    placeholder = { Text("Agregar una nota", color = Pastel.Faint) },
                    shape = RoundedCornerShape(12.dp),
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
                Text("Creada el " + Fmt.SHORT_DATE.format(task.createdAt), color = Pastel.Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { confirmDelete = true }) { Text("Eliminar tarea", color = Pastel.Danger) }
            }
        }
    }

    if (pickDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (task.dueDate ?: today).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        task.dueDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                        planner.changed()
                    }
                    pickDate = false
                }) { Text("Listo") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Cancelar") } },
        ) { DatePicker(state, showModeToggle = false) }
    }
    if (confirmDelete) {
        ConfirmDialog("¿Eliminar tarea?", "Se eliminará «${task.title}». Esta acción no se puede deshacer.", "Eliminar",
            onConfirm = {
                confirmDelete = false
                planner.deleteTask(task)
                onDismiss()
            },
            onDismiss = { confirmDelete = false })
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) { content() }
}

@Composable
private fun transparentField() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
)
