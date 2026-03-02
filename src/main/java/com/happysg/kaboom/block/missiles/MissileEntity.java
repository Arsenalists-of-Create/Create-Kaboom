package com.happysg.kaboom.block.missiles;


import com.happysg.kaboom.block.missiles.nav.MissileNavStack;
import com.happysg.kaboom.block.missiles.util.*;
import com.happysg.kaboom.compat.vs2.VS2Utils;
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
import net.minecraftforge.network.PacketDistributor;
import org.joml.Quaternionf;
import org.joml.Vector3dc;
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
import java.io.BufferedWriter;
import java.nio.file.Path;
import java.util.*;

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

    private static final double MAX_SPEED = 10;
    private static final double MAX_THRUST_ACCEL = 0.5;   
    private static final int BURN_MB_PER_TICK_AT_FULL = 1; 
    private static final double BOUNCE_RESTITUTION = 0.35; 
    private static final int SUBSTEPS = 20;

    private boolean latchedInGround = false;
    @Nullable
    private Vec3 resolvedPosThisTick = null;
    private final Set<Long> forcedChunks = new HashSet<>();
    private static final int CHUNK_RADIUS = 1;
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
    @Nullable
    private Vec3 pendingVelocity = null;
    private boolean forceCustomColliders = true;
    private final MissileNavStack navStack = new MissileNavStack();
    @javax.annotation.Nullable
    private BlockPos navTargetPos = null;
    private List<AABB> customColliders = List.of();
    private Direction.Axis forwardAxis = Direction.Axis.Y;
    private int forwardSign = +1; 

    
    private BlockPos noseLocal = BlockPos.ZERO;


    
    private static final double NOSE_TIP_AHEAD = 0.55;
    private boolean launched = false;


    public MissileEntity(EntityType<?> type, Level level) {
        super(type, level);
    }


    public void initFromAssembly(Contraption contraption, BlockPos controllerPos, BlockPos warheadLocalPos) {
        setPos(VS2Utils.getWorldPos(level(),controllerPos).getCenter());

        setContraption(contraption);
        setNoGravity(false);
        this.entityData.set(GRAVITY, -0.08f); 
        startAtInitialYaw();
        
        this.warheadpos = warheadLocalPos;
        

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

        
        this.capPos = BlockPos.ZERO;
        if (this.contraption != null && !this.contraption.getBlocks().isEmpty()) {
            BlockPos best = BlockPos.ZERO;
            for (BlockPos p : this.contraption.getBlocks().keySet()) {
                if (p.getY() > best.getY()) best = p;
            }
            this.capPos = best;
        }
        rebuildCustomColliders(0.40); 
        enforceCustomColliders();

        Vector3dc vector3dc =  VS2Utils.getVelocity(level(),controllerPos);
        if(vector3dc == null){
            setContraptionMotion(Vec3.ZERO);
            super.setDeltaMovement(Vec3.ZERO);
        } else {
            Vec3 velocity = new Vec3(vector3dc.x(), vector3dc.y(), vector3dc.z());
            setContraptionMotion(velocity);
            super.setDeltaMovement(velocity);
        }


        if (contraption instanceof MissileContraption mc && mc.guidanceTag != null && !mc.guidanceTag.isEmpty()) {
            MissileGuidanceData data = MissileGuidanceData.fromTag(mc.guidanceTag);
            buildInitialNavStack(data);
        } else {
            return;
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
            if (!launched && getFuelMbSynced() > 0) { 
                launched = true;
                engineSound = new MissileEngineSound(this);
                Minecraft.getInstance().getSoundManager().play(engineSound);
            }
        }
        if (level().isClientSide) {
            clientTickVisuals();
            if (!spawnedThrusterParticle) {
                spawnedThrusterParticle = true;
                navStack.setBoostAndCruiseHeights(position().y + 20, position().y + 400, level());

                float back = 1.2f;
                float up = 0.0f;
                float right = 0.0f;

                Vec3 p = position(); 
                level().addParticle(
                        new MissileAttachedParticleOptions(getId(), back, up, right), true,
                        p.x, p.y, p.z,
                        0, 0, 0
                );
            }
            super.tick();
            return;
        }

        serverTickMovement(); 
    }

    private void serverTickMovement() {
        fallDistance = 0;
        hasImpulse = true;

        tickChunkLoading();

        
        this.noPhysics = true;
        this.setNoGravity(false);

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
        final Vec3 vel0 = getDeltaMovement();

        
        MissileNavStack.NavOut navCmd = null;
        if (!navStack.isEmpty() && navTargetPos != null) {
            navCmd = navStack.tick(level(), pos0, vel0, navTargetPos);
        }

        
        if (navCmd == null && navTargetPos != null) {
            navCmd = computeTerminalNav(pos0, vel0, navTargetPos);
        }

        
        if ((tickCount % 5) == 0) {
            if (navCmd != null) {
                LOGGER.warn("[MISSILE] t={} pos={} v={} speed={} thr={} phase={} dbg={}",
                        tickCount, pos0, vel0,
                        String.format(Locale.ROOT, "%.3f", vel0.length()),
                        String.format(Locale.ROOT, "%.2f", navCmd.throttle()),
                        "NAV",
                        navCmd.dbg()
                );
            } else {
                LOGGER.warn("[MISSILE] t={} pos={} v={} speed={} thr={} phase={} dbg={}",
                        tickCount, pos0, vel0,
                        String.format(Locale.ROOT, "%.3f", vel0.length()),
                        "0.00",
                        "BALLISTIC",
                        "no_nav_no_target"
                );
            }
        }

        
        Vec3 aBase = getForcesWithParam(vel0);

        Vec3 aCtrl = (navCmd != null)
                ? computeControlAccelNav(navCmd.thrustDir(), navCmd.throttle())
                : Vec3.ZERO;

        Vec3 aTick = aBase.add(aCtrl);

        
        PhysicsStep step = integrateSubsteps(pos0, vel0, aTick, SUBSTEPS);
        Vec3 posPred = step.pos();
        Vec3 velPred = step.vel();

        
        move(MoverType.SELF, posPred.subtract(pos0));
        final Vec3 pos1 = position();

        
        Vec3 velNext = clampSpeed(velPred, MAX_SPEED);
        setContraptionMotion(velNext);
        super.setDeltaMovement(velNext);

        
        resolvedPosThisTick = null;
        tickCBCImpacts(pos0, pos1);
        tickWarhead();

        if (resolvedPosThisTick != null) {
            setPos(resolvedPosThisTick.x, resolvedPosThisTick.y, resolvedPosThisTick.z);
            
            return;
        }

        Vec3 headingVec =
                (navCmd != null && navCmd.aimDir() != null && navCmd.aimDir().lengthSqr() > 1e-8)
                        ? navCmd.aimDir()
                        : velNext;


        syncHeading(headingVec);
        sendPreciseMotion(pos1, velNext);
    }



    private MissileNavStack.NavOut computeTerminalNav(Vec3 pos, Vec3 vel, BlockPos target) {
        Vec3 tgt = Vec3.atCenterOf(target);
        Vec3 r = tgt.subtract(pos);
        double dist = r.length();

        double speed = vel.length();

        if (dist < 1e-6) {
            Vec3 dir = speed > 1e-6 ? vel.normalize() : new Vec3(0, 1, 0);
            return new MissileNavStack.NavOut(dir, dir, 0.0, true, "terminal:at_target");
        }

        Vec3 rHat = r.scale(1.0 / dist);

        final double NO_CORRECT_DIST = 10;
        if (dist <= NO_CORRECT_DIST) {
            Vec3 vHat = (speed > 1e-6) ? vel.scale(1.0 / speed) : rHat;

            
            
            return new MissileNavStack.NavOut(
                    vHat,   
                    vHat,   
                    0.0,    
                    true,   
                    "terminal:nocorrect5"
            );
        }
        
        final double aMax = MAX_THRUST_ACCEL; 
        final double vMax = MAX_SPEED;        

        
        double vSafe = Math.sqrt(Math.max(0.0, 2.0 * aMax * dist));
        double vDes = Math.min(vMax, 0.80 * vSafe);     
        vDes = Math.max(vDes, 1.0);                     

        Vec3 vDesVec = rHat.scale(vDes);

        
        final double tau = 6.0;
        Vec3 aCmd = vDesVec.subtract(vel).scale(1.0 / tau);

        
        if (speed > vDes + 0.25) {
            Vec3 vHat = speed > 1e-6 ? vel.scale(1.0 / speed) : rHat;
            Vec3 brakeBias = vHat.scale(-0.85).add(rHat.scale(0.15)); 
            if (brakeBias.lengthSqr() > 1e-10) {
                aCmd = brakeBias.normalize().scale(aMax);
            }
        }

        
        double aMag = aCmd.length();
        if (aMag < 1e-8) {
            return new MissileNavStack.NavOut(rHat, rHat, 0.0, dist < 1.5 && speed < 1.5, "terminal:zero");
        }
        if (aMag > aMax) {
            aCmd = aCmd.scale(aMax / aMag);
            aMag = aMax;
        }

        Vec3 thrustDir = aCmd.scale(1.0 / aMag);
        double throttle = aMag / aMax;

        boolean done = dist < 1.5 && speed < 1.5; 
        String dbg = String.format(Locale.ROOT, "terminal:vDes=%.2f dist=%.1f", vDes, dist);

        return new MissileNavStack.NavOut(thrustDir, rHat, throttle, done, dbg);
    }
    private void buildInitialNavStack(MissileGuidanceData data) {
        navTargetPos = null;

        MissileTargetSpec t = data.target();
        if (t == null || t.type() != MissileTargetSpec.TargetType.POINT) {
            return;
        }

        Vec3 tgt = t.point();
        if (tgt == null) return;

        navTargetPos = BlockPos.containing(tgt);

        
        Vec3 launch = position();

        double boostY  = launch.y + 120.0;
        double cruiseY = launch.y + 400;

        navStack.setBoostAndCruiseHeights(boostY, cruiseY, level());

        LOGGER.warn("[MISSILE] NAV heights: launchY={} boostY={} cruiseY={}",
                String.format(Locale.ROOT, "%.2f", launch.y),
                String.format(Locale.ROOT, "%.2f", boostY),
                String.format(Locale.ROOT, "%.2f", cruiseY));
    }

    protected Vec3 getForcesWithParam(Vec3 velocity) {
        
        
        double g = this.isNoGravity() ? 0.0
                : this.entityData.get(GRAVITY) *
                DimensionMunitionPropertiesHandler.getProperties(this.level()).gravityMultiplier();

        return new Vec3(0.0, g, 0.0);
    }

    private int getFuelMbSynced() {
        return entityData.get(FUEL_MB);
    }

    private void freezeInPlace() {
        setContraptionMotion(Vec3.ZERO);
        this.noPhysics = true;
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
        
        Vec3 now = toGlobalVector(Vec3.atCenterOf(localBlock), 0);

        
        Vec3 delta = entityPos.subtract(this.position());
        Vec3 base = now.add(delta);

        
        return base.add(worldDirUnit.scale(ahead));
    }

    private BlockPos pickLeadingLocal(Vec3 worldDirUnit) {
        if (contraption == null || contraption.getBlocks().isEmpty())
            return BlockPos.ZERO;

        double best = -Double.MAX_VALUE;
        BlockPos bestPos = BlockPos.ZERO;

        for (BlockPos lp : contraption.getBlocks().keySet()) {
            Vec3 wp = toGlobalVector(Vec3.atCenterOf(lp), 0); 
            double score = wp.dot(worldDirUnit);
            if (score > best) {
                best = score;
                bestPos = lp;
            }
        }
        return bestPos;
    }

    private Vec3 computeControlAccelNav(Vec3 desiredDirRaw, double throttleRaw) {
        if (desiredDirRaw == null || desiredDirRaw.lengthSqr() < 1e-8) return Vec3.ZERO;

        Vec3 desiredDir = desiredDirRaw.normalize();

        double throttle = Mth.clamp(throttleRaw, 0.0, 1.0);
        if (fuelMb <= 0) throttle = 0.0;

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

        return desiredDir.scale(MAX_THRUST_ACCEL * throttle);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(HEADING_X, 0f);
        entityData.define(HEADING_Y, 1f);
        entityData.define(HEADING_Z, 0f);
        entityData.define(FUEL_MB, 0);
        entityData.define(FUEL_CAP_MB, 0);
        entityData.define(GRAVITY, -0.08f);
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

        
        forcedChunks.removeIf(key -> {
            if (wanted.contains(key)) return false;
            ChunkPos p = new ChunkPos(key);
            sl.setChunkForced(p.x, p.z, false);
            return true;
        });
    }

    @Override
    public void applyLocalTransforms(PoseStack stack, float partialTicks) {
        
        Vector3f toDir = new Vector3f(
                entityData.get(HEADING_X),
                entityData.get(HEADING_Y),
                entityData.get(HEADING_Z)
        );

        if (toDir.lengthSquared() < 1e-12f)
            toDir.set(0, 1, 0);
        else
            toDir.normalize();

        
        Vector3f fromUp = new Vector3f(0, 1, 0);

        Quaternionf q = new Quaternionf().rotationTo(fromUp, toDir);

        
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
            minX = Math.min(minX, p.getX());
            maxX = Math.max(maxX, p.getX());
            minY = Math.min(minY, p.getY());
            maxY = Math.max(maxY, p.getY());
            minZ = Math.min(minZ, p.getZ());
            maxZ = Math.max(maxZ, p.getZ());
        }

        int spanX = maxX - minX;
        int spanY = maxY - minY;
        int spanZ = maxZ - minZ;

        
        if (spanX >= spanY && spanX >= spanZ) forwardAxis = Direction.Axis.X;
        else if (spanY >= spanX && spanY >= spanZ) forwardAxis = Direction.Axis.Y;
        else forwardAxis = Direction.Axis.Z;

        
        
        int maxAbsPos, minAbsPos;
        switch (forwardAxis) {
            case X -> {
                maxAbsPos = Math.abs(maxX);
                minAbsPos = Math.abs(minX);
                forwardSign = (maxAbsPos >= minAbsPos) ? (maxX >= 0 ? +1 : -1) : (minX >= 0 ? +1 : -1);
            }
            case Y -> {
                maxAbsPos = Math.abs(maxY);
                minAbsPos = Math.abs(minY);
                forwardSign = (maxAbsPos >= minAbsPos) ? (maxY >= 0 ? +1 : -1) : (minY >= 0 ? +1 : -1);
            }
            case Z -> {
                maxAbsPos = Math.abs(maxZ);
                minAbsPos = Math.abs(minZ);
                forwardSign = (maxAbsPos >= minAbsPos) ? (maxZ >= 0 ? +1 : -1) : (minZ >= 0 ? +1 : -1);
            }
        }

        
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

        
        this.capPos = noseLocal;

        LOGGER.warn("[MISSILE AXIS] axis={} sign={} noseLocal={} warheadLocal={}",
                forwardAxis, forwardSign, noseLocal, warheadpos);
    }

    private void enforceCustomColliders() {
        if (!forceCustomColliders) return;
        if (contraption == null) return;

        
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


    private PhysicsStep integrateSubsteps(Vec3 pos0, Vec3 vel0, Vec3 aTick, int substeps) {
        if (substeps <= 1) {
            Vec3 vel1 = vel0.add(aTick);              
            Vec3 pos1 = pos0.add(vel1);               
            return new PhysicsStep(pos1, vel1);
        }

        double dt = 1.0 / substeps;

        Vec3 pos = pos0;
        Vec3 vel = vel0;

        for (int i = 0; i < substeps; i++) {
            
            vel = vel.add(aTick.scale(dt));

            
            pos = pos.add(vel.scale(dt));
        }

        return new PhysicsStep(pos, vel);
    }

    private static Vec3 clampSpeed(Vec3 v, double max) {
        double sp = v.length();
        if (sp > max && sp > 1e-9) return v.scale(max / sp);
        return v;
    }

    private static Vec3 reflectVelocity(Vec3 velocity, Vec3 surfaceNormal, double restitution) {
        if (surfaceNormal.lengthSqr() < 1e-12) return velocity;
        Vec3 n = surfaceNormal.normalize();
        double vn = velocity.dot(n);
        if (vn >= 0.0) return velocity; 
        double e = Mth.clamp(restitution, 0.0, 1.0);
        return velocity.subtract(n.scale((1.0 + e) * vn));
    }

    @OnlyIn(Dist.CLIENT)
    private void clientTickVisuals() {
        
        if (!fuelDepleted) spawnSmokeClient(position(), getDeltaMovement());

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

        }


        super.remove(reason);
    }


    
    
    
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

            cl.addParticle(ModParticles.MISSILE_SMOKE.get(), true, x, y, z, motion.x, motion.y, motion.z);
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

        
        entityData.set(FUEL_MB, fuelMb);
        entityData.set(FUEL_CAP_MB, fuelCapacityMb);

        
        if (fuelMb == 0 && before > 0) {
            LOGGER.warn("[MISSILE] Fuel depleted at tick {}", level().getGameTime());
        }
    }


    






















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

        
        Vec3 entDisp = newPos.subtract(oldPos);
        if (entDisp.lengthSqr() < 1e-10) return;

        Vec3 worldDirUnit = entDisp.normalize();
        BlockPos leadLocal = pickLeadingLocal(worldDirUnit);

        Vec3 start = tipWorldAtEntityPos(oldPos, leadLocal, worldDirUnit, NOSE_TIP_AHEAD);
        Vec3 end = tipWorldAtEntityPos(newPos, leadLocal, worldDirUnit, NOSE_TIP_AHEAD);
        Vec3 entityToNose0 = start.subtract(oldPos);
        Vec3 disp0 = end.subtract(start);
        if (disp0.lengthSqr() < 1.0e-10) {
            return;
        }

        
        final double r = 0.35; 
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

            
            Direction missDir = Direction.getNearest(dirUnit.x, dirUnit.y, dirUnit.z);
            return BlockHitResult.miss(segEnd, missDir, BlockPos.containing(segEnd));
        };

        int maxIter = 20;
        boolean shouldRemove = false;
        boolean stop = false;

        Vec3 vel0 = getDeltaMovement();
        Vec3 accel = getForces(vel0);
        Vec3 traj = vel0.add(accel);

        
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

            
            BlockHitResult blockHit = clipCapsule.apply(start, end);

            Vec3 hitEnd = end;
            if (blockHit.getType() != HitResult.Type.MISS) {
                hitEnd = blockHit.getLocation();

            }

            
            if (i == 0) {
                
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

            
            if (onClip(ctx, start, hitEnd)) {
                shouldRemove = true;
                break;
            }

            
            AABB movementRegion = noseBox.expandTowards(disp).inflate(1);
            for (Entity target : level().getEntities(this, movementRegion)) {
                if (ctx.hasHitEntity(target)) continue;
                AABB bb = target.getBoundingBox();
                if (bb.intersects(noseBox) || bb.inflate(reach).clip(start, hitEnd).isPresent())
                    ctx.addEntity(target);
            }


            
            if (blockHit.getType() != HitResult.Type.MISS) {
                BlockPos bp = blockHit.getBlockPos().immutable();
                BlockState hitState = level().getChunkAt(bp).getBlockState(bp);


                AbstractCannonProjectile.ImpactResult result = calculateBlockPenetration(ctx, hitState, blockHit);


                double totalNose = start.distanceTo(end);
                double usedNose = start.distanceTo(hitEnd);
                double usedFrac = (totalNose <= 1e-9) ? 0.0 : Mth.clamp(usedNose / totalNose, 0.0, 1.0);


                Vec3 entDir = entDisp.lengthSqr() > 1e-10 ? entDisp.normalize() : segDirUnit;

                Vec3 backOffEnt = entDir.scale(0.05);
                Vec3 snappedEntityPos = oldPos.add(entDisp.scale(usedFrac)).subtract(backOffEnt);

                switch (result.kinematics()) {
                    case PENETRATE -> {
                        lastPenetratedBlock = hitState;
                        penetrationTime = 2;

                        double used = start.distanceTo(hitEnd);
                        double total = start.distanceTo(end);
                        double fracLeft = (total <= 1.0e-6) ? 0.0 : Math.max(0.0, (total - used) / total);

                        
                        start = hitEnd;
                        end = hitEnd.add(segDirUnit.scale((total - used))); 
                    }
                    case STOP -> {
                        resolvedPosThisTick = snappedEntityPos;
                        pendingVelocity = null;
                        lastPenetratedBlock = hitState;
                        penetrationTime = 2;
                        stop = true;
                    }
                    case BOUNCE -> {
                        resolvedPosThisTick = snappedEntityPos;

                        Vec3 normal = CBCUtils.getSurfaceNormalVector(this.level(), blockHit);
                        pendingVelocity = reflectVelocity(traj, normal, BOUNCE_RESTITUTION);

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

        var acc = (FuzeMixin) fuzed; 
        ItemStack fuzeStack = acc.getFuze();
        boolean baseFuze = acc.invokeGetFuzeProperties().baseFuze();

        
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
            
            return false;
        }

        if (warhead != null) {
            
            ProjectileContext wctx = new ProjectileContext(warhead, CBCConfigs.server().munitions.damageRestriction.get());
            for (Entity e : ctx.hitEntities()) wctx.addEntity(e);

            

            ((AbstractProjectileAccessor) warhead).invokeImpact(
                    new EntityHitResult(entity),
                    new AbstractCannonProjectile.ImpactResult(
                            AbstractCannonProjectile.ImpactResult.KinematicOutcome.PENETRATE,
                            warhead.getProjectileMass() <= 0
                    ),
                    wctx
            );

            
            EntityDamagePropertiesComponent props = warhead.getDamageProperties();
            if (props != null) {
                entity.setDeltaMovement(getDeltaMovement().scale(props.knockback()));

                DamageSource source = indirectArtilleryFire(props.ignoresEntityArmor());

                if (props.ignoresInvulnerability()) entity.invulnerableTime = 0;
                entity.hurt(source, props.entityDamage());
                if (!props.rendersInvulnerable()) entity.invulnerableTime = 0;
            }
        }


        
        return this.onImpact(
                new EntityHitResult(entity),
                new AbstractCannonProjectile.ImpactResult(AbstractCannonProjectile.ImpactResult.KinematicOutcome.PENETRATE, false),
                ctx
        );
    }

    protected DamageSource getDamage() {
        boolean bypassesArmor = false;
        if (warhead != null) bypassesArmor = warhead.getDamageProperties().ignoresEntityArmor();
        return new CannonDamageSource(CannonDamageSource.getDamageRegistry(this.level()).getHolderOrThrow(CBCDamageTypes.CANNON_PROJECTILE), bypassesArmor);
    }

    protected void tickWarhead() {
        if (this.contraption == null || this.warhead == null || this.warheadpos == null) {

            return;
        }

        
        if (!(this.warhead instanceof FuzedBigCannonProjectile fuzed))
            return;

        
        fuzed.setPos(this.toGlobalVector(Vec3.atCenterOf(warheadpos), 0));
        fuzed.setDeltaMovement(this.getDeltaMovement());

        
        FuzeMixin acc = (FuzeMixin) fuzed;
        if (acc.invokeCanDetonate(fz -> fz.onProjectileTick(acc.getFuze(), fuzed))) {
            this.detonate(warheadpos, fuzed);
            LOGGER.warn("explosion discard");
            fuzed.discard();
            this.warhead = null;
            this.warheadpos = null;
        }
    }


    protected Vec3 getForces(Vec3 velocity) {
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
            this.pendingVelocity = reflectVelocity(curVel, normal, BOUNCE_RESTITUTION);
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
                effectNormal = reflectVelocity(curVel, normal, BOUNCE_RESTITUTION);
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