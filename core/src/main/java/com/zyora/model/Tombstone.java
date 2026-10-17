package com.zyora.model;

import java.time.Instant;

/** Records that a task, list or event was deleted, so other devices can delete it too. */
public class Tombstone {
    public enum Kind { TASK, LIST, EVENT }

    private Kind kind;
    private String id;
    private Instant deletedAt;

    public Tombstone() {
    }

    public Tombstone(Kind kind, String id, Instant deletedAt) {
        this.kind = kind;
        this.id = id;
        this.deletedAt = deletedAt;
    }

    public Kind getKind() {
        return kind;
    }

    public void setKind(Kind kind) {
        this.kind = kind;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }
}
