package com.bluepotatobp.aeroswitch.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.debug.DebugScreenEntryList;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface SessionMinecraftAccessor {
    @Accessor("singleplayerServer") void aero$server(IntegratedServer value);
    @Accessor("pendingConnection") Connection aero$pending();
    @Accessor("pendingConnection") void aero$pending(Connection value);
    @Accessor("isLocalServer") boolean aero$local();
    @Accessor("isLocalServer") void aero$local(boolean value);
    @Accessor("pause") void aero$pause(boolean value);
    @Accessor("rightClickDelay") int aero$delay();
    @Accessor("rightClickDelay") void aero$delay(int value);
    @Accessor("reportingContext") void aero$reporting(ReportingContext value);
    @Mutable @Accessor("gameRenderer") void aero$gameRenderer(GameRenderer value);
    @Mutable @Accessor("levelRenderer") void aero$levelRenderer(LevelRenderer value);
    @Mutable @Accessor("levelExtractor") void aero$extractor(LevelExtractor value);
    @Mutable @Accessor("particleEngine") void aero$particles(ParticleEngine value);
    @Mutable @Accessor("gui") void aero$gui(Gui value);
    @Mutable @Accessor("debugEntries") DebugScreenEntryList aero$debugEntries();
    @Mutable @Accessor("debugEntries") void aero$debugEntries(DebugScreenEntryList value);
}
