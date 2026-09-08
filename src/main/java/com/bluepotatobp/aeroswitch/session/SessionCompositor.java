package com.bluepotatobp.aeroswitch.session;

import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.MainTarget;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.lwjgl.opengl.GL43C;

/** Composites per-session render targets into the presentation target and drives offscreen renders. */
final class SessionCompositor {
    private final SessionManager owner;
    private MainTarget compositeTarget;

    SessionCompositor(SessionManager owner) {
        this.owner = owner;
    }

    RenderTarget presentationTarget(RenderTarget focusedTarget) {
        if (!owner.isSplitPresented()) return focusedTarget;
        int width = owner.mc().getWindow().getWidth();
        int height = owner.mc().getWindow().getHeight();
        if (compositeTarget == null) compositeTarget = new MainTarget(width, height);
        else if (compositeTarget.width != width || compositeTarget.height != height) compositeTarget.resize(width, height);
        RenderSystem.getDevice().createCommandEncoder().submit();
        if (!(compositeTarget.getColorTexture() instanceof GlTexture destination)) return focusedTarget;
        for (ClientSession session : owner.layout.presentedSessions()) {
            RenderTarget source = session.renderer.mainRenderTarget();
            if (!(source.getColorTexture() instanceof GlTexture texture)) return focusedTarget;
            int[] bounds = owner.paneBounds(session.slot, width, height);
            int copyWidth = Math.min(source.width, bounds[2]);
            int copyHeight = Math.min(source.height, bounds[3]);
            // glCopyImageSubData uses a bottom-left texel origin, while paneBounds uses
            // top-left screen coords. Flip the destination Y so the composite is not
            // vertically mirrored (matters for GRID and the triple layouts).
            int dstY = height - bounds[1] - copyHeight;
            GL43C.glCopyImageSubData(texture.glId(), GL43C.GL_TEXTURE_2D, 0, 0, 0, 0,
                destination.glId(), GL43C.GL_TEXTURE_2D, 0, bounds[0], dstY, 0, copyWidth, copyHeight, 1);
        }
        return compositeTarget;
    }

    RenderTarget sessionRenderTarget(int slot) {
        owner.checkThread();
        owner.adopt();
        return owner.require(slot).renderer.mainRenderTarget();
    }

    Entity sessionCameraEntity(int slot) {
        owner.checkThread();
        owner.adopt();
        return owner.require(slot).renderer.mainCamera().entity();
    }

    /** Renders one inactive session offscreen (into its own target) at its throttled cadence. */
    void renderActiveOffscreen(ClientSession session) {
        Minecraft client = owner.mc();
        DeltaTracker deltaTracker = client.getDeltaTracker();
        client.gui.update();
        if (client.isGameLoadFinished() && client.level != null) client.level.update();
        client.gameRenderer.update(deltaTracker);
        client.gameRenderer.extract(deltaTracker, true);
        RenderSystem.executePendingTasks();
        client.gameRenderer.render(deltaTracker, true);
        if (owner.dimAmount() > 0) owner.dim.apply(session, owner.dimAmount());
        RenderSystem.getDevice().createCommandEncoder().submit();
        RenderSystem.getDynamicUniforms().reset();
        client.levelRenderer.endFrame();
    }

    void close() {
        if (compositeTarget != null) {
            compositeTarget.destroyBuffers();
            compositeTarget = null;
        }
    }
}
