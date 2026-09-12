package com.bluepotatobp.aeroswitch.session;

import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.MainTarget;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
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

    /**
     * Position the render camera of session {@code slot} was last left at, i.e. the
     * interpolated camera position of its most recent (offscreen or focused) render.
     * Used by the scenario harness to prove a paused pane does not drift.
     */
    Vec3 sessionCameraPosition(int slot) {
        owner.checkThread();
        owner.adopt();
        return owner.require(slot).renderer.mainCamera().position();
    }

    /** Interpolation phase handed to a paused pane: the end of its last ticked frame. */
    private static final float FROZEN_PARTIAL_TICK = 1.0F;

    /**
     * Picks the delta tracker a pane is rendered with.
     * <p>
     * A paused session does not tick, so its entities and camera stay at the
     * positions from their last ticked frame. The shared {@link Minecraft#getDeltaTracker()}
     * partial tick belongs to the FOCUSED session and keeps cycling 0..1, so
     * re-interpolating those frozen positions with it walks the pane one tick
     * forward and snaps it back on every render ("rubber banding", worst at high
     * inactive FPS). Vanilla freezes the partial tick while paused for the same
     * reason, so hand paused panes a tracker with a pinned partial tick and leave
     * running panes on the live clock. Nothing here touches entity or player velocity.
     */
    static DeltaTracker renderTracker(boolean paused, DeltaTracker live) {
        return paused ? new FrozenTracker(live) : live;
    }

    /**
     * Paused-pane tracker: pins only the interpolation phase, exactly like vanilla's
     * own paused timer, which freezes the partial tick but keeps reporting delta ticks.
     * <p>
     * The delta ticks must stay live. {@code DeltaTracker.ONE} reports a whole tick of
     * game time per frame, and per-frame consumers read that as real elapsed time - the
     * rain-fog smoothing in {@code AtmosphericFogEnvironment} steps by
     * {@code getGameTimeDeltaTicks() * 0.2} every render, so a paused pane would yank
     * that shared state 20% per render.
     */
    private record FrozenTracker(DeltaTracker live) implements DeltaTracker {
        @Override
        public float getGameTimeDeltaTicks() {
            return live.getGameTimeDeltaTicks();
        }

        @Override
        public float getGameTimeDeltaPartialTick(boolean ignoreFrozenGame) {
            return FROZEN_PARTIAL_TICK;
        }

        @Override
        public float getRealtimeDeltaTicks() {
            return live.getRealtimeDeltaTicks();
        }
    }

    /** Renders one inactive session offscreen (into its own target) at its throttled cadence. */
    void renderActiveOffscreen(ClientSession session) {
        Minecraft client = owner.mc();
        DeltaTracker deltaTracker = renderTracker(owner.scheduler.shouldPause(session), client.getDeltaTracker());
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
