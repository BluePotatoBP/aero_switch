package com.bluepotatobp.aeroswitch.compat;

import java.util.List;

/**
 * One curated mod entry, as parsed from the shipped compat JSON.
 *
 * <p>The note is populated exactly one of two ways: {@code note} carries a custom description, or
 * {@code noteId} indexes the shared {@link CompatData#notes()} array. Entries with neither have no
 * note; entries with both are rejected at parse time.
 */
public record ModEntry(
        String id,
        List<String> aliases,
        String name,
        Status status,
        String note,
        Integer noteId,
        String description) {

    public ModEntry {
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
    }
}
