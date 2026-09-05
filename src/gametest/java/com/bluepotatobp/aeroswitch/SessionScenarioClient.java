package com.bluepotatobp.aeroswitch;

import com.bluepotatobp.aeroswitch.diagnostics.ClientStateSnapshot;
import com.bluepotatobp.aeroswitch.session.SessionManager;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

/** Normal-client scenario: Fabric's game-test scheduler supports only one server thread. */
public final class SessionScenarioClient implements ClientModInitializer {
    private static final BlockPos MARKER = new BlockPos(0, 100, 0);
    private Phase phase = Phase.TITLE;
    private long deadline;
    private int waitTicks;
    private int switches;
    private IntegratedServer serverA;
    private IntegratedServer serverB;
    private ClientLevel levelA;
    private ClientLevel levelB;
    private LocalPlayer playerA;
    private LocalPlayer playerB;
    private CompletableFuture<ServerSample> sampleA;
    private CompletableFuture<ServerSample> sampleB;
    private long pausedTime;
    private long runningTime;
    private boolean executing;

    @Override
    public void onInitializeClient() {
        if (Boolean.getBoolean("aeroSwitch.sessionTest")
                && System.getProperty("aeroSwitch.scenario", "local-local").equals("local-local")) {
            deadline = System.nanoTime() + 120_000_000_000L;
            ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        }
    }

