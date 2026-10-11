package com.planora.android

import android.app.Application
import androidx.glance.appwidget.updateAll
import com.planora.android.sync.PhoneSync
import com.planora.android.widget.MonthWidget
import com.planora.service.JsonPlannerStore
import com.planora.service.Planner
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

/**
 * Holds the one [Planner] shared by the app and the widget. The planner is not thread safe, so it
 * is only touched on the main thread.
 */
class PlanoraApplication : Application() {
    lateinit var planner: Planner
        private set

    private val changes = MutableStateFlow(0)

    /** Bumped after every change, so screens and the widget know when to read the planner again. */
    val version: StateFlow<Int> = changes

    private val scope = MainScope()

    /** Sync with the computer over Wi‑Fi. */
    lateinit var sync: PhoneSync
        private set

    override fun onCreate() {
        super.onCreate()
        Locale.setDefault(Locale.forLanguageTag("es"))
        planner = Planner(JsonPlannerStore(File(filesDir, "planora.json").toPath()))
        planner.addListener {
            changes.value++
            scope.launch { MonthWidget().updateAll(this@PlanoraApplication) }
        }
        sync = PhoneSync(this, planner, scope)
    }
}
