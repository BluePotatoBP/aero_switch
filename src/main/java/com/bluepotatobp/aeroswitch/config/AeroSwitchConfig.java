package com.bluepotatobp.aeroswitch.config;

import com.bluepotatobp.aeroswitch.AeroSwitchClient;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Small JSON-backed user settings for the session overlay. */
public final class AeroSwitchConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE =
            FabricLoader.getInstance().getConfigDir().resolve("aero_switch.json");

    public boolean showBorders = true;
    public int borderThickness = 2;

    public static AeroSwitchConfig load() {
        AeroSwitchConfig config = new AeroSwitchConfig();
        if (!Files.exists(FILE)) {
            config.save();
            return config;
        }
        try {
            JsonObject json = GSON.fromJson(Files.readString(FILE), JsonObject.class);
            if (json == null) return config;
            if (json.has("showBorders") && json.get("showBorders").isJsonPrimitive()) {
                config.showBorders = json.get("showBorders").getAsBoolean();
            }
            if (json.has("borderThickness") && json.get("borderThickness").isJsonPrimitive()) {
                config.borderThickness = Math.clamp(json.get("borderThickness").getAsInt(), 1, 8);
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
            json.addProperty("showBorders", showBorders);
            json.addProperty("borderThickness", borderThickness);
            Files.writeString(FILE, GSON.toJson(json));
        } catch (IOException error) {
            AeroSwitchClient.LOGGER.warn("Could not save Aero Switch config", error);
        }
    }
}
