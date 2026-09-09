package com.bluepotatobp.aeroswitch.compat;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompatDataParserTest {

    @Test
    void parsesValidDocument() {
        CompatDataParser.ParseResult r = CompatDataParser.parse("""
                {
                  "notes": ["n0", "n1"],
                  "mods": [
                    { "id": "a", "status": "works" },
                    { "id": "b", "status": "patched", "noteId": 1 },
                    { "id": "c", "status": "broken", "note": "custom" }
                  ]
                }
                """);
        assertTrue(r.errors().isEmpty(), r.errors().toString());
        assertEquals(List.of("n0", "n1"), r.data().notes());
        assertEquals(3, r.data().mods().size());
        assertEquals(Status.BROKEN, r.data().mods().get(2).status());
    }

    @Test
    void statusIsCaseInsensitive() {
        CompatDataParser.ParseResult r = CompatDataParser.parse("{ \"mods\": [ { \"id\": \"x\", \"status\": \"PATCHED\" } ] }");
        assertEquals(Status.PATCHED, r.data().mods().get(0).status());
    }

    @Test
    void skipsEntryWithMissingId() {
        CompatDataParser.ParseResult r = CompatDataParser.parse("{ \"mods\": [ { \"status\": \"works\" } ] }");
        assertTrue(r.data().mods().isEmpty());
        assertEquals(1, r.errors().size());
    }

    @Test
    void skipsEntryWithUnknownStatus() {
        CompatDataParser.ParseResult r = CompatDataParser.parse("{ \"mods\": [ { \"id\": \"x\", \"status\": \"banana\" } ] }");
        assertTrue(r.data().mods().isEmpty());
        assertEquals(1, r.errors().size());
    }

    @Test
    void rejectsReservedUntestedStatus() {
        CompatDataParser.ParseResult r = CompatDataParser.parse("{ \"mods\": [ { \"id\": \"x\", \"status\": \"untested\" } ] }");
        assertTrue(r.data().mods().isEmpty());
        assertEquals(1, r.errors().size());
    }

    @Test
    void rejectsNoteAndNoteIdTogether() {
        CompatDataParser.ParseResult r = CompatDataParser.parse(
                "{ \"notes\": [\"n\"], \"mods\": [ { \"id\": \"x\", \"status\": \"works\", \"note\": \"c\", \"noteId\": 0 } ] }");
        assertTrue(r.data().mods().isEmpty());
        assertEquals(1, r.errors().size());
    }

    @Test
    void rejectsOutOfRangeNoteId() {
        CompatDataParser.ParseResult r = CompatDataParser.parse(
                "{ \"notes\": [\"n\"], \"mods\": [ { \"id\": \"x\", \"status\": \"works\", \"noteId\": 5 } ] }");
        assertTrue(r.data().mods().isEmpty());
        assertEquals(1, r.errors().size());
    }

    @Test
    void noteForResolvesSharedAndCustomNotes() {
        CompatDataParser.ParseResult r = CompatDataParser.parse("""
                {
                  "notes": ["shared"],
                  "mods": [
                    { "id": "a", "status": "works", "noteId": 0 },
                    { "id": "b", "status": "works", "note": "custom" },
                    { "id": "c", "status": "works" }
                  ]
                }
                """);
        CompatData data = r.data();
        assertEquals("shared", data.noteFor(data.mods().get(0)));
        assertEquals("custom", data.noteFor(data.mods().get(1)));
        assertNull(data.noteFor(data.mods().get(2)));
    }

    @Test
    void malformedJsonYieldsErrors() {
        CompatDataParser.ParseResult r = CompatDataParser.parse("{ not json");
        assertTrue(r.data().mods().isEmpty());
        assertFalse(r.errors().isEmpty());
    }
}
