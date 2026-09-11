package com.bluepotatobp.aeroswitch.compat;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompatCatalogTest {

    @Test
    void buildsCuratedRowsAndUntestedTail() {
        CompatData data = new CompatData(
                List.of("shared note"),
                List.of(
                        new ModEntry("sodium", List.of(), null, Status.WORKS, null, null, null, null),
                        new ModEntry("betterclouds", List.of(), null, Status.PATCHED, null, 0, null, "1.14.4"),
                        new ModEntry("axiom", List.of(), "Axiom", Status.PATCHED, "custom note", null, null, null)));
        Map<String, CompatCatalog.InstalledMod> installed = Map.of(
                "sodium", new CompatCatalog.InstalledMod("sodium", "Sodium", "Renderer"),
                "betterclouds", new CompatCatalog.InstalledMod("betterclouds", "Better Clouds", "Clouds"),
                "unlisted", new CompatCatalog.InstalledMod("unlisted", "Unlisted", "Desc"));

        List<CompatibilityRow> rows = CompatCatalog.build(data, installed);

        assertEquals(4, rows.size());

        CompatibilityRow sodium = rows.get(0);
        assertTrue(sodium.installed());
        assertEquals("Sodium", sodium.name());

        CompatibilityRow bc = rows.get(1);
        assertEquals("shared note", bc.note()); // noteId resolved for display
        assertFalse(bc.searchText().contains("shared note")); // shared note excluded from search

        CompatibilityRow axiom = rows.get(2);
        assertEquals("custom note", axiom.note());
        assertTrue(axiom.searchText().contains("custom note")); // custom note searchable

        assertEquals("1.14.4", rows.get(1).version()); // tested version carried to the row

        CompatibilityRow unlisted = rows.get(3);
        assertEquals(Status.UNTESTED, unlisted.status());
        assertTrue(unlisted.installed());
        assertEquals("Unlisted", unlisted.name());
    }

    @Test
    void aliasMatchesInstalledModAndSuppressesUntested() {
        CompatData data = new CompatData(List.of(), List.of(
                new ModEntry("bc", List.of("betterclouds", "qendolin_bc"), null, Status.PATCHED, "x", null, null, null)));
        Map<String, CompatCatalog.InstalledMod> installed = Map.of(
                "betterclouds", new CompatCatalog.InstalledMod("betterclouds", "Better Clouds", ""));

        List<CompatibilityRow> rows = CompatCatalog.build(data, installed);
        assertEquals(1, rows.size());
        assertTrue(rows.get(0).installed());
        assertEquals(Status.PATCHED, rows.get(0).status());
    }

    @Test
    void uninstalledCuratedRowKeepsCuratedName() {
        CompatData data = new CompatData(List.of(), List.of(
                new ModEntry("iris", List.of(), "Iris", Status.WORKS, null, null, "Shader packs", null)));
        List<CompatibilityRow> rows = CompatCatalog.build(data, Map.of());
        assertEquals(1, rows.size());
        CompatibilityRow iris = rows.get(0);
        assertFalse(iris.installed());
        assertEquals("Iris", iris.name());
        assertTrue(iris.searchText().contains("shader packs")); // curated description searchable
    }

    @Test
    void untestedRowsSortedByName() {
        CompatData data = new CompatData(List.of(), List.of());
        Map<String, CompatCatalog.InstalledMod> installed = Map.of(
                "z", new CompatCatalog.InstalledMod("z", "Zeta", ""),
                "a", new CompatCatalog.InstalledMod("a", "Alpha", ""));

        List<CompatibilityRow> rows = CompatCatalog.build(data, installed);
        assertEquals(List.of("Alpha", "Zeta"), rows.stream().map(CompatibilityRow::name).toList());
    }
}
