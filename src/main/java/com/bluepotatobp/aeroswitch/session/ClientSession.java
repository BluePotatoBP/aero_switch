package com.bluepotatobp.aeroswitch.session;

import com.bluepotatobp.aeroswitch.compat.VoxyCompat;
import com.bluepotatobp.aeroswitch.mixin.CreativeModeInventoryScreenAccessor;
import com.bluepotatobp.aeroswitch.mixin.SessionMinecraftAccessor;
import com.bluepotatobp.aeroswitch.mixin.SessionOptionsAccessor;
import com.bluepotatobp.aeroswitch.mixin.SessionParticleAccessor;
import com.bluepotatobp.aeroswitch.mixin.SessionCloudAccessor;
import com.bluepotatobp.aeroswitch.mixin.SessionHudAccessor;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.components.debug.DebugScreenEntryList;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
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
import net.minecraft.world.item.CreativeModeTab;
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
    int serverRenderDistance;
    CreativeModeTab creativeTab;
    DebugScreenEntryList debugEntries;

    ClientSession(int slot) {
        this.slot = slot;
    }

    void capture(Minecraft mc) {
        VoxyCompat.capture(slot);
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
        debugEntries = access.aero$debugEntries();
        cameraType = mc.options.getCameraType();
        serverRenderDistance = ((SessionOptionsAccessor) mc.options).aero$serverRenderDistance();
        if (gui.screen() instanceof CreativeModeInventoryScreen) {
            creativeTab = CreativeModeInventoryScreenAccessor.aero$getSelectedTab();
        }
        if (level != null || server != null || pending != null) occupied = true;
    }

    void install(Minecraft mc) {
        VoxyCompat.install(slot);
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
        access.aero$debugEntries(debugEntries);
        if (cameraType != null) mc.options.setCameraType(cameraType);
        ((SessionOptionsAccessor) mc.options).aero$serverRenderDistance(serverRenderDistance);
        if (gui.screen() instanceof CreativeModeInventoryScreen screen && creativeTab != null) {
            // Restore the shared static tab (the tab highlight), then re-sync the
            // shared CONTAINER buffer (the item grid) from this screen's own item list
            // at its current scroll offset. Do NOT call selectTab(): it resets the
            // scroll to the top and rebuilds menu.slots from originalSlots, which is
            // null on screens that never entered the Inventory tab (that branch is
            // what crashed when another session was on the Inventory tab).
            CreativeModeInventoryScreenAccessor.aero$setSelectedTab(creativeTab);
            screen.getMenu().scrollTo(((CreativeModeInventoryScreenAccessor) screen).aero$getScrollOffs());
        }
    }

    void createEngines(Minecraft mc) {
        reporting = mc.getReportingContext();
        cameraType = mc.options.getCameraType();
        debugEntries = new DebugScreenEntryList(mc.gameDirectory, mc.getFixerUpper());
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
