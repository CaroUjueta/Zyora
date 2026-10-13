package com.planora.android.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.planora.model.TaskList
import com.planora.service.Planner
import com.planora.service.SmartList

/** Where the "Listas" tab can go: a smart list or one of the user's lists. */
sealed interface ListDest {
    data class Smart(val smart: SmartList) : ListDest
    data class User(val id: String) : ListDest
}

/** Smart lists on top, then the user's lists as a tree; long press a list for more options. */
@Composable
fun ListsScreen(planner: Planner, onOpen: (ListDest) -> Unit) {
    observePlanner()
    // Ids of the lists whose sublists are hidden.
    val collapsed = rememberSaveable(
        saver = listSaver<SnapshotStateList<String>, String>(save = { it.toList() }, restore = { it.toMutableStateList() })
    ) { mutableStateListOf() }
    var newList by remember { mutableStateOf<TaskList?>(null) }
    var creatingRoot by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<TaskList?>(null) }
    var recoloring by remember { mutableStateOf<TaskList?>(null) }
    var deleting by remember { mutableStateOf<TaskList?>(null) }
    val visible = planner.listTree().filter { list ->
        planner.pathOf(list).dropLast(1).none { it.id in collapsed }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp)) {
        item {
            Text("Zyora", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Pastel.AccentInk,
                modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 12.dp))
        }
        items(listOf(SmartList.IMPORTANT, SmartList.PLANNED, SmartList.ALL)) { smart ->
            NavRow(iconRes = smart.iconRes(), name = smart.label(),
                count = planner.tasksFor(smart).count { !it.isCompleted }, onClick = { onOpen(ListDest.Smart(smart)) })
        }
        item { HorizontalDivider(color = Pastel.Line, modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) }
        items(visible, key = { it.id }) { list ->
            var menu by remember { mutableStateOf(false) }
            val depth = planner.pathOf(list).size - 1
            val hasChildren = planner.childrenOf(list).isNotEmpty()
            Box {
                NavRow(
                    dotColor = Pastel.bar(list.color),
                    name = list.name,
                    count = planner.tasksInList(list).count { !it.isCompleted },
                    indent = depth,
                    arrow = if (!hasChildren) null else if (list.id in collapsed) "▸" else "▾",
                    onArrow = { if (!collapsed.remove(list.id)) collapsed.add(list.id) },
                    onClick = { onOpen(ListDest.User(list.id)) },
                    onLongClick = { menu = true },
                )
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Color.White) {
                    DropdownMenuItem(text = { Text("Nueva sublista") }, onClick = { menu = false; newList = list })
                    DropdownMenuItem(text = { Text("Renombrar") }, onClick = { menu = false; renaming = list })
                    DropdownMenuItem(text = { Text("Cambiar color") }, onClick = { menu = false; recoloring = list })
                    DropdownMenuItem(text = { Text("Eliminar lista", color = Pastel.Danger) }, onClick = { menu = false; deleting = list })
                }
            }
        }
        item {
            Text("+ Nueva lista", color = Pastel.Accent, fontSize = 15.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { creatingRoot = true }
                    .padding(14.dp))
            Text("Mantén presionada una lista para crear sublistas, renombrarla, cambiar su color o eliminarla.",
                color = Pastel.Faint, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 14.dp))
        }
        item {
            HorizontalDivider(color = Pastel.Line, modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp))
            SyncRow()
        }
    }

    if (creatingRoot) {
        NameDialog("Nueva lista", "", "Crear", onConfirm = { planner.addList(it); creatingRoot = false }, onDismiss = { creatingRoot = false })
    }
    newList?.let { parent ->
        NameDialog("Nueva sublista en «${parent.name}»", "", "Crear",
            onConfirm = {
                collapsed.remove(parent.id)
                planner.addList(it, parent)
                newList = null
            },
            onDismiss = { newList = null })
    }
    renaming?.let { list ->
        NameDialog("Renombrar lista", list.name, "Guardar",
            onConfirm = { planner.renameList(list, it); renaming = null }, onDismiss = { renaming = null })
    }
    recoloring?.let { list ->
        ColorDialog("Color de «${list.name}»", list.color,
            onConfirm = { planner.setListColor(list, it); recoloring = null }, onDismiss = { recoloring = null })
    }
    deleting?.let { list ->
        val doomed = planner.subtree(list)
        if (doomed.size >= planner.lists().size) {
            ConfirmDialog("No se puede eliminar", "Debe existir al menos una lista fuera de «${list.name}».", "Entendido",
                onConfirm = { deleting = null }, onDismiss = { deleting = null })
        } else {
            val tasks = planner.tasksInList(list).size
            val subs = doomed.size - 1
            val text = "Se eliminarán " + (if (tasks == 1) "1 tarea" else "$tasks tareas") +
                (if (subs == 0) "" else if (subs == 1) " y 1 sublista" else " y $subs sublistas") +
                ". Esta acción no se puede deshacer."
            ConfirmDialog("¿Eliminar «${list.name}»?", text, "Eliminar",
                onConfirm = { planner.deleteList(list); deleting = null }, onDismiss = { deleting = null })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NavRow(
    name: String,
    count: Int,
    onClick: () -> Unit,
    indent: Int = 0,
    arrow: String? = null,
    onArrow: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    /** A line icon for the smart lists, or a dot in the list's color for the user's lists. */
    @DrawableRes iconRes: Int? = null,
    dotColor: Color = Pastel.Accent,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = (8 + indent * 20).dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(arrow ?: "", color = Pastel.Muted, fontSize = 12.sp,
            modifier = Modifier.width(18.dp).clickable(enabled = arrow != null, onClick = onArrow))
        Box(Modifier.width(26.dp), contentAlignment = Alignment.Center) {
            if (iconRes != null) NavIcon(iconRes) else Dot(dotColor)
        }
        Spacer(Modifier.width(12.dp))
        Text(name, color = Pastel.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        if (count > 0) {
            Text(count.toString(), color = Pastel.AccentInk, fontSize = 12.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Pastel.AccentSoft)
                    .padding(horizontal = 8.dp, vertical = 2.dp))
        }
    }
}
