package com.bluepotatobp.aeroswitch;

import com.bluepotatobp.aeroswitch.diagnostics.ClientStateSnapshot;
import com.bluepotatobp.aeroswitch.session.SessionManager;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.player.LocalPlayer;

public final class RemoteSessionScenario implements ClientModInitializer {
    private String scenario;
    private int phase;
    private int wait;
    private int switches;
    private long deadline;
    private boolean executing;
    private FutureTask<TcpFixture> starting;
    private TcpFixture fixtureA;
    private TcpFixture fixtureB;
    private int physicsStage;
    private int remoteSlot;
    private double airborneY;
    private final ClientLevel[] levels = new ClientLevel[2];
    private final LocalPlayer[] players = new LocalPlayer[2];

    @Override
    public void onInitializeClient() {
        if (!Boolean.getBoolean("aeroSwitch.sessionTest")) return;
        scenario = System.getProperty("aeroSwitch.scenario", "local-local");
        // local scenarios are handled by SessionScenarioClient.
        if (scenario.equals("local-local") || scenario.equals("creative-tabs") || scenario.equals("debug-overlay")) return;
        require(scenario.equals("remote-remote") || scenario.equals("local-remote") || scenario.equals("remote-local"),
                "Unknown session scenario: " + scenario);
        deadline = System.nanoTime() + 120_000_000_000L;
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            if (fixtureA != null) fixtureA.stop();
            if (fixtureB != null) fixtureB.stop();
        });
    }

    private void tick(Minecraft client) {
        if (executing || phase == 11) return;
        executing = true;
        try {
            require(System.nanoTime() < deadline, "TCP scenario timed out in phase " + phase);
            if (wait > 0) {
                wait--;
                return;
            }
            SessionManager sessions = SessionManager.get();
            switch (phase) {
                case 0 -> {
                    if (!(client.gui.screen() instanceof TitleScreen)) return;
                    starting = TcpFixture.start(client, "TcpA");
                    advance();
                }
                case 1 -> {
                    if (!starting.isDone()) return;
                    fixtureA = result(starting);
                    if (!fixtureA.server().isReady()) return;
                    if (scenario.equals("remote-remote")) {
                        starting = TcpFixture.start(client, "TcpB");
                    }
                    advance();
                }
                case 2 -> {
                    if (scenario.equals("remote-remote")) {
                        if (!starting.isDone()) return;
                        fixtureB = result(starting);
                        if (!fixtureB.server().isReady()) return;
                    }
                    advance();
                    if (scenario.equals("local-remote")) {
                        openLocal(client);
                    } else {
                        connect(client, fixtureA);
                    }
                }
                case 3 -> {
                    if (!playing(client)) return;
                    capture(client, 0);
                    require(sessions.sessionCount() == 1, "First TCP scenario session must be adopted");
                    sessions.prepareNewSession();
                    advance();
                    if (scenario.equals("remote-local")) {
                        openLocal(client);
                    } else {
                        connect(client, fixtureB == null ? fixtureA : fixtureB);
                    }
                }
                case 4 -> {
                    if (!playing(client)) return;
                    capture(client, 1);
                    require(sessions.sessionCount() == 2, "Second TCP scenario session must be retained");
                    require(players[0].connection.getConnection().isConnected(), "Opening second session disconnected first");
                    require(players[0].connection != players[1].connection, "Connections must be independent");
                    remoteSlot = scenario.equals("local-remote") ? 1 : 0;
                    sessions.focus(1 - remoteSlot);
                    fixtureA.server().execute(() -> {
                        var source = fixtureA.server().createCommandSourceStack();
                        fixtureA.server().getCommands().performPrefixedCommand(source, "gamemode survival @a");
                        fixtureA.server().getCommands().performPrefixedCommand(source, "tp @a 8 90 8");
                    });
                    advance();
                }
                case 5 -> {
                    if (physicsStage == 0) {
                        if (players[remoteSlot].getY() < 85) return;
                        airborneY = players[remoteSlot].getY();
                        physicsStage = 1;
                        wait = 20;
                        return;
                    }
                    if (physicsStage == 1) {
                        require(players[remoteSlot].getY() < airborneY - 2,
                                "Inactive remote player did not simulate falling after a server teleport");
                        physicsStage = 2;
                    }
                    int slot = switches % 2;
                    sessions.focus(slot);
                    require(client.level == levels[slot] && client.player == players[slot], "Wrong session after focus");
                    require(ClientStateSnapshot.capture(client).consistent(), "TCP session context mismatch");
                    for (LocalPlayer player : players) {
                        require(player.connection.getConnection().isConnected(), "Background TCP session disconnected");
                    }
                    if (++switches == 20) {
                        advance();
                        wait = 400;
                    } else {
                        wait = 5;
                    }
                }
                case 6 -> {
                    for (LocalPlayer player : players) {
                        require(player.connection.getConnection().isConnected(), "Soak disconnected a session");
                    }
                    sessions.close(0);
                    require(sessions.sessionCount() == 1 && client.level == levels[1],
                            "Closing first session disturbed second");
                    advance();
                    wait = 20;
                }
                case 7 -> {
                    require(players[1].connection.getConnection().isConnected(), "Remaining TCP session disconnected");
                    sessions.close(1);
                    require(sessions.sessionCount() == 0, "TCP sessions leaked");
                    fixtureA.stop();
                    advance();
                }
                case 8 -> {
                    if (!fixtureA.server().isShutdown()) return;
                    if (fixtureB != null) fixtureB.stop();
                    advance();
                }
                case 9 -> {
                    if (fixtureB != null && !fixtureB.server().isShutdown()) return;
                    advance();
                }
                case 10 -> {
                    AeroSwitchClient.LOGGER.info("AERO_SESSION_TEST_PASSED {} pid={}", scenario, ProcessHandle.current().pid());
                    phase = 11;
                    client.stop();
                }
                default -> throw new AssertionError("Unexpected TCP scenario phase");
            }
        } finally {
            executing = false;
        }
    }

    private void capture(Minecraft client, int slot) {
        levels[slot] = client.level;
        players[slot] = client.player;
        boolean expectedRemote = scenario.equals("remote-remote")
                || scenario.equals("remote-local") && slot == 0 || scenario.equals("local-remote") && slot == 1;
        require(client.getConnection().getConnection().isMemoryConnection() != expectedRemote,
                "Scenario must exercise actual loopback TCP for remote sessions");
        require((client.getSingleplayerServer() == null) == expectedRemote, "Incorrect local-server ownership");
    }

    private static TcpFixture result(FutureTask<TcpFixture> task) {
        try {
            return task.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted starting TCP fixture", e);
        } catch (ExecutionException e) {
            throw new AssertionError("TCP fixture startup failed", e.getCause());
        }
    }

    private static boolean playing(Minecraft client) {
        return client.level != null && client.player != null && client.gui.screen() == null
                && client.getConnection().getConnection().isConnected();
    }

    private void advance() {
        phase++;
        deadline = System.nanoTime() + 120_000_000_000L;
        AeroSwitchClient.LOGGER.info("AERO_SESSION_TEST_PHASE {} {}", scenario, phase);
    }

    private static void openLocal(Minecraft client) {
        client.createWorldOpenFlows().openWorld("AeroA", () -> {
            throw new AssertionError("Local fixture open cancelled");
        });
    }

    private static void connect(Minecraft client, TcpFixture fixture) {
        String address = "127.0.0.1:" + fixture.port();
        ConnectScreen.startConnecting(new TitleScreen(), client, ServerAddress.parseString(address),
                new ServerData("Aero Switch fixture", address, ServerData.Type.OTHER), false, null);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
