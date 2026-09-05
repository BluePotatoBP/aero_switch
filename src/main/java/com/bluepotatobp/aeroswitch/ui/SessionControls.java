package com.bluepotatobp.aeroswitch.ui;

import com.bluepotatobp.aeroswitch.session.SessionManager;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public final class SessionControls {
    private SessionControls() { }

    public static void initialize() {
        if (!SessionManager.get().isEnabled()) return;
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("aero_switch", "sessions"));
        KeyMapping managerKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.aero_switch.manager", InputConstants.Type.KEYSYM, InputConstants.KEY_F8, category));
        KeyMapping switchKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.aero_switch.next", InputConstants.Type.KEYSYM, InputConstants.KEY_F7, category));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (managerKey.consumeClick()) {
                client.gui.setScreen(new SessionScreen());
            }
            while (switchKey.consumeClick()) {
                SessionManager manager = SessionManager.get();
                int next = manager.focusedSlot() == 0 ? 1 : 0;
                if (manager.hasSession(next)) {
                    manager.focus(next);
                }
            }
        });
    }
}
