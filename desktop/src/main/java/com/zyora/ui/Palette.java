package com.zyora.ui;

import com.zyora.service.Shades;

/**
 * The soft tones the views paint with, derived from a stored list or event color by
 * {@link Shades} (shared with the phone app).
 */
final class Palette {
    private Palette() {
    }

    /** Looked-up colors for an item (see {@code -item-*} in zyora.css): fill, accent bar and text. */
    static String itemStyle(String color) {
        return "-item-fill: " + fill(color) + "; -item-bar: " + bar(color) + "; -item-ink: " + ink(color) + ";";
    }

    /** A very light tint for backgrounds. */
    static String fill(String color) {
        return Shades.fill(color);
    }

    /** Between {@link #fill} and {@link #bar}, for the list banners. */
    static String banner(String color) {
        return Shades.banner(color);
    }

    /** The pastel tone itself, for bars, dots and accents. */
    static String bar(String color) {
        return Shades.bar(color);
    }

    /** A dark tone of the same hue, readable on top of {@link #fill}. */
    static String ink(String color) {
        return Shades.ink(color);
    }
}
