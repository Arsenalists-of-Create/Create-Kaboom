package com.happysg.kaboom.explosion;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.happysg.kaboom.CreateKaboom.MODID;

/**
 * Kaboom terrain-effect engine.
 *
 * <p>Vanilla/NeoForge is deliberately used only as a synchronous 3D reachability probe.
 * {@link KaboomExplosionCapture} snapshots the vanilla candidate list during
 * {@code ExplosionEvent.Detonate}, clears the vanilla destruction/entity lists, and this
 * class converts the snapshot into silent queued material operations.</p>
 *
 * <p>No world state is read or written off-thread.</p>
 */
public final class KaboomExplosionEngine {

    public static final TagKey<Block> BLAST_PROTECTED = blockTag("blast_protected");
    public static final TagKey<Block> BLAST_FOLIAGE = blockTag("blast_foliage");
    public static final TagKey<Block> BLAST_CRACKABLE_MASONRY =
            blockTag("blast_crackable_masonry");
    public static final TagKey<Block> BLAST_WOOD = blockTag("blast_wood");

    /*
     * Optional material-behavior tag only. It is never used to choose the primary blast volume;
     * it simply lets modpacks opt their natural terrain blocks into the post-plan floating-terrain
     * cleanup described below.
     */
    public static final TagKey<Block> BLAST_TERRAIN = blockTag("blast_terrain");

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int TREE_HORIZONTAL_LIMIT = 16;
    private static final int TREE_UPWARD_LIMIT = 48;
    private static final int TREE_DOWNWARD_LIMIT = 8;
    private static final int MAX_TREE_PARTS = 4_096;

    /*
     * Vanilla's candidate list comes from a finite set of propagation rays. On a large surface
     * blast that list has natural ray gaps, which otherwise show up as the square/cubic-looking
     * holes visible in the crater. This bounded, solid-only continuity pass fills those local
     * gaps without consulting heightmaps or crossing actual air cavities.
     */
    private static final int MIN_SURFACE_CONTINUITY_STEPS = 4;
    private static final int MAX_SURFACE_CONTINUITY_STEPS = 12;
    private static final Direction[] SURFACE_CONTINUITY_DIRECTIONS = {
            Direction.DOWN,
            Direction.NORTH,
            Direction.SOUTH,
            Direction.WEST,
            Direction.EAST
    };

    /*
     * Surface HE now separates the visible terrain crater from the wider surface-effects radius.
     * The old 18-block profile radius remains the broad damage/effects radius; only 62% of it is
     * used for the actual bowl excavation. This keeps tree/building/foliage damage broad while
     * leaving a noticeably smaller crater in the ground.
     */
    private static final float SURFACE_CRATER_RADIUS_FRACTION = 0.62F;
    private static final float SURFACE_CRATER_DEPTH_FRACTION = 0.70F;
    private static final int MAX_SURFACE_CRATER_UPWARD_RANGE = 3;

    /*
     * Post-plan stabilization is deliberately local and capped. It only removes detached natural
     * terrain components after the bowl has undercut them; it does not walk through air, inspect
     * heightmaps, or treat buildings as generic terrain.
     */
    private static final int SURFACE_STABILITY_MARGIN = 2;
    private static final int SURFACE_STABILITY_DEPTH_MARGIN = 3;
    private static final int SURFACE_STABILITY_MAX_UPWARD = 10;
    private static final int MAX_FLOATING_TERRAIN_CLEANUP = 2_048;

    /*
     * Do not request neighbor/shape propagation here: this is a direct terrain replacement,
     * not normal player breaking. UPDATE_SUPPRESS_DROPS prevents state-change-driven drops;
     * UPDATE_CLIENTS keeps clients synchronized.
     */
    private static final int SILENT_BLOCK_CHANGE_FLAGS =
            Block.UPDATE_CLIENTS
                    | Block.UPDATE_KNOWN_SHAPE
                    | Block.UPDATE_SUPPRESS_DROPS;

    private KaboomExplosionEngine() {
    }

    /**
     * Primary entry point. The custom entity path is used exactly once: vanilla probe entity
     * damage is cleared during {@code ExplosionEvent.Detonate}, then {@link #applyEntityEffects}
     * runs after candidate capture succeeds.
     */
    public static BlastResult detonate(
            ServerLevel level,
            Vec3 center,
            BlastProfile profile,
            Entity source,
            DamageSource damageSource
    ) {
        return detonate(
                level,
                center,
                profile,
                source,
                damageSource,
                level.getRandom().nextLong(),
                completion -> {
                }
        );
    }

    public static BlastResult detonate(
            ServerLevel level,
            Vec3 center,
            BlastProfile profile,
            Entity source,
            DamageSource damageSource,
            CompletionListener completionListener
    ) {
        return detonate(
                level,
                center,
                profile,
                source,
                damageSource,
                level.getRandom().nextLong(),
                completionListener
        );
    }

    public static BlastResult detonate(
            ServerLevel level,
            Vec3 center,
            BlastProfile profile,
            Entity source,
            DamageSource damageSource,
            long seed,
            CompletionListener completionListener
    ) {
        long startedAtNanos = System.nanoTime();

        BlockPos origin = BlockPos.containing(center.x, center.y, center.z);
        float burialFactor = getBurialFactor(level, origin, profile);
        boolean buriedMode = burialFactor >= profile.buriedModeThreshold();

        float terrainRadius = buriedMode
                ? profile.buriedRadius()
                : getSurfaceCraterRadius(profile);

        int maxDepth = buriedMode
                ? profile.buriedMaxDepth()
                : getSurfaceCraterMaxDepth(profile);

        /*
         * A buried blast has far less outward foliage effect, but this no longer participates
         * in selecting terrain. It only controls post-candidate foliage/wood behavior.
         */
        float foliageRadius = profile.foliageRadius() * (1.0F - burialFactor * 0.80F);

        BlastContext context = new BlastContext(
                level,
                center,
                origin,
                profile,
                source,
                damageSource != null ? damageSource : level.damageSources().generic(),
                seed,
                burialFactor,
                buriedMode,
                terrainRadius,
                maxDepth,
                foliageRadius
        );

        /*
         * The thread-local arm only bridges the short synchronous Level#explode call. The
         * captured session itself is stored and consumed by actual Explosion object identity.
         */
        KaboomExplosionCapture.ProbeHandle probeHandle =
                KaboomExplosionCapture.arm(level);

        Explosion vanillaProbe = null;
        boolean probeReturnedNormally = false;

        try {
            /*
             * NeoForge 1.21.1 maps ExplosionInteraction.NONE to vanilla KEEP. Vanilla still
             * computes its 3D toBlow list, but it will not perform terrain destruction in
             * finalizeExplosion even if another event listener later repopulates the list.
             */
            vanillaProbe = level.explode(
                    source,
                    context.damageSource,
                    null,
                    center,
                    getProbeStrength(context),
                    false,
                    Level.ExplosionInteraction.NONE
            );
            probeReturnedNormally = true;
        } finally {
            if (!probeReturnedNormally) {
                /*
                 * If a listener or the probe itself throws after Detonate captured candidates,
                 * discard that identity-keyed session rather than leaking it.
                 */
                KaboomExplosionCapture.discard(probeHandle);
            }

            KaboomExplosionCapture.disarm(probeHandle);
        }

        KaboomExplosionCapture.CapturedProbe capturedProbe =
                vanillaProbe == null
                        ? null
                        : KaboomExplosionCapture.consume(vanillaProbe);

        /*
         * ExplosionEvent.Start may have cancelled the probe, or another incompatible coremod
         * may have bypassed Detonate. Do not apply custom damage or terrain in that case.
         */
        if (capturedProbe == null) {
            long probeElapsed = System.nanoTime() - startedAtNanos;

            return BlastResult.notQueued(
                    burialFactor,
                    buriedMode,
                    terrainRadius,
                    maxDepth,
                    probeElapsed
            );
        }

        BlastPlan plan = buildPlan(context, capturedProbe.positions());

        /*
         * This is intentionally after successful capture. Vanilla entity damage and knockback
         * were suppressed by the capture event, so the custom damage path cannot double-hit.
         */
        applyEntityEffects(context);

        long probeAndPlanNanos = System.nanoTime() - startedAtNanos;

        KaboomBlastQueue.enqueue(
                new QueuedBlast(
                        context,
                        plan,
                        startedAtNanos,
                        completionListener != null ? completionListener : completion -> {
                        }
                )
        );

        return new BlastResult(
                true,
                burialFactor,
                buriedMode,
                terrainRadius,
                maxDepth,
                plan.size(),
                probeAndPlanNanos
        );
    }

