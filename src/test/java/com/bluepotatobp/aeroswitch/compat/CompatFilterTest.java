package com.bluepotatobp.aeroswitch.compat;

import org.junit.jupiter.api.Test;

import java.util.List;

import net.fabricmc.loader.api.metadata.ModOrigin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompatFilterTest {

    private static CompatibilityRow row(String id, Status status, String searchText, boolean installed) {
        return new CompatibilityRow(id, id, status, null, searchText, installed);
    }

    @Test
    void normalizeQueryTrimsAndLowercases() {
        assertEquals("foo bar", CompatFilter.normalizeQuery("  Foo BAR "));
        assertEquals("", CompatFilter.normalizeQuery(null));
    }

    @Test
    void effectiveQueryRequiresTwoChars() {
        assertNull(CompatFilter.effectiveQuery("a"));
        assertNull(CompatFilter.effectiveQuery(" a "));
        assertNull(CompatFilter.effectiveQuery(""));
        assertNull(CompatFilter.effectiveQuery(null));
        assertEquals("ab", CompatFilter.effectiveQuery("AB"));
    }

    @Test
    void baseAppliesOnlyInstalledAndSearch() {
        List<CompatibilityRow> rows = List.of(
                row("a", Status.WORKS, "sodium render", true),
                row("b", Status.BROKEN, "physics explode", true),
                row("c", Status.WORKS, "sodium render", false));

        assertEquals(2, CompatFilter.base(rows, "", true).size());
        assertEquals(3, CompatFilter.base(rows, "", false).size());
        assertEquals(List.of("a"), ids(CompatFilter.base(rows, "sodium", true)));
        assertEquals(List.of("a", "c"), ids(CompatFilter.base(rows, "sodium", false)));
        assertEquals(2, CompatFilter.base(rows, "s", true).size()); // one char query is ignored, only installed kept
    }

    @Test
    void searchUsesSearchTextOnly() {
        // searchText is precomputed by CompatCatalog; the filter just substring-matches it.
        List<CompatibilityRow> rows = List.of(row("a", Status.WORKS, "better clouds vanilla fallback", true));
        assertFalse(CompatFilter.base(rows, "fallback", true).isEmpty());
        assertTrue(CompatFilter.base(rows, "shared-note", true).isEmpty());
    }

    @Test
    void countsComputePerStatus() {
        List<CompatibilityRow> rows = List.of(
                row("a", Status.WORKS, "", true),
                row("b", Status.WORKS, "", true),
                row("c", Status.PATCHED, "", true),
                row("d", Status.BROKEN, "", true),
                row("e", Status.UNTESTED, "", true));
        CompatFilter.Counts c = CompatFilter.counts(rows);
        assertEquals(5, c.total());
        assertEquals(2, c.works());
        assertEquals(1, c.patched());
        assertEquals(1, c.broken());
        assertEquals(1, c.untested());
    }

    @Test
    void applyTypeFiltersAndNullMeansAll() {
        List<CompatibilityRow> rows = List.of(
                row("a", Status.WORKS, "", true),
                row("b", Status.BROKEN, "", true));
        assertEquals(2, CompatFilter.applyType(rows, null).size());
        assertEquals(List.of("b"), ids(CompatFilter.applyType(rows, Status.BROKEN)));
    }

    @Test
    void onlyUserInstalledModsAreListed() {
        assertTrue(CompatFilter.isUserInstalledMod("sodium", ModOrigin.Kind.PATH));
        assertTrue(CompatFilter.isUserInstalledMod("fabric-api-base", ModOrigin.Kind.PATH));
        assertFalse(CompatFilter.isUserInstalledMod("fabric-api-base", ModOrigin.Kind.NESTED));
        assertFalse(CompatFilter.isUserInstalledMod("fabricloader", ModOrigin.Kind.UNKNOWN));
        assertFalse(CompatFilter.isUserInstalledMod("minecraft", ModOrigin.Kind.PATH));
        assertFalse(CompatFilter.isUserInstalledMod("java", ModOrigin.Kind.PATH));
        assertFalse(CompatFilter.isUserInstalledMod("any", null));
    }

    @Test
    void paginationMath() {
        assertEquals(1, CompatFilter.pageCount(0, 10));
        assertEquals(1, CompatFilter.pageCount(5, 10));
        assertEquals(1, CompatFilter.pageCount(10, 10));
        assertEquals(3, CompatFilter.pageCount(21, 10));

        assertEquals(0, CompatFilter.clampPage(-5, 3));
        assertEquals(0, CompatFilter.clampPage(0, 3));
        assertEquals(2, CompatFilter.clampPage(99, 3));
    }

    @Test
    void pageSlicesRows() {
        List<CompatibilityRow> rows = List.of(
                row("a", Status.WORKS, "", true),
                row("b", Status.WORKS, "", true),
                row("c", Status.WORKS, "", true));
        assertEquals(2, CompatFilter.page(rows, 0, 2).size());
        assertEquals(List.of("c"), ids(CompatFilter.page(rows, 1, 2)));
        assertTrue(CompatFilter.page(rows, 2, 2).isEmpty());
    }

    private static List<String> ids(List<CompatibilityRow> rows) {
        return rows.stream().map(CompatibilityRow::id).toList();
    }
}
