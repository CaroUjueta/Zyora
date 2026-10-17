package com.zyora.service;

import com.zyora.model.CalendarEvent;
import com.zyora.model.Step;
import com.zyora.model.Task;
import com.zyora.model.TaskList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A computer and a phone that edit separately and then sync the way the apps do. */
class SyncMergerTest {
    private final TestClock clock = new TestClock(Instant.parse("2026-10-12T10:00:00Z"));
    private Planner pc;
    private Planner phone;

    @BeforeEach
    void setUp() {
        pc = new Planner(new InMemoryStore(), clock);
        tick();
        phone = new Planner(new InMemoryStore(), clock);
        tick();
    }

    @Test
    void newItemsFromEachSideReachTheOther() {
        pc.addTask("Desde el PC", pc.defaultList());
        CalendarEvent event = new CalendarEvent();
        event.setTitle("Cita");
        event.setStart(LocalDateTime.of(2026, 10, 13, 9, 0));
        event.setEnd(LocalDateTime.of(2026, 10, 13, 10, 0));
        phone.addEvent(event);
        phone.addTask("Desde el teléfono", phone.defaultList());

        sync();

        for (Planner p : List.of(pc, phone)) {
            assertEquals(List.of("Desde el PC", "Desde el teléfono"), titles(p));
            assertEquals(1, p.events().size());
        }
    }

    @Test
    void theDefaultListsOfBothDevicesBecomeOne() {
        phone.addTask("Comprar pan", phone.defaultList());
        TaskList work = phone.addList("Trabajo");
        pc.addList("trabajo ");

        sync();

        for (Planner p : List.of(pc, phone)) {
            assertEquals(List.of("Tareas", "trabajo"), p.lists().stream().map(TaskList::getName).toList());
            assertEquals("Tareas", p.listOf(p.tasks().get(0)).getName());
        }
        assertTrue(pc.findList(work.getId()).isEmpty());

        // Syncing again changes nothing.
        String before = json(pc);
        sync();
        assertEquals(before, json(pc));
        assertEquals(before, json(phone));
    }

    @Test
    void theMostRecentEditWins() {
        Task task = pc.addTask("Original", pc.defaultList());
        sync();

        pc.findTask(task.getId()).orElseThrow().setTitle("Editada en el PC");
        pc.changed();
        tick();
        Task onPhone = phone.findTask(task.getId()).orElseThrow();
        onPhone.setTitle("Editada en el teléfono");
        onPhone.getSteps().add(new Step("Paso"));
        phone.changed();

        sync();

        for (Planner p : List.of(pc, phone)) {
            Task t = p.findTask(task.getId()).orElseThrow();
            assertEquals("Editada en el teléfono", t.getTitle());
            assertEquals(1, t.getSteps().size());
        }
    }

    @Test
    void deletionsReachTheOtherDevice() {
        Task task = pc.addTask("Borrar", pc.defaultList());
        TaskList list = pc.addList("Viaje");
        pc.addTask("Maleta", list);
        sync();

        phone.deleteTask(phone.findTask(task.getId()).orElseThrow());
        phone.deleteList(phone.findList(list.getId()).orElseThrow());
        sync();

        for (Planner p : List.of(pc, phone)) {
            assertTrue(p.tasks().isEmpty());
            assertTrue(p.findList(list.getId()).isEmpty());
        }
    }

    @Test
    void anEditAfterTheDeletionKeepsTheItem() {
        Task task = pc.addTask("Importante", pc.defaultList());
        sync();

        phone.deleteTask(phone.findTask(task.getId()).orElseThrow());
        tick();
        pc.findTask(task.getId()).orElseThrow().setImportant(true);
        pc.changed();
        sync();

        assertTrue(pc.findTask(task.getId()).isPresent());
        assertTrue(phone.findTask(task.getId()).isPresent());
    }

    @Test
    void anEditBeforeTheDeletionDoesNotBringTheItemBack() {
        Task task = pc.addTask("Vieja", pc.defaultList());
        sync();

        pc.findTask(task.getId()).orElseThrow().setTitle("Vieja, editada");
        pc.changed();
        tick();
        phone.deleteTask(phone.findTask(task.getId()).orElseThrow());
        sync();

        assertTrue(pc.tasks().isEmpty());
        assertTrue(phone.tasks().isEmpty());
    }

    @Test
    void screensKeepTheirObjectsAfterASync() {
        Task task = phone.addTask("Abierta", phone.defaultList());
        task.getSteps().add(new Step("Uno"));
        phone.changed();
        sync();

        Task shown = pc.findTask(task.getId()).orElseThrow();
        tick();
        phone.findTask(task.getId()).orElseThrow().setNotes("nota");
        phone.changed();
        sync();

        assertSame(shown, pc.findTask(task.getId()).orElseThrow());
        assertEquals("nota", shown.getNotes());
        assertEquals(1, shown.getSteps().size());
    }

    @Test
    void dataSurvivesTheTripAsJson() throws IOException {
        pc.addTask("Viaja", pc.defaultList());
        pc.deleteTask(pc.addTask("Borrada", pc.defaultList()));
        PlannerData back = PlannerJson.read(new ByteArrayInputStream(PlannerJson.write(pc.snapshot())));
        assertEquals(json(pc), new String(PlannerJson.write(back)));
        assertFalse(back.getDeleted().isEmpty());
    }

    /** What happens when the phone taps «Sincronizar»: the computer merges, then the phone. */
    private void sync() {
        tick();
        PlannerData fromPhone = phone.snapshot();
        pc.replaceWith(SyncMerger.merge(pc.snapshot(), fromPhone, clock.instant()));
        PlannerData answer = pc.snapshot();
        tick();
        phone.replaceWith(SyncMerger.merge(answer, phone.snapshot(), clock.instant()));
        tick();
    }

    private void tick() {
        clock.now = clock.now.plus(Duration.ofMinutes(1));
    }

    private static List<String> titles(Planner p) {
        return p.tasks().stream().map(Task::getTitle).sorted().toList();
    }

    private static String json(Planner p) {
        return new String(PlannerJson.write(p.snapshot()));
    }

    private static final class InMemoryStore implements PlannerStore {
        @Override
        public PlannerData load() {
            return new PlannerData();
        }

        @Override
        public void save(PlannerData data) {
        }
    }

    private static final class TestClock extends Clock {
        Instant now;

        TestClock(Instant now) {
            this.now = now;
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
}
