package com.bluepotatobp.aeroswitch.session;

import com.bluepotatobp.aeroswitch.AeroSwitchClient;
import com.bluepotatobp.aeroswitch.mixin.GameRendererAccessor;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostChainConfig;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.UniformValue;
import net.minecraft.resources.Identifier;
import org.joml.Vector4f;

/** Owns and applies the "dim inactive instances" desaturation post chain. */
final class SessionDimEffect {
    private static final Identifier DIM_SWAP_TARGET =
            Identifier.fromNamespaceAndPath("aero_switch", "dim_swap");
    private static final Identifier SCREENQUAD_SHADER =
            Identifier.fromNamespaceAndPath("minecraft", "core/screenquad");
    private static final Identifier DESATURATE_SHADER =
            Identifier.fromNamespaceAndPath("aero_switch", "post/desaturate");
    private static final Identifier BLIT_SHADER =
            Identifier.fromNamespaceAndPath("minecraft", "post/blit");

    private final SessionManager owner;
    private PostChain dimChain;
    private Projection dimProjection;
    private ProjectionMatrixBuffer dimProjectionBuffer;
    private int cachedDimAmount = -1;

    SessionDimEffect(SessionManager owner) {
        this.owner = owner;
    }

    /** Desaturates an inactive session's finished pane by the configured amount. */
    void apply(ClientSession session, int dimAmount) {
        if (dimAmount <= 0) return;
        PostChain chain = chain(dimAmount);
        if (chain != null) {
            RenderTarget target = session.renderer.mainRenderTarget();
            FrameGraphBuilder frame = new FrameGraphBuilder();
            PostChain.TargetBundle targets = PostChain.TargetBundle.of(
                PostChain.MAIN_TARGET_ID, frame.importExternal("main", target));
            chain.addToFrame(frame, target.width, target.height, targets);
            frame.execute(((GameRendererAccessor) session.renderer).aero$resourcePool());
        }
    }

    /**
     * Returns a post chain whose desaturate pass blends toward luminance by
     * {@code dimAmount}. Post chains bake uniform values at build time, so the
     * chain is rebuilt lazily whenever the amount changes (at most once per
     * inactive offscreen render).
     */
    private PostChain chain(int dimAmount) {
        if (cachedDimAmount == dimAmount) return dimChain;
        if (dimProjection == null) {
            dimProjection = new Projection();
            dimProjection.setupOrtho(0.1F, 1000.0F, 1.0F, 1.0F, false);
            dimProjectionBuffer = new ProjectionMatrixBuffer("aero_switch_dim");
        }
        float mix = dimAmount / 100.0F;
        PostChainConfig config = new PostChainConfig(
                Map.of(DIM_SWAP_TARGET, new PostChainConfig.InternalTarget(Optional.empty(), Optional.empty(), false, 0)),
                List.of(
                        new PostChainConfig.Pass(SCREENQUAD_SHADER, DESATURATE_SHADER,
                                List.of(new PostChainConfig.TargetInput("In", PostChain.MAIN_TARGET_ID, false, false)),
                                DIM_SWAP_TARGET,
                                Map.of("DesaturateConfig", List.of(new UniformValue.FloatUniform(mix)))),
                        new PostChainConfig.Pass(SCREENQUAD_SHADER, BLIT_SHADER,
                                List.of(new PostChainConfig.TargetInput("In", DIM_SWAP_TARGET, false, false)),
                                PostChain.MAIN_TARGET_ID,
                                Map.of("BlitConfig", List.of(new UniformValue.Vec4Uniform(new Vector4f(1, 1, 1, 1)))))));
        if (dimChain != null) dimChain.close();
        dimChain = null;
        try {
            dimChain = PostChain.load(config, owner.mc().getTextureManager(),
                    Set.of(PostChain.MAIN_TARGET_ID), PostChain.MAIN_TARGET_ID,
                    dimProjection, dimProjectionBuffer);
        } catch (Exception error) {
            AeroSwitchClient.LOGGER.error("Could not build Aero Switch dim post chain", error);
        }
        cachedDimAmount = dimAmount;
        return dimChain;
    }

    void close() {
        if (dimChain != null) {
            dimChain.close();
            dimChain = null;
        }
        if (dimProjectionBuffer != null) {
            dimProjectionBuffer.close();
            dimProjectionBuffer = null;
        }
    }
}
