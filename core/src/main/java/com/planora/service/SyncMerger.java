package com.planora.service;

import com.planora.model.Syncable;
import com.planora.model.Task;
import com.planora.model.TaskList;
import com.planora.model.Tombstone;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Combines the data of two devices into one document that both can keep.
 *
 * <ul>
 *   <li>An item that only one side has is kept, unless the other side deleted it.</li>
 *   <li>When both have it, the most recent edit ({@code updatedAt}) wins.</li>
 *   <li>A deletion wins over edits made before it, and loses to edits made after it.</li>
 *   <li>Two lists that were created separately with the same name in the same place (like the
 *       «Tareas» list every device starts with) become one, so nothing shows up twice.</li>
 * </ul>
 *
 * Merging is order independent except for ties and for the order of the lists, which follow
 * {@code base}.
 */
public final class SyncMerger {
    private SyncMerger() {
    }

    public static PlannerData merge(PlannerData base, PlannerData other, Instant now) {
        base = PlannerJson.copy(base, PlannerData.class);
        other = PlannerJson.copy(other, PlannerData.class);

        Map<String, Tombstone> graves = new LinkedHashMap<>();
        for (Tombstone t : concat(base.getDeleted(), other.getDeleted())) {
            Tombstone known = graves.get(key(t.getKind(), t.getId()));
            if (known == null || isAfter(t.getDeletedAt(), known.getDeletedAt())) {
                graves.put(key(t.getKind(), t.getId()), t);
            }
        }

        joinListsWithTheSameName(base, other, graves, now);

        PlannerData merged = new PlannerData();
        merged.setLists(pick(base.getLists(), other.getLists(), Tombstone.Kind.LIST, graves));
        merged.setTasks(pick(base.getTasks(), other.getTasks(), Tombstone.Kind.TASK, graves));
        merged.setEvents(pick(base.getEvents(), other.getEvents(), Tombstone.Kind.EVENT, graves));
        merged.setDeleted(new ArrayList<>(graves.values()));
        return merged;
    }

    /**
     * Moves the content of each list that only {@code other} has into a list of {@code base} with
     * the same name and parent, when {@code other} does not know that list yet.
     */
    private static void joinListsWithTheSameName(PlannerData base, PlannerData other,
                                                 Map<String, Tombstone> graves, Instant now) {
        Set<String> baseIds = ids(base.getLists());
        Set<String> otherIds = ids(other.getLists());
        Map<String, String> moved = new HashMap<>();
        // Parents first: a sublist can only find its twin once its parent has been matched.
        boolean progress = true;
        while (progress) {
            progress = false;
            for (TaskList list : other.getLists()) {
                if (baseIds.contains(list.getId()) || moved.containsKey(list.getId())
                        || graves.containsKey(key(Tombstone.Kind.LIST, list.getId()))) {
                    continue;
                }
                String parent = moved.getOrDefault(list.getParentId(), list.getParentId());
                for (TaskList twin : base.getLists()) {
                    if (!otherIds.contains(twin.getId()) && !moved.containsValue(twin.getId())
                            && Objects.equals(twin.getParentId(), parent)
                            && sameName(twin.getName(), list.getName())) {
                        moved.put(list.getId(), twin.getId());
                        progress = true;
                        break;
                    }
                }
            }
        }
        if (moved.isEmpty()) {
            return;
        }
        other.getLists().removeIf(l -> moved.containsKey(l.getId()));
        moved.keySet().forEach(id -> graves.put(key(Tombstone.Kind.LIST, id), new Tombstone(Tombstone.Kind.LIST, id, now)));
        for (TaskList list : other.getLists()) {
            if (moved.containsKey(list.getParentId())) {
                list.setParentId(moved.get(list.getParentId()));
                list.setUpdatedAt(now);
            }
        }
        for (Task task : other.getTasks()) {
            if (moved.containsKey(task.getListId())) {
                task.setListId(moved.get(task.getListId()));
                task.setUpdatedAt(now);
            }
        }
    }

    private static <T extends Syncable> List<T> pick(List<T> base, List<T> other, Tombstone.Kind kind,
                                                     Map<String, Tombstone> graves) {
        Map<String, T> byId = new LinkedHashMap<>();
        base.forEach(item -> byId.put(item.getId(), item));
        for (T item : other) {
            T mine = byId.get(item.getId());
            if (mine == null || isAfter(item.getUpdatedAt(), mine.getUpdatedAt())) {
                byId.put(item.getId(), item);
            }
        }
        List<T> out = new ArrayList<>();
        for (T item : byId.values()) {
            Tombstone grave = graves.get(key(kind, item.getId()));
            if (grave == null || isAfter(item.getUpdatedAt(), grave.getDeletedAt())) {
                out.add(item);
            }
        }
        return out;
    }

    private static boolean isAfter(Instant a, Instant b) {
        return orEpoch(a).isAfter(orEpoch(b));
    }

    private static Instant orEpoch(Instant instant) {
        return instant == null ? Instant.EPOCH : instant;
    }

    private static boolean sameName(String a, String b) {
        return a != null && b != null && a.strip().equalsIgnoreCase(b.strip());
    }

    private static Set<String> ids(List<? extends Syncable> items) {
        Set<String> ids = new HashSet<>();
        items.forEach(i -> ids.add(i.getId()));
        return ids;
    }

    private static String key(Tombstone.Kind kind, String id) {
        return kind + ":" + id;
    }

    private static <T> List<T> concat(List<T> a, List<T> b) {
        List<T> out = new ArrayList<>(a);
        out.addAll(b);
        return out;
    }
}
