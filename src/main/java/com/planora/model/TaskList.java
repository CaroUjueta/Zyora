package com.planora.model;

import java.util.UUID;

/** A user-defined list of tasks. Its color is used to paint the list's tasks on the calendar. */
public class TaskList {
    private String id = UUID.randomUUID().toString();
    private String name;
    private String color = "#2564cf";

    public TaskList() {
    }

    public TaskList(String name, String color) {
        this.name = name;
        this.color = color;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    @Override
    public String toString() {
        return name;
    }
}
