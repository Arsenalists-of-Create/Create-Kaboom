package com.happysg.kaboom.explosion;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Explosion;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ExplosionEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static com.happysg.kaboom.CreateKaboom.MODID;

/**
 * Bridges a synchronous vanilla probe explosion to Kaboom's custom planner.
 *
 * <p>The actual capture/session map is keyed by {@link Explosion} object identity. The short
 * thread-local arm exists only because the Explosion instance is created inside Level#explode and
 * is therefore unavailable to the caller until after Detonate fires.</p>
 */
@EventBusSubscriber(modid = MODID)
public final class KaboomExplosionCapture {

    private static final ThreadLocal<ProbeHandle> ARMED_PROBE =
            new ThreadLocal<>();

    private static final Map<Explosion, CapturedProbe> CAPTURES =
            Collections.synchronizedMap(new IdentityHashMap<>());

    private KaboomExplosionCapture() {
    }

    static ProbeHandle arm(ServerLevel level) {
        ProbeHandle existing = ARMED_PROBE.get();

        if (existing != null) {
            throw new IllegalStateException(
                    "Kaboom explosion probe was armed recursively on the same thread"
            );
        }

        ProbeHandle handle = new ProbeHandle(level);
        ARMED_PROBE.set(handle);

        return handle;
    }

    static void disarm(ProbeHandle handle) {
        if (ARMED_PROBE.get() == handle) {
            ARMED_PROBE.remove();
        }
    }

    static void discard(ProbeHandle handle) {
        if (handle.explosion != null) {
            CAPTURES.remove(handle.explosion);
        }
    }

    static CapturedProbe consume(Explosion explosion) {
        return CAPTURES.remove(explosion);
    }

    /**
     * Detonate fires after vanilla has built its affected block/entity lists and before normal
     * explosion damage/finalization consumes them.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void captureKaboomProbe(ExplosionEvent.Detonate event) {
        ProbeHandle handle = ARMED_PROBE.get();

        if (handle == null
                || handle.claimed
                || event.getLevel() != handle.level) {
            return;
        }

        List<BlockPos> positions = new ArrayList<>(
                event.getAffectedBlocks().size()
        );

        for (BlockPos pos : event.getAffectedBlocks()) {
            positions.add(pos.immutable());
        }

        CapturedProbe capturedProbe = new CapturedProbe(positions);

        handle.claimed = true;
        handle.explosion = event.getExplosion();

        CAPTURES.put(event.getExplosion(), capturedProbe);

        /*
         * This is a probe, not vanilla terrain destruction. Clear both lists now:
         * - no vanilla per-block breaking, drops, or block-break particles;
         * - no vanilla damage/knockback, because Kaboom applies exactly one custom entity path.
         *
         * ExplosionInteraction.NONE additionally maps to vanilla KEEP as a fail-safe against
         * block destruction should another listener repopulate the block list later.
         */
        event.getAffectedBlocks().clear();
        event.getAffectedEntities().clear();
    }

    /**
     * Runs after normal listeners so another listener cannot repopulate a Kaboom probe's entity
     * or block list before vanilla consumes it. Normal explosions are untouched because only
     * actual captured probe Explosion instances exist in CAPTURES.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void suppressKaboomProbeVanillaEffects(
            ExplosionEvent.Detonate event
    ) {
        if (!CAPTURES.containsKey(event.getExplosion())) {
            return;
        }

        event.getAffectedBlocks().clear();
        event.getAffectedEntities().clear();
    }

    static final class ProbeHandle {

        private final ServerLevel level;
        private Explosion explosion;
        private boolean claimed;

        private ProbeHandle(ServerLevel level) {
            this.level = level;
        }
    }

    static final class CapturedProbe {

        private final List<BlockPos> positions;

        private CapturedProbe(List<BlockPos> positions) {
            this.positions = List.copyOf(positions);
        }

        List<BlockPos> positions() {
            return positions;
        }
    }
}
