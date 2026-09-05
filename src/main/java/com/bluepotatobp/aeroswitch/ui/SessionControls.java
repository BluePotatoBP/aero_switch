package com.bluepotatobp.aeroswitch.ui;

import com.bluepotatobp.aeroswitch.session.SessionManager;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.resources.Identifier;

public final class SessionControls {
    private static KeyMapping managerKey;
    private static KeyMapping switchKey;

    private SessionControls() { }

    public static void initialize() {
        if (!SessionManager.get().isEnabled()) return;
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("aero_switch", "sessions"));
        managerKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.aero_switch.manager", InputConstants.Type.KEYSYM, InputConstants.KEY_F8, category));
        switchKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.aero_switch.next", InputConstants.Type.KEYSYM, InputConstants.KEY_F7, category));
    }

    /**
     * Handles F8/F7 at the raw keyboard event level. Vanilla only "clicks" key
     * mappings while no screen is open, so tick/render-based polling misses the
     * manager toggle whenever a pause screen or any other GUI is showing.
     *
     * @return true when the event was consumed and vanilla key handling should stop
     */
    public static boolean handleKeyPress(int action, KeyEvent event) {
        if (action != 1) return false;
        SessionManager manager = SessionManager.get();
        if (!manager.isEnabled() || managerKey == null || switchKey == null) return false;
        if (Minecraft.getInstance().gui.screen() instanceof KeyBindsScreen) return false;
        if (managerKey.matches(event)) {
            manager.setFocusedScreen(manager.focusedScreen() instanceof SessionScreen ? null : new SessionScreen());
            return true;
        }
        if (switchKey.matches(event)) {
            int next = manager.focusedSlot() == 0 ? 1 : 0;
            if (manager.hasSession(next)) {
                manager.focus(next);
            }
            return true;
        }
        return false;
    }
}
