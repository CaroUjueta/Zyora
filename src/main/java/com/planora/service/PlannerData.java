package com.planora.service;

import com.planora.model.CalendarEvent;
import com.planora.model.Task;
import com.planora.model.TaskList;

import java.util.ArrayList;
import java.util.List;

/** Everything Planora persists, as one document. */
public class PlannerData {
    private List<TaskList> lists = new ArrayList<>();
    private List<Task> tasks = new ArrayList<>();
    private List<CalendarEvent> events = new ArrayList<>();

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
}
