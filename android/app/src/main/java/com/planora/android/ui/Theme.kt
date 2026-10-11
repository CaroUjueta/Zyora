package com.planora.android.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import android.graphics.Color as AndroidColor

/** The same pastel palette as the desktop app (planora.css). */
object Pastel {
    val Accent = Color(0xFF8F7BD6)
    val AccentSoft = Color(0xFFEEE8FB)
    val AccentInk = Color(0xFF5B4A9E)
    val Danger = Color(0xFFD9667A)
    val Text = Color(0xFF3B3550)
    val Muted = Color(0xFF8C859E)
    val Faint = Color(0xFFB9B3C8)
    val Line = Color(0xFFEEEAF3)
    val Surface = Color(0xFFFFFDFB)
    val SurfaceAlt = Color(0xFFF8F5FC)
    val Chip = Color(0xFFF1EDF8)
    val Star = Color(0xFFE88BB0)
    val Peach = Color(0xFFFFF1E6)
    val PeachInk = Color(0xFFC46A3A)
    val Pink = Color(0xFFF6C9D9)
    val Sky = Color(0xFFCFE2F7)
    val Mint = Color(0xFFC8EBD9)

    // Shades derived from a stored list/event color, keeping only its hue (see Palette.java).
    fun fill(hex: String?) = shade(hex, 0.20f, 0.98f)
    fun banner(hex: String?) = shade(hex, 0.28f, 0.96f)
    fun bar(hex: String?) = shade(hex, 0.42f, 0.88f)
    fun ink(hex: String?) = shade(hex, 0.55f, 0.38f)

    private fun shade(hex: String?, saturation: Float, value: Float): Color {
        val argb = runCatching { AndroidColor.parseColor(hex) }.getOrDefault(0xFFA78BDB.toInt())
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(argb, hsv)
        val s = if (hsv[1] < 0.08f) 0f else saturation
        return Color(AndroidColor.HSVToColor(floatArrayOf(hsv[0], s, value)))
    }
}

@Composable
fun PlanoraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Pastel.Accent,
            onPrimary = Color.White,
            primaryContainer = Pastel.AccentSoft,
            onPrimaryContainer = Pastel.AccentInk,
            secondaryContainer = Pastel.AccentSoft,
            onSecondaryContainer = Pastel.AccentInk,
            background = Pastel.Surface,
            onBackground = Pastel.Text,
            surface = Pastel.Surface,
            onSurface = Pastel.Text,
            onSurfaceVariant = Pastel.Muted,
            surfaceContainerLow = Pastel.SurfaceAlt,
            surfaceContainer = Pastel.SurfaceAlt,
            surfaceContainerHigh = Color.White,
            surfaceContainerHighest = Pastel.Chip,
            outline = Pastel.Faint,
            outlineVariant = Pastel.Line,
            error = Pastel.Danger,
        ),
        content = content,
    )
}
