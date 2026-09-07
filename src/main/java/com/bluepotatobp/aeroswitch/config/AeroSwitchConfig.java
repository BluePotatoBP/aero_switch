package com.bluepotatobp.aeroswitch.config;

import com.bluepotatobp.aeroswitch.AeroSwitchClient;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Small JSON-backed user settings for the session overlay. */
public final class AeroSwitchConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE =
            FabricLoader.getInstance().getConfigDir().resolve("aero_switch.json");

    public int borderThickness = 2;
    public boolean showInfoPanel = true;
    public boolean compactInfoPanel = false;
    /** Desaturation applied to inactive session panes, 0..100 (0 disables). */
    public int dimAmount = 50;
    /** Required modifier mask for each of the 8 focus bindings (GLFW mod bits, 0 = none). */
    public int[] focusModifiers = new int[] {
            InputConstants.MOD_ALT, InputConstants.MOD_ALT, InputConstants.MOD_ALT, InputConstants.MOD_ALT,
            InputConstants.MOD_ALT, InputConstants.MOD_ALT, InputConstants.MOD_ALT, InputConstants.MOD_ALT};
    /** Boot timestamp (epoch millis) captured at load time; seed for the random layout-icon order. */
    public long lastBoot;
    /** Artwork index per layout mode (LayoutMode ordinal), or null until generated. */
    public int[] layoutIcons;

    public static AeroSwitchConfig load() {
        AeroSwitchConfig config = new AeroSwitchConfig();
        if (!Files.exists(FILE)) {
            config.save();
            return config;
        }
        try {
            JsonObject json = GSON.fromJson(Files.readString(FILE), JsonObject.class);
            if (json == null) return config;
            if (json.has("borderThickness") && json.get("borderThickness").isJsonPrimitive()) {
                config.borderThickness = Math.clamp(json.get("borderThickness").getAsInt(), 0, 8);
            }
            if (json.has("showInfoPanel") && json.get("showInfoPanel").isJsonPrimitive()) {
                config.showInfoPanel = json.get("showInfoPanel").getAsBoolean();
            }
            if (json.has("compactInfoPanel") && json.get("compactInfoPanel").isJsonPrimitive()) {
                config.compactInfoPanel = json.get("compactInfoPanel").getAsBoolean();
            }
            if (json.has("dimAmount") && json.get("dimAmount").isJsonPrimitive()) {
                config.dimAmount = Math.clamp(json.get("dimAmount").getAsInt(), 0, 100);
            } else if (json.has("dimInactive") && json.get("dimInactive").isJsonPrimitive()) {
                // Migrate the previous boolean toggle: off -> 0, on -> 50.
                config.dimAmount = json.get("dimInactive").getAsBoolean() ? 50 : 0;
            }
            if (json.has("focusModifiers") && json.get("focusModifiers").isJsonArray()) {
                JsonArray mods = json.getAsJsonArray("focusModifiers");
                for (int i = 0; i < Math.min(mods.size(), config.focusModifiers.length); i++) {
                    config.focusModifiers[i] = sanitizeModifier(mods.get(i).getAsInt());
                }
            }
            if (json.has("lastBoot") && json.get("lastBoot").isJsonPrimitive()) {
                config.lastBoot = json.get("lastBoot").getAsLong();
            }
            if (json.has("layoutIcons") && json.get("layoutIcons").isJsonArray()) {
                JsonArray icons = json.getAsJsonArray("layoutIcons");
                config.layoutIcons = new int[icons.size()];
                for (int i = 0; i < icons.size(); i++) {
                    config.layoutIcons[i] = Math.max(-1, icons.get(i).getAsInt());
                }
            }
        } catch (Exception error) {
            AeroSwitchClient.LOGGER.warn("Could not read Aero Switch config; using defaults", error);
        }
        return config;
    }

    public void save() {
        try {
            Files.createDirectories(FILE.getParent());
            JsonObject json = new JsonObject();
            json.addProperty("borderThickness", borderThickness);
            json.addProperty("showInfoPanel", showInfoPanel);
            json.addProperty("compactInfoPanel", compactInfoPanel);
            json.addProperty("dimAmount", dimAmount);
            JsonArray mods = new JsonArray();
            for (int modifier : focusModifiers) mods.add(modifier);
            json.add("focusModifiers", mods);
            json.addProperty("lastBoot", lastBoot);
            if (layoutIcons != null) {
                JsonArray icons = new JsonArray();
                for (int icon : layoutIcons) icons.add(icon);
                json.add("layoutIcons", icons);
            }
            Files.writeString(FILE, GSON.toJson(json));
        } catch (IOException error) {
            AeroSwitchClient.LOGGER.warn("Could not save Aero Switch config", error);
        }
    }

    private static int sanitizeModifier(int value) {
        return switch (value) {
            case InputConstants.MOD_SHIFT, InputConstants.MOD_CONTROL, InputConstants.MOD_ALT -> value;
            default -> 0;
        };
    }
}
