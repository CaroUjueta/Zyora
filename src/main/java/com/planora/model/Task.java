package com.planora.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A to-do item. Besides an optional due date, a task can be scheduled: given a start time and a
 * duration it becomes a time block on the calendar, which is what joins tasks and calendar.
 */
public class Task {
    private String id = UUID.randomUUID().toString();
    private String listId;
    private String title;
    private String notes = "";
    private boolean important;
    private boolean completed;
    private LocalDate dueDate;
    private LocalDateTime scheduledStart;
    private int durationMinutes = 30;
    /** The day the user added the task to "Mi día"; it only counts for that day. */
    private LocalDate myDay;
    private List<Step> steps = new ArrayList<>();
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime completedAt;

    public Task() {
    }

    public Task(String title, String listId) {
        this.title = title;
        this.listId = listId;
    }

    @JsonIgnore
    public boolean isScheduled() {
        return scheduledStart != null;
    }

    @JsonIgnore
    public LocalDateTime getScheduledEnd() {
        return scheduledStart == null ? null : scheduledStart.plusMinutes(durationMinutes);
    }

    /** Tasks show up in "Mi día" when added to it today or when they are scheduled for today. */
    public boolean isInMyDay(LocalDate today) {
        return today.equals(myDay) || (scheduledStart != null && scheduledStart.toLocalDate().equals(today));
    }

    public boolean isOverdue(LocalDate today) {
        return !completed && dueDate != null && dueDate.isBefore(today);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getListId() {
        return listId;
    }

    public void setListId(String listId) {
        this.listId = listId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public boolean isImportant() {
        return important;
    }

    public void setImportant(boolean important) {
        this.important = important;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public LocalDateTime getScheduledStart() {
        return scheduledStart;
    }

    public void setScheduledStart(LocalDateTime scheduledStart) {
        this.scheduledStart = scheduledStart;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(int durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public LocalDate getMyDay() {
        return myDay;
    }

    public void setMyDay(LocalDate myDay) {
        this.myDay = myDay;
    }

    public List<Step> getSteps() {
        return steps;
    }

    public void setSteps(List<Step> steps) {
        this.steps = steps;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
}
