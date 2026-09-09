package com.bluepotatobp.aeroswitch.mixin;

import com.bluepotatobp.aeroswitch.session.SessionManager;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Compatibility shim for Axiom.
 *
 * <p>Axiom renders its "quick tools" / context-menu screens (e.g.
 * {@code SwitchHotbarScreen}, whose side buttons toggle abilities like No Clip)
 * through its own {@code ContextMenuManager} rather than through the vanilla
 * {@code Gui.setScreen} pipeline. That manager re-implements the
 * raw-to-GUI-scaled mouse conversion inline from {@code MouseHandler.xpos()/ypos()}
 * instead of calling {@code MouseHandler.getScaledXPos/Pos(Window)}. Aero Switch
 * only remaps those two vanilla getters, so Axiom's screens end up hit-tested in
 * full-window coordinates while being drawn pane-locally: the exact "cursor is one
 * instance to the left" offset.</p>
 *
 * <p>{@code ContextMenuManager} is a thin forwarding layer, so translating its
 * input methods (plus the hover/tooltip coordinates it feeds back into
 * {@code Screen.extractRenderStateWithTooltipAndSubtitles}) makes every one of its
 * screens pane-aware in a single place.</p>
 */
@Mixin(targets = "com.moulberry.axiom.ContextMenuManager", remap = false)
public abstract class AxiomContextMenuCompatMixin {
    @WrapMethod(method = "mouseMoved")
    private void aero$paneMouseMoved(double x, double y, Operation<Void> original) {
        SessionManager manager = SessionManager.get();
        original.call(manager.paneScaledX(x), manager.paneScaledY(y));
    }

    @WrapMethod(method = "mouseClicked")
    private void aero$paneMouseClicked(MouseButtonEvent event, boolean pressed, Operation<Void> original) {
        SessionManager manager = SessionManager.get();
        original.call(new MouseButtonEvent(
                manager.paneScaledX(event.x()), manager.paneScaledY(event.y()), event.buttonInfo()), pressed);
    }

    @WrapMethod(method = "mouseReleased")
    private void aero$paneMouseReleased(MouseButtonEvent event, Operation<Void> original) {
        SessionManager manager = SessionManager.get();
        original.call(new MouseButtonEvent(
                manager.paneScaledX(event.x()), manager.paneScaledY(event.y()), event.buttonInfo()));
    }

    @WrapMethod(method = "mouseDragged")
    private void aero$paneMouseDragged(MouseButtonEvent event, double dx, double dy, Operation<Void> original) {
        SessionManager manager = SessionManager.get();
        // dx/dy are deltas (pane-independent); only the event position is remapped.
        original.call(new MouseButtonEvent(
                manager.paneScaledX(event.x()), manager.paneScaledY(event.y()), event.buttonInfo()), dx, dy);
    }

    @WrapMethod(method = "mouseScrolled")
    private boolean aero$paneMouseScrolled(double x, double y, double horizontal, double vertical,
                                           Operation<Boolean> original) {
        SessionManager manager = SessionManager.get();
        return original.call(manager.paneScaledX(x), manager.paneScaledY(y), horizontal, vertical);
    }

    // ContextMenuManager.render computes the hover/tooltip cursor position itself from
    // the raw MouseHandler.xpos()/ypos() and hands it to the screen; remap that too so
    // hover highlights and tooltips land in the focused pane.
    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/Screen;extractRenderStateWithTooltipAndSubtitles"
                    + "(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"))
    private void aero$paneHover(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                float partialTick, Operation<Void> original) {
        SessionManager manager = SessionManager.get();
        original.call(screen, graphics,
                (int) manager.paneScaledX(mouseX), (int) manager.paneScaledY(mouseY), partialTick);
    }
}
