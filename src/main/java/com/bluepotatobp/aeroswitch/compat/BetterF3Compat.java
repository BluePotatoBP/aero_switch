package com.bluepotatobp.aeroswitch.compat;

import java.lang.reflect.Field;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Per-session scoping for BetterF3's shared animation and velocity state.
 * BetterF3's animation uses static fields, while its
 * {@code CoordsModule} is a singleton that renders the F3 velocity line by caching
 * the previous position in {@code prevPos} and computing
 * {@code prevPos - currentPos} (then scaling by FPS up to 20). Every Aero Switch
 * pane renders with a different installed player, but they all share the one
 * singleton, so each pane computes velocity against whichever pane rendered last
 * and the values appear as the negation of each other.
 *
 * <p>Snapshotting around focus switches is not enough: with two F3 menus open both
 * sessions run {@code CoordsModule.update} inside the same frame (the foreground
 * render plus the background offscreen renders), so the singleton is clobbered
 * several times per frame. We therefore bracket every session's
 * {@code DebugScreenOverlay.extractRenderState} (see
 * {@code BetterF3DebugScreenMixin}) and swap the singleton's position state
 * around it, keeping a private per-slot copy of the cache. Reflection keeps Aero
 * Switch free of a hard compile-time dependency on BetterF3.</p>
 *
 * <p>The swap is a no-op while a session has no level or camera. Vanilla extracts
 * the debug overlay every frame even before any world is joined, and seeding the
 * cache there with an origin position marks the slot as initialized with a bogus
 * previous position, so the first real render reports the session's coordinates
 * as a velocity jump.</p>
 */
public final class BetterF3Compat {
    /** Snapshot of BetterF3's static menu animation state. */
    public record AnimationState(int xPos, long lastUpdate, boolean closing) {
        public static final AnimationState INITIAL = new AnimationState(200, 0L, false);
    }

    /** Snapshot of BetterF3's velocity cache. Plain doubles keep this Minecraft-free for unit tests. */
    public record CoordsState(
            double prevX, double prevY, double prevZ,
            long positionUpdateTime,
            double velocityX, double velocityY, double velocityZ) {

        public static final CoordsState NONE =
                new CoordsState(0.0, 0.0, 0.0, 0L, 0.0, 0.0, 0.0);
    }

    private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("betterf3");
    private static final CoordsStateStore STORE = new CoordsStateStore(4);
    private static final AnimationStateStore ANIMATION_STORE = new AnimationStateStore(4);
    private static final Logger LOGGER = LogManager.getLogger("aero_switch");

    private static volatile boolean loggedResolve;
    private static volatile boolean loggedReadError;
    private static volatile boolean loggedWriteError;
    private static volatile boolean loggedAnimationResolve;
    private static volatile boolean loggedAnimationError;

    private static volatile Object coordsModule;
    private static volatile Field prevPos;
    private static volatile Field velocity;
    private static volatile Field positionUpdateTime;
    private static volatile Field moduleListLeft;
    private static volatile Field moduleListRight;
    private static volatile Field animationXPos;
    private static volatile Field animationLastUpdate;
    private static volatile Field animationClosing;

    private BetterF3Compat() { }

    /**
     * Swaps the singleton's position cache to the given session at the head of
     * its debug overlay extraction, persisting the previous owner's state first.
     * No-op until the session has a level and a camera: a pre-world render must
     * never seed the cache with an origin position, or the first real render
     * subtracts the session's coordinates from (0,0,0) and the velocity jumps to
     * thousands.
     *
     * <p>All bookkeeping happens here at the head of the extraction. The end of
     * the extraction cannot be used reliably: {@code extractRenderState} returns
     * early when no debug entries are visible, and a mixin TAIL callback only
     * runs at the method's final return.</p>
     */
    public static void beginRender(int slot) {
        scopeAnimation(slot);
        if (!resolve()) return;
        try {
            CoordsState current = currentPositionState();
            if (current == null) return;
            CoordsState live = readState();
            CoordsState next = beginScoped(slot, live, current, STORE);
            if (next != null) {
                writeState(next);
            }
        } catch (Throwable ignored) {
            // BetterF3 changed shape; leave its velocity cache shared.
        }
    }

    /** Clears a session's cached position state when its slot is freed. */
    public static void reset(int slot) {
        STORE.reset(slot);
        ANIMATION_STORE.reset(slot);
    }

    private static void scopeAnimation(int slot) {
        if (!resolveAnimation()) return;
        try {
            AnimationState live = new AnimationState(
                    animationXPos.getInt(null),
                    animationLastUpdate.getLong(null),
                    animationClosing.getBoolean(null));
            AnimationState next = ANIMATION_STORE.begin(slot, live);
            if (next == null) return;
            animationXPos.setInt(null, next.xPos());
            animationLastUpdate.setLong(null, next.lastUpdate());
            animationClosing.setBoolean(null, next.closing());
        } catch (Throwable t) {
            if (!loggedAnimationError) {
                loggedAnimationError = true;
                LOGGER.warn("BetterF3 animation state scoping failed ({})", t.getClass().getSimpleName());
            }
        }
    }

    private static boolean resolveAnimation() {
        if (animationXPos != null) return true;
        if (!LOADED) return false;
        synchronized (BetterF3Compat.class) {
            if (animationXPos != null) return true;
            try {
                Class<?> utils = Class.forName("me.cominixo.betterf3.utils.Utils");
                Field xPos = utils.getField("xPos");
                Field lastUpdate = utils.getField("lastAnimationUpdate");
                Field closing = utils.getField("closingAnimation");
                animationLastUpdate = lastUpdate;
                animationClosing = closing;
                animationXPos = xPos;
                if (!loggedAnimationResolve) {
                    loggedAnimationResolve = true;
                    LOGGER.info("BetterF3 animation scoping active");
                }
                return true;
            } catch (Throwable t) {
                if (!loggedAnimationResolve) {
                    loggedAnimationResolve = true;
                    LOGGER.warn("BetterF3 animation state could not be resolved ({})", t.getClass().getSimpleName());
                }
                return false;
            }
        }
    }

