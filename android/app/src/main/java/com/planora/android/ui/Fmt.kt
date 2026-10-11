package com.planora.android.ui

import com.planora.model.TaskList
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Spanish date and time formatting, like the desktop app's Fmt. */
object Fmt {
    val ES: Locale = Locale.forLanguageTag("es")
    val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    val LONG_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", ES)
    val SHORT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", ES)
    val MONTH_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", ES)

    fun capitalize(s: String) = s.replaceFirstChar { it.titlecase(ES) }

    /** List names joined as a breadcrumb, e.g. "Universidad › Cálculo". */
    fun path(lists: List<TaskList>) = lists.joinToString(" › ") { it.name }
}
