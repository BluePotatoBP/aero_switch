package com.bluepotatobp.aeroswitch.diagnostics;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.PacketProcessor;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SessionDiagnostics {
    private static final Logger LOGGER = LoggerFactory.getLogger("aero_switch/diagnostics");
    private static final boolean ENABLED = FabricLoader.getInstance().isDevelopmentEnvironment()
            && Boolean.getBoolean("aeroSwitch.diagnostics");
    private static final Map<String, AtomicLong> COUNTS = new ConcurrentHashMap<>();
    private static final AtomicLong QUEUE_HIGH_WATER = new AtomicLong();
    private static volatile Thread clientThread;
    private static ClientStateSnapshot previous;
    private static long nextSample;

    private SessionDiagnostics() {
    }

    public static void initialize() {
        if (!ENABLED) {
            return;
        }
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            clientThread = Thread.currentThread();
            event("client.started", client);
            FabricLoader.getInstance().getAllMods().stream()
                    .map(mod -> mod.getMetadata().getId() + "=" + mod.getMetadata().getVersion().getFriendlyString())
                    .sorted().forEach(mod -> LOGGER.info("baseline mod={}", mod));
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            event("client.stopping", client);
            LOGGER.info("final counters={}", counters());
        });
        ClientTickEvents.END_CLIENT_TICK.register(SessionDiagnostics::onTick);
        ClientPlayConnectionEvents.INIT.register((handler, client) -> event("play.init", handler));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> event("play.join", handler));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> event("play.disconnect", handler));
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            if (server instanceof IntegratedServer) event("server.starting", server);
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (server instanceof IntegratedServer) event("server.started", server);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (server instanceof IntegratedServer) event("server.stopping", server);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            if (server instanceof IntegratedServer) event("server.stopped", server);
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommands.literal("aeroswitch")
                        .then(ClientCommands.literal("diagnostics").executes(context -> {
                            ClientStateSnapshot snapshot = ClientStateSnapshot.capture(Minecraft.getInstance());
                            LOGGER.info("requested snapshot={} counters={}", snapshot, counters());
                            context.getSource().sendFeedback(Component.literal(
                                    "Aero Switch: single-session research harness. State and counters written to the log."));
                            return 1;
                        }))));
        LOGGER.info("Read-only diagnostics enabled; no packet contents, server addresses, or account data are logged");
    }

    private static void onTick(Minecraft client) {
        increment("client.ticks");
        ClientStateSnapshot current = ClientStateSnapshot.capture(client);
        if (!current.equals(previous)) {
            LOGGER.info("state {}", current);
            if (!current.consistent()) {
                increment("state.inconsistent");
                LOGGER.error("Player/world/listener relationship mismatch at end of client tick");
            }
            previous = current;
        }
        long now = System.nanoTime();
        if (now >= nextSample) {
            nextSample = now + 5_000_000_000L;
            Runtime runtime = Runtime.getRuntime();
            LOGGER.info("sample counters={} heapUsedBytes={} heapCommittedBytes={} threads={}",
                    counters(), runtime.totalMemory() - runtime.freeMemory(), runtime.totalMemory(),
                    java.lang.management.ManagementFactory.getThreadMXBean().getThreadCount());
        }
    }

    public static boolean enabled() {
        return ENABLED;
    }

    public static void event(String name, Object owner) {
        if (ENABLED) {
            increment(name);
            LOGGER.info("event={} owner={} thread={}",
                    name, ClientStateSnapshot.identity(owner), Thread.currentThread().getName());
        }
    }

    public static void frame() {
        if (ENABLED) increment("client.frames");
    }

    public static void packetQueue(PacketProcessor processor, int size) {
        if (ENABLED && Thread.currentThread() == clientThread
                && processor == Minecraft.getInstance().packetProcessor()) {
            QUEUE_HIGH_WATER.accumulateAndGet(size, Math::max);
            increment("client.packetDrains");
        }
    }

    public static long count(String name) {
        AtomicLong counter = COUNTS.get(name);
        return counter == null ? 0 : counter.get();
    }

    public static Map<String, Long> counters() {
        Map<String, Long> snapshot = new TreeMap<>();
        COUNTS.forEach((key, value) -> snapshot.put(key, value.get()));
        snapshot.put("client.packetQueueHighWater", QUEUE_HIGH_WATER.get());
        return Map.copyOf(snapshot);
    }

    private static void increment(String name) {
        COUNTS.computeIfAbsent(name, key -> new AtomicLong()).incrementAndGet();
    }
}
