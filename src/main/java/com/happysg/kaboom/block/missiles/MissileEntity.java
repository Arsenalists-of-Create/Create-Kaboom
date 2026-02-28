package com.happysg.kaboom.block.missiles;

import com.happysg.kaboom.async.AsyncMissilePlanner;

import com.happysg.kaboom.block.missiles.chaining.ChainSystem;
import com.happysg.kaboom.block.missiles.util.*;
import com.happysg.kaboom.mixin.AbstractProjectileAccessor;
import com.happysg.kaboom.mixin.FuzeMixin;
import com.happysg.kaboom.networking.NetworkHandler;
import com.happysg.kaboom.registry.ModParticles;
import com.happysg.kaboom.sounds.MissileEngineSound;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.OrientedContraptionEntity;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.network.PacketDistributor;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;
import rbasamoyai.createbigcannons.CreateBigCannons;
import rbasamoyai.createbigcannons.block_armor_properties.BlockArmorPropertiesHandler;
import rbasamoyai.createbigcannons.block_armor_properties.BlockArmorPropertiesProvider;
import rbasamoyai.createbigcannons.config.CBCCfgMunitions;
import rbasamoyai.createbigcannons.config.CBCConfigs;
import rbasamoyai.createbigcannons.index.CBCDamageTypes;
import rbasamoyai.createbigcannons.index.CBCEntityTypes;
import rbasamoyai.createbigcannons.multiloader.NetworkPlatform;
import rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile;
import rbasamoyai.createbigcannons.munitions.CannonDamageSource;
import rbasamoyai.createbigcannons.munitions.ImpactExplosion;
import rbasamoyai.createbigcannons.munitions.ProjectileContext;
import rbasamoyai.createbigcannons.munitions.big_cannon.AbstractBigCannonProjectile;
import rbasamoyai.createbigcannons.munitions.big_cannon.FuzedBigCannonProjectile;
import rbasamoyai.createbigcannons.munitions.big_cannon.ProjectileBlock;
import rbasamoyai.createbigcannons.munitions.big_cannon.solid_shot.SolidShotProjectile;
import rbasamoyai.createbigcannons.munitions.config.DimensionMunitionPropertiesHandler;
import rbasamoyai.createbigcannons.munitions.config.FluidDragHandler;
import rbasamoyai.createbigcannons.munitions.config.components.BallisticPropertiesComponent;
import rbasamoyai.createbigcannons.munitions.config.components.EntityDamagePropertiesComponent;
import rbasamoyai.createbigcannons.network.ClientboundPlayBlockHitEffectPacket;
import rbasamoyai.createbigcannons.utils.CBCUtils;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class MissileEntity extends OrientedContraptionEntity {

    private static final EntityDataAccessor<Float> HEADING_X =
            SynchedEntityData.defineId(MissileEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> HEADING_Y =
            SynchedEntityData.defineId(MissileEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> HEADING_Z =
            SynchedEntityData.defineId(MissileEntity.class, EntityDataSerializers.FLOAT);
    public static final EntityDataAccessor<Integer> FUEL_MB =
            SynchedEntityData.defineId(MissileEntity.class, EntityDataSerializers.INT);
    protected static final EntityDataAccessor<Float> GRAVITY =
            SynchedEntityData.defineId(MissileEntity.class, EntityDataSerializers.FLOAT);

    private static final EntityDataAccessor<Integer> FUEL_CAP_MB =
            SynchedEntityData.defineId(MissileEntity.class, EntityDataSerializers.INT);
    // --- Fuel / thrust tuning ---
    private static final double MAX_TURN_DEG_PER_TICK = 25; // try 12–18 for missiles

    // speed/physics
    private static final double MAX_SPEED = 22;
    private static final double GRAVITY_PER_TICK = 0.08;
    // thrust (requires fuel)
    private static final double MAX_THRUST_ACCEL = 1.2;   // blocks/tick^2 at throttle=1
    private static final int    BURN_MB_PER_TICK_AT_FULL = 100; // tune to taste
    // blocks/tick
    private static final double MAX_SPEED_PER_TICK = 22;
    private static final double MIN_SPEED_PER_TICK = 0; // or ~0.5 if you want minimum cruise

    // "velocity blending" response per tick (0..1)
    private static final double CONTROL_RESPONSE = 0.35;

    // accel clamps (blocks/tick^2)
    private static final double MAX_STEER_ACCEL  = 1.5;  // sideways steer (no fuel)
    private static final double MAX_BRAKE_ACCEL  = 2;  // braking/airbrake (no fuel)
    // steering (works even with fuel=0)


    // async cadence
    private static final int SUBSTEPS = 50;

    private final AsyncMissilePlanner planner = new AsyncMissilePlanner(PLANNER_EXECUTOR);
    private final AsyncMissilePlanner.WorldView worldView = new AsyncMissilePlanner.WorldView() {
        @Override
        public AsyncMissilePlanner.TargetSnapshot getTarget(UUID id) {
            return null;
        }
    };
    private volatile AsyncMissilePlanner.Output latestPlan = AsyncMissilePlanner.Output.idle();

    private boolean latchedInGround = false;
    @Nullable private Vec3 resolvedPosThisTick = null;
    @Nullable protected Vec3 nextVelocity = null; // keep from your CBC code
    private final Set<Long> forcedChunks = new HashSet<>();
    private static final int CHUNK_RADIUS = 7; // 0=just current chunk, 1=3x3, 2=5x5

    private static final Executor PLANNER_EXECUTOR = Executors.newFixedThreadPool(
            2,
            r -> { Thread t = new Thread(r, "kaboom-missile-planner"); t.setDaemon(true); return t; }
    );


    private record PhysicsStep(Vec3 pos, Vec3 vel) {
    }

    private static final Logger LOGGER = LogUtils.getLogger();
    @OnlyIn(Dist.CLIENT)
    private boolean spawnedThrusterParticle = false;
    private int fuelMb;
    private int fuelCapacityMb;
    private Vec3 lastVelForSmoke = Vec3.ZERO;
    private boolean fuelDepleted = false;
    @OnlyIn(Dist.CLIENT)
    private MissileEngineSound engineSound;

    @Nullable private Vec3 pendingVelocity = null; // used for NEXT tick (bounce)
    // Always use our own contraption colliders (instead of Create's generated ones)
    private boolean forceCustomColliders = true;

    // Cached custom collider set (local-space AABBs)
    private List<AABB> customColliders = List.of();

    // Which way is "forward" in contraption-local space
    private Direction.Axis forwardAxis = Direction.Axis.Y;
    private int forwardSign = +1; // +1 or -1 along that axis

    // Nose-most block in local contraption coords (farthest forward)
    private BlockPos noseLocal = BlockPos.ZERO;



    // How far ahead of the nose block center to collide (half block-ish)
    private static final double NOSE_TIP_AHEAD = 0.55;
    private boolean launched  =false;

    private final ChainSystem chainSystem = new ChainSystem();



    public MissileEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    public ChainSystem getChainSystem() {
        return chainSystem;
    }

    public void initFromAssembly(Contraption contraption, BlockPos controllerPos, BlockPos warheadLocalPos) {
        setPos(controllerPos.getX() + 0.5, controllerPos.getY() + 1.5, controllerPos.getZ() + 0.5);

        setContraption(contraption);
        setNoGravity(false);
        this.entityData.set(GRAVITY,  -0.08f); // or whatever baseline you want
        startAtInitialYaw();
        // Already local (relative to controller)
        this.warheadpos = warheadLocalPos;
        // Reset state

        if (contraption instanceof MissileContraption mc) {
            this.fuelMb = mc.fuelAmountMb;
            this.fuelCapacityMb = mc.fuelCapacityMb;
        }
        this.entityData.set(FUEL_MB, fuelMb);
        this.entityData.set(FUEL_CAP_MB, fuelCapacityMb);

        if (contraption instanceof MissileContraption mc) {
            LOGGER.warn("[MISSILE INIT] guidanceTag={}", mc.guidanceTag);
            if (mc.guidanceTag != null && !mc.guidanceTag.isEmpty()) {
                MissileGuidanceData parsed = MissileGuidanceData.fromTag(mc.guidanceTag);
                LOGGER.warn("[MISSILE INIT] parsed target={} profile={}", parsed.target(), parsed.profile());
            }
        }
        recomputeForwardAxisAndNose();

        // pick a "nose" for mass / sweeps (usually highest Y in local space)
        this.capPos = BlockPos.ZERO;
        if (this.contraption != null && !this.contraption.getBlocks().isEmpty()) {
            BlockPos best = BlockPos.ZERO;
            for (BlockPos p : this.contraption.getBlocks().keySet()) {
                if (p.getY() > best.getY()) best = p;
            }
            this.capPos = best;
        }
        rebuildCustomColliders(0.40); // tune
        enforceCustomColliders();

        if (contraption instanceof MissileContraption mc && mc.guidanceTag != null && !mc.guidanceTag.isEmpty()) {
            MissileGuidanceData data = MissileGuidanceData.fromTag(mc.guidanceTag);
            applyGuidance(data); // programs the planner from target+profile

            // Prime so tick 0 isn't idle
            latestPlan = planner.prime(worldView, new AsyncMissilePlanner.Snapshot(
                    level().getGameTime(),
                    this.position(),
                    Vec3.ZERO,
                    getYRot(),
                    getXRot(),
                    fuelMb,
                    MAX_SPEED,
                    GRAVITY_PER_TICK
            ));
            // Small kickoff so steering/drag behaves immediately (don't use (0,1,0), that's huge)
            super.setDeltaMovement(new Vec3(0, 1, 0));
        } else {
            LOGGER.warn("falling back");
            // Optional fallback if guidance missing
            planner.clearProgram().coast(0.0);
            latestPlan = AsyncMissilePlanner.Output.idle();
        }
        blockMass.clear();
        blockMass.put(this.capPos, 10.0f);

        this.warhead = null;
        if (this.warheadpos != null) {
            var info = this.contraption.getBlocks().get(this.warheadpos);
            if (info != null && info.state().getBlock() instanceof ProjectileBlock<?> pb) {
                AbstractCannonProjectile proj = pb.getProjectile(level(), List.of(info));
                if (proj instanceof AbstractBigCannonProjectile big) {
                    this.warhead = big;
                }
            }
        }

        // Load chain system data from contraption if present
        if (contraption instanceof MissileContraption mc && mc.chainSystemTag != null) {
            chainSystem.load(mc.chainSystemTag);
        }
    }

    /**
     * Syncs the chain system state into the contraption's NBT for persistence.
     * Called periodically from serverTickMovement.
     */
    private void syncChainSystemToContraption() {
        if (this.contraption instanceof MissileContraption mc) {
            mc.chainSystemTag = chainSystem.save();
        }
    }

    @Override
    public void tick() {
        if (this.contraption == null) {
            LOGGER.warn("contraption null");
            discard();
            return;
        }

        enforceCustomColliders();
        if (level().isClientSide) {
            if (!launched && getFuelMbSynced() > 0) { // <-- the important part
                launched = true;
                engineSound = new MissileEngineSound(this);
                Minecraft.getInstance().getSoundManager().play(engineSound);
            }
        }
        if (level().isClientSide) {
            clientTickVisuals();
            if (!spawnedThrusterParticle) {
                spawnedThrusterParticle = true;


                float back = 1.2f;
                float up = 0.0f;
                float right = 0.0f;

                Vec3 p = position(); // initial spawn pos doesn't matter much; particle will snap in its tick()
                level().addParticle(
                        new MissileAttachedParticleOptions(getId(), back, up, right),true,
                        p.x, p.y, p.z,
                        0, 0, 0
                );
            }
            super.tick();
            return;
        }

        serverTickMovement(); // DO NOT call super.tick() on server
    }

    private void serverTickMovement() {
        fallDistance = 0;
        hasImpulse = true;

        // Tick chain system before movement
        if (level() instanceof ServerLevel sl) {
            chainSystem.tickFromEntity(position(), sl);
            // Sync chain data to contraption NBT every 20 ticks for persistence
            if (tickCount % 20 == 0) {
                syncChainSystemToContraption();
                chainSystem.populateEntityIds(sl);
                NetworkHandler.CHANNEL.send(
                        PacketDistributor.TRACKING_ENTITY.with(() -> this),
                        new com.happysg.kaboom.networking.ChainSystemSyncPacket(getId(), chainSystem.save())
                );
            }
        }

        tickChunkLoading();
        if ((tickCount % 5 == 0)) {
            Vec3 vNow = getDeltaMovement();
            LOGGER.warn("[MISSILE] t={} pos={} v={} speed={} fuel={} throttleReq={} desired={} done={} dbg={}",
                    tickCount, position(), vNow,
                    String.format(Locale.ROOT, "%.3f", vNow.length()),
                    fuelMb,
                    String.format(Locale.ROOT, "%.2f", latestPlan.throttleReq()),
                    latestPlan.aimDir(),
                    latestPlan.done(),
                    latestPlan.debug()
            );
        }

        // We do movement + impacts ourselves
        this.noPhysics = true;
        this.setNoGravity(false);

        // If embedded/stuck, freeze and just tick fuzes
        if (latchedInGround) {
            freezeInPlace();
            tickWarhead();
            sendPreciseMotion(position(), Vec3.ZERO);
            return;
        }


        if (pendingVelocity != null) {
            Vec3 pv = clampSpeed(pendingVelocity, MAX_SPEED);
            super.setDeltaMovement(pv);
            setContraptionMotion(pv);
            pendingVelocity = null;
        }

        final Vec3 pos0 = position();
        final Vec3 vel0 = getDeltaMovement(); // canonical velocity for integration

        // 1) Pump planner EVERY tick (planner internally rate-limits / avoids backlog)
        AsyncMissilePlanner.Snapshot snap = new AsyncMissilePlanner.Snapshot(
                level().getGameTime(),
                pos0,
                vel0,
                getYRot(),
                getXRot(),
                fuelMb,
                MAX_SPEED,
                GRAVITY_PER_TICK // positive magnitude, e.g. 0.08
        );
        latestPlan = planner.tick(worldView, snap);

        // 2) Forces + control
        Vec3 aBase = getForcesWithParam(vel0);              // drag + gravity (uses vel0)
        Vec3 aCtrl = computeControlAccel(latestPlan, vel0); // steering always, thrust with fuel
        Vec3 aTick = aBase.add(aCtrl);

        // TODO: Apply chain system weight modifier to thrust/gravity when launched would be cool
        // double chainWeight = chainSystem.calculateWeight((ServerLevel) level());
        // if (chainWeight > 0) { /* reduce thrust, increase gravity drag */ }

        // 3) Integrate predicted position (substeps help at high speed)
        PhysicsStep step = integrateSubsteps(pos0, vel0, aTick, SUBSTEPS);
        Vec3 posPred = step.pos();

        // 4) Move (no collision resolution; CBC sweep handles impacts)
        Vec3 intendedDelta = posPred.subtract(pos0);
        move(MoverType.SELF, intendedDelta);

        final Vec3 pos1 = position();
        Vec3 velActual = pos1.subtract(pos0);
        velActual = clampSpeed(velActual, MAX_SPEED);

        // 5) Write back movement for THIS tick
        setContraptionMotion(velActual);
        super.setDeltaMovement(velActual);

        // 6) CBC sweeps + warhead tick
        resolvedPosThisTick = null;
        tickCBCImpacts(pos0, pos1);
        tickWarhead();

        // 7) Handle STOP / snap-to-surface
        if (resolvedPosThisTick != null) {
            latchedInGround = true;
            this.setOnGround(true);

            setPos(resolvedPosThisTick.x, resolvedPosThisTick.y, resolvedPosThisTick.z);
            super.setDeltaMovement(Vec3.ZERO);
            setContraptionMotion(Vec3.ZERO);

            syncHeading(new Vec3(0, -1, 0));
            sendPreciseMotion(position(), Vec3.ZERO);

            resolvedPosThisTick = null;
            return;
        }

        Vec3 headingVec = (latestPlan.aimDir() != null && latestPlan.aimDir().lengthSqr() > 1e-8)
                ? latestPlan.aimDir()
                : velActual;

        syncHeading(headingVec);
        sendPreciseMotion(pos1, velActual);

    }
    protected Vec3 getForcesWithParam(Vec3 velocity) {
        // Drag opposes velocity
        double drag = getDragForce(velocity);
        Vec3 dragVec = velocity.lengthSqr() > 1e-12 ? velocity.normalize().scale(-drag) : Vec3.ZERO;

        // Gravity (GRAVITY data is negative in your code: -0.08f)
        double g = this.isNoGravity() ? 0.0 : this.entityData.get(GRAVITY) *
                DimensionMunitionPropertiesHandler.getProperties(this.level()).gravityMultiplier();

        return dragVec.add(0.0, g, 0.0);
    }
    private int getFuelMbSynced() {
        return entityData.get(FUEL_MB);
    }

    private void freezeInPlace() {
        setContraptionMotion(Vec3.ZERO);
        this.noPhysics = true;
    }
    private void applyGuidance(MissileGuidanceData data) {
        var p = data.profile();
        var t = data.target();

        planner.setPlanCadence(p.planCadenceTicks());
        if (t.type() == MissileTargetSpec.TargetType.POINT) {
            boolean useHigh = t.highArc() || p.preferHighArc();
            planner.clearProgram()
                    .boostUp(p.boostAltitude(), p.boostTolerance(), p.boostThrottle())
                    .arcTo(t.point(), 20.0, true, 25, .9);
        } else if (t.type() == MissileTargetSpec.TargetType.ENTITY && t.entityId() != null) {
            planner.intercept(t.entityId(), p.assumedSpeed(), p.arriveRadius(), p.terminalThrottle());
        }
        }
    private void sendPreciseMotion(Vec3 pos, Vec3 v) {
        if (!(level() instanceof ServerLevel)) return;

        int lerpSteps = 3;
        NetworkHandler.CHANNEL.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> this),
                new PreciseMotionSyncPacket(
                        getId(),
                        pos.x, pos.y, pos.z,
                        v.x, v.y, v.z,
                        getYRot(), getXRot(),
                        onGround(),
                        lerpSteps
                )
        );
    }

    private void syncHeading(Vec3 v) {
        Vec3 h = v.lengthSqr() > 1e-8 ? v.normalize() : new Vec3(0, 1, 0);
        entityData.set(HEADING_X, (float) h.x);
        entityData.set(HEADING_Y, (float) h.y);
        entityData.set(HEADING_Z, (float) h.z);

    }



    private Vec3 tipWorldAtEntityPos(Vec3 entityPos, BlockPos localBlock, Vec3 worldDirUnit, double ahead) {
        // world position of local block center at "now"
        Vec3 now = toGlobalVector(Vec3.atCenterOf(localBlock), 0);

        // shift to where it would be if entity was at entityPos
        Vec3 delta = entityPos.subtract(this.position());
        Vec3 base = now.add(delta);

        // push slightly forward along direction of motion (capsule tip)
        return base.add(worldDirUnit.scale(ahead));
    }
    private BlockPos pickLeadingLocal(Vec3 worldDirUnit) {
        if (contraption == null || contraption.getBlocks().isEmpty())
            return BlockPos.ZERO;

        double best = -Double.MAX_VALUE;
        BlockPos bestPos = BlockPos.ZERO;

        for (BlockPos lp : contraption.getBlocks().keySet()) {
            Vec3 wp = toGlobalVector(Vec3.atCenterOf(lp), 0); // world pos of that block center (at current transform)
            double score = wp.dot(worldDirUnit);
            if (score > best) {
                best = score;
                bestPos = lp;
            }
        }
        return bestPos;
    }


    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(HEADING_X, 0f);
        entityData.define(HEADING_Y, 1f);
        entityData.define(HEADING_Z, 0f);
        entityData.define(FUEL_MB, 0);
        entityData.define(FUEL_CAP_MB, 0);
        entityData.define(GRAVITY,-0.08f);
    }

    private void tickChunkLoading() {
        if (!(level() instanceof ServerLevel sl)) return;

        ChunkPos cp = new ChunkPos(blockPosition());
        Set<Long> wanted = new HashSet<>();

        for (int dx = -CHUNK_RADIUS; dx <= CHUNK_RADIUS; dx++) {
            for (int dz = -CHUNK_RADIUS; dz <= CHUNK_RADIUS; dz++) {
                ChunkPos p = new ChunkPos(cp.x + dx, cp.z + dz);
                long key = p.toLong();
                wanted.add(key);

                if (!forcedChunks.contains(key)) {
                    sl.setChunkForced(p.x, p.z, true);
                    forcedChunks.add(key);
                }
            }
        }

        // unforce chunks we no longer need
        forcedChunks.removeIf(key -> {
            if (wanted.contains(key)) return false;
            ChunkPos p = new ChunkPos(key);
            sl.setChunkForced(p.x, p.z, false);
            return true;
        });
    }


    @Override
    public void applyLocalTransforms(PoseStack stack, float partialTicks) {
        // Read synced heading on client
        Vector3f toDir = new Vector3f(
                entityData.get(HEADING_X),
                entityData.get(HEADING_Y),
                entityData.get(HEADING_Z)
        );

        if (toDir.lengthSquared() < 1e-12f)
            toDir.set(0, 1, 0);
        else
            toDir.normalize();

        // Your missile model is "primarily up": local +Y is the nose.
        Vector3f fromUp = new Vector3f(0, 1, 0);

        Quaternionf q = new Quaternionf().rotationTo(fromUp, toDir);

        // Standard contraption transform pattern
        stack.translate(-0.5f, 0.0f, -0.5f);
        TransformStack tstack = TransformStack.of(stack)
                .nudge(getId())
                .center();

        stack.mulPose(q);

        tstack.uncenter();
    }

    @Override
    public void lerpMotion(double x, double y, double z) {
        Vec3 v = new Vec3(x, y, z);
        setContraptionMotion(v);
        super.setDeltaMovement(v);
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps, boolean teleport) {
        if (this.tickCount < 2) return;
        super.lerpTo(x, y, z, yRot, xRot, steps, teleport);
    }



    private void recomputeForwardAxisAndNose() {
        if (this.contraption == null || this.contraption.getBlocks().isEmpty()) {
            forwardAxis = Direction.Axis.Y;
            forwardSign = +1;
            noseLocal = BlockPos.ZERO;
            return;
        }


        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        for (BlockPos p : this.contraption.getBlocks().keySet()) {
            minX = Math.min(minX, p.getX()); maxX = Math.max(maxX, p.getX());
            minY = Math.min(minY, p.getY()); maxY = Math.max(maxY, p.getY());
            minZ = Math.min(minZ, p.getZ()); maxZ = Math.max(maxZ, p.getZ());
        }

        int spanX = maxX - minX;
        int spanY = maxY - minY;
        int spanZ = maxZ - minZ;

        // Choose the dominant axis the missile is "long" in
        if (spanX >= spanY && spanX >= spanZ) forwardAxis = Direction.Axis.X;
        else if (spanY >= spanX && spanY >= spanZ) forwardAxis = Direction.Axis.Y;
        else forwardAxis = Direction.Axis.Z;

        // Decide sign: whichever side has the larger magnitude away from 0
        // (works if controller is near 0; if not, it still usually works because missiles are mostly one-directional)
        int maxAbsPos, minAbsPos;
        switch (forwardAxis) {
            case X -> { maxAbsPos = Math.abs(maxX); minAbsPos = Math.abs(minX); forwardSign = (maxAbsPos >= minAbsPos) ? (maxX >= 0 ? +1 : -1) : (minX >= 0 ? +1 : -1); }
            case Y -> { maxAbsPos = Math.abs(maxY); minAbsPos = Math.abs(minY); forwardSign = (maxAbsPos >= minAbsPos) ? (maxY >= 0 ? +1 : -1) : (minY >= 0 ? +1 : -1); }
            case Z -> { maxAbsPos = Math.abs(maxZ); minAbsPos = Math.abs(minZ); forwardSign = (maxAbsPos >= minAbsPos) ? (maxZ >= 0 ? +1 : -1) : (minZ >= 0 ? +1 : -1); }
        }

        // Now pick the nose-most block by projection along (axis * sign)
        BlockPos best = BlockPos.ZERO;
        int bestProj = Integer.MIN_VALUE;

        for (BlockPos p : this.contraption.getBlocks().keySet()) {
            int proj = switch (forwardAxis) {
                case X -> p.getX() * forwardSign;
                case Y -> p.getY() * forwardSign;
                case Z -> p.getZ() * forwardSign;
            };
            if (proj > bestProj) {
                bestProj = proj;
                best = p;
            }
        }

        noseLocal = best;

        // Also update capPos to match "nose-most" (your mass / sweeps should use the real nose)
        this.capPos = noseLocal;

        LOGGER.warn("[MISSILE AXIS] axis={} sign={} noseLocal={} warheadLocal={}",
                forwardAxis, forwardSign, noseLocal, warheadpos);
    }
    private void enforceCustomColliders() {
        if (!forceCustomColliders) return;
        if (contraption == null) return;

        // Keep winning even if Create's async collider builder finishes later
        contraption.simplifiedEntityColliders = Optional.of(customColliders);
    }
    private void rebuildCustomColliders(double radius) {
        if (contraption == null || contraption.getBlocks().isEmpty()) {
            customColliders = List.of(new AABB(0, 0, 0, 1, 1, 1));
            return;
        }

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        for (BlockPos p : contraption.getBlocks().keySet()) {
            minX = Math.min(minX, p.getX());
            minY = Math.min(minY, p.getY());
            minZ = Math.min(minZ, p.getZ());
            maxX = Math.max(maxX, p.getX());
            maxY = Math.max(maxY, p.getY());
            maxZ = Math.max(maxZ, p.getZ());
        }

        // block volumes in local space are [x..x+1], so max is +1
        double x0 = minX, x1 = maxX + 1.0;
        double y0 = minY, y1 = maxY + 1.0;
        double z0 = minZ, z1 = maxZ + 1.0;

        double cx = (x0 + x1) * 0.5;
        double cy = (y0 + y1) * 0.5;
        double cz = (z0 + z1) * 0.5;

        AABB body;
        AABB capA;
        AABB capB;

        switch (forwardAxis) {
            case X -> {
                body = new AABB(x0, cy - radius, cz - radius,
                        x1, cy + radius, cz + radius);
                double a = x0 + 0.5, b = x1 - 0.5;
                capA = new AABB(a - radius, cy - radius, cz - radius, a + radius, cy + radius, cz + radius);
                capB = new AABB(b - radius, cy - radius, cz - radius, b + radius, cy + radius, cz + radius);
            }
            case Y -> {
                body = new AABB(cx - radius, y0, cz - radius,
                        cx + radius, y1, cz + radius);
                double a = y0 + 0.5, b = y1 - 0.5;
                capA = new AABB(cx - radius, a - radius, cz - radius, cx + radius, a + radius, cz + radius);
                capB = new AABB(cx - radius, b - radius, cz - radius, cx + radius, b + radius, cz + radius);
            }
            case Z -> {
                body = new AABB(cx - radius, cy - radius, z0,
                        cx + radius, cy + radius, z1);
                double a = z0 + 0.5, b = z1 - 0.5;
                capA = new AABB(cx - radius, cy - radius, a - radius, cx + radius, cy + radius, a + radius);
                capB = new AABB(cx - radius, cy - radius, b - radius, cx + radius, cy + radius, b + radius);
            }
            default -> throw new IllegalStateException("Unexpected axis " + forwardAxis);
        }

        customColliders = List.of(body, capA, capB);
    }


















    private PhysicsStep integrateSubsteps(Vec3 pos0, Vec3 v0, Vec3 aTick, int substeps) {
        double dt = 1.0 / substeps;
        Vec3 pos = pos0;
        Vec3 v = v0;

        for (int i = 0; i < substeps; i++) {
            v = v.add(aTick.scale(dt));
            pos = pos.add(v.scale(dt));
        }

        // clamp ONCE (optional; you already clamp after move())
        v = clampSpeed(v, MAX_SPEED);
        return new PhysicsStep(pos, v);
    }

    private static Vec3 clampSpeed(Vec3 v, double max) {
        double sp = v.length();
        if (sp > max && sp > 1e-9) return v.scale(max / sp);
        return v;
    }

    @OnlyIn(Dist.CLIENT)
    private void clientTickVisuals() {
        // smoke
        if (!fuelDepleted) spawnSmokeClient(position(), getDeltaMovement());

    }

    private Vec3 computeControlAccel(AsyncMissilePlanner.Output plan, Vec3 v) {
        double speed = v.length();

        // --- Aim dir (steering + visuals) ---
        Vec3 aim = plan.aimDir();
        if (aim == null || aim.lengthSqr() < 1e-8) {
            aim = (speed > 1e-8) ? v.normalize() : new Vec3(0, 1, 0);
        } else {
            aim = aim.normalize();
        }

        // --- Desired travel direction (planner uses thrustDir as desired velocity direction) ---
        Vec3 desiredDir = plan.thrustDir();
        if (desiredDir == null || desiredDir.lengthSqr() < 1e-8) {
            desiredDir = aim; // fallback
        } else {
            desiredDir = desiredDir.normalize();
        }

        // Optional gimbal/turn limit so it doesn't snap direction instantly
        Vec3 currentDir = (speed > 1e-8) ? v.normalize() : desiredDir;
        desiredDir = limitTurnSafe(currentDir, desiredDir, MAX_TURN_DEG_PER_TICK);

        // --- Convert throttleReq (0..1) into desired speed ---
        double speedFrac = Mth.clamp(plan.throttleReq(), 0.0, 1.0);

        // IMPORTANT: replace MAX_SPEED_PER_TICK with YOUR max speed (blocks/tick)
        double desiredSpeed;
        if (speedFrac <= 1e-6) {
            // coast: keep current speed so we can still steer sideways
            desiredSpeed = speed;
        } else {
            desiredSpeed = Mth.clamp(speedFrac * MAX_SPEED_PER_TICK, MIN_SPEED_PER_TICK, MAX_SPEED_PER_TICK);
        }
        desiredSpeed = Math.min(desiredSpeed, MAX_SPEED_PER_TICK);

        Vec3 desiredVel = desiredDir.scale(desiredSpeed);

        // --- Velocity-setpoint control (the overshoot killer) ---
        // "Acceleration" here is just "move velocity toward desired velocity" per tick.
        Vec3 dv = desiredVel.subtract(v);

        // 0.25..0.60 typical. Higher = snappier, lower = floatier.
        Vec3 dvCmd = dv.scale(CONTROL_RESPONSE);
// Let gravity bend the arc: weak vertical tracking unless we're clearly diving
        double yTrack = (desiredDir.y < -0.15) ? 1.0 : 0.15; // dive = 100%, cruise = 15%
        dvCmd = new Vec3(dvCmd.x, dvCmd.y * yTrack, dvCmd.z);
        // Split dvCmd into parallel + perpendicular relative to current motion axis
        Vec3 axis = (speed > 1e-6) ? v.scale(1.0 / speed) : desiredDir; // stable when nearly stopped
        double dvAlong = dvCmd.dot(axis);
        Vec3 dvParallel = axis.scale(dvAlong);
        Vec3 dvPerp = dvCmd.subtract(dvParallel);

        // --- Steering clamp (perpendicular component) ---
        double steerScale = Mth.clamp(plan.steerScale(), 0.0, 1.0);
        double maxSteer = MAX_STEER_ACCEL * steerScale;

        double perpMag = dvPerp.length();
        if (perpMag > maxSteer && perpMag > 1e-9) {
            dvPerp = dvPerp.scale(maxSteer / perpMag);
        }

        // --- Along-track: braking + thrust ---
        Vec3 dvAlongFinal = Vec3.ZERO;

        if (dvAlong < 0.0) {
            // Braking / airbrake (recommended to prevent eternal overshoot even when out of fuel)
            double brake = Math.min(-dvAlong, MAX_BRAKE_ACCEL);
            dvAlongFinal = axis.scale(-brake);
        } else if (dvAlong > 0.0 && speedFrac > 1e-6) {
            // Forward thrust (costs fuel)
            double thrust = Math.min(dvAlong, MAX_THRUST_ACCEL);

            double throttle = (fuelMb > 0) ? (thrust / MAX_THRUST_ACCEL) : 0.0;

            if (throttle > 0.0) {
                int requestedBurn = (int) Math.ceil(BURN_MB_PER_TICK_AT_FULL * throttle);
                if (requestedBurn <= 0) requestedBurn = 1;

                if (fuelMb < requestedBurn) {
                    throttle *= (fuelMb / (double) requestedBurn);
                    requestedBurn = fuelMb;
                }

                if (requestedBurn > 0) burnFuel(requestedBurn);
                if (fuelMb <= 0) throttle = 0.0;
            }

            dvAlongFinal = axis.scale(MAX_THRUST_ACCEL * throttle);
        }

        return dvPerp.add(dvAlongFinal);
    }

    private static Vec3 limitTurnSafe(Vec3 currentDir, Vec3 desiredDir, double maxTurnDeg) {
        if (currentDir.lengthSqr() < 1e-8 || desiredDir.lengthSqr() < 1e-8) return desiredDir;
        Vec3 a = currentDir.normalize();
        Vec3 b = desiredDir.normalize();

        double dot = Mth.clamp(a.dot(b), -1.0, 1.0);

        if (dot < -0.9995) {
            Vec3 axis = a.cross(new Vec3(0, 1, 0));
            if (axis.lengthSqr() < 1e-8) axis = a.cross(new Vec3(1, 0, 0));
            axis = axis.normalize();

            double maxRad = Math.toRadians(maxTurnDeg);
            Vec3 rotated = a.scale(Math.cos(maxRad))
                    .add(axis.cross(a).scale(Math.sin(maxRad)))
                    .add(axis.scale(axis.dot(a) * (1.0 - Math.cos(maxRad))));
            return rotated.normalize();
        }

        double angle = Math.acos(dot);
        if (angle < 1e-6) return b;

        double maxRad = Math.toRadians(maxTurnDeg);
        if (angle <= maxRad) return b;

        double t = maxRad / angle;
        double sinAngle = Math.sin(angle);

        double w1 = Math.sin((1.0 - t) * angle) / sinAngle;
        double w2 = Math.sin(t * angle) / sinAngle;

        return a.scale(w1).add(b.scale(w2)).normalize();
    }
    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide && level() instanceof ServerLevel sl) {
            for (long key : forcedChunks) {
                ChunkPos p = new ChunkPos(key);
                sl.setChunkForced(p.x, p.z, false);
            }
            forcedChunks.clear();
            chainSystem.releaseAll(sl);
        }
        super.remove(reason);
    }







    // -------------------------
    // Smoke trail (server-side)
    // -------------------------
    @OnlyIn(Dist.CLIENT)
    private void spawnSmokeClient(Vec3 pos, Vec3 v) {
        ClientLevel cl = (ClientLevel) level();

        double speed = v.length();
        double accel = v.subtract(lastVelForSmoke).length();
        lastVelForSmoke = v;

        float speedFactor = (float) Mth.clamp((speed - 0.15) / 0.85, 0.0, 1.0);
        float accelFactor = (float) Mth.clamp(accel / 0.12, 0.0, 1.0);
        float intensity = 0.35f + 0.85f * speedFactor + 0.9f * accelFactor;

        Vec3 forward = v.lengthSqr() > 1.0e-6 ? v.normalize() : new Vec3(0, 1, 0);
        Vec3 back = forward.scale(-1);

        double behind = 0.65;
        int count = Mth.clamp((int) Math.ceil(2.0f * intensity), 1, 10);
        double coneRadius = 0.05 + 0.18 * intensity;

        double inherit = 0.20;
        double pushBack = 0.05;

        for (int i = 0; i < count; i++) {
            double ox = (cl.random.nextDouble() - 0.5) * coneRadius;
            double oy = (cl.random.nextDouble() - 0.5) * coneRadius;
            double oz = (cl.random.nextDouble() - 0.5) * coneRadius;

            double x = pos.x + back.x * behind + ox;
            double y = pos.y + 0.15 + back.y * behind + oy;
            double z = pos.z + back.z * behind + oz;

            double tx = (cl.random.nextDouble() - 0.5) * 0.03 * intensity;
            double ty = (cl.random.nextDouble() - 0.5) * 0.02 * intensity;
            double tz = (cl.random.nextDouble() - 0.5) * 0.03 * intensity;

            Vec3 motion = v.scale(inherit).add(back.scale(pushBack)).add(tx, ty, tz);

            cl.addParticle(ModParticles.MISSILE_SMOKE.get(),true, x, y, z, motion.x, motion.y, motion.z);
        }
    }
    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
    return true;
    }




    private void burnFuel(int mb) {
        if (mb <= 0) return;

        int before = fuelMb;
        fuelMb = Math.max(0, fuelMb - mb);

        // keep synced data in lockstep
        entityData.set(FUEL_MB, fuelMb);
        entityData.set(FUEL_CAP_MB, fuelCapacityMb);

        // optional: if fuel type matters later, you can also zero fluid when empty
        if (fuelMb == 0 && before > 0) {
            LOGGER.warn("[MISSILE] Fuel depleted at tick {}", level().getGameTime());
        }
    }



    // MissileEntity.java
    public void setTargetPoint(Vec3 targetPos) {
        // Example flight program; tweak to taste
        planner.clearProgram()
                .climbTo(128, 2.0, .3)
                .pitchOver(2.5, 0.0, .2)
                .arcTo(targetPos, 18.0, true, 6.0, 0.3); // highArc=true => comes down on it
    }

    public void setTargetEntity(UUID targetId) {
        planner.clearProgram()
                .climbTo(128, 2.0, 1.0)
                .pitchOver(2.5, 0.0, 0.6)
                .intercept(targetId, 18.0, 2.5, 1.0);
    }


    // BOMB ZONE
