package com.planora.service;

import com.planora.model.CalendarEvent;
import com.planora.model.Task;
import com.planora.model.TaskList;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The single source of truth for tasks, lists and events. Every mutation is saved and then
 * announced to listeners, which is how the UI stays in sync.
 *
 * <p>UI code may edit a task or event in place and then call {@link #changed()}.
 */
public class Planner {
    private static final String[] LIST_COLORS = {
            "#2564cf", "#c239b3", "#0f7b0f", "#ca5010", "#038387", "#8764b8", "#c50f1f", "#986f0b"
    };

    private static final Comparator<Task> TASK_ORDER = Comparator
            .comparing(Task::isCompleted)
            .thenComparing(Task::isImportant, Comparator.reverseOrder())
            .thenComparing(Planner::sortDate, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Task::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()));

    private final PlannerStore store;
    private final Clock clock;
    private final PlannerData data;
    private final List<Runnable> listeners = new ArrayList<>();

    public Planner(PlannerStore store) {
        this(store, Clock.systemDefaultZone());
    }

    public Planner(PlannerStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
        this.data = store.load();
        if (data.getLists().isEmpty()) {
            data.getLists().add(new TaskList("Tareas", LIST_COLORS[0]));
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

    public List<Task> tasksInList(TaskList list) {
        return sortedTasks(t -> list.getId().equals(t.getListId()));
    }

    /** Pending tasks that have no time block yet: the ones you can drag onto the calendar. */
    public List<Task> unscheduledTasks() {
        return sortedTasks(t -> !t.isCompleted() && !t.isScheduled());
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
        changed();
    }

    // ---- lists ---------------------------------------------------------------------------

    public TaskList addList(String name) {
        String color = LIST_COLORS[data.getLists().size() % LIST_COLORS.length];
        TaskList list = new TaskList(name.strip(), color);
        data.getLists().add(list);
        changed();
        return list;
    }

    public void renameList(TaskList list, String name) {
        list.setName(name.strip());
        changed();
    }

    /** Deletes a list together with its tasks. The last remaining list cannot be deleted. */
    public boolean deleteList(TaskList list) {
        if (data.getLists().size() <= 1) {
            return false;
        }
        data.getTasks().removeIf(t -> list.getId().equals(t.getListId()));
        data.getLists().remove(list);
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
        changed();
    }

    // ---- change notification -------------------------------------------------------------

    /** Saves and notifies listeners. Call after editing a task, list or event in place. */
    public void changed() {
        store.save(data);
        List.copyOf(listeners).forEach(Runnable::run);
    }

    public void addListener(Runnable listener) {
        listeners.add(listener);
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
