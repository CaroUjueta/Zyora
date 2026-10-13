package com.planora.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/** How Zyora data looks as JSON, the same in the data file and between devices. */
public final class PlannerJson {
    static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(SerializationFeature.INDENT_OUTPUT);

    private PlannerJson() {
    }

    public static byte[] write(PlannerData data) {
        try {
            return MAPPER.writeValueAsBytes(data);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static PlannerData read(InputStream in) throws IOException {
        return MAPPER.readValue(in, PlannerData.class);
    }

    /** A deep copy that shares nothing with the original. */
    static <T> T copy(T value, Class<T> type) {
        return MAPPER.convertValue(value, type);
    }

    /** Overwrites every field of {@code target} with the ones of {@code source}. */
    static void copyInto(Object target, Object source) {
        try {
            MAPPER.updateValue(target, source);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
