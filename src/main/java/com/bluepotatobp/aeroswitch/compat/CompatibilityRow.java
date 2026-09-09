package com.bluepotatobp.aeroswitch.compat;

/**
 * A display row for the compatibility table.
 *
 * @param searchText precomputed lowercase haystack built from name + description + custom note only.
 *                   Shared (noteId-based) note text is deliberately excluded from search.
 */
public record CompatibilityRow( String id, String name, Status status, String note, String searchText, boolean installed ) {}
