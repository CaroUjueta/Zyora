package com.zyora.ui;

import com.zyora.model.CalendarEvent;
import com.zyora.model.Task;
import com.zyora.service.Planner;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;

import java.util.Optional;

/** Drag-and-drop of tasks and events, carried as "task:&lt;id&gt;" / "event:&lt;id&gt;" strings. */
final class DragPayload {
    private static final String TASK = "task:";
    private static final String EVENT = "event:";

    private DragPayload() {
    }

    static ClipboardContent of(Task task) {
        ClipboardContent content = new ClipboardContent();
        content.putString(TASK + task.getId());
        return content;
    }

    static ClipboardContent of(CalendarEvent event) {
        ClipboardContent content = new ClipboardContent();
        content.putString(EVENT + event.getId());
        return content;
    }

    static boolean accepts(Dragboard db) {
        return db.hasString() && (db.getString().startsWith(TASK) || db.getString().startsWith(EVENT));
    }

    static Optional<Task> task(Planner planner, Dragboard db) {
        if (db.hasString() && db.getString().startsWith(TASK)) {
            return planner.findTask(db.getString().substring(TASK.length()));
        }
        return Optional.empty();
    }

    static Optional<CalendarEvent> event(Planner planner, Dragboard db) {
        if (db.hasString() && db.getString().startsWith(EVENT)) {
            return planner.findEvent(db.getString().substring(EVENT.length()));
        }
        return Optional.empty();
    }
}
