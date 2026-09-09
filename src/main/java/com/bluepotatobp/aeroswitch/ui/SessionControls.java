package com.bluepotatobp.aeroswitch.ui;

import com.bluepotatobp.aeroswitch.compat.ModCompatibility;
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
    /** [0..3] left/right/up/down, [4..7] slots 1..4. */
    private static final KeyMapping[] FOCUS = new KeyMapping[8];
    private static KeyMapping pendingRebind;

    /** Selectable modifier for a focus binding; the mask is GLFW mod bits. */
    public enum Modifier {
        NONE(0, "None"),
        ALT(InputConstants.MOD_ALT, "Alt"),
        CTRL(InputConstants.MOD_CONTROL, "Ctrl"),
        SHIFT(InputConstants.MOD_SHIFT, "Shift");

        public final int mask;
        public final String label;

        Modifier(int mask, String label) {
            this.mask = mask;
            this.label = label;
        }

        public static Modifier fromMask(int mask) {
            for (Modifier modifier : values()) if (modifier.mask == mask) return modifier;
            return NONE;
        }
    }

    private SessionControls() { }

    public static void initialize() {
        if (!SessionManager.get().isEnabled() && !ModCompatibility.blocked()) return;
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("aero_switch", "sessions"));
        managerKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.aero_switch.manager", InputConstants.Type.KEYSYM, InputConstants.KEY_F8, category));
        switchKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.aero_switch.next", InputConstants.Type.KEYSYM, InputConstants.KEY_F7, category));
        FOCUS[0] = register("key.aero_switch.focus_left", InputConstants.KEY_LEFT, category);
        FOCUS[1] = register("key.aero_switch.focus_right", InputConstants.KEY_RIGHT, category);
        FOCUS[2] = register("key.aero_switch.focus_up", InputConstants.KEY_UP, category);
        FOCUS[3] = register("key.aero_switch.focus_down", InputConstants.KEY_DOWN, category);
        FOCUS[4] = register("key.aero_switch.focus_slot_1", InputConstants.KEY_1, category);
        FOCUS[5] = register("key.aero_switch.focus_slot_2", InputConstants.KEY_2, category);
        FOCUS[6] = register("key.aero_switch.focus_slot_3", InputConstants.KEY_3, category);
        FOCUS[7] = register("key.aero_switch.focus_slot_4", InputConstants.KEY_4, category);
    }

    private static KeyMapping register(String name, int key, KeyMapping.Category category) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping(name, InputConstants.Type.KEYSYM, key, category));
    }

    /** The eight focus-switch bindings shown in the manager's keybinds tab. */
    public static KeyMapping[] focusBindings() {
        return FOCUS;
    }

    /** The F8 manager-toggle and F7 next-session keys shown in the keybinds tab. */
    public static KeyMapping[] sessionKeys() {
        return new KeyMapping[] { managerKey, switchKey };
    }

    public static void beginRebind(KeyMapping mapping) {
        pendingRebind = mapping;
    }

    public static void cancelRebind() {
        pendingRebind = null;
    }

    public static KeyMapping rebinding() {
        return pendingRebind;
    }

    /**
     * Handles F8/F7 plus the Alt+arrow / Alt+number focus bindings at the raw
     * keyboard event level. Vanilla only "clicks" key mappings while no screen is
     * open, so tick/render-based polling misses the manager toggle whenever a pause
     * screen or any other GUI is showing.
     *
     * @return true when the event was consumed and vanilla key handling should stop
     */
    public static boolean handleKeyPress(int action, KeyEvent event) {
        if (action != 1) return false;
        SessionManager manager = SessionManager.get();
        if (!manager.isEnabled()) {
            if (!ModCompatibility.blocked()) return false;
            Minecraft client = Minecraft.getInstance();
            if (client.gui.screen() instanceof KeyBindsScreen) return false;
            if (matchesSessionBinding(event)) {
                ModCompatibility.warnIfBlocked(client);
                return true;
            }
            return false;
        }

        // A pending rebind consumes the next non-modifier key press.
        if (pendingRebind != null) {
            captureRebind(event);
            return true;
        }

        if (Minecraft.getInstance().gui.screen() instanceof KeyBindsScreen) return false;
        if (managerKey == null || switchKey == null) return false;

        if (managerKey.matches(event)) {
            boolean wasOpen = manager.focusedScreen() instanceof SessionScreen;
            manager.setFocusedScreen(wasOpen ? null : new SessionScreen());
            if (wasOpen) cancelRebind();
            return true;
        }
        if (switchKey.matches(event)) {
            for (int step = 1; step <= manager.maxSessions(); step++) {
                int next = (manager.focusedSlot() + step) % manager.maxSessions();
                if (manager.hasSession(next)) {
                    manager.focus(next);
                    break;
                }
            }
            return true;
        }

        for (int i = 0; i < FOCUS.length; i++) {
            if (!FOCUS[i].matches(event)) continue;
            int mask = manager.focusModifierMask(i);
            if (mask != 0 && (event.modifiers() & mask) != mask) return false;
            return switch (i) {
                case 0 -> manager.focusDirection(SessionManager.Direction.LEFT);
                case 1 -> manager.focusDirection(SessionManager.Direction.RIGHT);
                case 2 -> manager.focusDirection(SessionManager.Direction.UP);
                case 3 -> manager.focusDirection(SessionManager.Direction.DOWN);
                default -> manager.focusByNumber(i - 3);
            };
        }
        return false;
    }

    private static boolean matchesSessionBinding(KeyEvent event) {
        if (managerKey != null && managerKey.matches(event)) return true;
        if (switchKey != null && switchKey.matches(event)) return true;
        for (KeyMapping mapping : FOCUS) {
            if (mapping != null && mapping.matches(event)) return true;
        }
        return false;
    }

    private static void captureRebind(KeyEvent event) {
        int key = event.key();
        // Ignore modifier keys: the user is expected to press the actual key, so an
        // Alt/Shift/Ctrl that arrives first does not become the binding.
        if (isModifier(key)) return;
        if (key == InputConstants.KEY_ESCAPE) {
            pendingRebind.setKey(InputConstants.UNKNOWN);
        } else {
            pendingRebind.setKey(InputConstants.getKey(event));
        }
        pendingRebind = null;
        KeyMapping.resetMapping();
    }

    private static boolean isModifier(int key) {
        return key == InputConstants.KEY_LALT || key == InputConstants.KEY_RALT
                || key == InputConstants.KEY_LSHIFT || key == InputConstants.KEY_RSHIFT
                || key == InputConstants.KEY_LCONTROL || key == InputConstants.KEY_RCONTROL
                || key == InputConstants.KEY_LSUPER || key == InputConstants.KEY_RSUPER
                || key == InputConstants.KEY_CAPSLOCK || key == InputConstants.KEY_NUMLOCK
                || key == InputConstants.KEY_SCROLLLOCK;
    }
}