    private void tick(Minecraft client) {
        if (executing || phase == Phase.DONE) return;
        executing = true;
        try {
            require(System.nanoTime() < deadline, "Timed out in phase " + phase);
            if (waitTicks > 0) {
                waitTicks--;
                return;
            }
            SessionManager sessions = SessionManager.get();
            switch (phase) {
                case TITLE -> {
                    if (!(client.gui.screen() instanceof TitleScreen)) return;
                    require(sessions.isEnabled(), "Experimental engine must be enabled");
                    transition(Phase.FIRST);
                    open(client, "AeroA");
                }
                case FIRST -> {
                    if (!playing(client)) return;
                    serverA = client.getSingleplayerServer();
                    levelA = client.level;
                    playerA = client.player;
                    require(serverA != null && sessions.sessionCount() == 1, "First session must be adopted");
                    serverA.execute(() -> serverA.overworld().setBlock(MARKER, Blocks.EMERALD_BLOCK.defaultBlockState(), 3));
                    sessions.prepareNewSession();
                    transition(Phase.SECOND);
                    open(client, "AeroB");
                }
                case SECOND -> {
                    if (!playing(client)) return;
                    serverB = client.getSingleplayerServer();
                    levelB = client.level;
                    playerB = client.player;
                    require(sessions.sessionCount() == 2, "Both sessions must be retained");
                    require(serverB != null && serverB != serverA && levelA != levelB && playerA != playerB,
                            "Local worlds must have separate server, level and player objects");
                    require(playerA.connection != playerB.connection, "Listeners must be independent");
                    require(playerA.connection.getConnection().isConnected(), "Opening B must not disconnect A");
                    require(sessions.focusedSlot() == 1, "New session B should be focused");
                    transition(Phase.PAUSE_SAMPLE);
                    waitTicks = 30;
                }
                case PAUSE_SAMPLE -> {
                    sampleA = sample(serverA);
                    sampleB = sample(serverB);
                    transition(Phase.PAUSE_READ);
                }
                case PAUSE_READ -> {
                    if (!sampleA.isDone() || !sampleB.isDone()) return;
                    require(sampleA.join().paused(), "Inactive local A must pause by default");
                    require(!sampleB.join().paused(), "Focused B must remain running");
                    pausedTime = sampleA.join().time();
                    runningTime = sampleB.join().time();
                    transition(Phase.PAUSE_COMPARE);
                    waitTicks = 30;
                }
                case PAUSE_COMPARE -> {
                    sampleA = sample(serverA);
                    sampleB = sample(serverB);
                    transition(Phase.PAUSE_ASSERT);
                }
                case PAUSE_ASSERT -> {
                    if (!sampleA.isDone() || !sampleB.isDone()) return;
                    require(sampleA.join().time() == pausedTime, "A simulation advanced despite pause");
                    require(sampleB.join().time() > runningTime, "B stopped advancing while A was paused");
                    sessions.setKeepRunning(0, true);
                    transition(Phase.RUN_SAMPLE);
                    waitTicks = 30;
                }
                case RUN_SAMPLE -> {
                    sampleA = sample(serverA);
                    transition(Phase.RUN_ASSERT);
                }
                case RUN_ASSERT -> {
                    if (!sampleA.isDone()) return;
                    require(!sampleA.join().paused() && sampleA.join().time() > pausedTime,
                            "Keep-running must resume the inactive server");
                    transition(Phase.SWITCHING);
                }
                case SWITCHING -> {
                    int slot = switches % 2;
                    sessions.focus(slot);
                    require(client.level == (slot == 0 ? levelA : levelB), "Focus selected the wrong level");
                    require(client.player == (slot == 0 ? playerA : playerB), "Focus selected the wrong player");
                    require(client.getSingleplayerServer() == (slot == 0 ? serverA : serverB),
                            "Focus selected the wrong integrated server");
                    require(ClientStateSnapshot.capture(client).consistent(), "Switched context is inconsistent");
                    require(playerA.connection.getConnection().isConnected()
                            && playerB.connection.getConnection().isConnected(), "Switching disconnected a session");
                    if (++switches == 20) {
                        sessions.close(0);
                        transition(Phase.CLOSED_FIRST);
                    } else {
                        waitTicks = 5;
                    }
                }
                case CLOSED_FIRST -> {
                    if (!serverA.isShutdown()) return;
                    require(sessions.sessionCount() == 1 && client.level == levelB,
                            "Closing A must leave B focused and connected");
                    require(playerB.connection.getConnection().isConnected(), "Closing A disconnected B");
                    sessions.close(1);
                    transition(Phase.CLOSED_BOTH);
                }
                case CLOSED_BOTH -> {
                    if (!serverB.isShutdown()) return;
                    require(sessions.sessionCount() == 0 && client.level == null && client.player == null,
                            "Closing both sessions must clear active state");
                    transition(Phase.REOPEN);
                    open(client, "AeroA");
                }
                case REOPEN -> {
                    if (!playing(client)) return;
                    sampleA = sample(client.getSingleplayerServer());
                    transition(Phase.PERSISTED);
                }
                case PERSISTED -> {
                    if (!sampleA.isDone()) return;
                    require(sampleA.join().marker(), "A's saved marker was not preserved");
                    sessions.close(sessions.focusedSlot());
                    transition(Phase.FINISH);
                }
                case FINISH -> {
                    require(sessions.sessionCount() == 0, "A session leaked after the scenario");
                    AeroSwitchClient.LOGGER.info("AERO_SESSION_TEST_PASSED local-local pid={}", ProcessHandle.current().pid());
                    phase = Phase.DONE;
                    client.stop();
                }
                case DONE -> { }
            }
        } finally {
            executing = false;
        }
    }

    private static CompletableFuture<ServerSample> sample(IntegratedServer server) {
        return server.submit(() -> new ServerSample(server.isPaused(), server.overworld().getGameTime(),
                server.overworld().getBlockState(MARKER).is(Blocks.EMERALD_BLOCK)));
    }

    private static boolean playing(Minecraft client) {
        return client.level != null && client.player != null && client.gui.screen() == null
                && client.getConnection() != null && client.getConnection().getConnection().isConnected();
    }

    private void transition(Phase next) {
        phase = next;
        deadline = System.nanoTime() + 120_000_000_000L;
        AeroSwitchClient.LOGGER.info("AERO_SESSION_TEST_PHASE {}", next);
    }

    private static void open(Minecraft client, String save) {
        client.createWorldOpenFlows().openWorld(save, () -> {
            throw new AssertionError("Disposable save failed to open: " + save);
        });
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private record ServerSample(boolean paused, long time, boolean marker) { }

    private enum Phase {
        TITLE, FIRST, SECOND, PAUSE_SAMPLE, PAUSE_READ, PAUSE_COMPARE, PAUSE_ASSERT,
        RUN_SAMPLE, RUN_ASSERT, SWITCHING, CLOSED_FIRST, CLOSED_BOTH, REOPEN, PERSISTED, FINISH, DONE
    }
}
