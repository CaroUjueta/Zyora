package com.zyora.android

import android.app.Application
import androidx.glance.appwidget.updateAll
import com.zyora.android.sync.PhoneSync
import com.zyora.android.widget.MonthWidget
import com.zyora.service.JsonPlannerStore
import com.zyora.service.Planner
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
class ZyoraApplication : Application() {
    lateinit var planner: Planner
        private set

    private val changes = MutableStateFlow(0)

    /** Bumped after every change, so screens and the widget know when to read the planner again. */
    val version: StateFlow<Int> = changes

    private val scope = MainScope()

    /** The user's name for the greeting on Inicio. */
    lateinit var profile: Profile
        private set

    /** Sync with the computer over Wi‑Fi. */
    lateinit var sync: PhoneSync
        private set

    override fun onCreate() {
        super.onCreate()
        Locale.setDefault(Locale.forLanguageTag("es"))
        planner = Planner(JsonPlannerStore(File(filesDir, "zyora.json").toPath()))
        planner.addListener {
            changes.value++
            scope.launch { MonthWidget().updateAll(this@ZyoraApplication) }
        }
        sync = PhoneSync(this, planner, scope)
        profile = Profile(this)
    }
}
