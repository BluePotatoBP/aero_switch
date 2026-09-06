package com.bluepotatobp.aeroswitch.mixin;

import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Vanilla stores the creative inventory's selected tab in a private static field,
 * so multiple open creative inventories share one tab. Expose get/set accessors, the
 * scroll offset, and the {@code selectTab} invoker so {@code ClientSession} can
 * save/restore a per-session tab (and its item grid) when contexts switch.
 *
 * <p>Restoring a tab must NOT call {@code selectTab}: it resets the scroll to the top
 * and rebuilds the per-screen {@code menu.slots} from the instance field
 * {@code originalSlots} (which is null on screens that never entered the Inventory
 * tab, and crashes when another session was on the Inventory tab). Instead callers set
 * the static tab and re-sync the shared item-grid buffer via
 * {@code screen.getMenu().scrollTo(getScrollOffs())}, which preserves the scroll.
 */
@Mixin(CreativeModeInventoryScreen.class)
public interface CreativeModeInventoryScreenAccessor {
    @Accessor("selectedTab")
    static CreativeModeTab aero$getSelectedTab() {
        throw new AssertionError("Mixin accessor");
    }

    @Accessor("selectedTab")
    static void aero$setSelectedTab(CreativeModeTab value) {
        throw new AssertionError("Mixin accessor");
    }

    @Accessor("scrollOffs")
    float aero$getScrollOffs();

    @Accessor("scrollOffs")
    void aero$setScrollOffs(float value);

    @Invoker("selectTab")
    void aero$selectTab(CreativeModeTab value);
}
