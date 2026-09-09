package com.bluepotatobp.aeroswitch.ui;

import com.bluepotatobp.aeroswitch.compat.CompatFilter;
import com.bluepotatobp.aeroswitch.compat.CompatibilityRow;
import com.bluepotatobp.aeroswitch.compat.ModCompatibility;
import com.bluepotatobp.aeroswitch.compat.Status;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiTableFlags;
import imgui.type.ImString;
import net.minecraft.client.Minecraft;

import java.util.List;

/** The "Mod compatibility" deck tab: curated table with search, mutually-exclusive filters, and pagination. */
final class ModCompatTab {

    private static final long DEBOUNCE_NANOS = 500_000_000L;
    private static final ImString SEARCH = new ImString(128);
    /** Applied, normalized (trimmed + lowercased) search query. */
    private static String applied = "";
    private static long lastEditNanos;
    /** Active type filter; {@code null} = All. */
    private static Status type;
    private static boolean onlyInstalled = true;
    private static int page;

    private ModCompatTab() { }

    static void draw(Minecraft client) {
        float scale = ImGuiSessionOverlay.guiScale;
        List<CompatibilityRow> rows = ModCompatibility.rows();

        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        if (ImGui.inputTextWithHint("##compat_search", "Search (min 2 chars)...", SEARCH)) {
            lastEditNanos = System.nanoTime();
        }
        long now = System.nanoTime();
        String normalized = CompatFilter.normalizeQuery(SEARCH.get());
        if (!normalized.equals(applied) && now - lastEditNanos >= DEBOUNCE_NANOS) {
            applied = normalized;
            page = 0;
        }

        List<CompatibilityRow> base = CompatFilter.base(rows, applied, onlyInstalled);
        CompatFilter.Counts counts = CompatFilter.counts(base);

        filterButton("All", counts.total(), type == null, () -> type = null);
        ImGui.sameLine();
        filterButton("Works", counts.works(), type == Status.WORKS, () -> type = Status.WORKS);
        ImGui.sameLine();
        filterButton("Patched", counts.patched(), type == Status.PATCHED, () -> type = Status.PATCHED);
        ImGui.sameLine();
        filterButton("Broken", counts.broken(), type == Status.BROKEN, () -> type = Status.BROKEN);
        ImGui.sameLine();
        filterButton("Untested", counts.untested(), type == Status.UNTESTED, () -> type = Status.UNTESTED);

        ImGui.dummy(0.0F, 4.0F * scale);
        boolean only = onlyInstalled;
        if (ImGui.checkbox("Only installed", only)) {
            onlyInstalled = !only;
            page = 0;
        }
        ImGui.separator();

        List<CompatibilityRow> filtered = CompatFilter.applyType(base, type);
        float paginationHeight = ImGui.getFrameHeightWithSpacing();
        float rowHeight = ImGui.getTextLineHeightWithSpacing();
        int pageSize = Math.max(5, (int) ((ImGui.getContentRegionAvailY() - paginationHeight) / rowHeight));
        int pages = CompatFilter.pageCount(filtered.size(), pageSize);
        page = CompatFilter.clampPage(page, pages);
        List<CompatibilityRow> pageRows = CompatFilter.page(filtered, page, pageSize);

        if (pageRows.isEmpty()) {
            ImGui.textDisabled("No mods match.");
        } else if (ImGui.beginTable("compat_table", 3, ImGuiTableFlags.RowBg | ImGuiTableFlags.SizingStretchSame | ImGuiTableFlags.NoSavedSettings)) {
            ImGui.tableSetupScrollFreeze(0, 1);
            ImGui.tableSetupColumn("Name");
            ImGui.tableSetupColumn("Compatibility", ImGuiTableColumnFlags.WidthFixed, 150.0F * scale);
            ImGui.tableSetupColumn("Note");
            ImGui.tableHeadersRow();
            for (CompatibilityRow row : pageRows) {
                drawRow(row);
            }
            ImGui.endTable();
        }

        if (pages > 1) {
            ImGui.dummy(0.0F, 2.0F * scale);
            float btnWidth = 64.0F * scale;
            if (ImGui.button("Prev", btnWidth, 0.0F)) {
                page = Math.max(0, page - 1);
            }
            ImGui.sameLine();
            ImGui.text("Page " + (page + 1) + " of " + pages);
            ImGui.sameLine(ImGui.getContentRegionAvailX() - btnWidth);
            if (ImGui.button("Next", btnWidth, 0.0F)) {
                page = Math.min(pages - 1, page + 1);
            }
        }
    }

    private static void filterButton(String label, int count, boolean selected, Runnable onClick) {
        if (selected) {
            ImGui.pushStyleColor(ImGuiCol.Button, 44, 114, 142, 255);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 56, 139, 171, 255);
        }
        boolean clicked = ImGui.button(label + " (" + count + ")", 0.0F, 0.0F);
        if (selected) {
            ImGui.popStyleColor();
            ImGui.popStyleColor();
        }
        if (clicked) {
            onClick.run();
            page = 0;
        }
    }

    private static void drawRow(CompatibilityRow row) {
        ImGui.tableNextRow();
        ImGui.tableSetColumnIndex(0);
        if (row.installed()) {
            ImGui.text(row.name());
        } else {
            ImGui.pushStyleColor(ImGuiCol.Text, 130, 130, 130, 255);
            ImGui.text(row.name());
            ImGui.popStyleColor();
        }
        ImGui.tableSetColumnIndex(1);
        drawStatus(row.status());
        ImGui.tableSetColumnIndex(2);
        String note = row.note();
        if (note == null || note.isBlank()) {
            ImGui.pushStyleColor(ImGuiCol.Text, 130, 130, 130, 255);
            ImGui.text("-");
            ImGui.popStyleColor();
        } else {
            String shortNote = note.length() > 60 ? note.substring(0, 60) + "..." : note;
            ImGui.text(shortNote);
            if (ImGui.isItemHovered()) {
                ImGui.beginTooltip();
                ImGui.pushTextWrapPos(ImGui.getFontSize() * 40.0F);
                ImGui.textWrapped(note);
                ImGui.popTextWrapPos();
                ImGui.endTooltip();
            }
        }
    }

    private static void drawStatus(Status status) {
        switch (status) {
            case WORKS -> statusText("Works", 96, 205, 130, 255);
            case PATCHED -> statusText("Patched", 224, 185, 60, 255);
            case BROKEN -> statusText("Broken", 224, 80, 80, 255);
            case UNTESTED -> statusText("Untested", 150, 150, 150, 255);
        }
    }

    private static void statusText(String label, int r, int g, int b, int a) {
        ImGui.pushStyleColor(ImGuiCol.Text, r, g, b, a);
        ImGui.text(label);
        ImGui.popStyleColor();
    }
}
