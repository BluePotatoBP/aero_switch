package com.bluepotatobp.aeroswitch;

import com.bluepotatobp.aeroswitch.session.SessionManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.pipeline.RenderTarget;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Shared helpers for the automated session scenarios. */
final class ScenarioSupport {
    static final BlockPos MARKER = new BlockPos(0, 100, 0);

    private ScenarioSupport() { }

    static void open(Minecraft client, String save) {
        client.createWorldOpenFlows().openWorld(save, () -> {
            throw new AssertionError("Disposable save failed to open: " + save);
        });
    }

    static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    static boolean playing(Minecraft client) {
        return client.level != null && client.player != null && client.gui.screen() == null
                && client.getConnection() != null && client.getConnection().getConnection().isConnected();
    }

    static double horizontalDistance(Vec3 current, Vec3 start) {
        double x = current.x() - start.x();
        double z = current.z() - start.z();
        return Math.sqrt(x * x + z * z);
    }

    static CompletableFuture<ServerSample> sample(IntegratedServer server) {
        return server.submit(() -> new ServerSample(server.isPaused(), server.overworld().getGameTime(),
                server.overworld().getBlockState(MARKER).is(Blocks.EMERALD_BLOCK)));
    }

    static CompletableFuture<SplitSample> captureSplit(Minecraft client, SessionManager sessions) {
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

    static CompletableFuture<ImageSample> captureImage(Minecraft client, String name, RenderTarget target) {
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

    record ServerSample(boolean paused, long time, boolean marker) { }

    record SplitSample(long leftNonBlack, long rightNonBlack, long halfPixels) { }

    record ImageSample(long nonBlack, long leftNonBlack, long rightNonBlack, long pixels) { }
}
