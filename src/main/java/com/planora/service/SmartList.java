package com.planora.service;

import com.planora.model.Task;

import java.time.LocalDate;

/** Built-in views that gather tasks from every list. */
public enum SmartList {
    MY_DAY("Mi día", "☀"),
    IMPORTANT("Importante", "★"),
    PLANNED("Planeado", "◷"),
    ALL("Todas las tareas", "≡");

    private final String label;
    private final String icon;

    SmartList(String label, String icon) {
        this.label = label;
        this.icon = icon;
    }

    public String label() {
        return label;
    }

    public String icon() {
        return icon;
    }

    public boolean includes(Task task, LocalDate today) {
        return switch (this) {
            case MY_DAY -> task.isInMyDay(today);
            case IMPORTANT -> task.isImportant();
            case PLANNED -> task.getDueDate() != null || task.isScheduled();
            case ALL -> true;
        };
    }
}
