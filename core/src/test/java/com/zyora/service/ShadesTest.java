package com.zyora.service;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShadesTest {
    @Test
    void derivesTheFourTonesOfAColor() {
        assertEquals(List.of("#f5f1ff", "#ebe3ff", "#c6b0f3", "#534370"), tones("#a78bdb"));
        assertEquals(List.of("#fbf3dd", "#f4e7c3", "#d5bc75", "#5c4b14"), tones("#e8c55c"));
    }

    @Test
    void saturatedColorsBecomePastelAndGreysStayGrey() {
        assertEquals("#f5a79a", Shades.bar("#ff0000"));
        assertEquals("#bdbdc5", Shades.bar("#bdbdc5"));
    }

    @Test
    void badColorsFallBackToLavender() {
        assertEquals(Shades.bar("#a78bdb"), Shades.bar(null));
        assertEquals(Shades.bar("#a78bdb"), Shades.bar("rojo"));
    }

    @Test
    void everyPaletteColorLooksDifferent() {
        Set<String> bars = new HashSet<>();
        Planner.COLORS.forEach(c -> bars.add(Shades.bar(c)));
        assertEquals(Planner.COLORS.size(), bars.size());
    }

    private static List<String> tones(String color) {
        return List.of(Shades.fill(color), Shades.banner(color), Shades.bar(color), Shades.ink(color));
    }
}
