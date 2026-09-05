package com.bluepotatobp.aeroswitch;

import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.FutureTask;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.Services;
import net.minecraft.server.WorldStem;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.dedicated.DedicatedServerSettings;
import net.minecraft.server.notifications.NotificationManager;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.util.datafix.DataFixers;

/** Test-only, loopback-only server. No extra JVM or Minecraft client is launched. */
public record TcpFixture(DedicatedServer server, int port) {
    public static FutureTask<TcpFixture> start(Minecraft client, String save) {
        FutureTask<TcpFixture> task = new FutureTask<>(() -> {
            Path directory = client.gameDirectory.toPath().resolve("fixtures").resolve(save);
            Files.createDirectories(directory);
            int port;
            try (ServerSocket reservation = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
                port = reservation.getLocalPort();
            }
            Properties properties = new Properties();
            properties.setProperty("server-ip", "127.0.0.1");
            properties.setProperty("server-port", Integer.toString(port));
            properties.setProperty("online-mode", "false");
            properties.setProperty("enforce-secure-profile", "false");
            properties.setProperty("view-distance", "3");
            properties.setProperty("simulation-distance", "3");
            properties.setProperty("gamemode", "survival");
            properties.setProperty("spawn-protection", "0");
            properties.setProperty("pause-when-empty-seconds", "0");
            properties.setProperty("enable-jmx-monitoring", "false");
            Path settingsFile = directory.resolve("server.properties");
            try (var writer = Files.newBufferedWriter(settingsFile)) {
                properties.store(writer, "Disposable loopback-only Aero Switch test fixture");
            }
            var access = client.getLevelSource().validateAndCreateAccess(save);
            boolean transferred = false;
            var packs = ServerPacksSource.createPackRepository(access);
            WorldStem stem = null;
            try {
                stem = client.createWorldOpenFlows().loadWorldStem(access, access.getUnfixedDataTag(false), false, packs);
                WorldStem loaded = stem;
                NotificationManager notifications = new NotificationManager();
                DedicatedServerSettings settings = new DedicatedServerSettings(settingsFile);
                Services services = Services.create(YggdrasilAuthenticationService.createOffline(Proxy.NO_PROXY), directory.toFile());
                DedicatedServer server = MinecraftServer.spin(thread -> {
                    var fixture = new DedicatedServer(thread, access, packs, loaded, Optional.empty(), settings,
                            DataFixers.getDataFixer(), services, null, notifications);
                    notifications.setServer(fixture);
                    return fixture;
                });
                transferred = true;
                return new TcpFixture(server, port);
            } finally {
                if (!transferred) {
                    if (stem != null) stem.close();
                    access.close();
                }
            }
        });
        client.execute(task);
        return task;
    }

    public void stop() {
        server.halt(false);
    }
}
