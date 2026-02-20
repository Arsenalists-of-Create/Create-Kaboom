package com.happysg.kaboom.block.missiles;

import com.happysg.kaboom.async.MissileAutopilotPlanner;
import com.happysg.kaboom.block.aerialBombs.baseTypes.AerialBombProjectile;
import com.happysg.kaboom.block.missiles.util.PreciseMotionSyncPacket;
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
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
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
import rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile;
import rbasamoyai.createbigcannons.munitions.ImpactExplosion;
import rbasamoyai.createbigcannons.munitions.ProjectileContext;
import rbasamoyai.createbigcannons.munitions.big_cannon.FuzedBigCannonProjectile;
import rbasamoyai.createbigcannons.munitions.big_cannon.ProjectileBlock;
import rbasamoyai.createbigcannons.munitions.big_cannon.SimpleShellBlock;
import rbasamoyai.createbigcannons.munitions.config.components.BallisticPropertiesComponent;
import rbasamoyai.createbigcannons.munitions.config.components.EntityDamagePropertiesComponent;
import rbasamoyai.createbigcannons.munitions.fuzes.FuzeItem;
import rbasamoyai.createbigcannons.network.ClientboundPlayBlockHitEffectPacket;
import rbasamoyai.createbigcannons.utils.CBCUtils;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class MissileEntity extends OrientedContraptionEntity {

    private static final EntityDataAccessor<Float> HEADING_X =
            SynchedEntityData.defineId(MissileEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> HEADING_Y =
            SynchedEntityData.defineId(MissileEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> HEADING_Z =
            SynchedEntityData.defineId(MissileEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> FUEL_MB =
            SynchedEntityData.defineId(MissileEntity.class, EntityDataSerializers.INT);
    private ItemStack fuze;

    private static final EntityDataAccessor<Integer> FUEL_CAP_MB =
            SynchedEntityData.defineId(MissileEntity.class, EntityDataSerializers.INT);
    // --- Fuel / thrust tuning ---


    private final Set<Long> forcedChunks = new HashSet<>();
    private static final int CHUNK_RADIUS = 1; // 0=just current chunk, 1=3x3, 2=5x5
    private static final int BASE_BURN_MB_PER_TICK = 4;     // idle-ish burn when thrusting
    private static final double BURN_PER_ACCEL = 18.0;      // extra burn per |a| (blocks/tick^2)
    private static final double MIN_THRUST_ACCEL = 0.01;    // below this, consider "not thrusting"

    private static final double GRAVITY_PER_TICK = 0.08;
    private static final double BALLISTIC_STEER_GAIN = 0.35;
    private static final double MAX_LATERAL_ACCEL = 0.10;
    private static final double MIN_SPEED_FOR_STEER = 0.15;
    private Vec3 inGroundPos;


    private record PhysicsStep(Vec3 pos, Vec3 vel) {
    }

    private static final Logger LOGGER = LogUtils.getLogger();

    private enum FlightPhase {ASCENT, CRUISE, BALLISTIC, IMPACT}

    // Target: climb to Y=128, then fly to (0,128,5000)
    private static final Vec3 TARGET = new Vec3(0, 0, 5000);
    private int fuelMb;
    private int fuelCapacityMb;
    private FluidStack fuelFluid = FluidStack.EMPTY;


    private static final double ASCENT_SPEED = 1.2;
    private static final double CRUISE_SPEED = 2.5;
    private static final double MAX_SPEED = 6.0;

    private static final double GAIN_ASCENT = 0.35;
    private static final double GAIN_CRUISE = 0.25;

    private static final double ARRIVAL_RADIUS = 6.0;
    private static final double PITCH_START_Y = 24;  // start tipping soon after launch
    private static final double PITCH_END_Y = 128;  // finish tipping well before 128
    private static final double CRUISE_SWITCH_Y = 96;
    private FlightPhase phase = FlightPhase.ASCENT;
    private Vec3 vel = Vec3.ZERO;
    private Vec3 lastVelForSmoke = Vec3.ZERO;
    private boolean fuelDepleted = false;
    private volatile MissileAutopilotPlanner.Command latestCmd = MissileAutopilotPlanner.Command.NONE;
    private CompletableFuture<MissileAutopilotPlanner.Command> cmdFuture;
    @OnlyIn(Dist.CLIENT)
    private MissileEngineSound engineSound;

    public MissileEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    public void initFromAssembly(Contraption contraption, BlockPos controllerPos, BlockPos warheadPos) {
        LOGGER.warn("[MISSILE] initFromAssembly(): blocks={} controller={}",
                contraption.getBlocks().size(), controllerPos);

        setPos(controllerPos.getX() + 0.5, controllerPos.getY() + 2.5, controllerPos.getZ() + 0.5);


        setContraption(contraption);
        setNoGravity(true);
        startAtInitialYaw();

        // Reset state
        phase = FlightPhase.ASCENT;
        vel = Vec3.ZERO;
        if (contraption instanceof MissileContraption mc) {
            this.fuelMb = mc.fuelAmountMb;
            this.fuelCapacityMb = mc.fuelCapacityMb;

            if (mc.fuelFluidTag != null) {
                this.fuelFluid = FluidStack.loadFluidStackFromNBT(mc.fuelFluidTag);
            } else {
                this.fuelFluid = FluidStack.EMPTY;
            }
            entityData.set(FUEL_MB, this.fuelMb);
            entityData.set(FUEL_CAP_MB, this.fuelCapacityMb);
        }
        super.setDeltaMovement(Vec3.ZERO);
    }

    private void serverPreTickSafety() {
        fallDistance = 0;
        this.hasImpulse = true;
    }

    @Override
    public void tick() {
        // client: start sound once
        if (level().isClientSide && tickCount == 1) {
            engineSound = new MissileEngineSound(this);
            Minecraft.getInstance().getSoundManager().play(engineSound);
        }
        if (level().isClientSide) {
            int fuelNow = entityData.get(FUEL_MB);
            fuelDepleted = (fuelNow <= 0);
            this.fuelMb = entityData.get(FUEL_MB);
            this.fuelCapacityMb = entityData.get(FUEL_CAP_MB);
        }
        if (level().isClientSide && fuelDepleted && engineSound != null) {
            Minecraft.getInstance().getSoundManager().stop(engineSound);
            engineSound = null;
        }
        if (this.contraption == null) {
            discard();
            return;
        }

        // client smoke
        if (level().isClientSide && !fuelDepleted) {
            spawnSmokeClient(position(), getDeltaMovement());
        }
        int fuelNow = entityData.get(FUEL_MB);
        LogUtils.getLogger().warn("fuel(sync): {} depleted?: {}", fuelNow, fuelDepleted);
        // ---- SERVER ----
        if (!level().isClientSide) {
            tickChunkLoading();
            serverPreTickSafety();

            // If we're ballistic: let vanilla tick handle gravity + collision
            if (phase == FlightPhase.BALLISTIC) {
                ballisticTickServer();

                syncHeading(getDeltaMovement());
                sendPreciseMotion(position(), getDeltaMovement());
                updatePhase(position());
                return;
            }

            // Powered flight: custom physics
            pumpAsyncPlanner();

            PhysicsStep step = stepPhysics();
            applyPhysics(step);

            syncHeading(step.vel());
            sendPreciseMotion(step.pos(), step.vel());
            spawnSmokeIfNeeded(step.pos(), step.vel());
            updatePhase(step.pos());
        }

        super.tick();
    }

    private void ballisticTickServer() {

        setNoGravity(true);
        noPhysics = false;

        final double g = 0.08;
        final double drag = 0.98;


        vel = new Vec3(vel.x * drag, (vel.y - g) * drag, vel.z * drag);


        Vec3 before = position();
        move(MoverType.SELF, vel);


        Vec3 after = position();
        Vec3 actualDelta = after.subtract(before);


        super.setDeltaMovement(actualDelta);
        setContraptionMotion(actualDelta);

        if (Math.abs(actualDelta.x - vel.x) > 1e-6) vel = new Vec3(0, vel.y, vel.z);
        if (Math.abs(actualDelta.y - vel.y) > 1e-6) vel = new Vec3(vel.x, 0, vel.z);
        if (Math.abs(actualDelta.z - vel.z) > 1e-6) vel = new Vec3(vel.x, vel.y, 0);
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
    public void remove(RemovalReason reason) {
        if (!level().isClientSide && level() instanceof ServerLevel sl) {
            for (long key : forcedChunks) {
                ChunkPos p = new ChunkPos(key);
                sl.setChunkForced(p.x, p.z, false);
            }
            forcedChunks.clear();
        }
        super.remove(reason);
    }

    private Vec3 computeThrustAccelAndBurnFuel() {
        if (phase == FlightPhase.BALLISTIC) return Vec3.ZERO;
        if (fuelMb <= 0) {
            onFuelDepleted();
            return Vec3.ZERO;
        }

        Vec3 desiredVel = computeDesiredVelocity(position());
        double gain = (phase == FlightPhase.ASCENT) ? GAIN_ASCENT : GAIN_CRUISE;
        Vec3 a = desiredVel.subtract(vel).scale(gain);

        double aMag = a.length();
        if (aMag > MIN_THRUST_ACCEL) {
            int burn = (int) Math.ceil(BASE_BURN_MB_PER_TICK + BURN_PER_ACCEL * aMag);
            burnFuel(burn);
            if (fuelMb <= 0) {
                onFuelDepleted();
                return Vec3.ZERO;
            }
        }

        return a;
    }

    private Vec3 computeBallisticSteerAccel(Vec3 pos, Vec3 v) {
        double sp = v.length();
        if (sp < MIN_SPEED_FOR_STEER) return Vec3.ZERO;
        Vec3 vDir = v.scale(1.0 / sp);
        Vec3 desiredDir = TARGET.subtract(pos).normalize();
        Vec3 error = desiredDir.subtract(vDir);
        Vec3 lateral = error.subtract(vDir.scale(error.dot(vDir)));

        if (lateral.lengthSqr() < 1e-10) return Vec3.ZERO;

        Vec3 a = lateral.normalize().scale(BALLISTIC_STEER_GAIN * sp);

        // Clamp turn authority
        double aMag = a.length();
        if (aMag > MAX_LATERAL_ACCEL) a = a.scale(MAX_LATERAL_ACCEL / aMag);

        return a;
    }

    private PhysicsStep stepPhysics() {
        final int substeps = 6;
        final double dt = 1.0 / substeps;

        Vec3 pos = position();
        Vec3 v = vel;
        Vec3 aGravity = new Vec3(0, -GRAVITY_PER_TICK, 0);
        Vec3 aThrust = computeThrustAccelAndBurnFuel();
        Vec3 aSteer = (phase == FlightPhase.BALLISTIC) ? computeBallisticSteerAccel(pos, v) : Vec3.ZERO;
        Vec3 aTick = aGravity.add(aThrust).add(aSteer);
        for (int i = 0; i < substeps; i++) {
            pos = pos.add(v.scale(dt)).add(aTick.scale(0.5 * dt * dt));
            v = v.add(aTick.scale(dt));
        }
        double sp = v.length();
        if (sp > MAX_SPEED)
            v = v.scale(MAX_SPEED / sp);

        return new PhysicsStep(pos, v);
    }

    private void applyPhysics(PhysicsStep step) {
        setPos(step.pos());
        setContraptionMotion(step.vel());
        super.setDeltaMovement(step.vel()); // still important for client-side visuals
        vel = step.vel();
    }

    private void syncHeading(Vec3 v) {
        Vec3 h = v.lengthSqr() > 1e-8 ? v.normalize() : new Vec3(0, 1, 0);
        entityData.set(HEADING_X, (float) h.x);
        entityData.set(HEADING_Y, (float) h.y);
        entityData.set(HEADING_Z, (float) h.z);
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

    private void spawnSmokeIfNeeded(Vec3 pos, Vec3 v) {
        if (level().isClientSide)
            spawnSmokeClient(pos, v);
    }

    private void updatePhase(Vec3 pos) {
        if (phase == FlightPhase.ASCENT && pos.y >= CRUISE_SWITCH_Y) {
            phase = FlightPhase.CRUISE;
        }

        if (phase == FlightPhase.CRUISE && pos.distanceTo(TARGET) <= ARRIVAL_RADIUS) {
            phase = FlightPhase.IMPACT;
            explodeAndDie();
        }
    }

    private void explodeAndDie() {
        level().explode(this, getX(), getY(), getZ(), 6f, Level.ExplosionInteraction.TNT);
        discard();
    }

    private void pumpAsyncPlanner() {
        long t = level().getGameTime();

        // Consume finished job
        if (cmdFuture != null && cmdFuture.isDone()) {
            try {
                latestCmd = cmdFuture.getNow(MissileAutopilotPlanner.Command.NONE);
            } catch (Exception ignored) {
            }
            cmdFuture = null;
        }
        setNoGravity(phase != FlightPhase.BALLISTIC);
        if (fuelMb <= 0 && phase != FlightPhase.IMPACT) {
            onFuelDepleted();
        }
        // Launch a new job periodically
        if (cmdFuture == null && (t % 2 == 0)) {
            var snap = new MissileAutopilotPlanner.Snapshot(
                    this.position(), // main thread read
                    this.vel,
                    switch (this.phase) {
                        case ASCENT -> MissileAutopilotPlanner.Phase.ASCENT;
                        case CRUISE -> MissileAutopilotPlanner.Phase.CRUISE;
                        case BALLISTIC -> MissileAutopilotPlanner.Phase.BALLISTIC;
                        case IMPACT -> MissileAutopilotPlanner.Phase.IMPACT;
                    },
                    TARGET
            );


            var cfg = new MissileAutopilotPlanner.Config(ASCENT_SPEED, CRUISE_SPEED);

            cmdFuture = CompletableFuture.supplyAsync(() ->
                    MissileAutopilotPlanner.plan(snap, cfg)
            );
        }
    }

    /**
     * VISUAL ROTATION: Make contraption's local +Y axis ("up") point along motion.
     * This is the piece your file was missing.
     */
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

            cl.addParticle(ModParticles.MISSILE_SMOKE.get(), x, y, z, motion.x, motion.y, motion.z);
        }
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(HEADING_X, 0f);
        entityData.define(HEADING_Y, 1f);
        entityData.define(HEADING_Z, 0f);
        entityData.define(FUEL_MB, 0);
        entityData.define(FUEL_CAP_MB, 0);
    }

    private Vec3 computeDesiredVelocity(Vec3 pos) {
        Vec3 toTarget = TARGET.subtract(pos);
        Vec3 horiz = new Vec3(toTarget.x, 0, toTarget.z);
        Vec3 horizDir = horiz.lengthSqr() < 1e-10 ? new Vec3(0, 0, 1) : horiz.normalize();

        if (phase == FlightPhase.ASCENT) {
            return new Vec3(0, ASCENT_SPEED, 0);
        }

        // t=0 -> straight up, t=1 -> mostly horizontal
        double t = Mth.clamp((pos.y - PITCH_START_Y) / (PITCH_END_Y - PITCH_START_Y), 0.0, 1.0);
        t = t * t * (3.0 - 2.0 * t); // smoothstep

        // Blend UP -> HORIZONTAL
        Vec3 dir = new Vec3(0, 1, 0).scale(1.0 - t).add(horizDir.scale(t)).normalize();

        // Altitude hold toward TARGET_Y so it levels near 128
        double altErr = TARGET.y - pos.y;                 // + if below target altitude
        double yHold = Mth.clamp(altErr * 0.02, -0.25, 0.25);
        dir = new Vec3(dir.x, dir.y + yHold, dir.z).normalize();

        // Speed ramps up through the turn
        double speed = Mth.lerp(t, ASCENT_SPEED, CRUISE_SPEED);
        return dir.scale(speed);
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

    private void onFuelDepleted() {
        if (fuelDepleted) return;
        fuelDepleted = true;

        LOGGER.warn("[MISSILE] Fuel depleted at tick {}", level().getGameTime());
        phase = FlightPhase.BALLISTIC;

        cmdFuture = null;
        latestCmd = MissileAutopilotPlanner.Command.NONE;

        setNoGravity(true);     // we do gravity ourselves in ballisticTickServer()
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


    public static final BallisticPropertiesComponent BALLISTIC_PROPERTIES = new BallisticPropertiesComponent(-0.1, .01, false, 2.0f, 1, 1, 0.70f);
    public static final EntityDamagePropertiesComponent DAMAGE_PROPERTIES = new EntityDamagePropertiesComponent(30, false, true, true, 2);
    private int explosionCountdown;
    private AbstractCannonProjectile cannonProjectile;


    public boolean canLingerInGround() {
        boolean checkFuze = false;
        if (cannonProjectile != null && cannonProjectile instanceof FuzedBigCannonProjectile fuzedWarhead) {
            checkFuze = ((FuzeMixin) fuzedWarhead).getFuze().getItem() instanceof FuzeItem fuzeItem && fuzeItem.canLingerInGround(((FuzeMixin) fuzedWarhead).getFuze(), fuzedWarhead);
        }
        return !this.level().isClientSide && this.level().hasChunkAt(this.blockPosition()) && checkFuze;
    }


    public void setExplosionCountdown(int value) {
        this.explosionCountdown = Math.max(value, -1);
    }

    public int getExplosionCountdown() {
        return this.explosionCountdown;
    }
}

//
//
//@Override
//protected AbstractCannonProjectile.ImpactResult calculateBlockPenetration(ProjectileContext projectileContext, BlockState state, BlockHitResult blockHitResult) {
//    BlockPos pos = blockHitResult.getBlockPos();
//    Vec3 hitLoc = blockHitResult.getLocation();
//
//    BallisticPropertiesComponent ballistics = this.getBallisticProperties();
//    BlockArmorPropertiesProvider blockArmor = BlockArmorPropertiesHandler.getProperties(state);
//    boolean unbreakable = projectileContext.griefState() == CBCCfgMunitions.GriefState.NO_DAMAGE || state.getDestroySpeed(this.level(), pos) == -1;
//
//    Vec3 accel = this.getForces(this.position(), this.getDeltaMovement());
//    Vec3 curVel = this.getDeltaMovement().add(accel);
//
//    Vec3 normal = CBCUtils.getSurfaceNormalVector(this.level(), blockHitResult);
//    double incidence = Math.max(0, curVel.normalize().dot(normal.reverse()));
//    double velMag = (curVel.length());
//    if(type == AerialBombProjectile.BombType.AP) velMag = velMag*((double) 17 /size);
//    double mass = this.getProjectileMass();
//
//    double bonusMomentum = 1 + Math.max(0, (velMag - CBCConfigs.server().munitions.minVelocityForPenetrationBonus.getF())
//            * CBCConfigs.server().munitions.penetrationBonusScale.getF());
//    double incidentVel = velMag * incidence;
//    double momentum = mass * incidentVel * bonusMomentum;
//
//    double toughness = blockArmor.toughness(this.level(), state, pos, true);
//    double toughnessPenalty = toughness - momentum;
//    double hardnessPenalty = blockArmor.hardness(this.level(), state, pos, true) - ballistics.penetration();
//    double bounceBonus = Math.max(1 - hardnessPenalty, 0);
//
//    double projectileDeflection = ballistics.deflection();
//    double baseChance = CBCConfigs.server().munitions.baseProjectileBounceChance.getF();
//    double bounceChance = projectileDeflection < 1e-2d || incidence > projectileDeflection ? 0 : Math.max(baseChance, 1 - incidence / projectileDeflection) * bounceBonus;
//
//    boolean surfaceImpact = this.canHitSurface();
//    boolean canBounce = CBCConfigs.server().munitions.projectilesCanBounce.get();
//    boolean blockBroken = toughnessPenalty < 1e-2d && !unbreakable;
//    AbstractCannonProjectile.ImpactResult.KinematicOutcome outcome;
//    if (surfaceImpact && canBounce && this.level().getRandom().nextDouble() < bounceChance) {
//        outcome = AbstractCannonProjectile.ImpactResult.KinematicOutcome.BOUNCE;
//    } else if (blockBroken && !this.level().isClientSide) {
//        outcome = AbstractCannonProjectile.ImpactResult.KinematicOutcome.PENETRATE;
//    } else {
//        outcome = AbstractCannonProjectile.ImpactResult.KinematicOutcome.STOP;
//    }
//    boolean shatter = surfaceImpact && outcome != AbstractCannonProjectile.ImpactResult.KinematicOutcome.BOUNCE && hardnessPenalty > ballistics.toughness();
//    float durabilityPenalty = ((float) Math.max(0, hardnessPenalty) + 1) * (float) toughness / (float) incidentVel;
//
//    state.onProjectileHit(this.level(), state, blockHitResult, this);
//    if (!this.level().isClientSide) {
//        boolean bounced = outcome == AbstractCannonProjectile.ImpactResult.KinematicOutcome.BOUNCE;
//        Vec3 effectNormal;
//        if (bounced) {
//            double elasticity = 1.7f;
//            effectNormal = curVel.subtract(normal.scale(normal.dot(curVel) * elasticity));
//        } else {
//            effectNormal = curVel.reverse();
//        }
//        for (BlockState state1 : blockArmor.containedBlockStates(this.level(), state, pos.immutable(), true)) {
//            projectileContext.addPlayedEffect(new ClientboundPlayBlockHitEffectPacket(state1, this.getType(), bounced, true,
//                    hitLoc.x, hitLoc.y, hitLoc.z, (float) effectNormal.x, (float) effectNormal.y, (float) effectNormal.z));
//        }
//    }
//    if (blockBroken) {
//        this.setProjectileMass(incidentVel < 1e-4d ? 0 : Math.max(this.getProjectileMass() - durabilityPenalty, 0));
//        this.level().setBlock(pos, Blocks.AIR.defaultBlockState(), ProjectileBlock.UPDATE_ALL_IMMEDIATE);
//
//        if (surfaceImpact) {
//            float f = (float) toughness / (float) momentum;
//            float overPenetrationPower = f < 0.15f ? 2 - 2 * f : 0;
//            if (overPenetrationPower > 0 && outcome == AbstractCannonProjectile.ImpactResult.KinematicOutcome.PENETRATE)
//                projectileContext.queueExplosion(pos, overPenetrationPower);
//        }
//    } else {
//        if (outcome == AbstractCannonProjectile.ImpactResult.KinematicOutcome.STOP) {
//            this.setProjectileMass(0);
//        } else {
//            this.setProjectileMass(incidentVel < 1e-4d ? 0 : Math.max(this.getProjectileMass() - durabilityPenalty / 2f, 0));
//        }
//        Vec3 spallLoc = hitLoc.add(curVel.normalize().scale(2));
//        if (!this.level().isClientSide) {
//            ImpactExplosion explosion = new ImpactExplosion(this.level(), this, this.indirectArtilleryFire(false), spallLoc.x, spallLoc.y, spallLoc.z, 2, Level.ExplosionInteraction.NONE);
//            CreateBigCannons.handleCustomExplosion(this.level(), explosion);
//        }
//        SoundType sound = state.getSoundType();
//        if (!this.level().isClientSide)
//            this.level().playSound(null, spallLoc.x, spallLoc.y, spallLoc.z, sound.getBreakSound(), SoundSource.BLOCKS,
//                    sound.getVolume(), sound.getPitch());
//    }
//    shatter |= this.onImpact(blockHitResult, new AbstractCannonProjectile.ImpactResult(outcome, shatter), projectileContext);
//    return new AbstractCannonProjectile.ImpactResult(outcome, shatter);
//}
//
//}