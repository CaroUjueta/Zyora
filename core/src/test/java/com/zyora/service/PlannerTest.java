package com.zyora.service;

import com.zyora.model.CalendarEvent;
import com.zyora.model.Step;
import com.zyora.model.Task;
import com.zyora.model.TaskList;
import com.zyora.model.Tombstone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    void upcomingTasksCoverTheNextDaysOverdueAndFinishedToday() {
        Task overdue = planner.addTask("Vencida", planner.defaultList(), t -> t.setDueDate(TODAY.minusDays(3)));
        Task myDay = planner.addTask("Hoy", planner.defaultList(), t -> t.setMyDay(TODAY));
        Task tomorrow = planner.addTask("Mañana", planner.defaultList());
        planner.scheduleTask(tomorrow, TODAY.plusDays(1).atTime(9, 0));
        Task tooFar = planner.addTask("Lejos", planner.defaultList(), t -> t.setDueDate(TODAY.plusDays(8)));
        Task undated = planner.addTask("Sin fecha", planner.defaultList());
        Task done = planner.addTask("Hecha", planner.defaultList(), t -> t.setMyDay(TODAY));
        planner.setCompleted(done, true);

        assertEquals(List.of(overdue, myDay, tomorrow, done), planner.upcomingTasks(7));
        assertEquals(TODAY.plusDays(1), planner.planDay(tomorrow));
        assertEquals(null, planner.planDay(undated));
        assertFalse(planner.upcomingTasks(7).contains(tooFar));
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
    void parentListShowsTheTasksOfItsSublists() {
        TaskList uni = planner.addList("Universidad");
        TaskList calculo = planner.addList("Cálculo", uni);
        TaskList parciales = planner.addList("Parciales", calculo);
        Task own = planner.addTask("Matrícula", uni);
        Task nested = planner.addTask("Taller 3", calculo);
        Task deep = planner.addTask("Parcial 1", parciales);

        assertEquals(List.of(own, nested, deep).size(), planner.tasksInList(uni).size());
        assertTrue(planner.tasksInList(uni).containsAll(List.of(own, nested, deep)));
        assertEquals(List.of(nested, deep).size(), planner.tasksInList(calculo).size());
        assertEquals(List.of(deep), planner.tasksInList(parciales));
        assertEquals(List.of(planner.defaultList(), uni, calculo, parciales), planner.listTree());
        assertEquals(List.of(uni, calculo, parciales), planner.pathOf(parciales));
    }

    @Test
    void deletingAListDeletesItsSublistsAndTheirTasks() {
        TaskList uni = planner.addList("Universidad");
        TaskList calculo = planner.addList("Cálculo", uni);
        planner.addTask("Taller 3", calculo);
        Task kept = planner.addTask("Comprar pan", planner.defaultList());

        assertTrue(planner.deleteList(uni));

        assertEquals(List.of(planner.defaultList()), planner.lists());
        assertEquals(List.of(kept), planner.tasks());
    }

    @Test
    void cannotDeleteAListThatHoldsEveryOtherList() {
        TaskList root = planner.defaultList();
        planner.addList("Sub", root);

        assertFalse(planner.deleteList(root));
        assertEquals(2, planner.lists().size());
    }

    @Test
    void jsonStoreRoundTrip(@TempDir Path dir) {
        JsonPlannerStore json = new JsonPlannerStore(dir.resolve("sub").resolve("zyora.json"));
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
        TaskList sub = first.addList("Sub", first.defaultList());
        Task gone = first.addTask("Borrada", sub);
        first.deleteTask(gone);

        Planner second = new Planner(json, CLOCK);

        Task loaded = second.findTask(task.getId()).orElseThrow();
        assertEquals("Persistir", loaded.getTitle());
        assertEquals(TODAY, loaded.getDueDate());
        assertEquals(TODAY.atTime(11, 15), loaded.getScheduledStart());
        assertTrue(loaded.isImportant());
        assertEquals("Reunión", second.findEvent(event.getId()).orElseThrow().getTitle());
        assertEquals(first.defaultList().getId(), second.findList(sub.getId()).orElseThrow().getParentId());
        assertEquals(CLOCK.instant(), loaded.getUpdatedAt());
        assertEquals(gone.getId(), json.load().getDeleted().get(0).getId());
    }

    @Test
    void newItemsAreStampedWhenAdded() {
        Task task = planner.addTask("Nueva", planner.defaultList());

        assertEquals(CLOCK.instant(), task.getUpdatedAt());
        assertEquals(CLOCK.instant(), planner.defaultList().getUpdatedAt());
    }

    @Test
    void onlyItemsThatChangedGetANewStamp() {
        MutableClock clock = new MutableClock(CLOCK.instant());
        Planner p = new Planner(new InMemoryStore(), clock);
        Task edited = p.addTask("Editada", p.defaultList());
        Task untouched = p.addTask("Igual", p.defaultList());
        Instant before = clock.instant();

        clock.advance(Duration.ofMinutes(5));
        edited.getSteps().add(new Step("Paso"));
        p.changed();

        assertEquals(clock.instant(), edited.getUpdatedAt());
        assertEquals(before, untouched.getUpdatedAt());
        assertEquals(before, p.defaultList().getUpdatedAt());
    }

    @Test
    void itemsFromOlderFilesGetAStampWhenLoaded() {
        InMemoryStore old = new InMemoryStore();
        TaskList list = new TaskList("Vieja", "#2564cf");
        old.initial.getLists().add(list);
        old.initial.getTasks().add(new Task("Sin fecha de cambio", list.getId()));

        Planner p = new Planner(old, CLOCK);

        assertEquals(CLOCK.instant(), list.getUpdatedAt());
        assertNotNull(p.tasks().get(0).getUpdatedAt());
    }

    @Test
    void deletionsLeaveTombstonesForOtherDevices() {
        TaskList uni = planner.addList("Universidad");
        TaskList calculo = planner.addList("Cálculo", uni);
        Task taller = planner.addTask("Taller", calculo);
        Task pan = planner.addTask("Pan", planner.defaultList());
        CalendarEvent event = new CalendarEvent();
        event.setStart(TODAY.atTime(9, 0));
        event.setEnd(TODAY.atTime(10, 0));
        planner.addEvent(event);

        planner.deleteList(uni);
        planner.deleteTask(pan);
        planner.deleteEvent(event);

        List<String> buried = store.last.getDeleted().stream().map(t -> t.getKind() + ":" + t.getId()).toList();
        assertTrue(buried.containsAll(List.of("LIST:" + uni.getId(), "LIST:" + calculo.getId(),
                "TASK:" + taller.getId(), "TASK:" + pan.getId(), "EVENT:" + event.getId())));
        assertEquals(5, buried.size());
        assertTrue(store.last.getDeleted().stream().allMatch(t -> CLOCK.instant().equals(t.getDeletedAt())));
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    void listColorCanBeChanged() {
        TaskList list = planner.addList("Casa");
        int saves = store.saves;

        planner.setListColor(list, "#7cc7a3");

        assertEquals("#7cc7a3", list.getColor());
        assertEquals(saves + 1, store.saves);
    }

    private static final class InMemoryStore implements PlannerStore {
        final PlannerData initial = new PlannerData();
        PlannerData last;
        int saves;

        @Override
        public PlannerData load() {
            return initial;
        }

        @Override
        public void save(PlannerData data) {
            last = data;
            saves++;
        }
    }
}
