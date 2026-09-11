package com.bluepotatobp.aeroswitch.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Scopes Voxy's process-global instance and lifecycle flag per client session. */
public final class VoxyCompat {
    private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("voxy");
    private static final VoxyStateStore STORE = new VoxyStateStore(4);
    private static final Logger LOGGER = LogManager.getLogger("aero_switch");

    private static volatile Field instanceField;
    private static volatile Field inSessionField;
    private static volatile Method sessionEnd;
    private static volatile Method shutdownInstance;
    private static volatile boolean loggedResolve;
    private static volatile boolean loggedAccessError;

    private VoxyCompat() { }

    public static void capture(int slot) {
        if (!resolve()) return;
        try {
            STORE.capture(slot, new VoxyStateStore.State(
                    instanceField.get(null), inSessionField.getBoolean(null)));
        } catch (Throwable t) {
            logAccessError("capture", t);
        }
    }

    public static void install(int slot) {
        if (!resolve()) return;
        VoxyStateStore.State state = STORE.state(slot);
        try {
            instanceField.set(null, state.instance());
            inSessionField.setBoolean(null, state.inSession());
        } catch (Throwable t) {
            logAccessError("install", t);
        }
    }

    /** Ends and forgets the Voxy lifecycle owned by a closing session. */
    public static void close(int slot) {
        if (!resolve()) {
            STORE.reset(slot);
            return;
        }
        try {
            if (inSessionField.getBoolean(null)) {
                sessionEnd.invoke(null);
            } else if (instanceField.get(null) != null) {
                shutdownInstance.invoke(null);
            }
        } catch (Throwable t) {
            logAccessError("shutdown", t);
        } finally {
            STORE.reset(slot);
        }
    }

    private static boolean resolve() {
        if (instanceField != null) return true;
        if (!LOADED) return false;
        synchronized (VoxyCompat.class) {
            if (instanceField != null) return true;
            try {
                Class<?> common = Class.forName("me.cortex.voxy.commonImpl.VoxyCommon");
                Class<?> events = Class.forName("me.cortex.voxy.client.ClientSessionEvents");
                Field instance = common.getDeclaredField("INSTANCE");
                instance.setAccessible(true);
                inSessionField = events.getField("inSession");
                sessionEnd = events.getMethod("sessionEnd");
                shutdownInstance = common.getMethod("shutdownInstance");
                instanceField = instance;
                if (!loggedResolve) {
                    loggedResolve = true;
                    LOGGER.info("Voxy session scoping active");
                }
                return true;
            } catch (Throwable t) {
                if (!loggedResolve) {
                    loggedResolve = true;
                    LOGGER.warn("Voxy session state could not be resolved ({})", t.getClass().getSimpleName());
                }
                return false;
            }
        }
    }

    private static void logAccessError(String operation, Throwable error) {
        if (!loggedAccessError) {
            loggedAccessError = true;
            LOGGER.warn("Voxy session {} failed ({})", operation, error.getClass().getSimpleName());
        }
    }
}