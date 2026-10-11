package com.planora.model;

import java.time.Instant;
import java.util.UUID;

/**
 * A user-defined list of tasks. Its color is used to paint the list's tasks on the calendar. A list
 * can live inside another one (a sublist); top-level lists have no parent.
 */
public class TaskList implements Syncable {
    private String id = UUID.randomUUID().toString();
    private String name;
    private String color = "#a78bdb";
    private String parentId;
    private Instant updatedAt;

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

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    @Override
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return name;
    }
}
