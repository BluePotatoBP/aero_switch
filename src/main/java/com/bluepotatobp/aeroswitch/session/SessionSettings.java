package com.bluepotatobp.aeroswitch.session;

import com.bluepotatobp.aeroswitch.config.AeroSwitchConfig;
import com.mojang.blaze3d.platform.InputConstants;

/** Config-backed user settings owned by {@link SessionManager}, persisted to aero_switch.json. */
final class SessionSettings {
    private final AeroSwitchConfig config;
    private int borderThickness = 2;
    private boolean showInfoPanel = true;
    private boolean compactInfoPanel;
    private int dimAmount;
    private final int[] focusModifiers = new int[8];

    SessionSettings(AeroSwitchConfig config) {
        this.config = config;
        for (int i = 0; i < focusModifiers.length; i++) {
            focusModifiers[i] = config != null ? config.focusModifiers[i] : InputConstants.MOD_ALT;
        }
        if (config != null) {
            borderThickness = config.borderThickness;
            showInfoPanel = config.showInfoPanel;
            compactInfoPanel = config.compactInfoPanel;
            dimAmount = config.dimAmount;
        }
    }

    int borderThickness() {
        return borderThickness;
    }

    void setBorderThickness(int value) {
        borderThickness = Math.clamp(value, 0, 8);
        if (config != null) {
            config.borderThickness = borderThickness;
            config.save();
        }
    }

    boolean showInfoPanel() {
        return showInfoPanel;
    }

    void setShowInfoPanel(boolean value) {
        showInfoPanel = value;
        if (config != null) {
            config.showInfoPanel = value;
            config.save();
        }
    }

    boolean compactInfoPanel() {
        return compactInfoPanel;
    }

    void setCompactInfoPanel(boolean value) {
        compactInfoPanel = value;
        if (config != null) {
            config.compactInfoPanel = value;
            config.save();
        }
    }

    int dimAmount() {
        return dimAmount;
    }

    void setDimAmount(int value) {
        dimAmount = Math.clamp(value, 0, 100);
        if (config != null) {
            config.dimAmount = dimAmount;
            config.save();
        }
    }

    int focusModifierMask(int index) {
        return focusModifiers[index];
    }

    void setFocusModifier(int index, int mask) {
        focusModifiers[index] = mask;
        if (config != null) {
            config.focusModifiers[index] = mask;
            config.save();
        }
    }
}
