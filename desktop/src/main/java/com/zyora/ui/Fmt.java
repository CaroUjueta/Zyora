package com.zyora.ui;

import com.zyora.model.TaskList;
import javafx.util.StringConverter;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Spanish date and time formatting shared by the views. */
final class Fmt {
    static final Locale ES = Locale.forLanguageTag("es");
    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", ES);
    static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("EEE d MMM", ES);
    static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", ES);
    static final DateTimeFormatter DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("d MMM yyyy", ES);
    static final DateTimeFormatter MONTH_YEAR = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", ES);

    private Fmt() {
    }

    /** List names joined as a breadcrumb, e.g. "Universidad › Cálculo". */
    static String path(List<TaskList> lists) {
        return lists.stream().map(TaskList::getName).collect(Collectors.joining(" › "));
    }

    static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Every quarter hour of the day, for time pickers. */
    static List<LocalTime> timeOptions() {
        List<LocalTime> times = new ArrayList<>();
        for (int m = 0; m < 24 * 60; m += 15) {
            times.add(LocalTime.of(m / 60, m % 60));
        }
        return times;
    }

    static StringConverter<LocalTime> timeConverter() {
        return new StringConverter<>() {
            @Override
            public String toString(LocalTime time) {
                return time == null ? "" : TIME.format(time);
            }

            @Override
            public LocalTime fromString(String text) {
                try {
                    return text == null || text.isBlank() ? null : LocalTime.parse(text.strip(), TIME);
                } catch (DateTimeParseException e) {
                    return null;
                }
            }
        };
    }
}
