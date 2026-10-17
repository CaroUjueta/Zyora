package com.zyora.android.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import android.graphics.Color as AndroidColor
import com.zyora.service.Shades

/** The same pastel palette as the desktop app (zyora.css). */
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
    /** The ink of the line icons (ic_nav_*). */
    val Icon = Color(0xFF2827A3)
    val Lilac = Color(0xFFDDD0F5)

    // Tones derived from a stored list/event color, the same as on the computer (see Shades.java).
    fun fill(hex: String?) = Color(AndroidColor.parseColor(Shades.fill(hex)))
    fun banner(hex: String?) = Color(AndroidColor.parseColor(Shades.banner(hex)))
    fun bar(hex: String?) = Color(AndroidColor.parseColor(Shades.bar(hex)))
    fun ink(hex: String?) = Color(AndroidColor.parseColor(Shades.ink(hex)))
}

@Composable
fun ZyoraTheme(content: @Composable () -> Unit) {
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
