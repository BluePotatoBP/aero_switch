package com.bluepotatobp.aeroswitch.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModOrigin;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Fabric/Minecraft-facing side of the compatibility feature: loads the curated JSON, indexes the
 * installed mods, decides the boot-time self-disable, and emits the throttled chat warning.
 */
public final class ModCompatibility {

    public static final String DATA_PATH = "/assets/aero_switch/compat/mods.json";
    public static final String OVERRIDE_PROPERTY = "aeroSwitch.ignoreConflicts";

    private static final Logger LOGGER = LoggerFactory.getLogger("aero_switch.compat");
    private static final long WARN_INTERVAL_NANOS = 3_000_000_000L;

    private static volatile boolean blocked;
    private static volatile List<CompatibilityRow> catalog = List.of();
    private static volatile List<String> blockingNames = List.of();
    private static long lastWarnNanos;

    private ModCompatibility() { }

    /** Runs in {@code preLaunch}, before the game is constructed. */
    public static void scan() {
        CompatData data = loadData();
        List<CompatibilityRow> rows = CompatCatalog.build(data, installedMods());
        catalog = rows;

        List<String> names = new ArrayList<>();
        for (CompatibilityRow row : rows) {
            if (row.status() == Status.BROKEN && row.installed()) names.add(row.name());
        }
        blockingNames = List.copyOf(names);
        boolean override = Boolean.getBoolean(OVERRIDE_PROPERTY);
        blocked = !override && !blockingNames.isEmpty();

        if (blocked) {
            LOGGER.warn("Aero Switch session engine DISABLED: known-conflicting mods installed: {}. " + "Remove them or launch with -D{}=true to force enable.", blockingNames, OVERRIDE_PROPERTY);
        } else if (!blockingNames.isEmpty()) {
            LOGGER.warn("Known-conflicting mods installed ({}) but -D{}=true forces the engine on.", blockingNames, OVERRIDE_PROPERTY);
        }
    }

    public static boolean blocked() {
        return blocked;
    }

    public static List<CompatibilityRow> rows() {
        return catalog;
    }

    public static void warnIfBlocked(Minecraft client) {
        if (!blocked || blockingNames.isEmpty() || client.gui == null) return;
        long now = System.nanoTime();
        if (now - lastWarnNanos < WARN_INTERVAL_NANOS) return;
        lastWarnNanos = now;
        client.gui.hud.getChat().addClientSystemMessage(Component.translatable("chat.aero_switch.blocked", String.join(", ", blockingNames), "-D" + OVERRIDE_PROPERTY + "=true"));
    }

    private static CompatData loadData() {
        try (InputStream in = ModCompatibility.class.getResourceAsStream(DATA_PATH)) {
            if (in == null) {
                LOGGER.warn("Compat data not found at {}; the tab will list installed mods as untested only.", DATA_PATH);
                return new CompatData(List.of(), List.of());
            }
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            CompatDataParser.ParseResult result = CompatDataParser.parse(json);
            for (String error : result.errors()) LOGGER.warn("Compat data: {}", error);
            return result.data();
        } catch (Exception e) {
            LOGGER.warn("Failed to load compat data from {}", DATA_PATH, e);
            return new CompatData(List.of(), List.of());
        }
    }

    private static Map<String, CompatCatalog.InstalledMod> installedMods() {
        Map<String, CompatCatalog.InstalledMod> map = new HashMap<>();
        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            var meta = mod.getMetadata();
            String id = meta.getId();
            ModOrigin origin = mod.getOrigin();
            if (!CompatFilter.isUserInstalledMod(id, origin == null ? null : origin.getKind())) continue;
            map.put(id.toLowerCase(Locale.ROOT), new CompatCatalog.InstalledMod(id, meta.getName(), meta.getDescription()));
        }
        return map;
    }
}
