package com.bluepotatobp.aeroswitch.compat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Parses and validates the shipped {@code mods.json} document. Pure, side-effect free. */
public final class CompatDataParser {

    public record ParseResult(CompatData data, List<String> errors) {}

    private CompatDataParser() { }

    public static ParseResult parse(String json) {
        List<String> errors = new ArrayList<>();
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) {
                return new ParseResult(new CompatData(List.of(), List.of()), List.of("top-level value is not an object"));
            }
            JsonObject obj = root.getAsJsonObject();

            List<String> notes = new ArrayList<>();
            if (obj.has("notes")) {
                if (obj.get("notes").isJsonArray()) {
                    for (JsonElement el : obj.get("notes").getAsJsonArray()) {
                        notes.add(el.isJsonPrimitive() ? el.getAsString() : "");
                    }
                } else {
                    errors.add("'notes' is not an array");
                }
            }

            List<ModEntry> mods = new ArrayList<>();
            if (obj.has("mods")) {
                if (obj.get("mods").isJsonArray()) {
                    JsonArray arr = obj.get("mods").getAsJsonArray();
                    for (int i = 0; i < arr.size(); i++) {
                        JsonElement el = arr.get(i);
                        if (!el.isJsonObject()) {
                            errors.add("mods[" + i + "]: not an object");
                            continue;
                        }
                        ModEntry entry = parseEntry(el.getAsJsonObject(), i, notes.size(), errors);
                        if (entry != null) mods.add(entry);
                    }
                } else {
                    errors.add("'mods' is not an array");
                }
            } else {
                errors.add("missing 'mods' array");
            }

            return new ParseResult(new CompatData(notes, mods), errors);
        } catch (JsonSyntaxException | IllegalStateException e) {
            return new ParseResult(new CompatData(List.of(), List.of()), List.of("malformed JSON: " + e.getMessage()));
        }
    }

    private static ModEntry parseEntry(JsonObject o, int index, int noteCount, List<String> errors) {
        String id = str(o, "id");
        if (id == null || id.isBlank()) {
            errors.add("mods[" + index + "]: missing id");
            return null;
        }
        Status status = parseStatus(str(o, "status"));
        if (status == null || status == Status.UNTESTED) {
            errors.add("mods[" + index + "] (" + id + "): invalid status '" + str(o, "status") + "'");
            return null;
        }
        String note = str(o, "note");
        Integer noteId = intOrNull(o, "noteId");
        if (note != null && noteId != null) {
            errors.add("mods[" + index + "] (" + id + "): 'note' and 'noteId' are mutually exclusive");
            return null;
        }
        if (noteId != null && (noteId < 0 || noteId >= noteCount)) {
            errors.add("mods[" + index + "] (" + id + "): noteId " + noteId + " out of range 0.." + (noteCount - 1));
            return null;
        }
        return new ModEntry(id, strList(o, "aliases"), str(o, "name"), status, note, noteId, str(o, "description"), str(o, "version"));
    }

    private static Status parseStatus(String raw) {
        if (raw == null) return null;
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "works" -> Status.WORKS;
            case "patched" -> Status.PATCHED;
            case "broken" -> Status.BROKEN;
            default -> null;
        };
    }

    private static String str(JsonObject o, String key) {
        if (!o.has(key) || !o.get(key).isJsonPrimitive()) return null;
        JsonPrimitive p = o.get(key).getAsJsonPrimitive();
        return p.isString() ? p.getAsString() : null;
    }

    private static Integer intOrNull(JsonObject o, String key) {
        if (!o.has(key) || !o.get(key).isJsonPrimitive()) return null;
        JsonPrimitive p = o.get(key).getAsJsonPrimitive();
        return p.isNumber() ? p.getAsInt() : null;
    }

    private static List<String> strList(JsonObject o, String key) {
        if (!o.has(key) || !o.get(key).isJsonArray()) return List.of();
        JsonArray arr = o.get(key).getAsJsonArray();
        List<String> out = new ArrayList<>(arr.size());
        for (JsonElement el : arr) {
            if (el.isJsonPrimitive()) out.add(el.getAsString());
        }
        return out;
    }
}
