package com.zyora.android.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.zyora.android.MainActivity
import com.zyora.android.ZyoraApplication
import com.zyora.android.ui.Fmt
import com.zyora.android.ui.Pastel
import com.zyora.service.Planner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle as JavaTextStyle
import java.time.temporal.TemporalAdjusters

/** How many months away from the current one this widget is showing. */
private val MonthOffset = intPreferencesKey("month_offset")

/** One day of the grid, computed on the main thread (the planner is not thread safe). */
private data class WidgetDay(
    val date: LocalDate,
    val inMonth: Boolean,
    val isToday: Boolean,
    val items: List<Pair<String, String>>, // title to stored color
)

private data class WidgetMonth(val month: YearMonth, val days: List<WidgetDay>)

private fun buildMonth(planner: Planner, offset: Int): WidgetMonth {
    val today = planner.today()
    val month = YearMonth.from(today).plusMonths(offset.toLong())
    val first = month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val days = (0 until 42).map { i ->
        val date = first.plusDays(i.toLong())
        WidgetDay(date, YearMonth.from(date) == month, date == today,
            planner.agendaFor(date).map { it.title() to it.color() })
    }
    return WidgetMonth(month, days)
}

class MonthWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as ZyoraApplication
        provideContent {
            val offset = currentState<Preferences>()[MonthOffset] ?: 0
            val version by app.version.collectAsState()
            val month by produceState<WidgetMonth?>(null, offset, version) {
                value = withContext(Dispatchers.Main) { buildMonth(app.planner, offset) }
            }
            month?.let { MonthContent(it) } ?: Box(GlanceModifier.fillMaxSize().background(Pastel.Surface)) {}
        }
    }
}

class MonthWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MonthWidget()
}

/** Moves the widget a month back or forward; a delta of 0 goes back to the current month. */
class ShiftMonth : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val delta = parameters[Delta] ?: 0
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[MonthOffset] = if (delta == 0) 0 else (prefs[MonthOffset] ?: 0) + delta
        }
        MonthWidget().update(context, glanceId)
    }

    companion object {
        val Delta = ActionParameters.Key<Int>("delta")
    }
}

private fun color(c: Color) = ColorProvider(c)

@Composable
private fun MonthContent(model: WidgetMonth) {
    val context = LocalContext.current
    val size = LocalSize.current
    // Room for small titles when the widget is tall enough; otherwise just colored dots.
    val cellHeight = (size.height.value - 64) / 6
    val showTitles = cellHeight >= 58

    Column(
        GlanceModifier
            .fillMaxSize()
            .cornerRadius(22.dp)
            .background(Pastel.Surface)
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Row(GlanceModifier.fillMaxWidth().padding(start = 6.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                Fmt.capitalize(Fmt.MONTH_YEAR.format(model.month)),
                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color(Pastel.Text)),
                modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity(MainActivity.openIntent(context, null))),
            )
            HeaderButton("Hoy", 0)
            HeaderButton("‹", -1)
            HeaderButton("›", 1)
        }
        Row(GlanceModifier.fillMaxWidth()) {
            for (i in 0 until 7) {
                Text(
                    Fmt.capitalize(DayOfWeek.MONDAY.plus(i.toLong()).getDisplayName(JavaTextStyle.NARROW, Fmt.ES)),
                    style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color(Pastel.Muted), textAlign = TextAlign.Center),
                    modifier = GlanceModifier.defaultWeight().padding(vertical = 2.dp),
                )
            }
        }
        for (week in 0 until 6) {
            Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                for (d in 0 until 7) {
                    DayCell(model.days[week * 7 + d], showTitles, GlanceModifier.defaultWeight().fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun HeaderButton(text: String, delta: Int) {
    Box(
        GlanceModifier
            .height(28.dp)
            .padding(horizontal = 2.dp)
            .cornerRadius(14.dp)
            .background(Pastel.Chip)
            .clickable(actionRunCallback<ShiftMonth>(actionParametersOf(ShiftMonth.Delta to delta))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = TextStyle(fontSize = if (delta == 0) 11.sp else 15.sp, fontWeight = FontWeight.Bold, color = color(Pastel.AccentInk)),
            modifier = GlanceModifier.padding(horizontal = 9.dp),
        )
    }
}

@Composable
private fun DayCell(day: WidgetDay, showTitles: Boolean, modifier: GlanceModifier) {
    val context = LocalContext.current
    Column(
        modifier
            .padding(1.dp)
            .cornerRadius(8.dp)
            .background(if (day.isToday) Pastel.AccentSoft else Color.Transparent)
            .clickable(actionStartActivity(MainActivity.openIntent(context, day.date))),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            GlanceModifier.size(20.dp).cornerRadius(10.dp).background(if (day.isToday) Pastel.Accent else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                day.date.dayOfMonth.toString(),
                style = TextStyle(
                    fontSize = 11.sp,
                    fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    color = color(
                        when {
                            day.isToday -> Color.White
                            day.inMonth -> Pastel.Text
                            else -> Pastel.Faint
                        }
                    ),
                ),
            )
        }
        if (showTitles) {
            day.items.take(2).forEach { (title, c) ->
                Spacer(GlanceModifier.height(1.dp))
                Text(
                    title,
                    maxLines = 1,
                    style = TextStyle(fontSize = 8.sp, color = color(Pastel.ink(c))),
                    modifier = GlanceModifier.fillMaxWidth().cornerRadius(3.dp).background(Pastel.fill(c)).padding(horizontal = 2.dp),
                )
            }
        } else if (day.items.isNotEmpty()) {
            Spacer(GlanceModifier.height(2.dp))
            Row {
                day.items.take(3).forEach { (_, c) ->
                    Box(GlanceModifier.size(5.dp).cornerRadius(3.dp).background(Pastel.bar(c))) {}
                    Spacer(GlanceModifier.width(2.dp))
                }
            }
        }
    }
}
