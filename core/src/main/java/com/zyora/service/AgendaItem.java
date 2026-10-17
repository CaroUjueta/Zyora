package com.zyora.service;

import com.zyora.model.CalendarEvent;
import com.zyora.model.Task;

import java.time.LocalDateTime;

/**
 * Something shown on the calendar for one day: either an event or a task (scheduled as a time
 * block, or all-day on its due date). Start and end are clipped to the day.
 */
public record AgendaItem(
        CalendarEvent event,
        Task task,
        String title,
        LocalDateTime start,
        LocalDateTime end,
        boolean allDay,
        String color,
        boolean completed) {

    public boolean isTask() {
        return task != null;
    }
}
