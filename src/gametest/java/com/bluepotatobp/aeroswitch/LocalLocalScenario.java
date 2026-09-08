package com.bluepotatobp.aeroswitch;

import com.bluepotatobp.aeroswitch.diagnostics.ClientStateSnapshot;
import com.bluepotatobp.aeroswitch.mixin.TestMouseAccessor;
import com.bluepotatobp.aeroswitch.session.SessionManager;
import com.bluepotatobp.aeroswitch.ui.SessionScreen;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.GL11C;

/** Normal-client local-local scenario: two local worlds opened in split-screen. */
final class LocalLocalScenario {
    private Phase phase = Phase.TITLE;
    private long deadline = System.nanoTime() + 120_000_000_000L;
    private int waitTicks;
    private int switches;
    private IntegratedServer serverA;
    private IntegratedServer serverB;
    private ClientLevel levelA;
    private ClientLevel levelB;
    private LocalPlayer playerA;
    private LocalPlayer playerB;
    private CompletableFuture<ScenarioSupport.ServerSample> sampleA;
    private CompletableFuture<ScenarioSupport.ServerSample> sampleB;
    private CompletableFuture<ScenarioSupport.SplitSample> splitCapture;
    private Vec3 movementStartA;
    private Vec3 movementStartB;
    private float movementPitchA;
    private float movementPitchB;
    private float movementYawA;
    private float movementYawB;
    private int movementTicks;
    private long pausedTime;
    private long runningTime;
    private boolean executing;

