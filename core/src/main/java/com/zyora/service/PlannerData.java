package com.zyora.service;

import com.zyora.model.CalendarEvent;
import com.zyora.model.Task;
import com.zyora.model.TaskList;
import com.zyora.model.Tombstone;

import java.util.ArrayList;
import java.util.List;

/** Everything Zyora persists, as one document. */
public class PlannerData {
    private List<TaskList> lists = new ArrayList<>();
    private List<Task> tasks = new ArrayList<>();
    private List<CalendarEvent> events = new ArrayList<>();
    /** What was deleted on this device and not yet removed everywhere else. */
    private List<Tombstone> deleted = new ArrayList<>();

    public List<TaskList> getLists() {
        return lists;
    }

    public void setLists(List<TaskList> lists) {
        this.lists = lists;
    }

    public List<Task> getTasks() {
        return tasks;
    }

    public void setTasks(List<Task> tasks) {
        this.tasks = tasks;
    }

    public List<CalendarEvent> getEvents() {
        return events;
    }

    public void setEvents(List<CalendarEvent> events) {
        this.events = events;
    }

    public List<Tombstone> getDeleted() {
        return deleted;
    }

    public void setDeleted(List<Tombstone> deleted) {
        this.deleted = deleted;
    }
}
