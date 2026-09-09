package com.bluepotatobp.aeroswitch.compat;

import java.util.List;

/** The parsed curated compat document: shared notes plus the ordered mod entries. */
public record CompatData(List<String> notes, List<ModEntry> mods) {

    public CompatData {
        notes = notes == null ? List.of() : List.copyOf(notes);
        mods = mods == null ? List.of() : List.copyOf(mods);
    }

    /** Resolves an entry's display note: custom note first, then the shared note indexed by {@code noteId}. */
    public String noteFor(ModEntry entry) {
        String custom = entry.note();
        if (custom != null) return custom;
        Integer noteId = entry.noteId();
        if (noteId == null) return null;
        return noteId >= 0 && noteId < notes.size() ? notes.get(noteId) : null;
    }
}
