package com.planora.ui;

import javafx.scene.paint.Color;

/**
 * Derives the soft shades the views paint with from a stored list or event color. Only the hue is
 * kept, so older saturated colors fit the pastel theme too.
 */
final class Palette {
    private static final Color FALLBACK = Color.web("#a78bdb");

    private Palette() {
    }

    /** Looked-up colors for an item (see {@code -item-*} in planora.css): fill, accent bar and text. */
    static String itemStyle(String color) {
        return "-item-fill: " + fill(color) + "; -item-bar: " + bar(color) + "; -item-ink: " + ink(color) + ";";
    }

    /** A very light tint for backgrounds. */
    static String fill(String color) {
        return shade(color, 0.20, 0.98);
    }

    /** Between {@link #fill} and {@link #bar}, for the list banners. */
    static String banner(String color) {
        return shade(color, 0.28, 0.96);
    }

    /** The pastel tone itself, for bars, dots and accents. */
    static String bar(String color) {
        return shade(color, 0.42, 0.88);
    }

    /** A dark tone of the same hue, readable on top of {@link #fill}. */
    static String ink(String color) {
        return shade(color, 0.55, 0.38);
    }

    static String hex(Color c) {
        return String.format("#%02x%02x%02x",
                Math.round(c.getRed() * 255), Math.round(c.getGreen() * 255), Math.round(c.getBlue() * 255));
    }

    private static String shade(String color, double saturation, double brightness) {
        Color c = parse(color);
        // Greys stay grey instead of picking up the hue of red.
        double s = c.getSaturation() < 0.08 ? 0 : saturation;
        return hex(Color.hsb(c.getHue(), s, brightness));
    }

    private static Color parse(String color) {
        try {
            return color == null ? FALLBACK : Color.web(color);
        } catch (IllegalArgumentException e) {
            return FALLBACK;
        }
    }
}