// BOMB ZONE
// BOMB ZONE
// BOMB ZONE
// BOMB ZONE
// BOMB ZONE
// BOMB ZONE
// BOMB ZONE
// BOMB ZONE
// BOMB ZONE
// BOMB ZONE
// BOMB ZONE
// BOMB ZONE
// BOMB ZONE
// BOMB ZONE
// BOMB ZONE
// BOMB ZONE
// BOMB ZONE
// BOMB ZONE
// BOMB ZONE
// BOMB ZONE





    public static final BallisticPropertiesComponent BALLISTIC_PROPERTIES = new BallisticPropertiesComponent(-0.08, 0, false, 2.0f, 1, 1, 0.70f);
    public static final EntityDamagePropertiesComponent DAMAGE_PROPERTIES = new EntityDamagePropertiesComponent(30, false, true, true, 2);
    protected Map<BlockPos, Float> blockMass = new HashMap<>();

    protected int inFluidTime = 0;
    protected int penetrationTime = 0;
    @Nullable
    protected BlockState lastPenetratedBlock = Blocks.AIR.defaultBlockState();
    protected AbstractBigCannonProjectile warhead = null;
    protected BlockPos warheadpos = null;
    protected BlockPos capPos = null;



    protected void tickCBCImpacts(Vec3 oldPos, Vec3 newPos) {
        resolvedPosThisTick = null;
        MissileProjectileContext ctx = new MissileProjectileContext(this, CBCConfigs.server().munitions.damageRestriction.get());

        // Sweep using the NOSE path, not entity origin
        Vec3 entDisp = newPos.subtract(oldPos);
        if (entDisp.lengthSqr() < 1e-10) return;

        Vec3 worldDirUnit = entDisp.normalize();
        BlockPos leadLocal = pickLeadingLocal(worldDirUnit);

        Vec3 start = tipWorldAtEntityPos(oldPos, leadLocal, worldDirUnit, NOSE_TIP_AHEAD);
        Vec3 end   = tipWorldAtEntityPos(newPos, leadLocal, worldDirUnit, NOSE_TIP_AHEAD);
        Vec3 entityToNose0 = start.subtract(oldPos);
        Vec3 disp0 = end.subtract(start);
        if (disp0.lengthSqr() < 1.0e-10) {
            return;
        }

        // --------- Build a small "capsule-ish" sweep by sampling offsets ----------
        final double r = 0.35; // tune
        Vec3 dirUnit = disp0.normalize();

        Vec3 right = dirUnit.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 1e-8) right = new Vec3(1, 0, 0);
        right = right.normalize();
        Vec3 up = right.cross(dirUnit).normalize();

        Vec3[] offsets = new Vec3[]{
                Vec3.ZERO,
                right.scale(r),
                right.scale(-r),
                up.scale(r),
                up.scale(-r)
        };

        // helper: choose earliest hit among the offset rays for a given segment
        java.util.function.BiFunction<Vec3, Vec3, BlockHitResult> clipCapsule = (segStart, segEnd) -> {
            BlockHitResult best = null;
            double bestDist = Double.POSITIVE_INFINITY;

            for (Vec3 off : offsets) {
                BlockHitResult hit = level().clip(new ClipContext(
                        segStart.add(off), segEnd.add(off),
                        ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE,
                        this
                ));
                if (hit.getType() != HitResult.Type.MISS) {
                    double d = segStart.distanceTo(hit.getLocation());
                    if (d < bestDist) {
                        bestDist = d;
                        best = hit;
                    }
                }
            }

            if (best != null) return best;

            // MISS fallback needs a direction; use movement direction
            Direction missDir = Direction.getNearest(dirUnit.x, dirUnit.y, dirUnit.z);
            return BlockHitResult.miss(segEnd, missDir, BlockPos.containing(segEnd));
        };

        int maxIter = 20;
        boolean shouldRemove = false;
        boolean stop = false;

        Vec3 vel0 = getDeltaMovement();
        Vec3 accel = getForces(vel0);
        Vec3 traj = vel0.add(accel);

        // Entity sweep: build region around *nose segment*, not entity origin
        double reach = Math.max(getBbWidth(), getBbHeight()) * 0.5;

        Vec3 nose = start;
        AABB noseBox = new AABB(nose, nose).inflate(0.25);

        for (int i = 0; i < maxIter; i++) {
            Vec3 disp = end.subtract(start);
            if (disp.lengthSqr() < 1.0e-10) {
                lastPenetratedBlock = Blocks.AIR.defaultBlockState();

                break;
            }

            Vec3 segDirUnit = disp.normalize();

            // --- Block hit on THIS segment (capsule-ish) ---
            BlockHitResult blockHit = clipCapsule.apply(start, end);

            Vec3 hitEnd = end;
            if (blockHit.getType() != HitResult.Type.MISS) {
                hitEnd = blockHit.getLocation();

            }

            // --- Fluid hit only on first segment ---
            if (i == 0) {
                // Use centerline for fluid check (fine), but clamp to hitEnd
                BlockHitResult fluidHit = level().clip(new ClipContext(
                        start, hitEnd,
                        ClipContext.Block.OUTLINE,
                        ClipContext.Fluid.ANY,
                        this
                ));

                if (fluidHit.getType() != HitResult.Type.MISS) {
                    BlockPos fp = fluidHit.getBlockPos();
                    BlockState fs = level().getBlockState(fp);

                    if (fs.getBlock() instanceof LiquidBlock && inFluidTime <= 0) {

                        stop = onImpactFluid(ctx, fs, level().getFluidState(fp), fluidHit.getLocation(), fluidHit);
                        inFluidTime = 2;
                        if (stop) break;
                    }
                }
            }

            // --- Fuze clip check on the segment (use clamped hitEnd) ---
            if (onClip(ctx, start, hitEnd)) {
                shouldRemove = true;
                break;
            }

            // --- Entity sweeping in moved region (use the segment) ---
            AABB movementRegion = noseBox.expandTowards(disp).inflate(1);
            for (Entity target : level().getEntities(this, movementRegion)) {
                if (ctx.hasHitEntity(target)) continue;
                AABB bb = target.getBoundingBox();
                if (bb.intersects(noseBox) || bb.inflate(reach).clip(start, hitEnd).isPresent())
                    ctx.addEntity(target);
            }


            // --- If we hit a block, do penetration/bounce/stop using THAT hit ---
            if (blockHit.getType() != HitResult.Type.MISS) {
                BlockPos bp = blockHit.getBlockPos().immutable();
                BlockState hitState = level().getChunkAt(bp).getBlockState(bp);


                AbstractCannonProjectile.ImpactResult result = calculateBlockPenetration(ctx, hitState, blockHit);


                double totalNose = start.distanceTo(end);
                double usedNose  = start.distanceTo(hitEnd);
                double usedFrac  = (totalNose <= 1e-9) ? 0.0 : Mth.clamp(usedNose / totalNose, 0.0, 1.0);


                Vec3 entDir  = entDisp.lengthSqr() > 1e-10 ? entDisp.normalize() : segDirUnit;

                Vec3 backOffEnt = entDir.scale(0.05);
                Vec3 snappedEntityPos = oldPos.add(entDisp.scale(usedFrac)).subtract(backOffEnt);

                switch (result.kinematics()) {
                    case PENETRATE -> {
                        lastPenetratedBlock = hitState;
                        penetrationTime = 2;

                        double used = start.distanceTo(hitEnd);
                        double total = start.distanceTo(end);
                        double fracLeft = (total <= 1.0e-6) ? 0.0 : Math.max(0.0, (total - used) / total);

                        // Continue from the hit point along the *original segment direction*
                        start = hitEnd;
                        end = hitEnd.add(segDirUnit.scale((total - used))); // distance-left, not scaled by old disp vector
                    }
                    case STOP -> {
                        resolvedPosThisTick = snappedEntityPos;
                        nextVelocity = Vec3.ZERO;
                        lastPenetratedBlock = hitState;
                        penetrationTime = 2;
                        stop = true;
                    }
                    case BOUNCE -> {
                        resolvedPosThisTick = snappedEntityPos;

                        Vec3 normal = CBCUtils.getSurfaceNormalVector(this.level(), blockHit);
                        double elasticity = 1.7f;
                        nextVelocity = traj.subtract(normal.scale(normal.dot(traj) * elasticity));

                        stop = true;
                    }
                }

                shouldRemove |= result.shouldRemove();
            } else {
                break;
            }

            if (stop || shouldRemove) {

                break;
            }

            // advance the seed AABB to the new start so entity sweep stays accurate next iter
            noseBox = new AABB(start, start).inflate(0.25);
        }

        for (Entity e : ctx.hitEntities())
            shouldRemove |= onHitEntity(e, ctx);



        if (!level().isClientSide) {
            if (ctx.griefState() != CBCCfgMunitions.GriefState.NO_DAMAGE) {
                Vec3 oldVel = getDeltaMovement();
                for (Map.Entry<BlockPos, Float> queued : ctx.getQueuedExplosions().entrySet()) {
                    Vec3 impactPos = Vec3.atCenterOf(queued.getKey());
                    ImpactExplosion explosion = new ImpactExplosion(level(), this, getDamage(),
                            impactPos.x, impactPos.y, impactPos.z,
                            queued.getValue(),
                            Level.ExplosionInteraction.BLOCK);
                    CreateBigCannons.handleCustomExplosion(level(), explosion);
                }
                setContraptionMotion(oldVel);
            }

            for (ClientboundPlayBlockHitEffectPacket pkt : ctx.getPlayedEffects())
                NetworkPlatform.sendToClientTracking(pkt, this);
        }

        if (!level().isClientSide || !stop) {
            Vec3 o = getOrientation();
            Vec3 look = o.lengthSqr() < 1e-8 ? new Vec3(0, -1, 0) : o.normalize();
            setXRot(pitchFromVector(look));
            setYRot(yawFromVector(look));
        }

        if (shouldRemove) {
            blockMass.remove(capPos);
        }
    }
    protected boolean onClip(MissileProjectileContext ctx, Vec3 start, Vec3 end) {
        if (warheadpos == null || !(warhead instanceof FuzedBigCannonProjectile fuzed)) {

            return false;
        }


        fuzed.setPos(this.toGlobalVector(Vec3.atCenterOf(warheadpos), 0));
        fuzed.setDeltaMovement(this.getDeltaMovement());

        var acc = (FuzeMixin) fuzed; // or your FuzedProjectileAccessor equivalent
        ItemStack fuzeStack = acc.getFuze();
        boolean baseFuze = acc.invokeGetFuzeProperties().baseFuze();

        // Minimal context: damage restriction only (no entity copying)
        ProjectileContext pctx = new ProjectileContext(fuzed, CBCConfigs.server().munitions.damageRestriction.get());
        for (Entity e : ctx.hitEntities()) pctx.addEntity(e);

        if (acc.invokeCanDetonate(fz -> fz.onProjectileClip(fuzeStack, fuzed, start, end, pctx, baseFuze))) {
            this.detonate(warheadpos, fuzed);
            fuzed.discard();
            this.warhead = null;
            warheadpos = null;
            return true;
        }

        return false;
    }
    protected boolean onHitEntity(Entity entity, MissileProjectileContext ctx) {
        if (level().isClientSide) {
            // server decides damage/explosions; client just says "not finished"
            return false;
        }

        if (warhead != null) {
            // Let the real projectile handle its own special behavior (Warium etc.)
            ProjectileContext wctx = new ProjectileContext(warhead, CBCConfigs.server().munitions.damageRestriction.get());
            for (Entity e : ctx.hitEntities()) wctx.addEntity(e);

            // Run CBC projectile's impact pipeline on the entity

                ((AbstractProjectileAccessor) warhead).invokeImpact(
                        new EntityHitResult(entity),
                        new AbstractCannonProjectile.ImpactResult(
                                AbstractCannonProjectile.ImpactResult.KinematicOutcome.PENETRATE,
                                warhead.getProjectileMass() <= 0
                        ),
                        wctx
                );

                // Optional: apply the projectile's damage/knockback numbers directly
                EntityDamagePropertiesComponent props = warhead.getDamageProperties();
                if (props != null) {
                    entity.setDeltaMovement(getDeltaMovement().scale(props.knockback()));

                    DamageSource source = indirectArtilleryFire(props.ignoresEntityArmor());

                    if (props.ignoresInvulnerability()) entity.invulnerableTime = 0;
                    entity.hurt(source, props.entityDamage());
                    if (!props.rendersInvulnerable()) entity.invulnerableTime = 0;
                }
            }


        // Always feed the hit into your fuze/warhead detonation decision
        return this.onImpact(
                new EntityHitResult(entity),
                new AbstractCannonProjectile.ImpactResult(AbstractCannonProjectile.ImpactResult.KinematicOutcome.PENETRATE, false),
                ctx
        );
    }
    protected DamageSource getDamage() {
        boolean bypassesArmor = false;
        if (warhead != null) 	bypassesArmor = warhead.getDamageProperties().ignoresEntityArmor();
        return new CannonDamageSource(CannonDamageSource.getDamageRegistry(this.level()).getHolderOrThrow(CBCDamageTypes.CANNON_PROJECTILE), bypassesArmor);
    }
    protected void tickWarhead() {
        if (this.contraption == null || this.warhead == null || this.warheadpos == null) {

            return;
        }

        // Only fuzed warheads have tick-based detonation logic
        if (!(this.warhead instanceof FuzedBigCannonProjectile fuzed))
            return;

        // Keep the warhead in sync with the missile
        fuzed.setPos(this.toGlobalVector(Vec3.atCenterOf(warheadpos), 0));
        fuzed.setDeltaMovement(this.getDeltaMovement());

        // Run fuze tick -> detonate if it says so
        FuzeMixin acc = (FuzeMixin) fuzed;
        if (acc.invokeCanDetonate(fz -> fz.onProjectileTick(acc.getFuze(), fuzed))) {
            this.detonate(warheadpos, fuzed);
            LOGGER.warn("explosion discard");
            fuzed.discard();
            this.warhead = null;
            this.warheadpos = null;
        }
    }



    protected Vec3 getForces( Vec3 velocity) {
        return velocity.normalize().scale(-this.getDragForce(velocity))
                .add(0.0d, this.getGravity(), 0.0d);
    }
    protected double getGravity() {
        return this.isNoGravity() ? 0 : this.entityData.get(GRAVITY) * DimensionMunitionPropertiesHandler
                .getProperties(this.level()).gravityMultiplier();
    }

    protected double getDragForce(Vec3 velocity) {
        double vel = velocity.length();
        double formDrag = BALLISTIC_PROPERTIES.drag();
        double density = DimensionMunitionPropertiesHandler.getProperties(this.level()).dragMultiplier();

        FluidState fluidState = this.level().getFluidState(this.blockPosition());
        if (!fluidState.isEmpty())
            density += FluidDragHandler.getFluidDrag(fluidState);

        double drag = formDrag * density * vel;
        return Math.min(drag, vel);
    }

    protected boolean onImpactFluid(MissileProjectileContext projectileContext, BlockState blockState, FluidState fluidState,
                                    Vec3 impactPos, BlockHitResult fluidHitResult) {
        Vec3 pos = this.position();
        Vec3 accel = this.getForces(this.getDeltaMovement());
        Vec3 curVel = this.getDeltaMovement().add(accel);

        Vec3 normal = CBCUtils.getSurfaceNormalVector(this.level(), fluidHitResult);
        double incidence = Math.max(0, curVel.normalize().dot(normal.reverse()));
        double velMag = curVel.length();
        double mass = 0;
        if (blockMass.containsKey(capPos)) {
            mass = blockMass.get(capPos);
        }
        double projectileDeflection = 0.7;
        double incidentVel = velMag * incidence;
        double momentum = mass * incidentVel;
        double fluidDensity = FluidDragHandler.getFluidDrag(fluidState);

        boolean canBounce = CBCConfigs.server().munitions.projectilesCanBounce.get();
        double baseChance = CBCConfigs.server().munitions.baseProjectileFluidBounceChance.getF();
        boolean criticalAngle = projectileDeflection > 1e-2d && incidence <= projectileDeflection;
        boolean buoyant = fluidDensity > 1e-2d && momentum < fluidDensity;

        double incidenceFactor = criticalAngle ? Math.max(0, 1 - incidence / projectileDeflection) : 0;
        double massFactor = buoyant ? 0 : Math.max(0, 1 - momentum / fluidDensity);
        double chance = Math.max(baseChance, incidenceFactor * massFactor);

        boolean bounced = canBounce && criticalAngle && buoyant && this.level().getRandom().nextDouble() < chance;
        if (bounced) {
            this.setContraptionMotion(fluidHitResult.getLocation().subtract(pos));
            double elasticity = 1.7f;
            this.nextVelocity = curVel.subtract(normal.scale(normal.dot(curVel) * elasticity));
        }
        if (!this.level().isClientSide) {
            Vec3 effectNormal = bounced ? normal.scale(incidentVel) : curVel.reverse();
            Vec3 fluidExplosionPos = fluidHitResult.getLocation();
            projectileContext.addPlayedEffect(new ClientboundPlayBlockHitEffectPacket(blockState, this.getType(), bounced, true,
                    fluidExplosionPos.x, fluidExplosionPos.y, fluidExplosionPos.z, (float) effectNormal.x,
                    (float) effectNormal.y, (float) effectNormal.z));
        }
        return bounced;
    }

    protected AbstractCannonProjectile.ImpactResult calculateBlockPenetration(MissileProjectileContext projectileContext, BlockState state, BlockHitResult blockHitResult) {
        BlockPos pos = blockHitResult.getBlockPos();
        Vec3 hitLoc = blockHitResult.getLocation();

        BallisticPropertiesComponent ballistics = BALLISTIC_PROPERTIES;
        double mass = 0;
        if (this.contraption.getBlocks().isEmpty()) {
            LOGGER.warn("calculateBlockPenetration discard");
            this.discard();
            return new AbstractCannonProjectile.ImpactResult(AbstractCannonProjectile.ImpactResult.KinematicOutcome.STOP, true);
        }

        if (blockMass.containsKey(capPos)) {
            mass = blockMass.get(capPos);
        }
        if (ballistics == null) {
            LOGGER.warn("null discard");
            this.discard();
            return new AbstractCannonProjectile.ImpactResult(AbstractCannonProjectile.ImpactResult.KinematicOutcome.STOP, true);
        }
        BlockArmorPropertiesProvider blockArmor = BlockArmorPropertiesHandler.getProperties(state);
        boolean unbreakable = projectileContext.griefState() == CBCCfgMunitions.GriefState.NO_DAMAGE || state.getDestroySpeed(this.level(), pos) == -1;

        Vec3 accel = this.getForces(this.getDeltaMovement());
        Vec3 curVel = this.getDeltaMovement().add(accel);

        Vec3 normal = CBCUtils.getSurfaceNormalVector(this.level(), blockHitResult);
        double incidence = Math.max(0, curVel.normalize().dot(normal.reverse()));
        double velMag = curVel.length();
        double bonusMomentum = 1 + Math.max(0, (velMag - CBCConfigs.server().munitions.minVelocityForPenetrationBonus.getF())
                * CBCConfigs.server().munitions.penetrationBonusScale.getF());
        double incidentVel = velMag * incidence;
        double momentum = mass * incidentVel * bonusMomentum;

        double toughness = blockArmor.toughness(this.level(), state, pos, true);
        double toughnessPenalty = toughness - momentum;
        double hardnessPenalty = blockArmor.hardness(this.level(), state, pos, true) - ballistics.penetration();

        double projectileDeflection = ballistics.deflection();
        double baseChance = CBCConfigs.server().munitions.baseProjectileBounceChance.getF();
        double bounceChance = projectileDeflection < 1e-2d || incidence > projectileDeflection ? 0 : Math.max(baseChance, 1 - incidence / projectileDeflection);

        boolean surfaceImpact = this.canHitSurface();
        boolean canBounce = CBCConfigs.server().munitions.projectilesCanBounce.get();
        boolean blockBroken = toughnessPenalty < 1e-2d && !unbreakable;
        AbstractCannonProjectile.ImpactResult.KinematicOutcome outcome;
        if (surfaceImpact && canBounce && this.level().getRandom().nextDouble() < bounceChance) {
            outcome = AbstractCannonProjectile.ImpactResult.KinematicOutcome.BOUNCE;
        } else if (blockBroken && !this.level().isClientSide) {
            outcome = AbstractCannonProjectile.ImpactResult.KinematicOutcome.PENETRATE;
        } else {
            outcome = AbstractCannonProjectile.ImpactResult.KinematicOutcome.STOP;
        }
        boolean shatter = surfaceImpact && outcome != AbstractCannonProjectile.ImpactResult.KinematicOutcome.BOUNCE && hardnessPenalty > ballistics.toughness();
        float durabilityPenalty = ((float) Math.max(0, hardnessPenalty) + 1) * (float) toughness / (float) incidentVel;

        if (warheadpos == capPos) {
            state.onProjectileHit(this.level(), state, blockHitResult, warhead);
        } else {
            state.onProjectileHit(this.level(), state, blockHitResult, new SolidShotProjectile(CBCEntityTypes.SHOT.get(), this.level()));
        }
        if (!this.level().isClientSide) {
            boolean bounced = outcome == AbstractCannonProjectile.ImpactResult.KinematicOutcome.BOUNCE;
            Vec3 effectNormal;
            if (bounced) {
                double elasticity = 1.7f;
                effectNormal = curVel.subtract(normal.scale(normal.dot(curVel) * elasticity));
            } else {
                effectNormal = curVel.reverse();
            }
            for (BlockState state1 : blockArmor.containedBlockStates(this.level(), state, pos.immutable(), true)) {
                projectileContext.addPlayedEffect(new ClientboundPlayBlockHitEffectPacket(state1, this.getType(), bounced, true,
                        hitLoc.x, hitLoc.y, hitLoc.z, (float) effectNormal.x, (float) effectNormal.y, (float) effectNormal.z));
            }
        }
        if (blockBroken) {
            this.blockMass.put(capPos, (incidentVel < 1e-4d ? 0 : Math.max(this.blockMass.get(capPos) - durabilityPenalty, 0)));
            this.level().setBlock(pos, Blocks.AIR.defaultBlockState(), ProjectileBlock.UPDATE_ALL_IMMEDIATE);
            if (surfaceImpact) {
                float f = (float) toughness / (float) momentum;
                float overPenetrationPower = f < 0.15f ? 2 - 2 * f : 0;
                if (overPenetrationPower > 0 && outcome == AbstractCannonProjectile.ImpactResult.KinematicOutcome.PENETRATE)
                    projectileContext.queueExplosion(pos, overPenetrationPower);
            }
        } else {
            if (outcome == AbstractCannonProjectile.ImpactResult.KinematicOutcome.STOP) {
                this.blockMass.put(capPos, 0f);
            } else {
                if (this.blockMass.containsKey(capPos)) {
                    this.blockMass.put(capPos, (incidentVel < 1e-4d ? 0 : Math.max(this.blockMass.get(capPos) - durabilityPenalty / 2f, 0)));
                }
            }
            Vec3 spallLoc = hitLoc.add(curVel.normalize().scale(2));
            if (!this.level().isClientSide) {
                ImpactExplosion explosion = new ImpactExplosion(this.level(), this, this.indirectArtilleryFire(false), spallLoc.x, spallLoc.y, spallLoc.z, 2, Level.ExplosionInteraction.NONE);
                CreateBigCannons.handleCustomExplosion(this.level(), explosion);
            }
            SoundType sound = state.getSoundType();
            if (!this.level().isClientSide)
                this.level().playSound(null, spallLoc.x, spallLoc.y, spallLoc.z, sound.getBreakSound(), SoundSource.BLOCKS,
                        sound.getVolume(), sound.getPitch());
        }
        shatter |= this.onImpact(blockHitResult, new AbstractCannonProjectile.ImpactResult(outcome, shatter), projectileContext);
        return new AbstractCannonProjectile.ImpactResult(outcome, shatter);
    }
    protected boolean canHitSurface() {

        return this.lastPenetratedBlock.isAir() && this.penetrationTime == 0;
    }
    public DamageSource indirectArtilleryFire(boolean bypassArmor) {
        return new CannonDamageSource(CannonDamageSource.getDamageRegistry(this.level()).getHolderOrThrow(CBCDamageTypes.CANNON_PROJECTILE), bypassArmor);
    }

    protected void detonate(BlockPos pos, FuzedBigCannonProjectile fuzed) {
        if (!level().isClientSide && level() instanceof ServerLevel sl) {
            chainSystem.releaseAll(sl);
        }
        Minecraft.getInstance().getSoundManager().stop(engineSound);
        LOGGER.warn("kaboom");
        BlockPos oldPos = this.blockPosition();
        Vec3 oldDelta = this.getDeltaMovement();
        fuzed.setDeltaMovement(oldDelta);
        ((FuzeMixin) fuzed).invokeDetonate((this.toGlobalVector(Vec3.atCenterOf(pos), 0)));
        this.setPos(oldPos.getX(), oldPos.getY(), oldPos.getZ());
        this.setContraptionMotion(oldDelta.scale(0.75));
        discard();
    }
    public Vec3 getOrientation() {

        return new Vec3(
                this.entityData.get(HEADING_X),
                this.entityData.get(HEADING_Y),
                this.entityData.get(HEADING_Z)
        );
    }
    protected boolean onImpact(HitResult hitResult,
                               AbstractCannonProjectile.ImpactResult impactResult,
                               MissileProjectileContext projectileContext) {

        if (!(this.warhead instanceof FuzedBigCannonProjectile fuzed) || this.warheadpos == null) {
            if (!level().isClientSide && impactResult.kinematics() == AbstractCannonProjectile.ImpactResult.KinematicOutcome.STOP) {
                level().explode(this, getX(), getY(), getZ(), 4.0f, Level.ExplosionInteraction.TNT);
                LOGGER.warn("impact discard");
                discard();
                return true;
            }
            return false;
        }

        Vec3 warheadWorld = this.toGlobalVector(Vec3.atCenterOf(this.warheadpos), 1.0f);
        fuzed.setPos(warheadWorld);

        // IMPORTANT: use real velocity, not your heading vector
        fuzed.setDeltaMovement(this.getDeltaMovement());

        FuzeMixin acc = (FuzeMixin) fuzed;
        boolean baseFuze = acc.invokeGetFuzeProperties().baseFuze();

        if (acc.invokeCanDetonate(fz ->
                fz.onProjectileImpact(acc.getFuze(), fuzed, hitResult, impactResult, baseFuze))) {
            detonate(this.warheadpos, fuzed);
            fuzed.discard();
            this.warhead = null;
            this.warheadpos = null;
            return true;
        }


        return false;
    }

}
