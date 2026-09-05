package com.bluepotatobp.aeroswitch.session;

import com.bluepotatobp.aeroswitch.mixin.SessionMinecraftAccessor;
import com.bluepotatobp.aeroswitch.mixin.SessionParticleAccessor;
import com.bluepotatobp.aeroswitch.mixin.SessionCloudAccessor;
import com.bluepotatobp.aeroswitch.mixin.SessionHudAccessor;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.Connection;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;
import net.minecraft.server.packs.resources.ReloadableResourceManager;

/** Explicit alias manifest. No vanilla world teardown is used when switching capsules. */
final class ClientSession {
    final int slot;
    ClientLevel level;
    LocalPlayer player;
    MultiPlayerGameMode gameMode;
    IntegratedServer server;
    Connection pending;
    volatile Connection connection;
    boolean local;
    boolean paused;
    volatile boolean occupied;
    int generation;
    boolean keepRunning;
    boolean visible;
    int inactiveFps = 5;
    long lastRender;
    long renderedFrames;
    int delay;
    int missTime;
    Entity crosshair;
    HitResult hit;
    ReportingContext reporting;
    GameRenderer renderer;
    LevelRenderer levelRenderer;
    LevelExtractor extractor;
    ParticleEngine particles;
    Gui gui;
    CameraType cameraType;

    ClientSession(int slot) {
        this.slot = slot;
    }

    void capture(Minecraft mc) {
        SessionMinecraftAccessor access = (SessionMinecraftAccessor) mc;
        level = mc.level;
        player = mc.player;
        gameMode = mc.gameMode;
        server = mc.getSingleplayerServer();
        pending = access.aero$pending();
        if (mc.getConnection() != null) connection = mc.getConnection().getConnection();
        else if (pending != null) connection = pending;
        local = access.aero$local();
        paused = mc.isPaused();
        delay = access.aero$delay();
        missTime = mc.missTime;
        crosshair = mc.crosshairPickEntity;
        hit = mc.hitResult;
        reporting = mc.getReportingContext();
        renderer = mc.gameRenderer;
        levelRenderer = mc.levelRenderer;
        extractor = mc.levelExtractor;
        particles = mc.particleEngine;
        gui = mc.gui;
        cameraType = mc.options.getCameraType();
        if (level != null || server != null || pending != null) occupied = true;
    }

    void install(Minecraft mc) {
        SessionMinecraftAccessor access = (SessionMinecraftAccessor) mc;
        mc.level = level;
        mc.player = player;
        mc.gameMode = gameMode;
        access.aero$server(server);
        access.aero$pending(pending);
        access.aero$local(local);
        access.aero$pause(paused);
        access.aero$delay(delay);
        mc.missTime = missTime;
        mc.crosshairPickEntity = crosshair;
        mc.hitResult = hit;
        access.aero$reporting(reporting);
        access.aero$gameRenderer(renderer);
        access.aero$levelRenderer(levelRenderer);
        access.aero$extractor(extractor);
        access.aero$particles(particles);
        access.aero$gui(gui);
        if (cameraType != null) mc.options.setCameraType(cameraType);
    }

    void createEngines(Minecraft mc) {
        reporting = mc.getReportingContext();
        cameraType = mc.options.getCameraType();
        renderer = new GameRenderer(mc,
                new ItemInHandRenderer(mc, mc.getEntityRenderDispatcher(), mc.getItemModelResolver()), mc.getModelManager());
        renderer.gameRenderState().windowRenderState.width = mc.getWindow().getWidth();
        renderer.gameRenderState().windowRenderState.height = mc.getWindow().getHeight();
        renderer.gameRenderState().framerateLimit = mc.getFramerateLimitTracker().getFramerateLimit();
        levelRenderer = new LevelRenderer(mc.getEntityRenderDispatcher(), mc.getBlockEntityRenderDispatcher(),
                mc.getModelManager(), mc.getTextureManager(), mc.getAtlasManager(), mc.getShaderManager(),
                renderer, mc.getWindow().getWidth(), mc.getWindow().getHeight());
        extractor = new LevelExtractor(mc, renderer.gameRenderState().levelRenderState, levelRenderer);
        extractor.onResourceManagerReload(mc.getResourceManager());
        ((SessionCloudAccessor) levelRenderer.cloudRenderer()).aero$texture(
                ((SessionCloudAccessor) mc.levelRenderer.cloudRenderer()).aero$texture());
        ReloadableResourceManager resources = (ReloadableResourceManager) mc.getResourceManager();
        resources.registerReloadListener(extractor);
        resources.registerReloadListener(levelRenderer.cloudRenderer());
        particles = new ParticleEngine(null, ((SessionParticleAccessor) mc.particleEngine).aero$resources());
        gui = new Gui(mc, new Hud(mc), renderer.gameRenderState().guiRenderState);
        ((SessionHudAccessor) gui.hud).aero$waypointStyles(mc.gui.hud.getWaypointStyles());
    }
}