    /**
     * Candidate range is intentionally larger than the final Kaboom shell. Vanilla determines
     * physical reachability; Kaboom's own profile filter narrows those reachable candidates to
     * the desired crater or buried ellipsoid.
     */
    private static float getProbeStrength(BlastContext context) {
        float shellReach = context.buriedMode
                ? Math.max(
                        context.terrainRadius,
                        context.maxDepth
                )
                : Math.max(
                        context.profile.surfaceRadius()
                                + context.profile.edgeRoughnessBlocks()
                                + 1.0F,
                        context.profile.surfaceUpwardRange()
                );

        float foliageReach = Math.max(
                context.foliageRadius,
                context.profile.foliageVerticalRange()
        );

        /*
         * A strength at least as large as the maximum desired reach leaves enough energy for
         * vanilla's resistance-aware rays to produce useful candidates through normal terrain.
         * The final filter prevents this extra probe reach from enlarging the Kaboom shape.
         */
        return Math.max(4.0F, Math.max(shellReach, foliageReach) + 2.0F);
    }

    private static float getSurfaceCraterRadius(BlastProfile profile) {
        return Math.max(
                2.0F,
                profile.surfaceRadius() * SURFACE_CRATER_RADIUS_FRACTION
        );
    }

    private static int getSurfaceCraterMaxDepth(BlastProfile profile) {
        return Math.max(
                1,
                Math.round(profile.surfaceMaxDepth() * SURFACE_CRATER_DEPTH_FRACTION)
        );
    }

    private static int getSurfaceCraterUpwardRange(BlastProfile profile) {
        return Math.max(
                1,
                Math.min(
                        MAX_SURFACE_CRATER_UPWARD_RANGE,
                        profile.surfaceUpwardRange()
                )
        );
    }

    /**
     * Builds the custom plan from vanilla propagation candidates. Surface HE uses those candidates
     * as seeds for a bounded solid-continuity mask; there is no heightmap lookup, world-surface
     * lookup, or per-column terrain reference.
     */
    private static BlastPlan buildPlan(
            BlastContext context,
            List<BlockPos> candidates
    ) {
        BlastPlan plan = new BlastPlan();
        LongOpenHashSet treeSeeds = new LongOpenHashSet();

        if (context.buriedMode) {
            /*
             * Buried/AP mode remains an exact candidate filter. Its job is to respect caves,
             * tunnels, and walls as closely as vanilla propagation provides them.
             */
            for (BlockPos candidate : candidates) {
                BlockPos pos = candidate.immutable();

                if (!context.level.hasChunkAt(pos)) {
                    continue;
                }

                ShellSample mainShell = getBuriedShellSample(context, pos);
                if (!mainShell.inside) {
                    continue;
                }

                BlockState state = context.level.getBlockState(pos);
                scheduleMainOperation(
                        context,
                        plan,
                        treeSeeds,
                        pos,
                        state,
                        mainShell
                );
            }
        } else {
            /*
             * Surface HE rasterizes a continuous mathematical bowl after vanilla has supplied
             * 3D reachable seeds. The bowl is intentionally smaller than the broad surface
             * damage radius; it is terrain excavation only, not the full destructive footprint.
             */
            planSmoothSurfaceBowl(
                    context,
                    candidates,
                    plan,
                    treeSeeds
            );
        }

        for (BlockPos candidate : candidates) {
            BlockPos pos = candidate.immutable();

            if (!context.level.hasChunkAt(pos)) {
                continue;
            }

            boolean insideMain = context.buriedMode
                    ? getBuriedShellSample(context, pos).inside
                    : getSurfaceShellSample(context, pos).inside;

            BlockState state = context.level.getBlockState(pos);

            /*
             * Surface-only effects retain the broad old radius but never perform generic terrain
             * excavation. They concentrate the outer destruction into foliage/tree damage, wood
             * damage or ignition, masonry cracking, and deterministic grass-to-dirt replacement.
             */
            if (!context.buriedMode && !insideMain) {
                SurfaceEffectSample surfaceEffects = getSurfaceEffectSample(context, pos);
                if (surfaceEffects.inside) {
                    planSurfaceEffects(
                            context,
                            plan,
                            treeSeeds,
                            pos,
                            state,
                            surfaceEffects
                    );
                }
            }

            /*
             * Outer foliage stripping and wood ignition remain exact vanilla candidates. The
             * continuity pass is deliberately not used here, so foliage cannot spread through an
             * open cave or across an unrelated structure.
             */
            FoliageSample foliageShell = getFoliageSample(context, pos);
            if (foliageShell.inside && !insideMain) {
                planOuterFoliageAndWood(
                        context,
                        plan,
                        pos,
                        state,
                        foliageShell.energy
                );
            }
        }

        if (!context.buriedMode) {
            /*
             * Remove local terrain islands that the planned bowl would otherwise leave suspended
             * above a valley wall or an uneven natural slope. This is a post-plan support check,
             * not a terrain-surface scan.
             */
            planUnsupportedSurfaceTerrainCleanup(context, plan);
        }

        for (long packedSeed : treeSeeds) {
            planConnectedTree(
                    context,
                    plan,
                    BlockPos.of(packedSeed)
            );
        }

        return plan;
    }

    /**
     * Builds a smooth surface bowl from a continuous radial floor function, then gates it through
     * a bounded continuity mask grown from actual vanilla candidate blocks. There is no heightmap
     * lookup and no terrain-surface scanner: the only vertical limits are calculated from the
     * detonation center and the bowl profile.
     */
    private static void planSmoothSurfaceBowl(
            BlastContext context,
            List<BlockPos> candidates,
            BlastPlan plan,
            LongOpenHashSet treeSeeds
    ) {
        LongOpenHashSet reachableBowl = buildSurfaceContinuityMask(
                context,
                candidates
        );

        for (long packedPos : reachableBowl) {
            BlockPos pos = BlockPos.of(packedPos);

            if (!context.level.hasChunkAt(pos)) {
                continue;
            }

            ShellSample shell = getSurfaceShellSample(context, pos);
            if (!shell.inside) {
                continue;
            }

            BlockState state = context.level.getBlockState(pos);
            scheduleMainOperation(
                    context,
                    plan,
                    treeSeeds,
                    pos,
                    state,
                    shell
            );
        }
    }

    /**
     * Creates a local, air-gap-aware closure around vanilla-reachable solid blocks. The closure
     * walks only through solid/vegetation blocks inside the mathematical bowl, never through air
     * or fluids, and never through protected/unbreakable/block-entity blocks. This fills finite
     * vanilla-ray gaps but cannot jump an open cave, shaft, or room.
     */
    private static LongOpenHashSet buildSurfaceContinuityMask(
            BlastContext context,
            List<BlockPos> candidates
    ) {
        LongOpenHashSet reachable = new LongOpenHashSet();
        ArrayDeque<SurfaceReachabilityNode> queue = new ArrayDeque<>();

        for (BlockPos candidate : candidates) {
            BlockPos pos = candidate.immutable();

            if (!context.level.hasChunkAt(pos)
                    || !getSurfaceShellSample(context, pos).inside) {
                continue;
            }

            BlockState state = context.level.getBlockState(pos);
            if (!isSurfaceContinuityPassable(context.level, pos, state)) {
                continue;
            }

            if (reachable.add(pos.asLong())) {
                queue.addLast(new SurfaceReachabilityNode(pos, 0));
            }
        }

        int maxSteps = Math.min(
                MAX_SURFACE_CONTINUITY_STEPS,
                Math.max(
                        MIN_SURFACE_CONTINUITY_STEPS,
                        context.maxDepth + 2
                )
        );

        while (!queue.isEmpty()) {
            SurfaceReachabilityNode node = queue.removeFirst();

            if (node.steps >= maxSteps) {
                continue;
            }

            for (Direction direction : SURFACE_CONTINUITY_DIRECTIONS) {
                BlockPos next = node.pos.relative(direction);

                if (!context.level.hasChunkAt(next)
                        || !getSurfaceShellSample(context, next).inside) {
                    continue;
                }

                long nextKey = next.asLong();
                if (reachable.contains(nextKey)) {
                    continue;
                }

                BlockState nextState = context.level.getBlockState(next);
                if (!isSurfaceContinuityPassable(
                        context.level,
                        next,
                        nextState
                )) {
                    continue;
                }

                reachable.add(nextKey);
                queue.addLast(new SurfaceReachabilityNode(
                        next.immutable(),
                        node.steps + 1
                ));
            }
        }

        return reachable;
    }

