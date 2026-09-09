package com.bluepotatobp.aeroswitch.compat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.fabricmc.loader.api.metadata.ModOrigin;

/** Pure filtering, counting, and pagination over compatibility rows. */
public final class CompatFilter {

    public static final int MIN_QUERY = 2;

    public record Counts(int total, int works, int patched, int broken, int untested) {}

    private CompatFilter() { }

    public static String normalizeQuery(String raw) {
        if (raw == null) return "";
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    /** The query is only active once it has at least {@link #MIN_QUERY} characters; otherwise null. */
    public static String effectiveQuery(String raw) {
        String normalized = normalizeQuery(raw);
        return normalized.length() >= MIN_QUERY ? normalized : null;
    }

    /** Applies the only-installed and search filters; type filtering is applied separately. */
    public static List<CompatibilityRow> base(List<CompatibilityRow> rows, String query, boolean onlyInstalled) {
        String q = effectiveQuery(query);
        List<CompatibilityRow> out = new ArrayList<>();
        for (CompatibilityRow row : rows) {
            if (onlyInstalled && !row.installed()) continue;
            if (q != null && !row.searchText().contains(q)) continue;
            out.add(row);
        }
        return out;
    }

    public static Counts counts(List<CompatibilityRow> base) {
        int works = 0, patched = 0, broken = 0, untested = 0;
        for (CompatibilityRow row : base) {
            switch (row.status()) {
                case WORKS -> works++;
                case PATCHED -> patched++;
                case BROKEN -> broken++;
                case UNTESTED -> untested++;
            }
        }
        return new Counts(base.size(), works, patched, broken, untested);
    }

    /** Keeps only rows of {@code type}; {@code null} means no type filter (All). */
    public static List<CompatibilityRow> applyType(List<CompatibilityRow> base, Status type) {
        if (type == null) return base;
        List<CompatibilityRow> out = new ArrayList<>();
        for (CompatibilityRow row : base) {
            if (row.status() == type) out.add(row);
        }
        return out;
    }

    public static int pageCount(int total, int pageSize) {
        if (pageSize <= 0 || total <= 0) return 1;
        return (total + pageSize - 1) / pageSize;
    }

    public static int clampPage(int page, int pageCount) {
        if (pageCount <= 0) return 0;
        if (page < 0) return 0;
        return Math.min(page, pageCount - 1);
    }

    public static List<CompatibilityRow> page(List<CompatibilityRow> rows, int page, int pageSize) {
        if (pageSize <= 0) return List.of();
        int from = Math.max(0, page) * pageSize;
        if (from >= rows.size()) return List.of();
        int to = Math.min(rows.size(), from + pageSize);
        return rows.subList(from, to);
    }

    /**
     * Whether a mod should be listed at all. Only top-level, user-installed jars qualify: the
     * built-in pseudo-mods and anything nested inside another jar are excluded. In a production
     * install Fabric API's bundled module jars are NESTED, so this hides them; the dev classpath
     * lays them out as separate PATH entries, which is a dev-only artifact.
     */
    public static boolean isUserInstalledMod(String id, ModOrigin.Kind kind) {
        return kind == ModOrigin.Kind.PATH && !isBuiltInMod(id);
    }

    private static boolean isBuiltInMod(String id) {
        return id.equals("minecraft") || id.equals("java") || id.equals("fabricloader");
    }
}
