package com.planora.ui;

import com.planora.service.AgendaItem;
import com.planora.service.Planner;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;

/**
 * A month calendar that floats on the desktop, like the phone's home-screen widget. It has no
 * title bar or taskbar button: drag it anywhere to move it, right-click for its menu. Whether it
 * is open, where it sits and whether it stays on top are kept in {@code widget.properties} next to
 * the data file.
 */
public class DesktopWidget {
    private static final double WIDTH = 400;
    private static final double HEIGHT = 430;
    /** Room around the card for its shadow; the window is transparent there. */
    private static final double SHADOW = 14;
    /** Lines under each day number; when there are more items, the last line says how many. */
    private static final int MAX_LINES = 2;

    private final Planner planner;
    private final Consumer<LocalDate> onOpen;
    private final Runnable onQuit;
    private final Path file;
    private final Stage stage = new Stage(StageStyle.TRANSPARENT);
    /** Never visible: owning the widget keeps it off the taskbar. */
    private final Stage owner = new Stage(StageStyle.UTILITY);
    private final ReadOnlyBooleanWrapper showing = new ReadOnlyBooleanWrapper(false);
    private final CheckMenuItem onTopItem = new CheckMenuItem("Siempre encima de las ventanas");
    private final Label monthLabel = new Label();
    private final GridPane grid = new GridPane();
    /** How many months away from the current one the widget is showing. */
    private int offset;
    private LocalDate shownToday;
    private double dragX;
    private double dragY;

