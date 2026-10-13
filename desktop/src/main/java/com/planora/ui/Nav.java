package com.planora.ui;

import com.planora.model.TaskList;
import com.planora.service.SmartList;

/** A destination in the sidebar: the home page, the calendar, a smart list, or one of the user's lists. */
record Nav(Kind kind, SmartList smart, TaskList list) {
    enum Kind { HOME, CALENDAR, SMART, LIST }

    static Nav home() {
        return new Nav(Kind.HOME, null, null);
    }

    static Nav calendar() {
        return new Nav(Kind.CALENDAR, null, null);
    }

    static Nav smart(SmartList smart) {
        return new Nav(Kind.SMART, smart, null);
    }

    static Nav list(TaskList list) {
        return new Nav(Kind.LIST, null, list);
    }

    String label() {
        return switch (kind) {
            case HOME -> "Inicio";
            case CALENDAR -> "Calendario";
            case SMART -> smart.label();
            case LIST -> list.getName();
        };
    }

    /** The icon in resources/com/planora/icons/nav; null for user lists, which show a dot in their color. */
    String iconName() {
        return switch (kind) {
            case HOME -> "home";
            case CALENDAR -> "calendar";
            case SMART -> switch (smart) {
                case MY_DAY -> "my-day";
                case IMPORTANT -> "important";
                case PLANNED -> "planned";
                case ALL -> "all-tasks";
            };
            case LIST -> null;
        };
    }

    /** The sidebar draws a line between sections: home and calendar, smart lists, user lists. */
    int section() {
        return switch (kind) {
            case HOME, CALENDAR -> 0;
            case SMART -> 1;
            case LIST -> 2;
        };
    }
}