    /**
     * The session's current root-vehicle position and game time, or null when it
     * has no level or camera yet. Never a zeroed sentinel: a pre-world render
     * must not seed the cache with an origin position.
     */
    private static CoordsState currentPositionState() {
        Minecraft client = Minecraft.getInstance();
        Entity camera = client.getCameraEntity();
        if (client.level == null || camera == null) return null;
        Entity vehicle = camera.getRootVehicle();
        return new CoordsState(vehicle.getX(), vehicle.getY(), vehicle.getZ(),
                client.level.getGameTime(), 0.0, 0.0, 0.0);
    }

    /**
     * Computes the state to install before {@code slot}'s render, or null when
     * nothing should be written. Nothing is written while the session has no
     * level or camera, nor when the singleton already holds this slot's live
     * state. A session that never had world state is seeded with its own current
     * position and marked initialized. Pure logic so it is unit-testable.
     */
    static CoordsState beginScoped(int slot, CoordsState live, CoordsState current, CoordsStateStore store) {
        if (current == null) return null;
        if (store.owner() == slot) return null;
        CoordsState saved = store.begin(slot, live);
        if (saved == null) return null;
        if (store.initialized(slot)) return saved;
        store.markInitialized(slot);
        return current;
    }

    /**
     * Resolves the rendered CoordsModule and its fields on first use. BetterF3
     * draws from the module lists in {@code BaseModule.modules} and
     * {@code BaseModule.modulesRight}; the copy kept in {@code allModules} can be
     * a stale instance left over from an earlier module rebuild, so only the
     * rendered lists are trusted. BetterF3 may rebuild those lists later (for
     * example on a resource reload), so the resolved instance is re-checked on
     * every call and re-resolved when it has left the rendered lists.
     */
    private static boolean resolve() {
        if (coordsModule != null && moduleStillListed()) return true;
        if (!LOADED) return false;
        synchronized (BetterF3Compat.class) {
            if (coordsModule != null && moduleStillListed()) return true;
            try {
                Class<?> baseModule = Class.forName("me.cominixo.betterf3.modules.BaseModule");
                moduleListLeft = baseModule.getField("modules");
                moduleListRight = baseModule.getField("modulesRight");
                Object candidate = findCoordsModule(moduleListLeft);
                if (candidate == null) candidate = findCoordsModule(moduleListRight);
                if (candidate != null) {
                    prevPos = candidate.getClass().getDeclaredField("prevPos");
                    velocity = candidate.getClass().getDeclaredField("velocity");
                    positionUpdateTime = candidate.getClass().getDeclaredField("positionUpdateTime");
                    prevPos.setAccessible(true);
                    velocity.setAccessible(true);
                    positionUpdateTime.setAccessible(true);
                    coordsModule = candidate;
                    if (!loggedResolve) {
                        loggedResolve = true;
                        LOGGER.info("BetterF3 velocity scoping active");
                    }
                    return true;
                }
            } catch (Throwable t) {
                if (!loggedResolve) {
                    loggedResolve = true;
                    LOGGER.warn("BetterF3 CoordsModule could not be resolved ({})", t.getClass().getSimpleName());
                }
                // BetterF3 changed shape; leave its velocity cache shared.
            }
            return false;
        }
    }

    private static Object findCoordsModule(Field listField) throws IllegalAccessException {
        Object value = listField.get(null);
        if (!(value instanceof List<?> modules)) return null;
        for (Object candidate : modules) {
            if (candidate != null && candidate.getClass().getName().endsWith(".CoordsModule")) return candidate;
        }
        return null;
    }

    private static boolean moduleStillListed() {
        try {
            return moduleListLeft != null && containsModule(moduleListLeft)
                    || moduleListRight != null && containsModule(moduleListRight);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean containsModule(Field listField) throws IllegalAccessException {
        Object value = listField.get(null);
        return value instanceof List<?> modules && modules.contains(coordsModule);
    }

    private static CoordsState readState() {
        try {
            Vec3 p = (Vec3) prevPos.get(coordsModule);
            Vec3 v = (Vec3) velocity.get(coordsModule);
            return new CoordsState(
                    p.x(), p.y(), p.z(),
                    positionUpdateTime.getLong(coordsModule),
                    v.x(), v.y(), v.z());
        } catch (Throwable t) {
            if (!loggedReadError) {
                loggedReadError = true;
                LOGGER.warn("BetterF3 readState failed ({})", t.getClass().getSimpleName());
            }
            return CoordsState.NONE;
        }
    }

    private static void writeState(CoordsState state) {
        try {
            prevPos.set(coordsModule, new Vec3(state.prevX(), state.prevY(), state.prevZ()));
            positionUpdateTime.setLong(coordsModule, state.positionUpdateTime());
            velocity.set(coordsModule, new Vec3(state.velocityX(), state.velocityY(), state.velocityZ()));
        } catch (Throwable t) {
            if (!loggedWriteError) {
                loggedWriteError = true;
                LOGGER.warn("BetterF3 writeState failed ({})", t.getClass().getSimpleName());
            }
            // Ignore; the velocity cache stays as-is.
        }
    }
}
