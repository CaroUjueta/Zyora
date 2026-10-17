package com.zyora.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Stores all data in a single JSON file, written atomically so a crash never leaves it half-written. */
public class JsonPlannerStore implements PlannerStore {
    private final Path file;

    public JsonPlannerStore(Path file) {
        this.file = file;
    }

    public Path file() {
        return file;
    }

    @Override
    public PlannerData load() {
        if (!Files.exists(file)) {
            return new PlannerData();
        }
        try {
            return PlannerJson.MAPPER.readValue(file.toFile(), PlannerData.class);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer " + file, e);
        }
    }

    @Override
    public void save(PlannerData data) {
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            PlannerJson.MAPPER.writeValue(tmp.toFile(), data);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar " + file, e);
        }
    }
}
