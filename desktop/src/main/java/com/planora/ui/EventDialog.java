package com.planora.ui;

import com.planora.model.CalendarEvent;
import com.planora.service.Planner;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.Window;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/** Creates or edits an event. The event is only modified when the user saves. */
class EventDialog extends Dialog<EventDialog.Action> {
    enum Action { SAVE, DELETE }

    private final TextField title = new TextField();
    private final DatePicker startDate = new DatePicker();
    private final ComboBox<LocalTime> startTime = timeBox();
    private final DatePicker endDate = new DatePicker();
    private final ComboBox<LocalTime> endTime = timeBox();
    private final CheckBox allDay = new CheckBox("Todo el día");
    private final ToggleGroup color = new ToggleGroup();
    private final TextField location = new TextField();
    private final TextArea notes = new TextArea();
    private final Label error = new Label();

    EventDialog(CalendarEvent event, boolean isNew, Window owner) {
        Dialogs.decorate(this, owner);
        setHeaderText(isNew ? "Nuevo evento" : "Editar evento");

        ButtonType save = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        ButtonType delete = new ButtonType("Eliminar", ButtonBar.ButtonData.LEFT);
        if (!isNew) {
            getDialogPane().getButtonTypes().add(delete);
            getDialogPane().lookupButton(delete).getStyleClass().add("danger");
        }

        title.setText(event.getTitle());
        title.setPromptText("Agregar un título");
        startDate.setValue(event.getStart().toLocalDate());
        if (event.isAllDay()) {
            // Sensible times in case the user turns "all day" off.
            startTime.setValue(LocalTime.of(9, 0));
            endTime.setValue(LocalTime.of(10, 0));
            endDate.setValue(event.getEnd().minusDays(1).toLocalDate());
        } else {
            startTime.setValue(event.getStart().toLocalTime());
            endTime.setValue(event.getEnd().toLocalTime());
            endDate.setValue(event.getEnd().toLocalDate());
        }
        allDay.setSelected(event.isAllDay());
        location.setText(event.getLocation());
        location.setPromptText("Ubicación");
        notes.setText(event.getNotes());
        notes.setPromptText("Notas");
        notes.setPrefRowCount(3);
        notes.setWrapText(true);
        error.getStyleClass().add("error-label");

        startTime.disableProperty().bind(allDay.selectedProperty());
        endTime.disableProperty().bind(allDay.selectedProperty());
        startDate.valueProperty().addListener((obs, old, d) -> {
            if (d != null && endDate.getValue() != null && endDate.getValue().isBefore(d)) {
                endDate.setValue(d);
            }
        });
        startTime.valueProperty().addListener((obs, old, t) -> {
            if (t != null && endTime.getValue() != null && startDate.getValue() != null
                    && startDate.getValue().equals(endDate.getValue()) && !endTime.getValue().isAfter(t)) {
                LocalDateTime end = startDate.getValue().atTime(t).plusHours(1);
                endDate.setValue(end.toLocalDate());
                endTime.setValue(end.toLocalTime());
            }
        });

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.getStyleClass().add("dialog-body");
        form.addRow(0, new Label("Título"), title);
        form.addRow(1, new Label("Inicio"), new HBox(6, startDate, startTime));
        form.addRow(2, new Label("Fin"), new HBox(6, endDate, endTime));
        form.addRow(3, new Label(""), allDay);
        form.addRow(4, new Label("Color"), swatches(event.getColor()));
        form.addRow(5, new Label("Ubicación"), location);
        form.addRow(6, new Label("Notas"), notes);
        form.add(error, 1, 7);
        getDialogPane().setContent(form);

        Node saveButton = getDialogPane().lookupButton(save);
        saveButton.disableProperty().bind(title.textProperty().map(String::isBlank));
        saveButton.addEventFilter(ActionEvent.ACTION, e -> {
            String problem = validate();
            if (problem != null) {
                error.setText(problem);
                e.consume();
            }
        });

        setResultConverter(button -> {
            if (button == save) {
                apply(event);
                return Action.SAVE;
            }
            return button == delete ? Action.DELETE : null;
        });
        setOnShown(e -> title.requestFocus());
    }

    private String validate() {
        if (startDate.getValue() == null || endDate.getValue() == null) {
            return "Indica las fechas de inicio y fin.";
        }
        if (allDay.isSelected()) {
            return endDate.getValue().isBefore(startDate.getValue()) ? "El fin debe ser posterior al inicio." : null;
        }
        if (startTime.getValue() == null || endTime.getValue() == null) {
            return "Indica las horas de inicio y fin.";
        }
        if (!endDate.getValue().atTime(endTime.getValue()).isAfter(startDate.getValue().atTime(startTime.getValue()))) {
            return "El fin debe ser posterior al inicio.";
        }
        return null;
    }

    private void apply(CalendarEvent event) {
        event.setTitle(title.getText().strip());
        event.setAllDay(allDay.isSelected());
        LocalDate from = startDate.getValue();
        LocalDate to = endDate.getValue();
        if (allDay.isSelected()) {
            event.setStart(from.atStartOfDay());
            event.setEnd(to.plusDays(1).atStartOfDay());
        } else {
            event.setStart(from.atTime(startTime.getValue()));
            event.setEnd(to.atTime(endTime.getValue()));
        }
        event.setColor((String) color.getSelectedToggle().getUserData());
        event.setLocation(location.getText().strip());
        event.setNotes(notes.getText());
    }

    private static ComboBox<LocalTime> timeBox() {
        ComboBox<LocalTime> box = new ComboBox<>();
        box.getItems().setAll(Fmt.timeOptions());
        box.setConverter(Fmt.timeConverter());
        box.setVisibleRowCount(10);
        box.setPrefWidth(100);
        return box;
    }

    /** One round swatch per palette color, plus the event's own color if it is not in the palette. */
    private HBox swatches(String current) {
        List<String> options = new ArrayList<>(Planner.COLORS);
        if (options.stream().noneMatch(c -> c.equalsIgnoreCase(current))) {
            options.add(0, current);
        }
        HBox row = new HBox(6);
        for (String c : options) {
            ToggleButton swatch = new ToggleButton();
            swatch.getStyleClass().add("swatch");
            swatch.setStyle("-fx-background-color: " + Palette.bar(c) + ";");
            swatch.textProperty().bind(swatch.selectedProperty().map(selected -> selected ? "✓" : ""));
            swatch.setUserData(c);
            swatch.setToggleGroup(color);
            swatch.setSelected(c.equalsIgnoreCase(current));
            row.getChildren().add(swatch);
        }
        // Clicking the selected swatch again must not leave the event without a color.
        color.selectedToggleProperty().addListener((obs, old, now) -> {
            if (now == null && old != null) {
                old.setSelected(true);
            }
        });
        return row;
    }
}
