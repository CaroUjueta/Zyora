package com.zyora.model;

import java.time.Instant;

/**
 * Something that is synchronized between devices: it has a stable id and remembers when its
 * content last changed, so the most recent edit can win when two devices disagree.
 */
public interface Syncable {
    String getId();

    Instant getUpdatedAt();

    void setUpdatedAt(Instant updatedAt);
}
