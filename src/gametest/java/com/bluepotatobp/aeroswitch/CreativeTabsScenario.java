package com.bluepotatobp.aeroswitch;

import com.bluepotatobp.aeroswitch.mixin.CreativeModeInventoryScreenAccessor;
import com.bluepotatobp.aeroswitch.session.SessionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;

/** Creative-inventory tab isolation scenario. */
final class CreativeTabsScenario {
    private CreativePhase phase = CreativePhase.TITLE;
    private long deadline = System.nanoTime() + 120_000_000_000L;
    private int wait;
    private int switches;
    private CreativeModeTab tabA;
    private CreativeModeTab tabB;
    private CreativeModeTab inventoryTab;
    private boolean executing;

    void tick(Minecraft client) {
        if (executing || phase == CreativePhase.DONE) return;
        executing = true;
        try {
            ScenarioSupport.require(System.nanoTime() < deadline, "Creative tab scenario timed out in phase " + phase);
            if (wait > 0) {
                wait--;
                return;
            }
            SessionManager sessions = SessionManager.get();
            switch (phase) {
                case TITLE -> {
                    if (!(client.gui.screen() instanceof TitleScreen)) return;
                    ScenarioSupport.require(sessions.isEnabled(), "Session engine must be enabled");
                    transition(CreativePhase.FIRST);
                    ScenarioSupport.open(client, "AeroA");
                }
                case FIRST -> {
                    if (!ScenarioSupport.playing(client)) return;
                    client.player.getAbilities().instabuild = true;
                    sessions.setLayoutMode(SessionManager.LayoutMode.SPLIT_VERTICAL);
                    sessions.prepareNewSession();
                    transition(CreativePhase.SECOND);
                    ScenarioSupport.open(client, "AeroB");
                }
                case SECOND -> {
                    if (!ScenarioSupport.playing(client)) return;
                    ScenarioSupport.require(sessions.sessionCount() == 2, "Both creative sessions must be retained");
                    client.player.getAbilities().instabuild = true;
                    sessions.focus(0);
                    // Opening a creative screen builds vanilla's tab contents, so the tabs
                    // must be resolved afterwards (getDisplayItems() is empty before then).
                    openCreativeScreen(client, sessions);
                    tabA = categoryTab(0);
                    tabB = categoryTab(1);
                    inventoryTab = inventoryTab();
                    ScenarioSupport.require(tabA != null && tabB != null && tabA != tabB && inventoryTab != null,
                            "Two distinct category tabs and the inventory tab must exist");
                    ((CreativeModeInventoryScreenAccessor) client.gui.screen()).aero$selectTab(tabA);
                    transition(CreativePhase.OPEN_B);
                }
                case OPEN_B -> {
                    sessions.focus(1);
                    openCreativeScreen(client, sessions);
                    ((CreativeModeInventoryScreenAccessor) client.gui.screen()).aero$selectTab(tabB);
                    // Two sessions on DIFFERENT category tabs: tickBackground + focus must
                    // keep each screen's item grid on its own tab.
                    wait = 20;
                    transition(CreativePhase.CADENCE);
                }
                case CADENCE -> {
                    sessions.focus(0);
                    assertTabShown(sessions, 0, tabA);
                    assertGridOwnTab(sessions);
                    sessions.focus(1);
                    assertTabShown(sessions, 1, tabB);
                    assertGridOwnTab(sessions);
                    if (++switches < 4) {
                        wait = 5;
                        return;
                    }
                    // Scroll preservation: a non-zero scroll must survive a focus
                    // round-trip (install() must not snap it back to the top).
                    sessions.focus(0);
                    ((CreativeModeInventoryScreenAccessor) client.gui.screen()).aero$setScrollOffs(0.5F);
                    sessions.focus(1);
                    sessions.focus(0);
                    ScenarioSupport.require(((CreativeModeInventoryScreenAccessor) sessions.focusedScreen()).aero$getScrollOffs() == 0.5F,
                            "Focus switches must not reset the creative inventory scroll");
                    ((CreativeModeInventoryScreenAccessor) sessions.focusedScreen()).aero$setScrollOffs(0.0F);
                    // Put the focused session on the Inventory tab: this is the state that
                    // used to NPE when the other (category-tab) session was installed.
                    sessions.focus(1);
                    ((CreativeModeInventoryScreenAccessor) client.gui.screen()).aero$selectTab(inventoryTab);
                    wait = 20;
                    transition(CreativePhase.CRASH);
                }
                case CRASH -> {
                    // Session 1 is on the Inventory tab; installing session 0 must neither
                    // crash nor leave its grid showing another session's items.
                    sessions.focus(0);
                    assertTabShown(sessions, 0, tabA);
                    assertGridOwnTab(sessions);
                    sessions.focus(1);
                    assertTabShown(sessions, 1, inventoryTab);
                    if (++switches < 8) {
                        wait = 5;
                        return;
                    }
                    transition(CreativePhase.FINISH);
                }
                case FINISH -> {
                    sessions.close(0);
                    sessions.close(1);
                    ScenarioSupport.require(sessions.sessionCount() == 0, "A creative session leaked after the scenario");
                    AeroSwitchClient.LOGGER.info("AERO_SESSION_TEST_PASSED creative-tabs pid={}",
                            ProcessHandle.current().pid());
                    phase = CreativePhase.DONE;
                    client.stop();
                }
                case DONE -> { }
            }
        } finally {
            executing = false;
        }
    }