    private static boolean isSurfaceContinuityPassable(
            ServerLevel level,
            BlockPos pos,
            BlockState state
    ) {
        if (state.isAir()
                || !state.getFluidState().isEmpty()
                || state.is(BLAST_PROTECTED)
                || state.hasBlockEntity()
                || state.getDestroySpeed(level, pos) < 0.0F) {
            return false;
        }

        /*
         * Replaceable plants are deliberately passable rather than acting like an air cavity.
         * This lets a flower/short-grass layer be stripped without preventing the supported grass
         * block directly below it from participating in the smooth bowl.
         */
        return true;
    }

    /**
     * The broad surface-effects shell is intentionally exact-candidate only. Unlike the smaller
     * bowl, it is never continuity-filled, so it cannot grow through a cave or turn broad terrain
     * into a second crater.
     */
    private static SurfaceEffectSample getSurfaceEffectSample(
            BlastContext context,
            BlockPos pos
    ) {
        double dx = pos.getX() + 0.5D - context.center.x;
        double dz = pos.getZ() + 0.5D - context.center.z;
        float horizontalDistance = (float) Math.sqrt(dx * dx + dz * dz);

        float effectRadius = Math.max(
                1.0F,
                context.profile.surfaceRadius()
                        + craterNoise(context.seed, pos.getX(), pos.getZ())
                        * context.profile.edgeRoughnessBlocks()
        );
        float normalizedHorizontalDistance = horizontalDistance / effectRadius;

        if (normalizedHorizontalDistance >= 1.0F) {
            return SurfaceEffectSample.OUTSIDE;
        }

        float vertical = (float) (pos.getY() + 0.5D - context.center.y);
        float downwardRange = Math.max(1.0F, context.maxDepth + 1.0F);
        float upwardRange = Math.max(1.0F, context.profile.surfaceUpwardRange());

        if (vertical < -downwardRange || vertical > upwardRange) {
            return SurfaceEffectSample.OUTSIDE;
        }

        float radialEnergy = (float) Math.pow(
                1.0F - normalizedHorizontalDistance,
                0.70F
        );
        float upwardFraction = vertical <= 0.0F
                ? 0.0F
                : vertical / upwardRange;

        return new SurfaceEffectSample(
                true,
                Mth.clamp(radialEnergy * (1.0F - upwardFraction * 0.20F), 0.0F, 1.0F),
                Mth.clamp(normalizedHorizontalDistance, 0.0F, 1.0F)
        );
    }

    private static void planSurfaceEffects(
            BlastContext context,
            BlastPlan plan,
            LongOpenHashSet treeSeeds,
            BlockPos pos,
            BlockState state,
            SurfaceEffectSample effects
    ) {
        if (cannotModify(context.level, pos, state)) {
            return;
        }

        if (isFoliage(state) || isSoftVegetation(state)) {
            plan.schedule(
                    pos,
                    state,
                    Operation.REMOVE_FOLIAGE,
                    effects.energy,
                    context.profile.maxBlockChanges()
            );
            return;
        }

        if (isGrassBlock(state)) {
            float dirtChance = lerp(
                    Math.max(0.55F, context.profile.grassDirtChanceInner()),
                    1.0F,
                    effects.normalizedHorizontalDistance
            );

            if (chance(
                    context.seed ^ 0x5346555246414345L,
                    pos.asLong(),
                    dirtChance
            )) {
                plan.schedule(
                        pos,
                        state,
                        Operation.CONVERT_GRASS_TO_DIRT,
                        effects.energy,
                        context.profile.maxBlockChanges()
                );
            }
            return;
        }

        if (isWood(state)) {
            if (effects.energy >= 0.52F) {
                plan.schedule(
                        pos,
                        state,
                        Operation.DESTROY,
                        effects.energy,
                        context.profile.maxBlockChanges()
                );
                treeSeeds.add(pos.asLong());
            } else if (context.profile.allowFire()
                    && effects.energy >= context.profile.woodIgniteThreshold()) {
                plan.schedule(
                        pos,
                        state,
                        Operation.IGNITE_WOOD,
                        effects.energy,
                        context.profile.maxBlockChanges()
                );
            }
            return;
        }

        if (isCrackableMasonry(state)
                && effects.energy >= Math.max(
                        0.35F,
                        context.profile.masonryCrackThreshold()
                )
                && getCrackedVariant(state) != null) {
            plan.schedule(
                    pos,
                    state,
                    Operation.CRACK_MASONRY,
                    effects.energy,
                    context.profile.maxBlockChanges()
            );
        }
    }

