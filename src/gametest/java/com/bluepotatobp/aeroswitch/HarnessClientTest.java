package com.bluepotatobp.aeroswitch;

import com.bluepotatobp.aeroswitch.diagnostics.ClientStateSnapshot;
import com.bluepotatobp.aeroswitch.diagnostics.SessionDiagnostics;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

public final class HarnessClientTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        boolean diagnostics = SessionDiagnostics.enabled();
        require(diagnostics == Boolean.getBoolean("aeroSwitch.diagnostics"), "Diagnostic flag must be honored");
        context.waitForScreen(TitleScreen.class);
        context.runOnClient(client -> {
            require(ClientStateSnapshot.capture(client).consistent(), "Title-screen state must be consistent");
            require(client.level == null && client.player == null, "Title screen must not own a world");
        });

        long initialStarts = SessionDiagnostics.count("server.started");
        long initialStops = SessionDiagnostics.count("server.stopped");
        long initialJoins = SessionDiagnostics.count("play.join");
        TestWorldSave save;
        try (var world = context.worldBuilder().create()) {
            save = world.getWorldSave();
            world.getClientLevel().waitForChunksRender();
            assertPlaying(context);
            long ticks = SessionDiagnostics.count("client.ticks");
            long frames = SessionDiagnostics.count("client.frames");
            context.waitTicks(40);
            if (diagnostics) {
                require(SessionDiagnostics.count("client.ticks") >= ticks + 40, "Client ticks must continue");
                require(SessionDiagnostics.count("client.frames") > frames, "Rendering must continue");
            }
            world.getServer().runOnServer(server -> server.overworld()
                    .setBlock(new BlockPos(0, 100, 0), Blocks.DIAMOND_BLOCK.defaultBlockState(), 3));
            context.runOnClient(client -> client.gui.setPauseScreen(false, false));
            context.waitFor(client -> client.isPaused());
            context.waitTicks(10);
            long pausedTime = world.getServer().computeOnServer(server -> {
                require(((IntegratedServer) server).isPaused(), "Integrated server must actually pause");
                return server.overworld().getGameTime();
            });
            context.waitTicks(20);
            require(world.getServer().computeOnServer(server -> server.overworld().getGameTime()) == pausedTime,
                    "Paused server simulation must not advance");
            context.runOnClient(client -> require(ClientStateSnapshot.capture(client).consistent(),
                    "Pausing must preserve player/world relationships"));
            context.setScreen(() -> null);
            context.waitFor(client -> !client.isPaused());
        }

        assertClosed(context);
        try (var world = save.open()) {
            world.getClientLevel().waitForChunksRender();
            assertPlaying(context);
            world.getServer().runOnServer(server -> require(
                    server.overworld().getBlockState(new BlockPos(0, 100, 0)).is(Blocks.DIAMOND_BLOCK),
                    "World contents must survive save, close and reopen"));
        }
        assertClosed(context);
        if (!diagnostics) {
            require(SessionDiagnostics.count("client.ticks") == 0, "Disabled diagnostics must not sample ticks");
            require(SessionDiagnostics.count("client.frames") == 0, "Disabled diagnostics must not sample frames");
            require(SessionDiagnostics.count("server.started") == 0, "Disabled diagnostics must not trace lifecycle");
            return;
        }
        require(SessionDiagnostics.count("server.started") == initialStarts + 2, "Both local opens must be observed");
        require(SessionDiagnostics.count("server.stopped") == initialStops + 2, "Both local shutdowns must be observed");
        require(SessionDiagnostics.count("play.join") == initialJoins + 2, "Both joins must be observed");
        require(SessionDiagnostics.count("connection.listener") >= 2, "Listener hooks must execute");
        require(SessionDiagnostics.count("client.packetDrains") > 0, "Packet queue hook must execute");
        require(SessionDiagnostics.count("server.paused") > 0, "Server pause hook must execute");
        require(SessionDiagnostics.count("state.inconsistent") == 0, "No stable-state ownership mismatch is allowed");
    }

    private static void assertPlaying(ClientGameTestContext context) {
        context.runOnClient(client -> {
            ClientStateSnapshot state = ClientStateSnapshot.capture(client);
            require(state.consistent() && state.connected(), "Play session must have consistent connected state");
            require(client.getSingleplayerServer() != null, "Integrated server must be retained");
        });
    }

    private static void assertClosed(ClientGameTestContext context) {
        context.waitFor(client -> client.level == null && client.player == null && client.getSingleplayerServer() == null);
        context.runOnClient(client -> {
            ClientStateSnapshot state = ClientStateSnapshot.capture(client);
            require(state.consistent() && !state.connected(), "Closing must clear the active session");
            require(state.pendingConnection().equals("none"), "Closing must clear pending connections");
        });
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
