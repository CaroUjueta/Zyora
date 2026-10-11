package com.planora.ui;

import com.planora.model.TaskList;
import com.planora.service.SmartList;

/** A destination in the sidebar: the calendar, a smart list, or one of the user's lists. */
record Nav(Kind kind, SmartList smart, TaskList list) {
    enum Kind { CALENDAR, SMART, LIST }

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
            case CALENDAR -> "Calendario";
            case SMART -> smart.label();
            case LIST -> list.getName();
        };
    }

    String icon() {
        return switch (kind) {
            case CALENDAR -> "▦";
            case SMART -> smart.icon();
            case LIST -> "●";
        };
    }
}
