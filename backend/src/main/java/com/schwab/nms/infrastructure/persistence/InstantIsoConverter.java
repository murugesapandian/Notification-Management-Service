package com.schwab.nms.infrastructure.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Stores every {@link Instant} column as a fixed-width, human-readable UTC string
 * ({@code yyyy-MM-dd'T'HH:mm:ss.SSS'Z'}, e.g. {@code 2026-09-10T05:04:14.826Z}) instead of
 * the epoch-millis integer SQLite otherwise persists a TIMESTAMP column as. Applies
 * automatically to every entity {@code Instant} attribute (see {@code db/migration/V1} for
 * the matching {@code VARCHAR(30)} column definitions).
 */
@Converter(autoApply = true)
public class InstantIsoConverter implements AttributeConverter<Instant, String> {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    @Override
    public String convertToDatabaseColumn(Instant attribute) {
        if (attribute == null) {
            return null;
        }
        return FORMATTER.withZone(ZoneOffset.UTC).format(attribute);
    }

    @Override
    public Instant convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        return LocalDateTime.parse(dbData, FORMATTER).toInstant(ZoneOffset.UTC);
    }
}