    /**
     * @param onOpen opens the main window, on the given day's calendar or, for {@code null}, as it was
     * @param onQuit closes Zyora altogether
     */
    public DesktopWidget(Planner planner, Path dataFile, String stylesheet, Consumer<LocalDate> onOpen, Runnable onQuit) {
        this.planner = planner;
        this.onOpen = onOpen;
        this.onQuit = onQuit;
        this.file = dataFile.toAbsolutePath().resolveSibling("widget.properties");

        VBox card = new VBox(6, header(), weekdays(), grid);
        card.getStyleClass().add("widget-card");
        VBox.setVgrow(grid, Priority.ALWAYS);
        StackPane root = new StackPane(card);
        root.getStyleClass().add("widget-root");
        root.setStyle("-fx-padding: " + SHADOW + ";");
        root.setOnContextMenuRequested(e -> menu().show(root, e.getScreenX(), e.getScreenY()));
        root.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            dragX = e.getScreenX() - stage.getX();
            dragY = e.getScreenY() - stage.getY();
        });
        root.addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                stage.setX(e.getScreenX() - dragX);
                stage.setY(e.getScreenY() - dragY);
            }
        });
        root.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {
            if (!e.isStillSincePress()) {
                save();
            }
        });

        for (int c = 0; c < 7; c++) {
            ColumnConstraints col = new ColumnConstraints();
            col.setPercentWidth(100.0 / 7);
            grid.getColumnConstraints().add(col);
        }
        for (int r = 0; r < 6; r++) {
            RowConstraints row = new RowConstraints();
            row.setPercentHeight(100.0 / 6);
            grid.getRowConstraints().add(row);
        }
        grid.getStyleClass().add("widget-grid");

        Scene scene = new Scene(root, WIDTH + 2 * SHADOW, HEIGHT + 2 * SHADOW, Color.TRANSPARENT);
        scene.getStylesheets().add(stylesheet);
        stage.setScene(scene);
        stage.setTitle("Zyora · Mes");
        stage.initOwner(owner);
        stage.setOnHidden(e -> showing.set(false));
        stage.setOnShown(e -> showing.set(true));

        owner.setOpacity(0);
        owner.setWidth(1);
        owner.setHeight(1);
        owner.setX(-10_000);
        owner.setY(-10_000);

        onTopItem.setOnAction(e -> {
            stage.setAlwaysOnTop(onTopItem.isSelected());
            save();
        });

        planner.addListener(this::refresh);
        // Moves "today" along when the date changes while the widget is open.
        Timeline clock = new Timeline(new KeyFrame(Duration.minutes(1), e -> {
            if (!planner.today().equals(shownToday)) {
                refresh();
            }
        }));
        clock.setCycleCount(Timeline.INDEFINITE);
        clock.play();
        refresh();
    }

    public ReadOnlyBooleanProperty showingProperty() {
        return showing.getReadOnlyProperty();
    }

    public boolean isShowing() {
        return showing.get();
    }

    /** Whether the widget was open the last time Zyora closed. */
    public boolean wasOpen() {
        return Boolean.parseBoolean(load().getProperty("open", "false"));
    }

    public void show() {
        if (stage.isShowing()) {
            stage.toFront();
            return;
        }
        Properties props = load();
        boolean onTop = Boolean.parseBoolean(props.getProperty("onTop", "false"));
        onTopItem.setSelected(onTop);
        stage.setAlwaysOnTop(onTop);
        placeAt(props);
        owner.show();
        stage.show();
        save();
    }

    public void hide() {
        stage.hide();
        owner.hide();
        save();
    }

    public void toggle() {
        if (isShowing()) {
            hide();
        } else {
            show();
        }
    }

    // ---- layout --------------------------------------------------------------------------

    private Node header() {
        monthLabel.getStyleClass().add("widget-month");
        monthLabel.setTooltip(new Tooltip("Abrir Zyora"));
        monthLabel.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.isStillSincePress()) {
                onOpen.accept(null);
            }
        });
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button close = headerButton("×", "Ocultar el widget", this::hide);
        close.getStyleClass().add("widget-close");
        HBox header = new HBox(4, monthLabel, spacer,
                headerButton("Hoy", "Volver al mes actual", () -> shift(0)),
                headerButton("‹", "Mes anterior", () -> shift(-1)),
                headerButton("›", "Mes siguiente", () -> shift(1)),
                close);
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    private static Button headerButton(String text, String tip, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add("widget-button");
        button.setTooltip(new Tooltip(tip));
        button.setFocusTraversable(false);
        button.setOnAction(e -> action.run());
        return button;
    }

    private Node weekdays() {
        GridPane row = new GridPane();
        for (int c = 0; c < 7; c++) {
            ColumnConstraints col = new ColumnConstraints();
            col.setPercentWidth(100.0 / 7);
            row.getColumnConstraints().add(col);
            String name = DayOfWeek.MONDAY.plus(c).getDisplayName(TextStyle.NARROW_STANDALONE, Fmt.ES);
            Label label = new Label(Fmt.capitalize(name));
            label.getStyleClass().add("widget-weekday");
            label.setMaxWidth(Double.MAX_VALUE);
            row.add(label, c, 0);
        }
        return row;
    }

    private ContextMenu menu() {
        MenuItem open = new MenuItem("Abrir Zyora");
        open.setOnAction(e -> onOpen.accept(null));
        MenuItem hide = new MenuItem("Ocultar el widget");
        hide.setOnAction(e -> hide());
        MenuItem quit = new MenuItem("Salir de Zyora");
        quit.setOnAction(e -> onQuit.run());
        return new ContextMenu(open, onTopItem, new SeparatorMenuItem(), hide, quit);
    }

    /** Moves a month back or forward; a delta of 0 goes back to the current month. */
    private void shift(int delta) {
        offset = delta == 0 ? 0 : offset + delta;
        refresh();
    }

    private void refresh() {
        LocalDate today = planner.today();
        shownToday = today;
        YearMonth month = YearMonth.from(today).plusMonths(offset);
        monthLabel.setText(Fmt.capitalize(Fmt.MONTH_YEAR.format(month)));
        grid.getChildren().clear();
        LocalDate first = month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        for (int i = 0; i < 42; i++) {
            grid.add(dayCell(first.plusDays(i), month, today), i % 7, i / 7);
        }
    }

    private Node dayCell(LocalDate day, YearMonth month, LocalDate today) {
        Label number = new Label(String.valueOf(day.getDayOfMonth()));
        number.getStyleClass().add("widget-day");
        VBox cell = new VBox(2, number);
        cell.setAlignment(Pos.TOP_CENTER);
        cell.getStyleClass().add("widget-cell");
        if (!YearMonth.from(day).equals(month)) {
            cell.getStyleClass().add("other-month");
        }
        if (day.equals(today)) {
            cell.getStyleClass().add("today");
        }

        List<AgendaItem> items = planner.agendaFor(day);
        int titles = items.size() > MAX_LINES ? MAX_LINES - 1 : items.size();
        for (AgendaItem item : items.subList(0, titles)) {
            Label chip = new Label(item.title());
            chip.getStyleClass().add("widget-item");
            if (item.completed()) {
                chip.getStyleClass().add("completed");
            }
            chip.setStyle(Palette.itemStyle(item.color()));
            chip.setMaxWidth(Double.MAX_VALUE);
            chip.setMinWidth(0);
            cell.getChildren().add(chip);
        }
        if (titles < items.size()) {
            Label more = new Label("+" + (items.size() - titles) + " más");
            more.getStyleClass().add("widget-more");
            cell.getChildren().add(more);
        }

        cell.setMinSize(0, 0);
        cell.setPrefHeight(0);
        cell.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(cell.widthProperty());
        clip.heightProperty().bind(cell.heightProperty());
        cell.setClip(clip);
        cell.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.isStillSincePress()) {
                onOpen.accept(day);
            }
        });
        return cell;
    }

    // ---- position and settings -----------------------------------------------------------

    /** The saved spot if it is still on a screen, otherwise the top right corner of the main one. */
    private void placeAt(Properties props) {
        try {
            double x = Double.parseDouble(props.getProperty("x"));
            double y = Double.parseDouble(props.getProperty("y"));
            Rectangle2D area = new Rectangle2D(x + SHADOW, y + SHADOW, 60, 60);
            if (!Screen.getScreensForRectangle(area).isEmpty()) {
                stage.setX(x);
                stage.setY(y);
                return;
            }
        } catch (NullPointerException | NumberFormatException ignored) {
            // Never placed, or the file was edited by hand.
        }
        Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
        stage.setX(bounds.getMaxX() - WIDTH - 2 * SHADOW - 16);
        stage.setY(bounds.getMinY() + 16);
    }

    private Properties load() {
        Properties props = new Properties();
        if (Files.exists(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                props.load(in);
            } catch (IOException ignored) {
                // Unreadable: the widget starts closed, in its default spot.
            }
        }
        return props;
    }

    private void save() {
        Properties props = new Properties();
        props.setProperty("open", String.valueOf(stage.isShowing()));
        props.setProperty("onTop", String.valueOf(onTopItem.isSelected()));
        props.setProperty("x", String.valueOf(stage.getX()));
        props.setProperty("y", String.valueOf(stage.getY()));
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream out = Files.newOutputStream(file)) {
                props.store(out, "Zyora: el widget de escritorio");
            }
        } catch (IOException ignored) {
            // Only the widget's spot is lost.
        }
    }
}
