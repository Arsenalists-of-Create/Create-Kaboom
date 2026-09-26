package com.happysg.kaboom.client;

import com.happysg.kaboom.block.missiles.assembly.MissileSize;
import com.happysg.kaboom.block.missiles.util.MissileLaunchSmokeOptions;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class MissileLaunchEffects {
    private static final double BLOCKED_EXHAUST_EDGE_OFFSET = 0.58;
    private static final double BLOCKED_EXHAUST_FORWARD_INSET = 0.14;
    private static final double BLOCKED_EXHAUST_SPEED_MULTIPLIER = 1.45;

    private MissileLaunchEffects() {}

    public static void emit(ClientLevel level, Vec3 thrusterCenter, Vec3 forward, Vec3 inheritedMotion,
                            MissileSize size) {
        if (forward.lengthSqr() < 1.0E-8) return;
        Vec3 forwardUnit = forward.normalize();
        Vec3 backward = forwardUnit.scale(-1.0);
        Vec3 base = thrusterCenter.add(backward.scale(0.5));
        Vec3 rearProbeStart = base.add(backward.scale(0.01));
        Vec3 rearProbeEnd = thrusterCenter.add(backward);
        BlockHitResult rearHit = probe(level, rearProbeStart, rearProbeEnd);
        boolean rearBlocked = rearHit.getType() != HitResult.Type.MISS;
        BlockState dustState = rearBlocked ? level.getBlockState(rearHit.getBlockPos()) : null;

        for (int i = 0; i < size.launchSmokePerTick(); ++i) {
            Vec3 direction;
            Vec3 spawn;
            if (!rearBlocked) {
                direction = randomCone(level, backward, 0.11);
                spawn = base.add(direction.scale(0.08));
            } else {
                direction = clearRadialSpoke(level, base, forwardUnit, backward);
                if (direction == null) continue;
                spawn = base.add(forwardUnit.scale(BLOCKED_EXHAUST_FORWARD_INSET))
                        .add(direction.scale(BLOCKED_EXHAUST_EDGE_OFFSET));
                BlockHitResult spokeHit = probe(level, spawn, spawn.add(direction.scale(1.0)));
                if (spokeHit.getType() != HitResult.Type.MISS) {
                    dustState = level.getBlockState(spokeHit.getBlockPos());
                }
            }
            double speed = size.launchSmokeRange() / 10.0 * (0.9 + level.random.nextDouble() * 0.2);
            if (rearBlocked) speed *= BLOCKED_EXHAUST_SPEED_MULTIPLIER;
            Vec3 motion = inheritedMotion.scale(0.2).add(direction.scale(speed));
            level.addParticle(new MissileLaunchSmokeOptions(size), true,
                    spawn.x, spawn.y, spawn.z, motion.x, motion.y, motion.z);
        }

        if (dustState == null) {
            BlockHitResult downHit = probe(level, base, base.add(0.0, -2.0, 0.0));
            if (downHit.getType() != HitResult.Type.MISS) {
                dustState = level.getBlockState(downHit.getBlockPos());
            }
        }
        if (dustState != null && !dustState.isAir()) {
            for (int i = 0; i < size.launchDustPerTick(); ++i) {
                double minimumRadius = rearBlocked ? BLOCKED_EXHAUST_EDGE_OFFSET : 0.0;
                Vec3 offset = randomPlane(level, backward)
                        .scale(minimumRadius + level.random.nextDouble() * 0.3);
                Vec3 motion = offset.scale(0.12).add(0.0, 0.05 + level.random.nextDouble() * 0.06, 0.0);
                level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, dustState), true,
                        base.x + offset.x, base.y + offset.y, base.z + offset.z,
                        motion.x, motion.y, motion.z);
            }
        }
    }

    private static Vec3 clearRadialSpoke(ClientLevel level, Vec3 base, Vec3 forward, Vec3 axis) {
        for (int attempt = 0; attempt < 8; ++attempt) {
            Vec3 spoke = randomPlane(level, axis);
            Vec3 edge = base.add(forward.scale(BLOCKED_EXHAUST_FORWARD_INSET))
                    .add(spoke.scale(BLOCKED_EXHAUST_EDGE_OFFSET));
            if (probe(level, edge, edge.add(spoke.scale(0.5))).getType() == HitResult.Type.MISS) {
                return spoke;
            }
        }
        return null;
    }

    private static Vec3 randomCone(ClientLevel level, Vec3 axis, double spread) {
        Vec3 radial = randomPlane(level, axis).scale(level.random.nextDouble() * spread);
        return axis.add(radial).normalize();
    }

    private static Vec3 randomPlane(ClientLevel level, Vec3 axis) {
        Vec3 reference = Math.abs(axis.y) > 0.9 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
        Vec3 right = axis.cross(reference).normalize();
        Vec3 up = right.cross(axis).normalize();
        double angle = level.random.nextDouble() * Math.PI * 2.0;
        return right.scale(Math.cos(angle)).add(up.scale(Math.sin(angle)));
    }

    private static BlockHitResult probe(ClientLevel level, Vec3 start, Vec3 end) {
        return level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, (Entity)null));
    }
}
