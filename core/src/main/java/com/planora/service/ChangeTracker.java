package com.planora.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.planora.model.Syncable;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Stamps {@code updatedAt} on every task, list and event whose content changed since the last
 * check. The UI edits objects in place and then calls {@link Planner#changed()}, so instead of
 * trusting each caller to say what it touched, this compares a fingerprint of every item.
 */
final class ChangeTracker {
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private Map<String, String> fingerprints = new HashMap<>();

    /** Remembers the loaded content. Items saved before sync existed get their first stamp. */
    void reset(PlannerData data, Instant now) {
        fingerprints = new HashMap<>();
        items(data).forEach(item -> {
            if (item.getUpdatedAt() == null) {
                item.setUpdatedAt(now);
            }
            fingerprints.put(key(item), fingerprint(item));
        });
    }

    /** Stamps new and modified items with {@code now}. */
    void stamp(PlannerData data, Instant now) {
        Map<String, String> current = new HashMap<>();
        items(data).forEach(item -> {
            String fingerprint = fingerprint(item);
            if (!fingerprint.equals(fingerprints.get(key(item)))) {
                item.setUpdatedAt(now);
            }
            current.put(key(item), fingerprint);
        });
        fingerprints = current;
    }

    private static Stream<Syncable> items(PlannerData data) {
        return Stream.of(data.getLists(), data.getTasks(), data.getEvents()).flatMap(l -> l.stream());
    }

    private static String key(Syncable item) {
        return item.getClass().getSimpleName() + ":" + item.getId();
    }

    private String fingerprint(Syncable item) {
        ObjectNode node = mapper.valueToTree(item);
        node.remove("updatedAt");
        return node.toString();
    }
}
