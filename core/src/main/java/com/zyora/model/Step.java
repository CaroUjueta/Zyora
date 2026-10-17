package com.zyora.model;

/** A sub-item of a task (a "paso" in the UI). */
public class Step {
    private String title;
    private boolean done;

    public Step() {
    }

    public Step(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public boolean isDone() {
        return done;
    }

    public void setDone(boolean done) {
        this.done = done;
    }
}