    private static void openCreativeScreen(Minecraft client, SessionManager sessions) {
        LocalPlayer player = client.player;
        player.getAbilities().instabuild = true;
        sessions.setFocusedScreen(new CreativeModeInventoryScreen(player, player.connection.enabledFeatures(), false));
    }

    private static void assertTabShown(SessionManager sessions, int slot, CreativeModeTab expected) {
        ScenarioSupport.require(sessions.focusedSlot() == slot, "Focus must land on session " + slot);
        ScenarioSupport.require(sessions.focusedScreen() instanceof CreativeModeInventoryScreen,
                "Session " + slot + " must have an open creative inventory");
        ScenarioSupport.require(CreativeModeInventoryScreenAccessor.aero$getSelectedTab() == expected,
                "Session " + slot + " must restore its own selected tab, not the other session's");
    }

    private static void assertGridOwnTab(SessionManager sessions) {
        CreativeModeInventoryScreen screen = (CreativeModeInventoryScreen) sessions.focusedScreen();
        var items = screen.getMenu().items;
        ScenarioSupport.require(items.size() > 45, "Category tab must expose enough display items to fill the grid");
        var container = screen.getMenu().slots.get(0).container;
        for (int i = 0; i < 45; i++) {
            ScenarioSupport.require(ItemStack.isSameItemSameComponents(container.getItem(i), items.get(i)),
                    "Item grid slot " + i + " must show this session's own tab, not another tab");
        }
    }

    private static CreativeModeTab categoryTab(int skip) {
        int seen = 0;
        for (CreativeModeTab tab : CreativeModeTabs.allTabs()) {
            if (tab.getType() == CreativeModeTab.Type.CATEGORY && !tab.getDisplayItems().isEmpty()) {
                if (seen++ == skip) return tab;
            }
        }
        return null;
    }

    private static CreativeModeTab inventoryTab() {
        for (CreativeModeTab tab : CreativeModeTabs.allTabs()) {
            if (tab.getType() == CreativeModeTab.Type.INVENTORY) return tab;
        }
        return null;
    }

    private void transition(CreativePhase next) {
        phase = next;
        deadline = System.nanoTime() + 120_000_000_000L;
        AeroSwitchClient.LOGGER.info("AERO_SESSION_TEST_PHASE {}", next);
    }

    private enum CreativePhase {
        TITLE, FIRST, SECOND, OPEN_B, CADENCE, CRASH, FINISH, DONE
    }
}
