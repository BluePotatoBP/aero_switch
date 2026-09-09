package com.bluepotatobp.aeroswitch.mixin;

import com.bluepotatobp.aeroswitch.session.SessionManager;
import java.lang.reflect.Method;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Compatibility shim for Axiom editor (the full-window ImGui UI).
 *
 * <p>Axiom's editor renders and hit-tests its UI over the WHOLE window, independent
 * of Aero Switch's per-pane input remapping (its {@code CustomImGuiImplGlfw} reads
 * the cursor straight from GLFW). That part is already correct and intentionally
 * left alone. The one conflict is Aero Switch's pane click-to-focus: every left
 * click with the mouse released would otherwise switch focus to whichever pane the
 * click lands in, so clicking an editor button that overlaps the non-focused pane
 * would steal focus mid-click.</p>
 *
 * <p>This mixin reports {@code EditorUI.isActive()} back to {@link SessionManager}
 * each frame so {@code SessionMouseMixin} can skip pane focus-switching while the
 * editor is up.</p>
 */
@Mixin(targets = "com.moulberry.axiom.editor.EditorUI", remap = false)
public abstract class AxiomImGuiCompatMixin {
    private static Method isActive;

    private static boolean editorActive() {
        try {
            if (isActive == null) {
                isActive = Class.forName("com.moulberry.axiom.editor.EditorUI").getDeclaredMethod("isActive");
                isActive.setAccessible(true);
            }
            return (Boolean) isActive.invoke(null);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    @Inject(method = "drawOverlay", at = @At("HEAD"))
    private static void aero$clearEditorActive(CallbackInfo ci) {
        SessionManager.setAxiomEditorActive(false);
    }

    @Inject(method = "drawOverlay", at = @At("TAIL"))
    private static void aero$setEditorActive(CallbackInfo ci) {
        SessionManager.setAxiomEditorActive(editorActive());
    }
}
