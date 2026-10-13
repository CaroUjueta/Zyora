package com.planora.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.annotation.DrawableRes
import com.planora.android.ui.CalendarScreen
import com.planora.android.ui.HomeScreen
import com.planora.android.ui.NameDialog
import com.planora.android.ui.NavIcon
import com.planora.android.ui.Fmt
import com.planora.android.ui.ListDest
import com.planora.android.ui.ListsScreen
import com.planora.android.ui.LocalPlannerVersion
import com.planora.android.ui.Pastel
import com.planora.android.ui.PlanoraTheme
import com.planora.android.ui.TaskListScreen
import com.planora.android.ui.TaskSheet
import com.planora.android.ui.observePlanner
import com.planora.model.Task
import com.planora.service.Planner
import com.planora.service.SmartList
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    /** A day the widget asked to open; the calendar tab shows it and clears it. */
    private val openDay = mutableStateOf<LocalDate?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Light background everywhere, so the status bar needs dark icons.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.Transparent.toArgb(), Color.Transparent.toArgb()),
            navigationBarStyle = SystemBarStyle.light(Color.Transparent.toArgb(), Color.Transparent.toArgb()),
        )
        openDay.value = dayFrom(intent)
        val app = application as PlanoraApplication
        setContent {
            val version by app.version.collectAsState()
            CompositionLocalProvider(LocalPlannerVersion provides version) {
                PlanoraTheme { PlanoraRoot(app.planner, app.profile, openDay) }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        (application as PlanoraApplication).sync.syncNow()
    }

    override fun onStop() {
        super.onStop()
        (application as PlanoraApplication).sync.syncIfChanged()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openDay.value = dayFrom(intent)
    }

    companion object {
        /** Opens the calendar on [day] (or just the app when null). Used by the widget. */
        fun openIntent(context: Context, day: LocalDate?): Intent =
            Intent(context, MainActivity::class.java)
                .setData(Uri.parse("planora://day/" + (day ?: "")))
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        private fun dayFrom(intent: Intent?): LocalDate? =
            intent?.data?.takeIf { it.scheme == "planora" && it.host == "day" }
                ?.lastPathSegment
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    }
}

private enum class Tab(val label: String, @DrawableRes val icon: Int) {
    HOME("Inicio", R.drawable.ic_nav_home),
    MY_DAY("Mi día", R.drawable.ic_nav_my_day),
    LISTS("Listas", R.drawable.ic_nav_lists),
    CALENDAR("Calendario", R.drawable.ic_nav_calendar),
}

@Composable
private fun PlanoraRoot(planner: Planner, profile: Profile, openDay: MutableState<LocalDate?>) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    // Asked once, on the first run; cancelling leaves it for the next time the app opens.
    var askName by rememberSaveable { mutableStateOf(!profile.hasName) }
    var listDest by remember { mutableStateOf<ListDest?>(null) }
    var calendarDay by remember { mutableStateOf(planner.today()) }
    var openTask by remember { mutableStateOf<Task?>(null) }

    LaunchedEffect(openDay.value) {
        openDay.value?.let {
            tab = Tab.CALENDAR
            calendarDay = it
            openDay.value = null
        }
    }
    BackHandler(enabled = tab == Tab.LISTS && listDest != null) { listDest = null }

    Scaffold(
        containerColor = Pastel.Surface,
        bottomBar = {
            NavigationBar(containerColor = Pastel.SurfaceAlt) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = {
                            if (t == Tab.LISTS && tab == Tab.LISTS) listDest = null
                            tab = t
                        },
                        icon = { NavIcon(t.icon, tint = if (tab == t) Pastel.Icon else Pastel.Muted) },
                        label = { Text(t.label) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = Pastel.AccentSoft,
                            selectedIconColor = Pastel.AccentInk,
                            selectedTextColor = Pastel.AccentInk,
                            unselectedIconColor = Pastel.Muted,
                            unselectedTextColor = Pastel.Muted,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                Tab.HOME -> HomeScreen(planner, profile, onOpenDay = {
                    calendarDay = it
                    tab = Tab.CALENDAR
                }) { openTask = it }
                Tab.MY_DAY -> SmartListScreen(planner, SmartList.MY_DAY) { openTask = it }
                Tab.LISTS -> when (val dest = listDest) {
                    null -> ListsScreen(planner) { listDest = it }
                    is ListDest.Smart -> SmartListScreen(planner, dest.smart) { openTask = it }
                    is ListDest.User -> UserListScreen(planner, dest.id, onGone = { listDest = null }) { openTask = it }
                }
                Tab.CALENDAR -> CalendarScreen(planner, calendarDay, { calendarDay = it }) { openTask = it }
            }
        }
    }

    openTask?.let { TaskSheet(planner, it) { openTask = null } }
    if (askName) {
        NameDialog("Te damos la bienvenida a Zyora. ¿Cómo te llamas?", "", "Guardar",
            onConfirm = { profile.rename(it); askName = false }, onDismiss = { askName = false })
    }
}

@Composable
private fun SmartListScreen(planner: Planner, smart: SmartList, onOpen: (Task) -> Unit) {
    observePlanner()
    val today = planner.today()
    val tasks = planner.tasksFor(smart)
    val myDay = smart == SmartList.MY_DAY
    val pending = tasks.count { !it.isCompleted }
    TaskListScreen(
        planner = planner,
        title = smart.label(),
        subtitle = if (myDay) Fmt.capitalize(Fmt.LONG_DATE.format(today)) else pendingText(pending),
        banner = when (smart) {
            SmartList.MY_DAY -> Pastel.Peach to Pastel.Pink
            SmartList.IMPORTANT -> Color(0xFFFDEEF3) to Color(0xFFF3C6E0)
            SmartList.PLANNED -> Color(0xFFECF4FD) to Pastel.Sky
            SmartList.ALL -> Color(0xFFEDF8F2) to Pastel.Mint
        },
        tasks = tasks,
        showList = { true },
        emptyText = if (myDay) "Concéntrate en tu día: agrega tareas para hoy." else "No hay tareas pendientes aquí.",
        onAdd = { title ->
            planner.addTask(title, planner.defaultList()) { t ->
                when (smart) {
                    SmartList.MY_DAY -> t.myDay = today
                    SmartList.IMPORTANT -> t.isImportant = true
                    SmartList.PLANNED -> t.dueDate = today
                    SmartList.ALL -> {}
                }
            }
        },
        onOpen = onOpen,
    )
}

@Composable
private fun UserListScreen(planner: Planner, id: String, onGone: () -> Unit, onOpen: (Task) -> Unit) {
    observePlanner()
    val list = planner.findList(id).orElse(null)
    if (list == null) {
        LaunchedEffect(Unit) { onGone() }
        return
    }
    val tasks = planner.tasksInList(list)
    val path = planner.pathOf(list)
    val pending = pendingText(tasks.count { !it.isCompleted })
    TaskListScreen(
        planner = planner,
        title = list.name,
        subtitle = if (path.size > 1) Fmt.path(path.dropLast(1)) + "  ·  " + pending else pending,
        banner = Pastel.fill(list.color) to Pastel.banner(list.color),
        tasks = tasks,
        // Only tasks from a sublist say where they come from.
        showList = { planner.listOf(it) !== list },
        onAdd = { planner.addTask(it, list) },
        onOpen = onOpen,
    )
}

private fun pendingText(n: Int) = if (n == 1) "1 tarea pendiente" else "$n tareas pendientes"
