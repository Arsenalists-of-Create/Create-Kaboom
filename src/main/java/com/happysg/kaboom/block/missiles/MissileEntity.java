package com.happysg.kaboom.block.missiles;

import com.happysg.kaboom.async.MissileAutopilotPlanner;
import com.happysg.kaboom.block.missiles.assembly.PreciseMotionSyncPacket;
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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;

import java.util.concurrent.CompletableFuture;

public class MissileEntity extends OrientedContraptionEntity {
    private static final EntityDataAccessor<Float> HEADING_X =
            SynchedEntityData.defineId(MissileEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> HEADING_Y =
            SynchedEntityData.defineId(MissileEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> HEADING_Z =
            SynchedEntityData.defineId(MissileEntity.class, EntityDataSerializers.FLOAT);
    private record PhysicsStep(Vec3 pos, Vec3 vel) {}
    private static final Logger LOGGER = LogUtils.getLogger();

    private enum FlightPhase { ASCENT, CRUISE, IMPACT }

    // Target: climb to Y=128, then fly to (0,128,5000)
    private static final Vec3 TARGET = new Vec3(0, 128, 5000);

    // Tuning (blocks/tick and blocks/tick^2-ish)
    private static final double ASCENT_SPEED = 1.2;
    private static final double CRUISE_SPEED = 2.5;
    private static final double MAX_SPEED = 6.0;

    private static final double GAIN_ASCENT = 0.35;
    private static final double GAIN_CRUISE = 0.25;

    private static final double ARRIVAL_RADIUS = 6.0;
    private static final double PITCH_START_Y = 24;  // start tipping soon after launch
    private static final double PITCH_END_Y   = 96;  // finish tipping well before 128
    private static final double CRUISE_SWITCH_Y = 24;
    private FlightPhase phase = FlightPhase.ASCENT;
    private Vec3 vel = Vec3.ZERO;
    private Vec3 lastVelForSmoke = Vec3.ZERO;
    private long lastPlanTick = -1;
    private volatile MissileAutopilotPlanner.Command latestCmd = MissileAutopilotPlanner.Command.NONE;
    private java.util.concurrent.CompletableFuture<MissileAutopilotPlanner.Command> cmdFuture;
    private record AutopilotSnapshot(Vec3 pos, Vec3 vel, FlightPhase phase, Vec3 target) {}
    private record AutopilotCommand(Vec3 desiredVel) {
        static final AutopilotCommand NONE = new AutopilotCommand(Vec3.ZERO);
    }

    public MissileEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    public void initFromAssembly(Contraption contraption, BlockPos controllerPos) {
        LOGGER.warn("[MISSILE] initFromAssembly(): blocks={} controller={}",
                contraption.getBlocks().size(), controllerPos);

        // Spawn above controller so we don't clip into blocks and stall
        setPos(controllerPos.getX() + 0.5, controllerPos.getY() + 2.5, controllerPos.getZ() + 0.5);

        setContraption(contraption);
        setNoGravity(true);
        startAtInitialYaw();

        // Reset state
        phase = FlightPhase.ASCENT;
        vel = Vec3.ZERO;

        // Also ensure the client starts with something sensible
        super.setDeltaMovement(Vec3.ZERO);
    }
    private void serverPreTickSafety() {
        setNoGravity(true);
        fallDistance = 0;
        this.hasImpulse = true;
    }
    @Override
    public void tick() {
        if (level().isClientSide && tickCount == 1) {
            Minecraft.getInstance().getSoundManager()
                    .play(new MissileEngineSound(this));
        }
        if (this.contraption == null) {
            discard();
            return;
        }

        if (!level().isClientSide) {
            serverPreTickSafety();

            // 1) update async brain (non-blocking)
            pumpAsyncPlanner();

            // 2) main-thread physics step
            PhysicsStep step = stepPhysics();

            // 3) apply to entity/contraption (main-thread)
            applyPhysics(step);

            // 4) net + visual sync (main-thread)
            syncHeading(step.vel());
            sendPreciseMotion(step.pos(), step.vel());
            spawnSmokeIfNeeded(step.pos(), step.vel());

            // 5) phase transitions & impact
            updatePhase(step.pos());
        }

        super.tick();
    }

    private Vec3 computeAccel() {
        Vec3 desiredVel = computeDesiredVelocity(position());

        double gain = (phase == FlightPhase.ASCENT) ? GAIN_ASCENT : GAIN_CRUISE;
        return desiredVel.subtract(vel).scale(gain);
    }
    private PhysicsStep stepPhysics() {
        // Compute accel ONCE so behavior doesn't change vs single-step
        Vec3 aTick = computeAccel(); // blocks/tick^2

        final int substeps = 6;          // 4/6/8
        final double dt = 1.0 / substeps;

        Vec3 pos = position();
        Vec3 v = vel;

        for (int i = 0; i < substeps; i++) {
            // pos += v*dt + 0.5*a*dt^2
            pos = pos.add(v.scale(dt)).add(aTick.scale(0.5 * dt * dt));
            // v += a*dt
            v = v.add(aTick.scale(dt));
        }

        // cap speed
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
                net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> this),
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
            spawnSmokeClient( pos, v);
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
            try { latestCmd = cmdFuture.getNow(MissileAutopilotPlanner.Command.NONE); }
            catch (Exception ignored) {}
            cmdFuture = null;
        }

        // Launch a new job periodically
        if (cmdFuture == null && (t % 2 == 0)) {
            var snap = new MissileAutopilotPlanner.Snapshot(
                    this.position(), // main thread read
                    this.vel,
                    switch (this.phase) {
                        case ASCENT -> MissileAutopilotPlanner.Phase.ASCENT;
                        case CRUISE -> MissileAutopilotPlanner.Phase.CRUISE;
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
}