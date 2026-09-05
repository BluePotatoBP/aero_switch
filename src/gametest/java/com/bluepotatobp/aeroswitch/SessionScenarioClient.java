package com.bluepotatobp.aeroswitch;

import com.bluepotatobp.aeroswitch.diagnostics.ClientStateSnapshot;
import com.bluepotatobp.aeroswitch.mixin.TestMouseAccessor;
import com.bluepotatobp.aeroswitch.session.SessionManager;
import com.bluepotatobp.aeroswitch.ui.SessionScreen;
import com.mojang.blaze3d.platform.NativeImage;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.GL11C;

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
    private CompletableFuture<SplitSample> splitCapture;
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
                        sessions.setLayoutMode(SessionManager.LayoutMode.SPLIT_VERTICAL);
                    sessions.prepareNewSession();
                        require(sessions.isSplitPresented(), "Pending second session must enter split presentation");
                        while (GL11C.glGetError() != GL11C.GL_NO_ERROR) { }
                        sessions.presentationTarget(client.gameRenderer.mainRenderTarget());
                        require(GL11C.glGetError() == GL11C.GL_NO_ERROR,
                            "Pending split composition produced an OpenGL error");
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
                    sessions.setLayoutMode(SessionManager.LayoutMode.SPLIT_VERTICAL);
                        SessionScreen managerScreen = new SessionScreen();
                        sessions.setFocusedScreen(managerScreen);
                        require(sessions.focusedScreen() == managerScreen,
                            "Manager screen must belong to focused session B");
                        require(managerScreen.width == sessions.renderGuiWidth(
                                client.getWindow().getWidth(), client.getWindow().getGuiScale()),
                            "Vanilla screens must initialize at pane width");
                        sessions.setFocusedScreen(null);
                        CameraType cameraB = client.options.getCameraType().cycle();
                        client.options.setCameraType(cameraB);
                        sessions.focus(0);
                        require(client.options.getCameraType() != cameraB,
                            "Perspective change leaked from session B into session A");
                        sessions.focus(1);
                        require(client.options.getCameraType() == cameraB,
                            "Session B perspective was not restored on focus");
                    require(sessions.sessionCameraEntity(0) == playerA && sessions.sessionCameraEntity(1) == playerB,
                            "Each renderer camera must remain attached to its own player");
                    require(sessions.focusPane(0, 0, 854, 480) && sessions.focusedSlot() == 0,
                            "Left pane must focus session A");
                    require(sessions.focusPane(853, 0, 854, 480) && sessions.focusedSlot() == 1,
                            "Right pane must focus session B");
                    sessions.focus(0);
                    movementStartA = playerA.position();
                    movementStartB = playerB.position();
                    movementPitchB = playerB.getXRot();
                    movementYawB = playerB.getYRot();
                    movementPitchA = playerA.getXRot();
                    movementYawA = playerA.getYRot();
                    require(client.mouseHandler.isMouseGrabbed(), "Mouse must be grabbed for the look test");
                    client.options.keyUp.setDown(true);
                    transition(Phase.MOVEMENT_ASSERT);
                }
                case MOVEMENT_ASSERT -> {
                    if (movementTicks++ < 12) {
                        TestMouseAccessor mouse = (TestMouseAccessor) client.mouseHandler;
                        mouse.aero$accumulatedDX(30.0);
                        mouse.aero$accumulatedDY(-30.0);
                        sessions.withSession(1, client.mouseHandler::handleAccumulatedMovement);
                        require(Math.abs(playerB.getXRot() - movementPitchB) < 0.01F
                                        && Math.abs(playerB.getYRot() - movementYawB) < 0.01F,
                                "Continuous mouse input leaked into the inactive player");
                        return;
                    }
                        client.options.keyUp.setDown(false);
                    require(Math.abs(playerA.getXRot() - movementPitchA) > 30.0F,
                            "Continuous mouse input did not noticeably tilt the focused camera");
                    require(Math.abs(playerA.getYRot() - movementYawA) > 30.0F,
                            "Continuous mouse input did not noticeably turn the focused camera");
                    require(Math.abs(playerB.getXRot() - movementPitchB) < 0.01F
                                    && Math.abs(playerB.getYRot() - movementYawB) < 0.01F,
                            "Mouse movement leaked into the inactive player during background frames");
                    require(horizontalDistance(playerA.position(), movementStartA) > 0.05,
                            "Focused player did not respond to forward input");
                    require(horizontalDistance(playerB.position(), movementStartB) < 0.001,
                            "Forward input leaked into the inactive player");
                    require(sessions.sessionCameraEntity(0) == playerA && sessions.sessionCameraEntity(1) == playerB,
                            "A renderer camera crossed session ownership during movement");
                    sessions.focus(1);
                    transition(Phase.PAUSE_SAMPLE);
                    waitTicks = 15;
                }
                case PAUSE_SAMPLE -> {
                    require(sessions.renderedFrames(0) > 0, "Visible inactive session did not render offscreen");
                    if (splitCapture == null) {
                        splitCapture = captureSplit(client, sessions);
                        return;
                    }
                    if (!splitCapture.isDone()) return;
                    SplitSample split = splitCapture.join();
                    require(split.leftNonBlack() > split.halfPixels() / 100,
                            "Left split pane is blank in aero-split.png");
                    require(split.rightNonBlack() > split.halfPixels() / 100,
                            "Right split pane is blank in aero-split.png");
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

    private static CompletableFuture<SplitSample> captureSplit(Minecraft client, SessionManager sessions) {
        int index = nextCaptureIndex(client.gameDirectory.toPath());
        CompletableFuture<ImageSample> left = captureImage(client, index + "-aero-slot0.png", sessions.sessionRenderTarget(0));
        CompletableFuture<ImageSample> right = captureImage(client, index + "-aero-slot1.png", sessions.sessionRenderTarget(1));
        CompletableFuture<ImageSample> composite = captureImage(client, index + "-aero-split.png",
                sessions.presentationTarget(client.gameRenderer.mainRenderTarget()));
        return CompletableFuture.allOf(left, right, composite).thenApply(ignored -> {
            ImageSample combined = composite.join();
            require(left.join().nonBlack() > left.join().pixels() / 100, "Slot 0 render target is blank");
            require(right.join().nonBlack() > right.join().pixels() / 100, "Slot 1 render target is blank");
            return new SplitSample(combined.leftNonBlack(), combined.rightNonBlack(), combined.pixels() / 2);
        });
    }

    private static int nextCaptureIndex(Path directory) {
        int highest = 0;
        try (DirectoryStream<Path> images = Files.newDirectoryStream(directory, "*-aero-split.png")) {
            for (Path image : images) {
                String name = image.getFileName().toString();
                String prefix = name.substring(0, name.length() - "-aero-split.png".length());
                try {
                    highest = Math.max(highest, Integer.parseInt(prefix));
                } catch (NumberFormatException ignored) {
                    // Ignore similarly named artifacts without a numeric run index.
                }
            }
        } catch (IOException exception) {
            throw new CompletionException(exception);
        }
        return highest + 1;
    }

    private static CompletableFuture<ImageSample> captureImage(Minecraft client, String name,
                                                                com.mojang.blaze3d.pipeline.RenderTarget target) {
        CompletableFuture<ImageSample> result = new CompletableFuture<>();
        Screenshot.takeScreenshot(target, image -> {
            try (NativeImage screenshot = image) {
                screenshot.writeToFile(client.gameDirectory.toPath().resolve(name));
                int middle = screenshot.getWidth() / 2;
                long left = 0;
                long right = 0;
                for (int y = 0; y < screenshot.getHeight(); y++) {
                    for (int x = 0; x < screenshot.getWidth(); x++) {
                        if ((screenshot.getPixel(x, y) & 0x00FFFFFF) == 0) continue;
                        if (x < middle) left++;
                        else right++;
                    }
                }
                result.complete(new ImageSample(left + right, left, right,
                        (long) screenshot.getWidth() * screenshot.getHeight()));
            } catch (IOException exception) {
                result.completeExceptionally(new CompletionException(exception));
            }
        });
        return result;
    }

    private static boolean playing(Minecraft client) {
        return client.level != null && client.player != null && client.gui.screen() == null
                && client.getConnection() != null && client.getConnection().getConnection().isConnected();
    }

    private static double horizontalDistance(Vec3 current, Vec3 start) {
        double x = current.x() - start.x();
        double z = current.z() - start.z();
        return Math.sqrt(x * x + z * z);
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

    private record SplitSample(long leftNonBlack, long rightNonBlack, long halfPixels) { }

    private record ImageSample(long nonBlack, long leftNonBlack, long rightNonBlack, long pixels) { }

    private enum Phase {
        TITLE, FIRST, SECOND, MOVEMENT_ASSERT, PAUSE_SAMPLE, PAUSE_READ, PAUSE_COMPARE, PAUSE_ASSERT,
        RUN_SAMPLE, RUN_ASSERT, SWITCHING, CLOSED_FIRST, CLOSED_BOTH, REOPEN, PERSISTED, FINISH, DONE
    }
}