    /**
     * Finds remaining, terrain-like components that would be disconnected from the stable ground
     * after the currently planned bowl removals. Only detached terrain is removed; buildings,
     * block entities, protected blocks, fluids, and air are never traversed by this pass.
     */
    private static void planUnsupportedSurfaceTerrainCleanup(
            BlastContext context,
            BlastPlan plan
    ) {
        int radius = Mth.ceil(
                context.terrainRadius
                        + context.profile.edgeRoughnessBlocks()
                        + SURFACE_STABILITY_MARGIN
        );

        int minX = context.origin.getX() - radius;
        int maxX = context.origin.getX() + radius;
        int minZ = context.origin.getZ() - radius;
        int maxZ = context.origin.getZ() + radius;
        int minY = Math.max(
                context.level.getMinBuildHeight(),
                Mth.floor(context.center.y - context.maxDepth - SURFACE_STABILITY_DEPTH_MARGIN)
        );
        int maxY = Math.min(
                context.level.getMaxBuildHeight() - 1,
                Mth.ceil(context.center.y + Math.min(
                        SURFACE_STABILITY_MAX_UPWARD,
                        context.profile.surfaceUpwardRange()
                ))
        );

        LongOpenHashSet remainingTerrain = new LongOpenHashSet();
        LongOpenHashSet anchoredTerrain = new LongOpenHashSet();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();

        /* First snapshot every remaining terrain cell. Anchor testing must happen only after
         * this set is complete; otherwise iteration order could mistake another detached terrain
         * cell for stable external support. */
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    BlockPos pos = new BlockPos(x, y, z);

                    if (!context.level.hasChunkAt(pos)
                            || plan.removesSupportingBlock(pos.asLong())) {
                        continue;
                    }

                    BlockState state = context.level.getBlockState(pos);
                    if (isSurfaceCleanupTerrain(context.level, pos, state)) {
                        remainingTerrain.add(pos.asLong());
                    }
                }
            }
        }

        for (long key : remainingTerrain) {
            BlockPos pos = BlockPos.of(key);

            if (isSurfaceCleanupAnchor(
                    context,
                    plan,
                    pos,
                    minX,
                    maxX,
                    minY,
                    maxY,
                    minZ,
                    maxZ,
                    remainingTerrain
            )) {
                anchoredTerrain.add(key);
                queue.addLast(pos);
            }
        }

        while (!queue.isEmpty()) {
            BlockPos pos = queue.removeFirst();

            for (Direction direction : Direction.values()) {
                BlockPos neighbor = pos.relative(direction);
                long neighborKey = neighbor.asLong();

                if (remainingTerrain.contains(neighborKey)
                        && anchoredTerrain.add(neighborKey)) {
                    queue.addLast(neighbor);
                }
            }
        }

        int cleanupLimit = Math.min(
                MAX_FLOATING_TERRAIN_CLEANUP,
                Math.max(64, context.profile.maxBlockChanges() / 3)
        );
        int scheduled = 0;

        for (long key : remainingTerrain) {
            if (anchoredTerrain.contains(key) || scheduled >= cleanupLimit) {
                continue;
            }

            BlockPos pos = BlockPos.of(key);
            BlockState state = context.level.getBlockState(pos);

            plan.schedule(
                    pos,
                    state,
                    Operation.DESTROY,
                    1.0F,
                    context.profile.maxBlockChanges()
            );
            scheduled++;
        }
    }

    private static boolean isSurfaceCleanupAnchor(
            BlastContext context,
            BlastPlan plan,
            BlockPos pos,
            int minX,
            int maxX,
            int minY,
            int maxY,
            int minZ,
            int maxZ,
            LongOpenHashSet remainingTerrain
    ) {
        if (pos.getX() == minX || pos.getX() == maxX
                || pos.getZ() == minZ || pos.getZ() == maxZ
                || pos.getY() == minY) {
            return true;
        }

        /*
         * A terrain block directly resting on an unplanned solid stays anchored even if that
         * support is not itself classified as natural terrain. Only down and horizontal contacts
         * count: a tree or roof above cannot falsely hold a floating terrain chunk in place.
         */
        for (Direction direction : SURFACE_CONTINUITY_DIRECTIONS) {
            BlockPos neighbor = pos.relative(direction);
            long neighborKey = neighbor.asLong();

            if (remainingTerrain.contains(neighborKey)
                    || plan.removesSupportingBlock(neighborKey)
                    || !context.level.hasChunkAt(neighbor)) {
                continue;
            }

            BlockState neighborState = context.level.getBlockState(neighbor);
            if (isStableUnplannedSupport(context.level, neighbor, neighborState)) {
                return true;
            }
        }

        return false;
    }

    private static boolean isStableUnplannedSupport(
            ServerLevel level,
            BlockPos pos,
            BlockState state
    ) {
        return !state.isAir()
                && !state.canBeReplaced()
                && state.getFluidState().isEmpty()
                && !state.getCollisionShape(
                        level,
                        pos,
                        CollisionContext.empty()
                ).isEmpty();
    }

    private static boolean isSurfaceCleanupTerrain(
            ServerLevel level,
            BlockPos pos,
            BlockState state
    ) {
        if (state.is(BLAST_TERRAIN)) {
            return !cannotModify(level, pos, state);
        }

        /*
         * Conservative vanilla fallback. Modded terrain still participates in the primary blast
         * via vanilla resistance/propagation; datapacks can add it to kaboom:blast_terrain when
         * they want this optional anti-floating cleanup behavior as well.
         */
        return state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.DIRT)
                || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.ROOTED_DIRT)
                || state.is(Blocks.PODZOL)
                || state.is(Blocks.MYCELIUM)
                || state.is(Blocks.MUD)
                || state.is(Blocks.CLAY)
                || state.is(Blocks.SAND)
                || state.is(Blocks.RED_SAND)
                || state.is(Blocks.GRAVEL)
                || state.is(Blocks.SANDSTONE)
                || state.is(Blocks.RED_SANDSTONE)
                || state.is(Blocks.STONE)
                || state.is(Blocks.GRANITE)
                || state.is(Blocks.DIORITE)
                || state.is(Blocks.ANDESITE)
                || state.is(Blocks.DEEPSLATE)
                || state.is(Blocks.COBBLED_DEEPSLATE)
                || state.is(Blocks.TUFF)
                || state.is(Blocks.CALCITE)
                || state.is(Blocks.DRIPSTONE_BLOCK)
                || state.is(Blocks.NETHERRACK)
                || state.is(Blocks.SOUL_SAND)
                || state.is(Blocks.SOUL_SOIL)
                || state.is(Blocks.BASALT)
                || state.is(Blocks.BLACKSTONE)
                || state.is(Blocks.END_STONE);
    }

    private static void scheduleMainOperation(
            BlastContext context,
            BlastPlan plan,
            LongOpenHashSet treeSeeds,
            BlockPos pos,
            BlockState state,
            ShellSample shell
    ) {
        Operation operation = resolveMainOperation(
                context,
                pos,
                state,
                shell.energy,
                shell.normalizedHorizontalDistance
        );

        if (operation == null) {
            return;
        }

        plan.schedule(
                pos,
                state,
                operation,
                shell.energy,
                context.profile.maxBlockChanges()
        );

        if (operation == Operation.DESTROY && isWood(state)) {
            treeSeeds.add(pos.asLong());
        }
    }

    /**
     * Broad but shallow surface shell.
     *
     * <p>Unlike a 3D ellipsoid, this behaves like a crater floor function per X/Z over the
     * vanilla-reachable candidate set: every reachable block from the bowl floor upward to the
     * local upward range is eligible. That produces a smooth bowl rather than a vanilla-like
     * cubic/spherical cavity while still respecting 3D reachability from the probe.</p>
     *
     * <p>This is still centered only on the detonation point. It does not consult a terrain
     * heightmap or scan for a world surface.</p>
     */
    private static ShellSample getSurfaceShellSample(
            BlastContext context,
            BlockPos pos
    ) {
        double dx = pos.getX() + 0.5D - context.center.x;
        double dz = pos.getZ() + 0.5D - context.center.z;
        float horizontalDistance = (float) Math.sqrt(dx * dx + dz * dz);

        float localRadius = getTerrainRadiusAt(context, pos.getX(), pos.getZ());
        float normalizedHorizontalDistance = horizontalDistance / localRadius;

        if (normalizedHorizontalDistance >= 1.0F) {
            return ShellSample.OUTSIDE;
        }

        /*
         * Radial distance is converted into a 0..1 bowl factor before the legacy-style bowl
         * exponent below is applied.
         */
        float radialFade = Mth.clamp(
                1.0F - normalizedHorizontalDistance * normalizedHorizontalDistance,
                0.0F,
                1.0F
        );
        /*
         * This is intentionally the same broad, shallow bowl curve that made the previous
         * surface-column crater look natural. Only the old terrain-surface lookup is gone.
         */
        float bowl = (float) Math.pow(radialFade, 1.35F);

        /*
         * Depth roughness fades out with the bowl term so the rim stays smooth and does not keep
         * isolated deep bites alive at the outer edge.
         */
        float depthNoise = craterNoise(
                context.seed ^ 0x44455054484E4F49L,
                pos.getX(),
                pos.getZ()
        ) * context.profile.depthRoughnessBlocks() * bowl;

        float downwardExtent = Math.max(
                0.0F,
                context.maxDepth * bowl + depthNoise
        );

        /*
         * Nearby structures and trees still take damage above the impact point, but that upward
         * range also fades toward the rim so the visible edge stays soft.
         */
        float upwardExtent = Math.max(
                0.0F,
                getSurfaceCraterUpwardRange(context.profile) * (0.25F + 0.75F * bowl)
        );

        double blockCenterY = pos.getY() + 0.5D;
        double floorY = context.center.y - downwardExtent;
        double ceilingY = context.center.y + upwardExtent;

        /*
         * Surface mode is not a 3D blob around the center. Instead, every vanilla-reachable block
         * between the local crater floor and local ceiling is part of the crater column. This is
         * what gives the terrain result a smooth bowl profile.
         */
        if (blockCenterY < floorY || blockCenterY > ceilingY) {
            return ShellSample.OUTSIDE;
        }

        float upwardFraction = blockCenterY <= context.center.y
                ? 0.0F
                : (float) ((blockCenterY - context.center.y)
                / Math.max(0.001D, upwardExtent));

        /*
         * Energy is primarily radial and falls fully to zero at the rim. A mild upward taper keeps
         * structures above the center from receiving full terrain-cutting energy.
         */
        float energy = Mth.clamp(
                bowl * (1.0F - upwardFraction * 0.25F),
                0.0F,
                1.0F
        );

        return new ShellSample(
                true,
                energy,
                Mth.clamp(normalizedHorizontalDistance, 0.0F, 1.0F)
        );
    }

    /**
     * Buried/AP mode uses a genuinely 3D ellipsoid around the actual detonation position.
     * Caves, walls, and air paths already influenced this candidate set through vanilla rays.
     */
    private static ShellSample getBuriedShellSample(
            BlastContext context,
            BlockPos pos
    ) {
        double dx = pos.getX() + 0.5D - context.center.x;
        double dy = pos.getY() + 0.5D - context.center.y;
        double dz = pos.getZ() + 0.5D - context.center.z;

        float horizontalRadius = getTerrainRadiusAt(
                context,
                pos.getX(),
                pos.getZ()
        );

        float verticalRadius = Math.max(1.0F, context.maxDepth);

        float horizontalNormalized = (float) Math.sqrt(dx * dx + dz * dz)
                / horizontalRadius;
        float verticalNormalized = (float) Math.abs(dy) / verticalRadius;
        float ellipsoidDistance = (float) Math.sqrt(
                horizontalNormalized * horizontalNormalized
                        + verticalNormalized * verticalNormalized
        );

        if (ellipsoidDistance > 1.0F) {
            return ShellSample.OUTSIDE;
        }

        return new ShellSample(
                true,
                Mth.clamp(1.0F - ellipsoidDistance, 0.0F, 1.0F),
                Mth.clamp(horizontalNormalized, 0.0F, 1.0F)
        );
    }

    private static FoliageSample getFoliageSample(
            BlastContext context,
            BlockPos pos
    ) {
        if (context.foliageRadius <= 0.01F) {
            return FoliageSample.OUTSIDE;
        }

        double dx = pos.getX() + 0.5D - context.center.x;
        double dz = pos.getZ() + 0.5D - context.center.z;
        float horizontalDistance = (float) Math.sqrt(dx * dx + dz * dz);
        float normalizedHorizontalDistance = horizontalDistance / context.foliageRadius;

        if (normalizedHorizontalDistance > 1.0F) {
            return FoliageSample.OUTSIDE;
        }

        float vertical = (float) (pos.getY() + 0.5D - context.center.y);
        float downwardRange = context.buriedMode
                ? context.maxDepth
                : context.maxDepth + 1.0F;
        float upwardRange = Math.max(1.0F, context.profile.foliageVerticalRange());

        if (vertical < -downwardRange || vertical > upwardRange) {
            return FoliageSample.OUTSIDE;
        }

        float verticalFraction = vertical < 0.0F
                ? -vertical / Math.max(0.001F, downwardRange)
                : vertical / upwardRange;

        float energy = Mth.clamp(
                (1.0F - normalizedHorizontalDistance)
                        * (1.0F - verticalFraction * 0.20F),
                0.0F,
                1.0F
        );

        return new FoliageSample(true, energy);
    }

    private static float getTerrainRadiusAt(
            BlastContext context,
            int x,
            int z
    ) {
        float roughness = craterNoise(context.seed, x, z);

        return Math.max(
                1.0F,
                context.terrainRadius
                        + roughness * context.profile.edgeRoughnessBlocks()
        );
    }

    /*
     * Interpolated value noise yields broad coherent lobes. It is deterministic per seed and
     * avoids the speckled, independent-per-block look of white noise.
     */
    private static float craterNoise(long seed, int x, int z) {
        float broad = valueNoise2D(seed, x, z, 0.16F);
        float detail = valueNoise2D(
                seed ^ 0x4B41424F4F4D4E4FL,
                x,
                z,
                0.48F
        );

        return broad * 0.75F + detail * 0.25F;
    }

    private static float valueNoise2D(
            long seed,
            int x,
            int z,
            float scale
    ) {
        float sampleX = x * scale;
        float sampleZ = z * scale;

        int x0 = Mth.floor(sampleX);
        int z0 = Mth.floor(sampleZ);

        float tx = smoothStep(sampleX - x0);
        float tz = smoothStep(sampleZ - z0);

        float a = hashNoise(seed, x0, z0);
        float b = hashNoise(seed, x0 + 1, z0);
        float c = hashNoise(seed, x0, z0 + 1);
        float d = hashNoise(seed, x0 + 1, z0 + 1);

        return lerp(
                lerp(a, b, tx),
                lerp(c, d, tx),
                tz
        );
    }

    private static float hashNoise(long seed, int x, int z) {
        long mixed = mix64(
                seed
                        ^ ((long) x * 341_873_128_712L)
                        ^ ((long) z * 132_897_987_541L)
        );

        return ((mixed >>> 40) / 16_777_215.0F) * 2.0F - 1.0F;
    }

    private static float smoothStep(float value) {
        return value * value * (3.0F - 2.0F * value);
    }

    private static Operation resolveMainOperation(
            BlastContext context,
            BlockPos pos,
            BlockState state,
            float normalizedEnergy,
            float normalizedDistance
    ) {
        if (cannotModify(context.level, pos, state)) {
            return null;
        }

        if (isFoliage(state) || isSoftVegetation(state)) {
            return Operation.REMOVE_FOLIAGE;
        }

        float blastPower = normalizedEnergy * context.profile.maxBlockResistance();

        if (isWood(state)) {
            float resistance = state.getBlock().getExplosionResistance();

            if (blastPower >= resistance && normalizedEnergy >= 0.35F) {
                return Operation.DESTROY;
            }

            if (context.profile.allowFire()
                    && normalizedEnergy >= context.profile.woodIgniteThreshold()) {
                return Operation.IGNITE_WOOD;
            }

            return null;
        }

        if (isGrassBlock(state)) {
            if (normalizedEnergy >= context.profile.grassDestroyThreshold()) {
                return Operation.DESTROY;
            }

            /*
             * Deterministic and monotonic toward the outer edge of the selected shell.
             */
            float dirtChance = lerp(
                    context.profile.grassDirtChanceInner(),
                    context.profile.grassDirtChanceOuter(),
                    Mth.clamp(normalizedDistance, 0.0F, 1.0F)
            );

            return chance(
                    context.seed ^ 0x4B41424F4F4D4752L,
                    pos.asLong(),
                    dirtChance
            )
                    ? Operation.CONVERT_GRASS_TO_DIRT
                    : null;
        }

        if (isCrackableMasonry(state)) {
            float resistance = state.getBlock().getExplosionResistance();

            if (normalizedEnergy >= context.profile.masonryDestroyThreshold()
                    && blastPower >= resistance) {
                return Operation.DESTROY;
            }

            if (normalizedEnergy >= context.profile.masonryCrackThreshold()
                    && getCrackedVariant(state) != null) {
                return Operation.CRACK_MASONRY;
            }

            return null;
        }

        float resistance = state.getBlock().getExplosionResistance();

        return blastPower >= resistance
                ? Operation.DESTROY
                : null;
    }

    private static void planOuterFoliageAndWood(
            BlastContext context,
            BlastPlan plan,
            BlockPos pos,
            BlockState state,
            float foliageEnergy
    ) {
        if (cannotModify(context.level, pos, state)) {
            return;
        }

        if (isFoliage(state) || isSoftVegetation(state)) {
            if (foliageEnergy >= context.profile.foliageStripThreshold()) {
                plan.schedule(
                        pos,
                        state,
                        Operation.REMOVE_FOLIAGE,
                        foliageEnergy,
                        context.profile.maxBlockChanges()
                );
            }

            return;
        }

        if (isWood(state)
                && context.profile.allowFire()
                && foliageEnergy >= context.profile.woodIgniteThreshold()) {
            plan.schedule(
                    pos,
                    state,
                    Operation.IGNITE_WOOD,
                    foliageEnergy,
                    context.profile.maxBlockChanges()
            );
        }
    }

    static boolean applyOneChange(
            BlastContext context,
            PlannedChange change
    ) {
        BlockPos pos = BlockPos.of(change.positionLong);

        if (!context.level.hasChunkAt(pos)) {
            return false;
        }

        BlockState current = context.level.getBlockState(pos);

        if (current.getBlock() != change.expectedBlock
                || cannotModify(context.level, pos, current)) {
            return false;
        }

        return switch (change.operation) {
            case DESTROY, REMOVE_FOLIAGE ->
                    replaceBlockSilently(
                            context.level,
                            pos,
                            Blocks.AIR.defaultBlockState()
                    );

            case CONVERT_GRASS_TO_DIRT -> {
                /*
                 * Changes are sorted bottom-to-top. If the planned support below has already
                 * been removed, this becomes air instead of leaving dirt floating.
                 */
                BlockState replacement = hasBlastSupportBelow(context.level, pos)
                        ? Blocks.DIRT.defaultBlockState()
                        : Blocks.AIR.defaultBlockState();

                yield replaceBlockSilently(
                        context.level,
                        pos,
                        replacement
                );
            }

            case CRACK_MASONRY -> {
                BlockState cracked = getCrackedVariant(current);

                yield cracked != null && replaceBlockSilently(
                        context.level,
                        pos,
                        cracked
                );
            }

            case IGNITE_WOOD ->
                    tryIgniteWood(
                            context,
                            pos,
                            change.normalizedEnergy
                    );
        };
    }

    private static boolean replaceBlockSilently(
            ServerLevel level,
            BlockPos pos,
            BlockState newState
    ) {
        return level.setBlock(
                pos,
                newState,
                SILENT_BLOCK_CHANGE_FLAGS
        );
    }

    private static boolean hasBlastSupportBelow(
            ServerLevel level,
            BlockPos pos
    ) {
        BlockPos belowPos = pos.below();

        if (!level.hasChunkAt(belowPos)) {
            return false;
        }

        BlockState below = level.getBlockState(belowPos);

        if (below.isAir()
                || below.canBeReplaced()
                || !below.getFluidState().isEmpty()
                || isFoliage(below)
                || isSoftVegetation(below)
                || isWood(below)
                || below.hasBlockEntity()) {
            return false;
        }

        /*
         * The full-cube fallback supports modded terrain without making terrain tags part of
         * initial candidate selection.
         */
        return below.isSolidRender(level, belowPos);
    }

    private static boolean tryIgniteWood(
            BlastContext context,
            BlockPos logPos,
            float normalizedEnergy
    ) {
        if (!context.profile.allowFire()) {
            return false;
        }

        float fireChance = context.profile.woodIgniteChance()
                * normalizedEnergy;

        boolean placedAnyFire = false;

        for (Direction direction : Direction.values()) {
            BlockPos firePos = logPos.relative(direction);

            if (!context.level.hasChunkAt(firePos)
                    || context.level.isRainingAt(firePos)) {
                continue;
            }

            BlockState targetState = context.level.getBlockState(firePos);

            if (!isOpen(targetState)
                    || !chance(context.seed, firePos.asLong(), fireChance)) {
                continue;
            }

            BlockState fireState = BaseFireBlock.getState(
                    context.level,
                    firePos
            );

            if (!fireState.canSurvive(context.level, firePos)) {
                continue;
            }

            if (replaceBlockSilently(context.level, firePos, fireState)) {
                placedAnyFire = true;
            }
        }

        return placedAnyFire;
    }

    /**
     * Custom damage/knockback path. Vanilla's entity list is cleared by the capture class, so
     * this is the only entity-damage path for Kaboom explosions.
     */
    private static void applyEntityEffects(BlastContext context) {
        if (context.profile.entityRadius() <= 0.0F) {
            return;
        }

        AABB searchBox = new AABB(
                context.center.x,
                context.center.y,
                context.center.z,
                context.center.x,
                context.center.y,
                context.center.z
        ).inflate(context.profile.entityRadius());

        List<Entity> entities = context.level.getEntities(
                context.source,
                searchBox,
                entity -> entity.isAlive() && !entity.isSpectator()
        );

        for (Entity entity : entities) {
            Vec3 targetCenter = entity.getBoundingBox().getCenter();
            double distance = targetCenter.distanceTo(context.center);

            if (distance >= context.profile.entityRadius()) {
                continue;
            }

            float normalizedDistance = (float) (
                    distance / context.profile.entityRadius()
            );

            float falloff = 1.0F - normalizedDistance;
            float damage = context.profile.entityDamage()
                    * falloff
                    * falloff;

            float exposure = getEntityExposure(
                    context.level,
                    context.center,
                    entity
            );

            float coverAdjustedExposure = Mth.lerp(
                    context.profile.coveredDamageMultiplier(),
                    1.0F,
                    exposure
            );

            damage *= coverAdjustedExposure;

            if (damage > 0.05F) {
                entity.hurt(context.damageSource, damage);
            }

            Vec3 push = entity.position().subtract(context.center);

            if (push.lengthSqr() < 0.0001D) {
                push = new Vec3(0.0D, 1.0D, 0.0D);
            }

            Vec3 knockback = push.normalize().scale(
                    context.profile.entityKnockback()
                            * falloff
                            * coverAdjustedExposure
            );

            entity.setDeltaMovement(
                    entity.getDeltaMovement().add(knockback)
            );
        }
    }

    private static float getEntityExposure(
            ServerLevel level,
            Vec3 explosionCenter,
            Entity entity
    ) {
        AABB box = entity.getBoundingBox();
        Vec3 center = box.getCenter();

        Vec3[] samples = {
                center,
                entity.getEyePosition(),
                new Vec3(box.minX, center.y, center.z),
                new Vec3(box.maxX, center.y, center.z),
                new Vec3(center.x, center.y, box.minZ),
                new Vec3(center.x, center.y, box.maxZ)
        };

        int visibleSamples = 0;

        for (Vec3 sample : samples) {
            if (hasClearBlastPath(level, explosionCenter, sample)) {
                visibleSamples++;
            }
        }

        return visibleSamples / (float) samples.length;
    }

    private static boolean hasClearBlastPath(
            ServerLevel level,
            Vec3 start,
            Vec3 end
    ) {
        Vec3 delta = end.subtract(start);

        if (delta.lengthSqr() < 0.0001D) {
            return true;
        }

        Vec3 rayStart = start.add(delta.normalize().scale(0.08D));

        BlockHitResult result = level.clip(
                new ClipContext(
                        rayStart,
                        end,
                        ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE,
                        CollisionContext.empty()
                )
        );

        return result.getType() == HitResult.Type.MISS;
    }

    private static float getBurialFactor(
            ServerLevel level,
            BlockPos origin,
            BlastProfile profile
    ) {
        float containmentScore = 0.0F;

        for (BurialProbe probe : BURIAL_PROBES) {
            if (Math.abs(probe.dy()) > profile.burialProbeHeight()) {
                continue;
            }

            BlockPos samplePos = origin.offset(
                    probe.dx(),
                    probe.dy(),
                    probe.dz()
            );

            if (isBlastContainingBlock(level, samplePos)) {
                containmentScore += probe.score();
            }
        }

        return Mth.clamp(
                containmentScore / Math.max(1.0F, profile.blocksForFullBurial()),
                0.0F,
                1.0F
        );
    }

    private static boolean isBlastContainingBlock(
            ServerLevel level,
            BlockPos pos
    ) {
        if (!level.hasChunkAt(pos)) {
            return false;
        }

        BlockState state = level.getBlockState(pos);

        if (isOpen(state)
                || !state.getFluidState().isEmpty()
                || isFoliage(state)
                || isSoftVegetation(state)
                || isWood(state)) {
            return false;
        }

        return !state.getCollisionShape(
                level,
                pos,
                CollisionContext.empty()
        ).isEmpty();
    }

    private static void planConnectedTree(
            BlastContext context,
            BlastPlan plan,
            BlockPos seed
    ) {
        if (!plan.claimTreeSearch(seed.asLong())) {
            return;
        }

        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        LongOpenHashSet visited = new LongOpenHashSet();
        List<TreePart> treeParts = new ArrayList<>();

        queue.add(seed);

        boolean foundLeaves = false;

        while (!queue.isEmpty() && treeParts.size() < MAX_TREE_PARTS) {
            BlockPos pos = queue.removeFirst();
            long packedPos = pos.asLong();

            if (!visited.add(packedPos)) {
                continue;
            }

            int horizontalDistance = Math.max(
                    Math.abs(pos.getX() - seed.getX()),
                    Math.abs(pos.getZ() - seed.getZ())
            );

            int verticalOffset = pos.getY() - seed.getY();

            if (horizontalDistance > TREE_HORIZONTAL_LIMIT
                    || verticalOffset > TREE_UPWARD_LIMIT
                    || verticalOffset < -TREE_DOWNWARD_LIMIT
                    || !context.level.hasChunkAt(pos)) {
                continue;
            }

            BlockState state = context.level.getBlockState(pos);

            /*
             * Protected blocks terminate this tree search. A protected custom log/leaves block
             * remains protected rather than being silently removed by the expansion.
             */
            if (cannotModify(context.level, pos, state)
                    || (!isWood(state) && !isFoliage(state))) {
                continue;
            }

            plan.markTreePart(packedPos);
            treeParts.add(new TreePart(pos, state));

            if (isFoliage(state)) {
                foundLeaves = true;
            }

            for (Direction direction : Direction.values()) {
                queue.addLast(pos.relative(direction));
            }
        }

        /*
         * Only tree-like clusters containing leaves are expanded. Log buildings therefore retain
         * normal shell behavior instead of being treated as an unlimited connected tree.
         */
        if (!foundLeaves) {
            return;
        }

        for (TreePart part : treeParts) {
            plan.schedule(
                    part.pos(),
                    part.state(),
                    Operation.DESTROY,
                    1.0F,
                    context.profile.maxBlockChanges()
            );
        }
    }

    private static boolean cannotModify(
            ServerLevel level,
            BlockPos pos,
            BlockState state
    ) {
        return state.isAir()
                || state.is(BLAST_PROTECTED)
                || state.getDestroySpeed(level, pos) < 0.0F
                || state.hasBlockEntity();
    }

    private static boolean isOpen(BlockState state) {
        return state.isAir() || state.canBeReplaced();
    }

    private static boolean isSoftVegetation(BlockState state) {
        return !state.isAir()
                && state.canBeReplaced()
                && state.getFluidState().isEmpty();
    }

    private static boolean isFoliage(BlockState state) {
        return state.is(BlockTags.LEAVES)
                || state.is(BLAST_FOLIAGE);
    }

    private static boolean isWood(BlockState state) {
        return state.is(BlockTags.LOGS)
                || state.is(BLAST_WOOD);
    }

    private static boolean isGrassBlock(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK);
    }

    private static boolean isCrackableMasonry(BlockState state) {
        return state.is(BLAST_CRACKABLE_MASONRY)
                || state.is(Blocks.STONE_BRICKS)
                || state.is(Blocks.DEEPSLATE_BRICKS)
                || state.is(Blocks.NETHER_BRICKS);
    }

    private static BlockState getCrackedVariant(BlockState state) {
        if (state.is(Blocks.STONE_BRICKS)) {
            return Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
        }

        if (state.is(Blocks.DEEPSLATE_BRICKS)) {
            return Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState();
        }

        if (state.is(Blocks.NETHER_BRICKS)) {
            return Blocks.CRACKED_NETHER_BRICKS.defaultBlockState();
        }

        return null;
    }

    private static boolean chance(
            long seed,
            long position,
            float probability
    ) {
        if (probability <= 0.0F) {
            return false;
        }

        if (probability >= 1.0F) {
            return true;
        }

        long mixed = mix64(seed ^ position);
        double value = (mixed >>> 11) * 0x1.0p-53;

        return value < probability;
    }

    private static long mix64(long value) {
        value = (value ^ (value >>> 33)) * 0xff51afd7ed558ccdL;
        value = (value ^ (value >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return value ^ (value >>> 33);
    }

    private static float lerp(
            float start,
            float end,
            float amount
    ) {
        return start + (end - start) * amount;
    }

    private static TagKey<Block> blockTag(String name) {
        return TagKey.create(
                Registries.BLOCK,
                ResourceLocation.fromNamespaceAndPath(MODID, name)
        );
    }

    public record BlastResult(
            boolean queued,
            float burialFactor,
            boolean buriedMode,
            float terrainRadius,
            int maxDepth,
            int plannedChanges,
            long probeAndPlanNanos
    ) {
        private static BlastResult notQueued(
                float burialFactor,
                boolean buriedMode,
                float terrainRadius,
                int maxDepth,
                long probeAndPlanNanos
        ) {
            return new BlastResult(
                    false,
                    burialFactor,
                    buriedMode,
                    terrainRadius,
                    maxDepth,
                    0,
                    probeAndPlanNanos
            );
        }
    }

    public record BlastCompletion(
            int actualChanges,
            int plannedChanges,
            long totalWallNanos,
            long terrainWorkNanos,
            int ticks
    ) {
    }

    @FunctionalInterface
    public interface CompletionListener {
        void onComplete(BlastCompletion completion);
    }

    public record BlastProfile(
            float surfaceRadius,
            float buriedRadius,
            int surfaceMaxDepth,
            int buriedMaxDepth,
            int surfaceUpwardRange,

            float edgeRoughnessBlocks,
            float depthRoughnessBlocks,

            float foliageRadius,
            int foliageVerticalRange,
            float foliageStripThreshold,

            float maxBlockResistance,
            float grassDestroyThreshold,
            float grassDirtChanceInner,
            float grassDirtChanceOuter,

            float masonryCrackThreshold,
            float masonryDestroyThreshold,

            float woodIgniteThreshold,
            float woodIgniteChance,
            boolean allowFire,

            float entityRadius,
            float entityDamage,
            float entityKnockback,
            float coveredDamageMultiplier,

            float buriedModeThreshold,
            int burialProbeHeight,
            int blocksForFullBurial,

            int maxBlockChanges
    ) {

        /**
         * Compatibility constructor for callers still using the former surface-column profile
         * layout. {@code maxSurfaceYOffset} now means surface upward shell range; it is not
         * consulted for any terrain/heightmap scan. {@code dropBlocks} is intentionally ignored:
         * Kaboom terrain changes never create vanilla drops.
         */
        @Deprecated
        public BlastProfile(
                float surfaceRadius,
                float buriedRadius,
                int surfaceMaxDepth,
                int buriedMaxDepth,

                float edgeRoughnessBlocks,
                float depthRoughnessBlocks,

                float foliageRadius,
                int foliageVerticalSearch,
                float foliageStripThreshold,

                int maxSurfaceYOffset,

                float maxBlockResistance,
                float grassDestroyThreshold,
                float grassDirtChanceInner,
                float grassDirtChanceOuter,

                float masonryCrackThreshold,
                float masonryDestroyThreshold,

                float woodIgniteThreshold,
                float woodIgniteChance,
                boolean allowFire,

                boolean dropBlocks,

                float entityRadius,
                float entityDamage,
                float entityKnockback,
                float coveredDamageMultiplier,

                int burialProbeHeight,
                int blocksForFullBurial,

                int maxBlockChanges
        ) {
            this(
                    surfaceRadius,
                    buriedRadius,
                    surfaceMaxDepth,
                    buriedMaxDepth,
                    maxSurfaceYOffset,
                    edgeRoughnessBlocks,
                    depthRoughnessBlocks,
                    foliageRadius,
                    foliageVerticalSearch,
                    foliageStripThreshold,
                    maxBlockResistance,
                    grassDestroyThreshold,
                    grassDirtChanceInner,
                    grassDirtChanceOuter,
                    masonryCrackThreshold,
                    masonryDestroyThreshold,
                    woodIgniteThreshold,
                    woodIgniteChance,
                    allowFire,
                    entityRadius,
                    entityDamage,
                    entityKnockback,
                    coveredDamageMultiplier,
                    0.42F,
                    burialProbeHeight,
                    blocksForFullBurial,
                    maxBlockChanges
            );
        }

        @Deprecated
        public int foliageVerticalSearch() {
            return foliageVerticalRange;
        }

        @Deprecated
        public int maxSurfaceYOffset() {
            return surfaceUpwardRange;
        }

        @Deprecated
        public boolean dropBlocks() {
            return false;
        }

        public static BlastProfile highExplosiveShell() {
            return new BlastProfile(
                    18.0F, // surfaceRadius
                    8.0F,  // buriedRadius
                    3,     // surfaceMaxDepth
                    10,    // buriedMaxDepth
                    10,    // surfaceUpwardRange

                    2.25F, // edgeRoughnessBlocks
                    0.50F, // depthRoughnessBlocks

                    25.0F, // foliageRadius
                    20,    // foliageVerticalRange
                    0.10F, // foliageStripThreshold

                    14.0F, // maxBlockResistance
                    0.50F, // grassDestroyThreshold
                    0.30F, // grassDirtChanceInner
                    0.92F, // grassDirtChanceOuter

                    0.28F, // masonryCrackThreshold
                    0.82F, // masonryDestroyThreshold

                    0.35F, // woodIgniteThreshold
                    0.28F, // woodIgniteChance
                    true,  // allowFire

                    12.0F, // entityRadius
                    20.0F, // entityDamage
                    1.35F, // entityKnockback
                    0.16F, // coveredDamageMultiplier

                    0.42F, // buriedModeThreshold
                    6,     // burialProbeHeight
                    5,     // blocksForFullBurial

                    5_000  // maxBlockChanges
            );
        }
    }

    static final class QueuedBlast {

        private final BlastContext context;
        private final List<PlannedChange> changes;
        private final long startedAtNanos;
        private final CompletionListener completionListener;

        private int nextChange;
        private int appliedChanges;
        private int ticks;
        private long terrainWorkNanos;

        QueuedBlast(
                BlastContext context,
                BlastPlan plan,
                long startedAtNanos,
                CompletionListener completionListener
        ) {
            this.context = context;
            this.changes = new ArrayList<>(plan.changes.values());
            this.startedAtNanos = startedAtNanos;
            this.completionListener = completionListener;

            /*
             * Lower supporting blocks are resolved first. This makes a grass-to-dirt operation
             * correctly turn into air if its support was destroyed by the same queued blast.
             */
            this.changes.sort(
                    Comparator.comparingInt(change ->
                            BlockPos.of(change.positionLong).getY()
                    )
            );
        }

        boolean process(
                long deadlineNanos,
                int maxChangesPerBlastTick
        ) {
            long workStartedAt = System.nanoTime();
            int processedThisTick = 0;
            ticks++;

            while (nextChange < changes.size()
                    && processedThisTick < maxChangesPerBlastTick
                    && System.nanoTime() < deadlineNanos) {

                PlannedChange change = changes.get(nextChange++);

                if (applyOneChange(context, change)) {
                    appliedChanges++;
                }

                processedThisTick++;
            }

            terrainWorkNanos += System.nanoTime() - workStartedAt;

            return nextChange >= changes.size();
        }

        void complete() {
            try {
                completionListener.onComplete(
                        new BlastCompletion(
                                appliedChanges,
                                changes.size(),
                                System.nanoTime() - startedAtNanos,
                                terrainWorkNanos,
                                ticks
                        )
                );
            } catch (RuntimeException exception) {
                LOGGER.error("Kaboom queued-blast completion callback failed", exception);
            }
        }
    }

    static final class BlastPlan {

        private final Long2ObjectOpenHashMap<PlannedChange> changes =
                new Long2ObjectOpenHashMap<>();

        private final LongOpenHashSet expandedTreeSearches =
                new LongOpenHashSet();

        boolean claimTreeSearch(long posLong) {
            return expandedTreeSearches.add(posLong);
        }

        void markTreePart(long posLong) {
            expandedTreeSearches.add(posLong);
        }

        void schedule(
                BlockPos pos,
                BlockState expectedState,
                Operation operation,
                float normalizedEnergy,
                int maxChanges
        ) {
            long key = pos.asLong();
            PlannedChange existing = changes.get(key);

            if (existing == null) {
                if (changes.size() >= maxChanges) {
                    return;
                }

                changes.put(
                        key,
                        new PlannedChange(
                                key,
                                expectedState.getBlock(),
                                operation,
                                normalizedEnergy
                        )
                );

                return;
            }

            boolean strongerOperation =
                    operation.priority > existing.operation.priority;

            boolean sameOperationMoreEnergy =
                    operation == existing.operation
                            && normalizedEnergy > existing.normalizedEnergy;

            if (strongerOperation || sameOperationMoreEnergy) {
                changes.put(
                        key,
                        new PlannedChange(
                                key,
                                expectedState.getBlock(),
                                operation,
                                normalizedEnergy
                        )
                );
            }
        }

        boolean removesSupportingBlock(long posLong) {
            PlannedChange change = changes.get(posLong);

            return change != null
                    && (change.operation == Operation.DESTROY
                    || change.operation == Operation.REMOVE_FOLIAGE);
        }

        int size() {
            return changes.size();
        }
    }

    static final class BlastContext {

        final ServerLevel level;
        final Vec3 center;
        final BlockPos origin;
        final BlastProfile profile;
        final Entity source;
        final DamageSource damageSource;
        final long seed;
        final float burialFactor;
        final boolean buriedMode;
        final float terrainRadius;
        final int maxDepth;
        final float foliageRadius;

        private BlastContext(
                ServerLevel level,
                Vec3 center,
                BlockPos origin,
                BlastProfile profile,
                Entity source,
                DamageSource damageSource,
                long seed,
                float burialFactor,
                boolean buriedMode,
                float terrainRadius,
                int maxDepth,
                float foliageRadius
        ) {
            this.level = level;
            this.center = center;
            this.origin = origin;
            this.profile = profile;
            this.source = source;
            this.damageSource = damageSource;
            this.seed = seed;
            this.burialFactor = burialFactor;
            this.buriedMode = buriedMode;
            this.terrainRadius = terrainRadius;
            this.maxDepth = maxDepth;
            this.foliageRadius = foliageRadius;
        }
    }

    enum Operation {
        IGNITE_WOOD(1),
        CRACK_MASONRY(2),
        CONVERT_GRASS_TO_DIRT(3),
        REMOVE_FOLIAGE(4),
        DESTROY(5);

        private final int priority;

        Operation(int priority) {
            this.priority = priority;
        }
    }

    record PlannedChange(
            long positionLong,
            Block expectedBlock,
            Operation operation,
            float normalizedEnergy
    ) {
    }

    private record BurialProbe(
            int dx,
            int dy,
            int dz,
            float score
    ) {
    }

    private record TreePart(
            BlockPos pos,
            BlockState state
    ) {
    }

    private record SurfaceReachabilityNode(
            BlockPos pos,
            int steps
    ) {
    }

    private record ShellSample(
            boolean inside,
            float energy,
            float normalizedHorizontalDistance
    ) {
        private static final ShellSample OUTSIDE =
                new ShellSample(false, 0.0F, 1.0F);
    }

    private record SurfaceEffectSample(
            boolean inside,
            float energy,
            float normalizedHorizontalDistance
    ) {
        private static final SurfaceEffectSample OUTSIDE =
                new SurfaceEffectSample(false, 0.0F, 1.0F);
    }

    private record FoliageSample(
            boolean inside,
            float energy
    ) {
        private static final FoliageSample OUTSIDE =
                new FoliageSample(false, 0.0F);
    }

    private static final BurialProbe[] BURIAL_PROBES = {
            new BurialProbe(0, 0, 0, 0.65F),

            new BurialProbe(0, -1, 0, 0.35F),
            new BurialProbe(0, -2, 0, 0.15F),

            new BurialProbe(1, 0, 0, 1.00F),
            new BurialProbe(-1, 0, 0, 1.00F),
            new BurialProbe(0, 0, 1, 1.00F),
            new BurialProbe(0, 0, -1, 1.00F),

            new BurialProbe(1, 0, 1, 0.55F),
            new BurialProbe(1, 0, -1, 0.55F),
            new BurialProbe(-1, 0, 1, 0.55F),
            new BurialProbe(-1, 0, -1, 0.55F),

            new BurialProbe(0, 1, 0, 0.10F),
            new BurialProbe(0, 2, 0, 0.05F)
    };
}
