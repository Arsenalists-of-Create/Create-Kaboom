package com.happysg.kaboom.client;

import com.happysg.kaboom.block.missiles.MissileEntity;
import com.happysg.kaboom.block.missiles.assembly.MissileSize;
import com.happysg.kaboom.block.missiles.parts.thrust.ThrusterBlockEntity;
import com.happysg.kaboom.sounds.BlockMissileLaunchSound;
import com.happysg.kaboom.sounds.MissileEngineSound;
import com.happysg.kaboom.sounds.MissileLaunchSound;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;

@OnlyIn(Dist.CLIENT)
public final class MissileClientEffects {
    private static final Map<Integer, MountedLaunch> MOUNTED_LAUNCHES = new HashMap<>();
    private static final Map<net.minecraft.core.BlockPos, BlockMissileLaunchSound> BLOCK_LAUNCHES = new HashMap<>();
    private static final Map<Integer, MissileEngineSound> FLIGHT_SOUNDS = new HashMap<>();
    private static final Map<Integer, PendingHandoff> PENDING_HANDOFFS = new HashMap<>();
    private static final Set<Integer> HANDED_OFF_MISSILES = new HashSet<>();
    private MissileClientEffects() {
    }

    public static void startEngineSound(MissileEntity missile) {
        if (HANDED_OFF_MISSILES.contains(missile.getId()) || PENDING_HANDOFFS.containsKey(missile.getId())) return;
        MissileEngineSound active = FLIGHT_SOUNDS.get(missile.getId());
        if (active != null && !active.isStopped()) return;
        playNewFlightSound(missile);
    }

    public static void queueBlockHandoff(BlockPos sourcePos, int targetEntityId) {
        PENDING_HANDOFFS.put(targetEntityId, new PendingHandoff(sourcePos.immutable(), -1, 20));
    }

    public static void queueMountedHandoff(int sourceEntityId, int targetEntityId) {
        PENDING_HANDOFFS.put(targetEntityId, new PendingHandoff(null, sourceEntityId, 20));
    }

    public static void startBlockLaunch(ThrusterBlockEntity thruster, int ticksRemaining) {
        BLOCK_LAUNCHES.entrySet().removeIf(entry -> entry.getValue().isStopped());
        net.minecraft.core.BlockPos key = thruster.getBlockPos().immutable();
        if (BLOCK_LAUNCHES.containsKey(key)) return;
        BlockMissileLaunchSound sound = new BlockMissileLaunchSound(thruster);
        BLOCK_LAUNCHES.put(key, sound);
        Minecraft.getInstance().getSoundManager().play(sound);
    }

    public static void startMountedLaunch(PitchOrientedContraptionEntity entity, Vec3 localNozzle,
                                          Vec3 localForward, MissileSize size, int ticksRemaining) {
        MountedLaunch current = MOUNTED_LAUNCHES.get(entity.getId());
        if (current != null && current.entity == entity && current.ticksRemaining > 0) return;
        MissileLaunchSound sound = new MissileLaunchSound(entity, localNozzle, ticksRemaining);
        Minecraft.getInstance().getSoundManager().play(sound);
        MOUNTED_LAUNCHES.put(entity.getId(), new MountedLaunch(entity, localNozzle, localForward,
                size, Math.max(1, ticksRemaining), sound));
    }

    public static void tickMountedLaunches() {
        resolveHandoffs();
        BLOCK_LAUNCHES.entrySet().removeIf(entry -> entry.getValue().isStopped());
        FLIGHT_SOUNDS.entrySet().removeIf(entry -> entry.getValue().isStopped());
        MOUNTED_LAUNCHES.values().removeIf(launch -> {
            if (launch.entity.isRemoved() || !launch.entity.isAlive() || launch.ticksRemaining <= 0) {
                return launch.sound.isStopped();
            }
            if (launch.entity.level() instanceof ClientLevel level) {
                Vec3 nozzle = launch.entity.toGlobalVector(launch.localNozzle, 0);
                Vec3 forwardPoint = launch.entity.toGlobalVector(launch.localNozzle.add(launch.localForward), 0);
                Vec3 worldForward = forwardPoint.subtract(nozzle);
                Vec3 thrusterCenter = worldForward.lengthSqr() < 1.0E-8
                        ? nozzle : nozzle.add(worldForward.normalize().scale(0.55));
                MissileLaunchEffects.emit(level, thrusterCenter, worldForward,
                        launch.entity.getDeltaMovement(), launch.size);
            }
            --launch.ticksRemaining;
            return false;
        });
    }

    private static void resolveHandoffs() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        HANDED_OFF_MISSILES.removeIf(id -> !(level.getEntity(id) instanceof MissileEntity));
        PENDING_HANDOFFS.entrySet().removeIf(entry -> {
            PendingHandoff pending = entry.getValue();
            if (!(level.getEntity(entry.getKey()) instanceof MissileEntity missile)) {
                return --pending.retries <= 0;
            }
            BlockMissileLaunchSound blockSound = pending.sourcePos == null
                    ? null : BLOCK_LAUNCHES.remove(pending.sourcePos);
            MountedLaunch mounted = pending.sourceEntityId < 0
                    ? null : MOUNTED_LAUNCHES.remove(pending.sourceEntityId);
            boolean handedOff = false;
            if (blockSound != null && !blockSound.isStopped()) {
                blockSound.handoff(missile);
                handedOff = true;
            } else if (mounted != null && !mounted.sound.isStopped()) {
                mounted.sound.handoff(missile);
                handedOff = true;
            }
            MissileEngineSound replacement = FLIGHT_SOUNDS.remove(missile.getId());
            if (replacement != null && handedOff) replacement.stopSound();
            if (handedOff) HANDED_OFF_MISSILES.add(missile.getId());
            else playNewFlightSound(missile);
            return true;
        });
    }

    private static void playNewFlightSound(MissileEntity missile) {
        MissileEngineSound sound = new MissileEngineSound(missile);
        FLIGHT_SOUNDS.put(missile.getId(), sound);
        Minecraft.getInstance().getSoundManager().play(sound);
    }

    private static final class MountedLaunch {
        private final PitchOrientedContraptionEntity entity;
        private final Vec3 localNozzle;
        private final Vec3 localForward;
        private final MissileSize size;
        private final MissileLaunchSound sound;
        private int ticksRemaining;

        private MountedLaunch(PitchOrientedContraptionEntity entity, Vec3 localNozzle, Vec3 localForward,
                              MissileSize size, int ticksRemaining, MissileLaunchSound sound) {
            this.entity = entity;
            this.localNozzle = localNozzle;
            this.localForward = localForward;
            this.size = size;
            this.ticksRemaining = ticksRemaining;
            this.sound = sound;
        }
    }

    private static final class PendingHandoff {
        private final BlockPos sourcePos;
        private final int sourceEntityId;
        private int retries;
        private PendingHandoff(BlockPos sourcePos, int sourceEntityId, int retries) {
            this.sourcePos = sourcePos;
            this.sourceEntityId = sourceEntityId;
            this.retries = retries;
        }
    }
}
