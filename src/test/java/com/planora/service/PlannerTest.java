package com.planora.service;

import com.planora.model.CalendarEvent;
import com.planora.model.Task;
import com.planora.model.TaskList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlannerTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 12);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-12T10:00:00Z"), ZoneOffset.UTC);

    private InMemoryStore store;
    private Planner planner;

    @BeforeEach
    void setUp() {
        store = new InMemoryStore();
        planner = new Planner(store, CLOCK);
    }

    @Test
    void createsADefaultListOnFirstRun() {
        assertEquals(1, planner.lists().size());
        assertEquals("Tareas", planner.defaultList().getName());
        assertEquals(1, store.saves);
    }

    @Test
    void myDayHoldsTasksAddedTodayAndTasksScheduledToday() {
        Task added = planner.addTask("Llamar", planner.defaultList(), t -> t.setMyDay(TODAY));
        Task addedYesterday = planner.addTask("Viejo", planner.defaultList(), t -> t.setMyDay(TODAY.minusDays(1)));
        Task scheduled = planner.addTask("Escribir informe", planner.defaultList());
        planner.scheduleTask(scheduled, TODAY.atTime(15, 0));

        List<Task> myDay = planner.tasksFor(SmartList.MY_DAY);

        assertTrue(myDay.contains(added));
        assertTrue(myDay.contains(scheduled));
        assertFalse(myDay.contains(addedYesterday));
    }

    @Test
    void scheduledTaskBecomesATimeBlockAndLeavesTheBacklog() {
        Task task = planner.addTask("Preparar clase", planner.defaultList());
        task.setDurationMinutes(90);
        assertTrue(planner.unscheduledTasks().contains(task));

        planner.scheduleTask(task, TODAY.atTime(9, 0));

        assertFalse(planner.unscheduledTasks().contains(task));
        List<AgendaItem> agenda = planner.agendaFor(TODAY);
        assertEquals(1, agenda.size());
        AgendaItem item = agenda.get(0);
        assertTrue(item.isTask());
        assertFalse(item.allDay());
        assertEquals(TODAY.atTime(10, 30), item.end());
    }

    @Test
    void dueDateShowsAsAllDayItem() {
        planner.addTask("Pagar luz", planner.defaultList(), t -> t.setDueDate(TODAY));

        AgendaItem item = planner.agendaFor(TODAY).get(0);

        assertTrue(item.allDay());
        assertEquals("Pagar luz", item.title());
    }

    @Test
    void multiDayEventIsClippedToEachDay() {
        CalendarEvent trip = new CalendarEvent();
        trip.setTitle("Viaje");
        trip.setStart(TODAY.atTime(18, 0));
        trip.setEnd(TODAY.plusDays(1).atTime(12, 0));
        planner.addEvent(trip);

        AgendaItem first = planner.agendaFor(TODAY).get(0);
        AgendaItem second = planner.agendaFor(TODAY.plusDays(1)).get(0);

        assertEquals(TODAY.plusDays(1).atStartOfDay(), first.end());
        assertEquals(TODAY.plusDays(1).atStartOfDay(), second.start());
        assertTrue(planner.agendaFor(TODAY.plusDays(2)).isEmpty());
    }

    @Test
    void completingRecordsTheTime() {
        Task task = planner.addTask("Comprar pan", planner.defaultList());

        planner.setCompleted(task, true);
        assertEquals(LocalDateTime.of(2026, 10, 12, 10, 0), task.getCompletedAt());

        planner.setCompleted(task, false);
        assertEquals(null, task.getCompletedAt());
    }

    @Test
    void importantTasksComeFirst() {
        Task normal = planner.addTask("Normal", planner.defaultList());
        Task important = planner.addTask("Urgente", planner.defaultList(), t -> t.setImportant(true));

        assertEquals(List.of(important, normal), planner.tasksFor(SmartList.ALL));
    }

    @Test
    void deletingAListDeletesItsTasksButTheLastListStays() {
        TaskList work = planner.addList("Trabajo");
        planner.addTask("Informe", work);
        planner.addTask("Leche", planner.defaultList());

        assertTrue(planner.deleteList(work));
        assertEquals(1, planner.tasks().size());
        assertFalse(planner.deleteList(planner.defaultList()));
    }

    @Test
    void notifiesListenersOnEveryChange() {
        AtomicInteger calls = new AtomicInteger();
        planner.addListener(calls::incrementAndGet);

        Task task = planner.addTask("Algo", planner.defaultList());
        planner.setCompleted(task, true);
        planner.deleteTask(task);

        assertEquals(3, calls.get());
    }

    @Test
    void jsonStoreRoundTrip(@TempDir Path dir) {
        JsonPlannerStore json = new JsonPlannerStore(dir.resolve("sub").resolve("planora.json"));
        Planner first = new Planner(json, CLOCK);
        Task task = first.addTask("Persistir", first.defaultList(), t -> {
            t.setDueDate(TODAY);
            t.setImportant(true);
        });
        first.scheduleTask(task, TODAY.atTime(11, 15));
        CalendarEvent event = new CalendarEvent();
        event.setTitle("Reunión");
        event.setStart(TODAY.atTime(9, 0));
        event.setEnd(TODAY.atTime(10, 0));
        first.addEvent(event);

        Planner second = new Planner(json, CLOCK);

        Task loaded = second.findTask(task.getId()).orElseThrow();
        assertEquals("Persistir", loaded.getTitle());
        assertEquals(TODAY, loaded.getDueDate());
        assertEquals(TODAY.atTime(11, 15), loaded.getScheduledStart());
        assertTrue(loaded.isImportant());
        assertEquals("Reunión", second.findEvent(event.getId()).orElseThrow().getTitle());
        assertEquals(1, second.lists().size());
    }

    private static final class InMemoryStore implements PlannerStore {
        int saves;

        @Override
        public PlannerData load() {
            return new PlannerData();
        }

        @Override
        public void save(PlannerData data) {
            saves++;
        }
    }
}
