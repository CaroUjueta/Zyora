package com.zyora.android

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Who uses the app on this phone, for the greeting on Inicio. Not synced with the computer. */
class Profile(context: Context) {
    private val prefs = context.getSharedPreferences("profile", Context.MODE_PRIVATE)

    var name by mutableStateOf(prefs.getString("name", "").orEmpty().trim())
        private set

    val hasName get() = name.isNotEmpty()

    fun rename(value: String) {
        name = value.trim()
        prefs.edit().putString("name", name).apply()
    }
}
