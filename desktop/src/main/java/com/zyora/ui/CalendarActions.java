package com.zyora.ui;

import com.zyora.model.CalendarEvent;
import com.zyora.model.Task;
import javafx.scene.input.Dragboard;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** What the calendar views ask the calendar pane to do in response to clicks and drops. */
interface CalendarActions {
    void openTask(Task task);

    void editEvent(CalendarEvent event);

    void createEvent(LocalDateTime start, boolean allDay);

    void openDay(LocalDate day);

    /** A task or event was dropped on a time slot. */
    void dropAt(Dragboard db, LocalDateTime start);

    /**
     * A task or event was dropped on a day. On the all-day row it becomes all-day (tasks lose their
     * time block and get a due date); elsewhere it keeps its time and only changes day.
     */
    void dropOnDay(Dragboard db, LocalDate day, boolean allDayRow);
}
