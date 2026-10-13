package com.planora.ui;

import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Who uses this copy of Zyora, kept in {@code profile.properties} next to the data file. Only on
 * this computer: it is not sent to the phone.
 */
public class Profile {
    private final Path file;
    private final ReadOnlyStringWrapper name = new ReadOnlyStringWrapper("");

    public Profile(Path dataFile) {
        this.file = dataFile.toAbsolutePath().resolveSibling("profile.properties");
        Properties props = new Properties();
        if (Files.exists(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                props.load(in);
            } catch (IOException ignored) {
                // Unreadable: Zyora asks for the name again.
            }
        }
        name.set(props.getProperty("name", "").strip());
    }

    ReadOnlyStringProperty nameProperty() {
        return name.getReadOnlyProperty();
    }

    boolean hasName() {
        return !name.get().isEmpty();
    }

    void setName(String value) {
        name.set(value.strip());
        Properties props = new Properties();
        props.setProperty("name", name.get());
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream out = Files.newOutputStream(file)) {
                props.store(out, "Zyora: tu nombre para el saludo de Inicio");
            }
        } catch (IOException ignored) {
            // Kept for this session; Zyora will ask again next time.
        }
    }
}
