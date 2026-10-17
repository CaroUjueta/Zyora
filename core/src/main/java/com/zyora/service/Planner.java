package com.zyora.service;

import com.zyora.model.CalendarEvent;
import com.zyora.model.Syncable;
import com.zyora.model.Task;
import com.zyora.model.TaskList;
import com.zyora.model.Tombstone;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The single source of truth for tasks, lists and events. Every mutation is saved and then
 * announced to listeners, which is how the UI stays in sync.
 *
 * <p>UI code may edit a task or event in place and then call {@link #changed()}.
 */
public class Planner {
    /**
     * Pastel colors for lists and events, shown in pickers as two rows of eight. Views paint with
     * {@link Shades}, which keeps each color's hue and how muted it is. First row: lavender, pink,
     * mint, peach, sky, butter, coral, aqua. Second row, softer: sand, pistachio, sage, misty blue,
     * periwinkle, lilac, mauve and pearl grey.
     */
    public static final List<String> COLORS = List.of(
            "#a78bdb", "#f19cbb", "#7cc7a3", "#f5b087", "#82b4e8", "#e8c55c", "#ee8f8f", "#6fc3c9",
            "#d0ba9c", "#b0c985", "#a9c7ae", "#aac0d9", "#acb9fb", "#d9aae4", "#d7b2c2", "#bdbdc5");

    private static final Comparator<Task> TASK_ORDER = Comparator
            .comparing(Task::isCompleted)
            .thenComparing(Task::isImportant, Comparator.reverseOrder())
            .thenComparing(Planner::sortDate, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Task::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()));

    private final PlannerStore store;
    private final Clock clock;
    private final PlannerData data;
    private final ChangeTracker tracker = new ChangeTracker();
    private final List<Runnable> listeners = new ArrayList<>();

    public Planner(PlannerStore store) {
        this(store, Clock.systemDefaultZone());
    }

    public Planner(PlannerStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
        this.data = store.load();
        tracker.reset(data, Instant.now(clock));
        if (data.getLists().isEmpty()) {
            data.getLists().add(new TaskList("Tareas", COLORS.get(0)));
            tracker.stamp(data, Instant.now(clock));
            store.save(data);
        }
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    // ---- queries -------------------------------------------------------------------------

    public List<TaskList> lists() {
        return Collections.unmodifiableList(data.getLists());
    }

    public List<Task> tasks() {
        return Collections.unmodifiableList(data.getTasks());
    }

    public List<CalendarEvent> events() {
        return Collections.unmodifiableList(data.getEvents());
    }

    public TaskList defaultList() {
        return data.getLists().get(0);
    }

    public TaskList listOf(Task task) {
        return findList(task.getListId()).orElse(defaultList());
    }

    public Optional<TaskList> findList(String id) {
        return data.getLists().stream().filter(l -> l.getId().equals(id)).findFirst();
    }

    public Optional<Task> findTask(String id) {
        return data.getTasks().stream().filter(t -> t.getId().equals(id)).findFirst();
    }

    public Optional<CalendarEvent> findEvent(String id) {
        return data.getEvents().stream().filter(e -> e.getId().equals(id)).findFirst();
    }

    public List<Task> tasksFor(SmartList smart) {
        LocalDate today = today();
        return sortedTasks(t -> smart.includes(t, today));
    }

    /** The tasks of a list and of all its sublists. */
    public List<Task> tasksInList(TaskList list) {
        Set<String> ids = new HashSet<>();
        subtree(list).forEach(l -> ids.add(l.getId()));
        return sortedTasks(t -> ids.contains(t.getListId()));
    }

    /** The parent of a sublist; empty for top-level lists (and for lists whose parent is gone). */
    public Optional<TaskList> parentOf(TaskList list) {
        return list.getParentId() == null ? Optional.empty() : findList(list.getParentId());
    }

    /** Direct sublists, or the top-level lists when {@code parent} is null, in creation order. */
    public List<TaskList> childrenOf(TaskList parent) {
        return data.getLists().stream()
                .filter(l -> parent == null ? parentOf(l).isEmpty() : parent.getId().equals(l.getParentId()))
                .toList();
    }

    /** Every list, each one followed by its sublists (depth first), as shown in the sidebar. */
    public List<TaskList> listTree() {
        List<TaskList> out = new ArrayList<>();
        childrenOf(null).forEach(root -> out.addAll(subtree(root)));
        return out;
    }

    /** The list and its ancestors, outermost first. */
    public List<TaskList> pathOf(TaskList list) {
        List<TaskList> path = new ArrayList<>();
        for (TaskList l = list; l != null && !path.contains(l); l = parentOf(l).orElse(null)) {
            path.add(0, l);
        }
        return path;
    }

    /** The list followed by all its sublists, depth first. */
    public List<TaskList> subtree(TaskList list) {
        List<TaskList> out = new ArrayList<>();
        collect(list, out);
        return out;
    }

    private void collect(TaskList list, List<TaskList> out) {
        if (out.contains(list)) {
            return; // a corrupted file could contain a cycle
        }
        out.add(list);
        childrenOf(list).forEach(child -> collect(child, out));
    }

    /** Pending tasks that have no time block yet: the ones you can drag onto the calendar. */
    public List<Task> unscheduledTasks() {
        return sortedTasks(t -> !t.isCompleted() && !t.isScheduled());
    }

    /**
     * The day a task belongs to on the home page: its time block, its due date, or today if it was
     * added to Mi día. Null for tasks without any date.
     */
    public LocalDate planDay(Task task) {
        if (task.isScheduled()) {
            return task.getScheduledStart().toLocalDate();
        }
        if (task.getDueDate() != null) {
            return task.getDueDate();
        }
        return task.isInMyDay(today()) ? today() : null;
    }

    /**
     * The home page's tasks: pending ones up to {@code daysAhead} days from today (overdue ones
     * too), then the ones finished today, each group by {@link #planDay}.
     */
    public List<Task> upcomingTasks(int daysAhead) {
        LocalDate today = today();
        LocalDate limit = today.plusDays(daysAhead);
        return data.getTasks().stream()
                .filter(t -> {
                    LocalDate day = planDay(t);
                    if (day == null || day.isAfter(limit)) {
                        return false;
                    }
                    return !t.isCompleted()
                            || (t.getCompletedAt() != null && t.getCompletedAt().toLocalDate().equals(today));
                })
                .sorted(Comparator.comparing(Task::isCompleted).thenComparing(this::planDay))
                .toList();
    }

    /** Events and tasks that fall on the given day, all-day items first. */
    public List<AgendaItem> agendaFor(LocalDate day) {
        LocalDateTime dayStart = day.atStartOfDay();
        LocalDateTime dayEnd = day.plusDays(1).atStartOfDay();
        List<AgendaItem> items = new ArrayList<>();
        for (CalendarEvent e : data.getEvents()) {
            if (e.getStart().isBefore(dayEnd) && e.getEnd().isAfter(dayStart)) {
                items.add(new AgendaItem(e, null, e.getTitle(),
                        max(e.getStart(), dayStart), min(e.getEnd(), dayEnd),
                        e.isAllDay(), e.getColor(), false));
            }
        }
        for (Task t : data.getTasks()) {
            String color = listOf(t).getColor();
            if (t.isScheduled()) {
                if (!t.getScheduledStart().isBefore(dayStart) && t.getScheduledStart().isBefore(dayEnd)) {
                    items.add(new AgendaItem(null, t, t.getTitle(), t.getScheduledStart(),
                            min(t.getScheduledEnd(), dayEnd), false, color, t.isCompleted()));
                }
            } else if (day.equals(t.getDueDate())) {
                items.add(new AgendaItem(null, t, t.getTitle(), dayStart, dayEnd, true, color, t.isCompleted()));
            }
        }
        items.sort(Comparator.comparing(AgendaItem::allDay, Comparator.reverseOrder())
                .thenComparing(AgendaItem::start));
        return items;
    }

    // ---- tasks ---------------------------------------------------------------------------

    public Task addTask(String title, TaskList list) {
        return addTask(title, list, t -> { });
    }

    /** Adds a task, letting the caller set extra fields before it is saved and announced. */
    public Task addTask(String title, TaskList list, Consumer<Task> init) {
        Task task = new Task(title.strip(), list.getId());
        task.setCreatedAt(now());
        init.accept(task);
        data.getTasks().add(task);
        changed();
        return task;
    }

    public void setCompleted(Task task, boolean completed) {
        task.setCompleted(completed);
        task.setCompletedAt(completed ? now() : null);
        changed();
    }

    public void scheduleTask(Task task, LocalDateTime start) {
        task.setScheduledStart(start);
        changed();
    }

    public void unscheduleTask(Task task) {
        task.setScheduledStart(null);
        changed();
    }

    public void deleteTask(Task task) {
        data.getTasks().remove(task);
        bury(Tombstone.Kind.TASK, task.getId());
        changed();
    }

    // ---- lists ---------------------------------------------------------------------------

    public TaskList addList(String name) {
        return addList(name, null);
    }

    /** Adds a list inside {@code parent}, or a top-level list when it is null. */
    public TaskList addList(String name, TaskList parent) {
        String color = COLORS.get(data.getLists().size() % COLORS.size());
        TaskList list = new TaskList(name.strip(), color);
        list.setParentId(parent == null ? null : parent.getId());
        data.getLists().add(list);
        changed();
        return list;
    }

    public void renameList(TaskList list, String name) {
        list.setName(name.strip());
        changed();
    }

    public void setListColor(TaskList list, String color) {
        list.setColor(color);
        changed();
    }

    /**
     * Deletes a list together with its sublists and all their tasks. At least one list must remain,
     * so a list that holds every other one cannot be deleted.
     */
    public boolean deleteList(TaskList list) {
        List<TaskList> doomed = subtree(list);
        if (doomed.size() >= data.getLists().size()) {
            return false;
        }
        Set<String> ids = new HashSet<>();
        doomed.forEach(l -> ids.add(l.getId()));
        data.getTasks().stream().filter(t -> ids.contains(t.getListId()))
                .forEach(t -> bury(Tombstone.Kind.TASK, t.getId()));
        data.getTasks().removeIf(t -> ids.contains(t.getListId()));
        data.getLists().removeAll(doomed);
        ids.forEach(id -> bury(Tombstone.Kind.LIST, id));
        changed();
        return true;
    }

    // ---- events --------------------------------------------------------------------------

    public void addEvent(CalendarEvent event) {
        data.getEvents().add(event);
        changed();
    }

    public void deleteEvent(CalendarEvent event) {
        data.getEvents().remove(event);
        bury(Tombstone.Kind.EVENT, event.getId());
        changed();
    }

    // ---- sync ----------------------------------------------------------------------------

    /** A copy of all the data, to send to another device. */
    public PlannerData snapshot() {
        return PlannerJson.copy(data, PlannerData.class);
    }

    /**
     * Replaces all the data with {@code incoming} (usually a {@link SyncMerger} result), saves and
     * notifies listeners. Tasks, lists and events that are still there keep their Java object, so
     * a screen that is showing one keeps working.
     */
    public void replaceWith(PlannerData incoming) {
        incoming = PlannerJson.copy(incoming, PlannerData.class);
        data.setLists(adopt(data.getLists(), incoming.getLists()));
        data.setTasks(adopt(data.getTasks(), incoming.getTasks()));
        data.setEvents(adopt(data.getEvents(), incoming.getEvents()));
        data.setDeleted(incoming.getDeleted());
        Instant now = Instant.now(clock);
        tracker.reset(data, now);
        if (data.getLists().isEmpty()) {
            data.getLists().add(new TaskList("Tareas", COLORS.get(0)));
        }
        changed();
    }

    private static <T extends Syncable> List<T> adopt(List<T> current, List<T> incoming) {
        Map<String, T> byId = new HashMap<>();
        current.forEach(item -> byId.put(item.getId(), item));
        List<T> out = new ArrayList<>();
        for (T item : incoming) {
            T existing = byId.get(item.getId());
            if (existing != null && existing.getClass() == item.getClass()) {
                PlannerJson.copyInto(existing, item);
                out.add(existing);
            } else {
                out.add(item);
            }
        }
        return out;
    }

    // ---- change notification -------------------------------------------------------------

    /** Stamps what changed, saves and notifies listeners. Call after editing a task, list or event in place. */
    public void changed() {
        tracker.stamp(data, Instant.now(clock));
        store.save(data);
        List.copyOf(listeners).forEach(Runnable::run);
    }

    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    private void bury(Tombstone.Kind kind, String id) {
        data.getDeleted().add(new Tombstone(kind, id, Instant.now(clock)));
    }

    private List<Task> sortedTasks(Predicate<Task> filter) {
        return data.getTasks().stream().filter(filter).sorted(TASK_ORDER).toList();
    }

    private static LocalDateTime sortDate(Task t) {
        if (t.isScheduled()) {
            return t.getScheduledStart();
        }
        return t.getDueDate() == null ? null : t.getDueDate().atStartOfDay();
    }

    private static LocalDateTime max(LocalDateTime a, LocalDateTime b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalDateTime min(LocalDateTime a, LocalDateTime b) {
        return a.isBefore(b) ? a : b;
    }
}
