package com.bluepotatobp.aeroswitch.session;

import com.bluepotatobp.aeroswitch.AeroSwitchClient;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

/** Boot-once random assignment of numbered layout artwork to layout modes. */
final class LayoutIconPicker {
    private final SessionManager owner;
    private int[] icons;
    private boolean ready;

    LayoutIconPicker(SessionManager owner) {
        this.owner = owner;
    }

    /** The artwork image index for a mode, or -1 when none is assigned. */
    int iconFor(SessionManager.LayoutMode mode) {
        int[] assigned = icons();
        int index = mode.ordinal();
        return assigned != null && index < assigned.length ? assigned[index] : -1;
    }

    /** Boot-once random assignment of numbered artwork to layout modes, seeded by the boot timestamp. */
    private int[] icons() {
        if (ready) return icons;
        ready = true;
        SessionManager.LayoutMode[] modes = SessionManager.LayoutMode.values();
        int[] assigned = new int[modes.length];
        Arrays.fill(assigned, -1);
        if (owner.config == null) {
            icons = assigned;
            return icons;
        }

        List<Integer> available = listLayoutImages(owner.mc());
        if (available.isEmpty()) {
            icons = assigned;
            return icons;
        }

        if (owner.config.lastBoot == owner.bootTimestamp && owner.config.layoutIcons != null
                && owner.config.layoutIcons.length == modes.length) {
            assigned = owner.config.layoutIcons;
        } else {
            List<Integer> shuffled = new ArrayList<>(available);
            Collections.shuffle(shuffled, new Random(owner.bootTimestamp));
            for (int i = 0; i < modes.length; i++) {
                assigned[i] = shuffled.get(i % shuffled.size());
            }
            owner.config.lastBoot = owner.bootTimestamp;
            owner.config.layoutIcons = assigned;
            owner.mc().schedule(owner.config::save);
        }
        icons = assigned;
        return icons;
    }

    private static List<Integer> listLayoutImages(Minecraft client) {
        List<Integer> numbers = new ArrayList<>();
        try {
            Map<Identifier, Resource> found = client.getResourceManager().listResources("textures/layout",
                    id -> id.getNamespace().equals("aero_switch")
                            && id.getPath().matches("textures/layout/[0-9]+\\.png"));
            for (Identifier id : found.keySet()) {
                String path = id.getPath();
                String name = path.substring("textures/layout/".length(), path.length() - ".png".length());
                try {
                    numbers.add(Integer.parseInt(name));
                } catch (NumberFormatException ignored) { }
            }
            Collections.sort(numbers);
        } catch (Exception error) {
            AeroSwitchClient.LOGGER.warn("Could not enumerate Aero Switch layout artwork; using diagrams", error);
        }
        return numbers;
    }
}
