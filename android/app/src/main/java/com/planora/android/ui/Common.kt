package com.planora.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.planora.service.Planner

/**
 * Tasks, lists and events are plain Java objects edited in place, so Compose cannot see when they
 * change. The root provides the planner's change counter here; composables that show those objects
 * call [observePlanner] so they redraw after every change.
 */
val LocalPlannerVersion = compositionLocalOf { 0 }

@Composable
fun observePlanner(): Int = LocalPlannerVersion.current

/** Round checkbox, like Microsoft To Do and Things. */
@Composable
fun RoundCheck(checked: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier, size: Int = 22) {
    Box(
        modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(if (checked) Pastel.Accent else Color.White)
            .border(1.5.dp, if (checked) Pastel.Accent else Pastel.Faint, CircleShape)
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Text("✓", color = Color.White, fontSize = (size * 0.55).sp, fontWeight = FontWeight.Bold)
    }
}

/** The gradient banner at the top of a task list. */
@Composable
fun Banner(title: String, subtitle: String, from: Color, to: Color) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.horizontalGradient(listOf(from, to)))
            .padding(start = 22.dp, end = 22.dp, top = 26.dp, bottom = 18.dp)
    ) {
        Text(title, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Pastel.Text)
        Spacer(Modifier.height(2.dp))
        Text(subtitle, fontSize = 14.sp, color = Pastel.Text.copy(alpha = 0.75f))
    }
}

@Composable
fun Dot(color: Color, size: Int = 10) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(color))
}

@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Pastel.AccentSoft else Color.White)
            .border(1.dp, if (selected) Pastel.Accent else Pastel.Line, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        color = if (selected) Pastel.AccentInk else Pastel.Muted,
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
    )
}

/** Asks for a single line of text. */
@Composable
fun NameDialog(title: String, initial: String, action: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(22.dp),
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            LaunchedEffect(Unit) { focus.requestFocus() }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim()) }, enabled = text.isNotBlank()) { Text(action) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Asks before a destructive action. */
@Composable
fun ConfirmDialog(title: String, text: String, action: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(22.dp),
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(text, color = Pastel.Muted) },
        confirmButton = {
            TextButton(onClick = onConfirm, colors = ButtonDefaults.textButtonColors(contentColor = Pastel.Danger)) {
                Text(action, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
fun SectionLabel(icon: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Text(icon, color = Pastel.Accent, fontSize = 13.sp)
        Spacer(Modifier.size(6.dp))
        Text(text, color = Pastel.Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

/** The palette as round swatches; eight must fit the narrowest dialog, so they share the width. */
@Composable
fun ColorSwatches(selected: String?, onPick: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        Planner.COLORS.forEach { c ->
            val isSelected = c.equals(selected, ignoreCase = true)
            Box(
                Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .clip(CircleShape)
                    .background(Pastel.bar(c))
                    .border(2.dp, if (isSelected) Pastel.Text else Color.Transparent, CircleShape)
                    .clickable { onPick(c) },
                contentAlignment = Alignment.Center,
            ) { if (isSelected) Text("✓", color = Color.White, fontWeight = FontWeight.Bold) }
        }
    }
}

/** Asks for one of the palette colors. */
@Composable
fun ColorDialog(title: String, initial: String?, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var color by remember { mutableStateOf(initial ?: Planner.COLORS[0]) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(22.dp),
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { ColorSwatches(color) { color = it } },
        confirmButton = { TextButton(onClick = { onConfirm(color) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
