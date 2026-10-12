package com.planora.service;

/**
 * Derives the soft tones the apps paint with from a stored list or event color, the same on the
 * computer and the phone.
 *
 * <p>Works in OKLCH, where equal lightness and chroma look equally light and equally colorful in
 * every hue (in plain HSV a green or cyan looks much louder than a blue at the same numbers). Each
 * role has a fixed lightness and chroma; the stored color only gives the hue and how muted it is,
 * so dusty colors (sand, sage) stay dusty, greys stay grey and old saturated colors become pastel.
 */
public final class Shades {
    /** A palette color's chroma; anything at least this colorful gets the full chroma of a role. */
    private static final double FULL_CHROMA = 0.095;
    private static final String FALLBACK = "#a78bdb";

    private Shades() {
    }

    /** A very light tint for backgrounds. */
    public static String fill(String color) {
        return shade(color, 0.965, 0.030);
    }

    /** Between {@link #fill} and {@link #bar}, for the list banners. */
    public static String banner(String color) {
        return shade(color, 0.93, 0.05);
    }

    /** The pastel tone itself, for bars, dots and swatches. */
    public static String bar(String color) {
        return shade(color, 0.80, 0.095);
    }

    /** A dark tone of the same hue, readable on top of {@link #fill} and {@link #banner}. */
    public static String ink(String color) {
        return shade(color, 0.42, 0.075);
    }

    private static String shade(String color, double lightness, double chroma) {
        double[] lch = toOklch(parse(color));
        return toHex(lightness, chroma * Math.min(1, lch[1] / FULL_CHROMA), lch[2]);
    }

    private static int parse(String color) {
        String hex = color == null ? FALLBACK : color.strip();
        if (!hex.matches("#[0-9a-fA-F]{6}")) {
            hex = FALLBACK;
        }
        return Integer.parseInt(hex.substring(1), 16);
    }

    private static double[] toOklch(int rgb) {
        double r = toLinear(((rgb >> 16) & 0xff) / 255.0);
        double g = toLinear(((rgb >> 8) & 0xff) / 255.0);
        double b = toLinear((rgb & 0xff) / 255.0);
        double l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
        double m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
        double s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
        double lightness = 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s;
        double a = 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s;
        double bb = 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s;
        double hue = (Math.toDegrees(Math.atan2(bb, a)) + 360) % 360;
        return new double[] {lightness, Math.hypot(a, bb), hue};
    }

    /** The color as {@code #rrggbb}, lowering the chroma as needed to stay inside sRGB. */
    private static String toHex(double lightness, double chroma, double hue) {
        double[] rgb = toLinearRgb(lightness, chroma, hue);
        if (!inGamut(rgb)) {
            double lo = 0;
            double hi = chroma;
            for (int i = 0; i < 24; i++) {
                double mid = (lo + hi) / 2;
                if (inGamut(toLinearRgb(lightness, mid, hue))) {
                    lo = mid;
                } else {
                    hi = mid;
                }
            }
            rgb = toLinearRgb(lightness, lo, hue);
        }
        return String.format("#%02x%02x%02x", to8bit(rgb[0]), to8bit(rgb[1]), to8bit(rgb[2]));
    }

    private static double[] toLinearRgb(double lightness, double chroma, double hue) {
        double a = chroma * Math.cos(Math.toRadians(hue));
        double b = chroma * Math.sin(Math.toRadians(hue));
        double l = Math.pow(lightness + 0.3963377774 * a + 0.2158037573 * b, 3);
        double m = Math.pow(lightness - 0.1055613458 * a - 0.0638541728 * b, 3);
        double s = Math.pow(lightness - 0.0894841775 * a - 1.2914855480 * b, 3);
        return new double[] {
                4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
                -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
                -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s,
        };
    }

    private static boolean inGamut(double[] rgb) {
        for (double c : rgb) {
            if (c < -1e-4 || c > 1 + 1e-4) {
                return false;
            }
        }
        return true;
    }

    private static double toLinear(double c) {
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static int to8bit(double linear) {
        double c = Math.max(0, Math.min(1, linear));
        double srgb = c <= 0.0031308 ? 12.92 * c : 1.055 * Math.pow(c, 1 / 2.4) - 0.055;
        return (int) Math.round(srgb * 255);
    }
}