    void tick(Minecraft client) {
        if (executing || phase == Phase.DONE) return;
        executing = true;
        try {
            ScenarioSupport.require(System.nanoTime() < deadline, "Timed out in phase " + phase);
            if (waitTicks > 0) {
                waitTicks--;
                return;
            }
            SessionManager sessions = SessionManager.get();
            switch (phase) {
                case TITLE -> {
                    if (!(client.gui.screen() instanceof TitleScreen)) return;
                    ScenarioSupport.require(sessions.isEnabled(), "Session engine must be enabled");
                    transition(Phase.FIRST);
                    ScenarioSupport.open(client, "AeroA");
                }
                case FIRST -> {
                    if (!ScenarioSupport.playing(client)) return;
                    serverA = client.getSingleplayerServer();
                    levelA = client.level;
                    playerA = client.player;
                    ScenarioSupport.require(serverA != null && sessions.sessionCount() == 1, "First session must be adopted");
                    serverA.execute(() -> serverA.overworld().setBlock(ScenarioSupport.MARKER,
                            Blocks.EMERALD_BLOCK.defaultBlockState(), 3));
                    sessions.setLayoutMode(SessionManager.LayoutMode.SPLIT_VERTICAL);
                    sessions.prepareNewSession();
                    ScenarioSupport.require(sessions.isSplitPresented(), "Pending second session must enter split presentation");
                    while (GL11C.glGetError() != GL11C.GL_NO_ERROR) { }
                    sessions.presentationTarget(client.gameRenderer.mainRenderTarget());
                    ScenarioSupport.require(GL11C.glGetError() == GL11C.GL_NO_ERROR,
                            "Pending split composition produced an OpenGL error");
                    transition(Phase.SECOND);
                    ScenarioSupport.open(client, "AeroB");
                }
                case SECOND -> {
                    if (!ScenarioSupport.playing(client)) return;
                    serverB = client.getSingleplayerServer();
                    levelB = client.level;
                    playerB = client.player;
                    ScenarioSupport.require(sessions.sessionCount() == 2, "Both sessions must be retained");
                    ScenarioSupport.require(serverB != null && serverB != serverA && levelA != levelB && playerA != playerB,
                            "Local worlds must have separate server, level and player objects");
                    ScenarioSupport.require(playerA.connection != playerB.connection, "Listeners must be independent");
                    ScenarioSupport.require(playerA.connection.getConnection().isConnected(), "Opening B must not disconnect A");
                    ScenarioSupport.require(sessions.focusedSlot() == 1, "New session B should be focused");
                    sessions.setLayoutMode(SessionManager.LayoutMode.SPLIT_VERTICAL);
                    SessionScreen managerScreen = new SessionScreen();
                    sessions.setFocusedScreen(managerScreen);
                    ScenarioSupport.require(sessions.focusedScreen() == managerScreen,
                            "Manager screen must belong to focused session B");
                    ScenarioSupport.require(managerScreen.width == sessions.renderGuiWidth(
                                    client.getWindow().getWidth(), client.getWindow().getGuiScale()),
                            "Vanilla screens must initialize at pane width");
                    sessions.setFocusedScreen(null);
                    CameraType cameraB = client.options.getCameraType().cycle();
                    client.options.setCameraType(cameraB);
                    sessions.focus(0);
                    ScenarioSupport.require(client.options.getCameraType() != cameraB,
                            "Perspective change leaked from session B into session A");
                    sessions.focus(1);
                    ScenarioSupport.require(client.options.getCameraType() == cameraB,
                            "Session B perspective was not restored on focus");
                    ScenarioSupport.require(sessions.sessionCameraEntity(0) == playerA && sessions.sessionCameraEntity(1) == playerB,
                            "Each renderer camera must remain attached to its own player");
                    ScenarioSupport.require(sessions.focusPane(0, 0, 854, 480) && sessions.focusedSlot() == 0,
                            "Left pane must focus session A");
                    ScenarioSupport.require(sessions.focusPane(853, 0, 854, 480) && sessions.focusedSlot() == 1,
                            "Right pane must focus session B");
                    sessions.focus(0);
                    movementStartA = playerA.position();
                    movementStartB = playerB.position();
                    movementPitchB = playerB.getXRot();
                    movementYawB = playerB.getYRot();
                    movementPitchA = playerA.getXRot();
                    movementYawA = playerA.getYRot();
                    ScenarioSupport.require(client.mouseHandler.isMouseGrabbed(), "Mouse must be grabbed for the look test");
                    client.options.keyUp.setDown(true);
                    transition(Phase.MOVEMENT_ASSERT);
                }
                case MOVEMENT_ASSERT -> {
                    if (movementTicks++ < 12) {
                        TestMouseAccessor mouse = (TestMouseAccessor) client.mouseHandler;
                        mouse.aero$accumulatedDX(30.0);
                        mouse.aero$accumulatedDY(-30.0);
                        sessions.withSession(1, client.mouseHandler::handleAccumulatedMovement);
                        ScenarioSupport.require(Math.abs(playerB.getXRot() - movementPitchB) < 0.01F
                                        && Math.abs(playerB.getYRot() - movementYawB) < 0.01F,
                                "Continuous mouse input leaked into the inactive player");
                        return;
                    }
                    client.options.keyUp.setDown(false);
                    ScenarioSupport.require(Math.abs(playerA.getXRot() - movementPitchA) > 30.0F,
                            "Continuous mouse input did not noticeably tilt the focused camera");
                    ScenarioSupport.require(Math.abs(playerA.getYRot() - movementYawA) > 30.0F,
                            "Continuous mouse input did not noticeably turn the focused camera");
                    ScenarioSupport.require(Math.abs(playerB.getXRot() - movementPitchB) < 0.01F
                                    && Math.abs(playerB.getYRot() - movementYawB) < 0.01F,
                            "Mouse movement leaked into the inactive player during background frames");
                    ScenarioSupport.require(ScenarioSupport.horizontalDistance(playerA.position(), movementStartA) > 0.05,
                            "Focused player did not respond to forward input");
                    ScenarioSupport.require(ScenarioSupport.horizontalDistance(playerB.position(), movementStartB) < 0.001,
                            "Forward input leaked into the inactive player");
                    ScenarioSupport.require(sessions.sessionCameraEntity(0) == playerA && sessions.sessionCameraEntity(1) == playerB,
                            "A renderer camera crossed session ownership during movement");
                    sessions.focus(1);
                    transition(Phase.PAUSE_SAMPLE);
                    waitTicks = 15;
                }
                case PAUSE_SAMPLE -> {
                    ScenarioSupport.require(sessions.renderedFrames(0) > 0, "Visible inactive session did not render offscreen");
                    if (splitCapture == null) {
                        splitCapture = ScenarioSupport.captureSplit(client, sessions);
                        return;
                    }
                    if (!splitCapture.isDone()) return;
                    ScenarioSupport.SplitSample split = splitCapture.join();
                    ScenarioSupport.require(split.leftNonBlack() > split.halfPixels() / 100,
                            "Left split pane is blank in aero-split.png");
                    ScenarioSupport.require(split.rightNonBlack() > split.halfPixels() / 100,
                            "Right split pane is blank in aero-split.png");
                    sampleA = ScenarioSupport.sample(serverA);
                    sampleB = ScenarioSupport.sample(serverB);
                    transition(Phase.PAUSE_READ);
                }
                case PAUSE_READ -> {
                    if (!sampleA.isDone() || !sampleB.isDone()) return;
                    ScenarioSupport.require(sampleA.join().paused(), "Inactive local A must pause by default");
                    ScenarioSupport.require(!sampleB.join().paused(), "Focused B must remain running");
                    pausedTime = sampleA.join().time();
                    runningTime = sampleB.join().time();
                    transition(Phase.PAUSE_COMPARE);
                    waitTicks = 30;
                }
                case PAUSE_COMPARE -> {
                    sampleA = ScenarioSupport.sample(serverA);
                    sampleB = ScenarioSupport.sample(serverB);
                    transition(Phase.PAUSE_ASSERT);
                }
                case PAUSE_ASSERT -> {
                    if (!sampleA.isDone() || !sampleB.isDone()) return;
                    ScenarioSupport.require(sampleA.join().time() == pausedTime, "A simulation advanced despite pause");
                    ScenarioSupport.require(sampleB.join().time() > runningTime, "B stopped advancing while A was paused");
                    sessions.setKeepRunning(0, true);
                    transition(Phase.RUN_SAMPLE);
                    waitTicks = 30;
                }
                case RUN_SAMPLE -> {
                    sampleA = ScenarioSupport.sample(serverA);
                    transition(Phase.RUN_ASSERT);
                }
                case RUN_ASSERT -> {
                    if (!sampleA.isDone()) return;
                    ScenarioSupport.require(!sampleA.join().paused() && sampleA.join().time() > pausedTime,
                            "Keep-running must resume the inactive server");
                    transition(Phase.SWITCHING);
                }
                case SWITCHING -> {
                    int slot = switches % 2;
                    sessions.focus(slot);
                    ScenarioSupport.require(client.level == (slot == 0 ? levelA : levelB), "Focus selected the wrong level");
                    ScenarioSupport.require(client.player == (slot == 0 ? playerA : playerB), "Focus selected the wrong player");
                    ScenarioSupport.require(client.getSingleplayerServer() == (slot == 0 ? serverA : serverB),
                            "Focus selected the wrong integrated server");
                    ScenarioSupport.require(ClientStateSnapshot.capture(client).consistent(), "Switched context is inconsistent");
                    ScenarioSupport.require(playerA.connection.getConnection().isConnected()
                            && playerB.connection.getConnection().isConnected(), "Switching disconnected a session");
                    if (++switches == 20) {
                        sessions.focus(0);
                        sessions.disconnect(new TitleScreen());
                        transition(Phase.CLOSED_FIRST);
                    } else {
                        waitTicks = 5;
                    }
                }
                case CLOSED_FIRST -> {
                    if (!serverA.isShutdown()) return;
                    ScenarioSupport.require(sessions.sessionCount() == 1 && client.level == levelB,
                            "Closing A must leave B focused and connected");
                    ScenarioSupport.require(playerB.connection.getConnection().isConnected(), "Closing A disconnected B");
                    sessions.close(1);
                    transition(Phase.CLOSED_BOTH);
                }
                case CLOSED_BOTH -> {
                    if (!serverB.isShutdown()) return;
                    ScenarioSupport.require(sessions.sessionCount() == 0 && client.level == null && client.player == null,
                            "Closing both sessions must clear active state");
                    transition(Phase.REOPEN);
                    ScenarioSupport.open(client, "AeroA");
                }
                case REOPEN -> {
                    if (!ScenarioSupport.playing(client)) return;
                    sampleA = ScenarioSupport.sample(client.getSingleplayerServer());
                    transition(Phase.PERSISTED);
                }
                case PERSISTED -> {
                    if (!sampleA.isDone()) return;
                    ScenarioSupport.require(sampleA.join().marker(), "A's saved marker was not preserved");
                    sessions.close(sessions.focusedSlot());
                    transition(Phase.FINISH);
                }
                case FINISH -> {
                    ScenarioSupport.require(sessions.sessionCount() == 0, "A session leaked after the scenario");
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

    private void transition(Phase next) {
        phase = next;
        deadline = System.nanoTime() + 120_000_000_000L;
        AeroSwitchClient.LOGGER.info("AERO_SESSION_TEST_PHASE {}", next);
    }

    private enum Phase {
        TITLE, FIRST, SECOND, MOVEMENT_ASSERT, PAUSE_SAMPLE, PAUSE_READ, PAUSE_COMPARE, PAUSE_ASSERT,
        RUN_SAMPLE, RUN_ASSERT, SWITCHING, CLOSED_FIRST, CLOSED_BOTH, REOPEN, PERSISTED, FINISH, DONE
    }
}
