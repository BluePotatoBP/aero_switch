package com.bluepotatobp.aeroswitch.compat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Turns parsed data + the installed-mod index into display rows. Pure, side-effect free. */
public final class CompatCatalog {

    /** An installed mod as observed through Fabric Loader. */
    public record InstalledMod(String id, String name, String description) {}

    private CompatCatalog() { }

    /**
     * Builds the row list: curated entries in JSON order, then untested (installed but uncatalogued)
     * mods sorted by name.
     */
    public static List<CompatibilityRow> build(CompatData data, Map<String, InstalledMod> installedByLowerId) {
        Map<String, InstalledMod> installed = installedByLowerId == null ? Map.of() : installedByLowerId;
        List<CompatibilityRow> rows = new ArrayList<>();
        Set<String> covered = new HashSet<>();

        for (ModEntry entry : data.mods()) {
            InstalledMod inst = findInstalled(entry, installed);
            boolean isInstalled = inst != null;
            if (isInstalled) {
                covered.add(inst.id().toLowerCase(Locale.ROOT));
            }
            String displayName = isInstalled
                    ? firstNonBlank(inst.name(), entry.name(), entry.id())
                    : firstNonBlank(entry.name(), entry.id());
            String description = firstNonBlank(inst != null ? inst.description() : null, entry.description());
            String note = data.noteFor(entry);
            String searchText = searchText(displayName, description, entry.note());
            rows.add(new CompatibilityRow(entry.id(), displayName, entry.status(), note, searchText, isInstalled));
        }

        List<CompatibilityRow> untested = new ArrayList<>();
        for (InstalledMod inst : installed.values()) {
            if (covered.contains(inst.id().toLowerCase(Locale.ROOT))) continue;
            String displayName = firstNonBlank(inst.name(), inst.id());
            String searchText = searchText(displayName, inst.description(), null);
            untested.add(new CompatibilityRow(inst.id(), displayName, Status.UNTESTED, null, searchText, true));
        }
        untested.sort(Comparator.comparing(row -> row.name().toLowerCase(Locale.ROOT)));
        rows.addAll(untested);
        return rows;
    }

    private static InstalledMod findInstalled(ModEntry entry, Map<String, InstalledMod> installed) {
        InstalledMod direct = installed.get(entry.id().toLowerCase(Locale.ROOT));
        if (direct != null) return direct;
        for (String alias : entry.aliases()) {
            InstalledMod viaAlias = installed.get(alias.toLowerCase(Locale.ROOT));
            if (viaAlias != null) return viaAlias;
        }
        return null;
    }

    /** Lowercased haystack for substring search: name + description + custom note (never shared notes). */
    private static String searchText(String name, String description, String customNote) {
        StringBuilder sb = new StringBuilder();
        append(sb, name);
        append(sb, description);
        append(sb, customNote);
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    private static void append(StringBuilder sb, String value) {
        if (value != null && !value.isBlank()) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(value.trim());
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return "";
    }
}
