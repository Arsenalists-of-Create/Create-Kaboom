package com.happysg.kaboom.client;

import com.happysg.kaboom.block.missiles.MissileEntity;
import com.happysg.kaboom.block.missiles.assembly.MissileSize;
import com.happysg.kaboom.networking.LaunchSoundHandoffPacket;
import com.happysg.kaboom.block.missiles.chaining.client.ChainRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;

@OnlyIn(Dist.CLIENT)
public final class ClientPacketHandlers {
    private ClientPacketHandlers() {
    }

    public static void handleChainSync(int entityId, CompoundTag chainSystemTag) {
        if (Minecraft.getInstance().level == null) {
            return;
        }
        Entity entity = Minecraft.getInstance().level.getEntity(entityId);
        if (entity instanceof MissileEntity missile) {
            missile.getChainSystem().load(chainSystemTag);
            ChainRenderer.TRACKED_MISSILES.add(entityId);
        }
    }

    public static void handlePreciseMotion(int entityId, double x, double y, double z,
                                           double dx, double dy, double dz, float yRot, float xRot,
                                           boolean onGround, int lerpSteps) {
        if (Minecraft.getInstance().level == null) {
            return;
        }
        Entity entity = Minecraft.getInstance().level.getEntity(entityId);
        if (entity != null) {
            entity.lerpTo(x, y, z, yRot, xRot, lerpSteps);
            entity.lerpMotion(dx, dy, dz);
            entity.setOnGround(onGround);
        }
    }

    public static void handleRocketPodLaunchSound(int entityId, BlockPos rearPos, int slot,
                                                   long launchId, int ticksRemaining) {
        if (Minecraft.getInstance().level == null) {
            return;
        }
        Entity entity = Minecraft.getInstance().level.getEntity(entityId);
        if (entity instanceof PitchOrientedContraptionEntity pitchEntity) {
            RocketClientEffects.startPodLaunchSound(
                    pitchEntity, rearPos, slot, launchId, ticksRemaining);
        }
    }

    public static void handleMountedMissileLaunchEffect(int entityId, Vec3 localNozzle,
                                                         Vec3 localForward, MissileSize size,
                                                         int ticksRemaining) {
        if (Minecraft.getInstance().level == null) return;
        Entity entity = Minecraft.getInstance().level.getEntity(entityId);
        if (entity instanceof PitchOrientedContraptionEntity pitchEntity) {
            MissileClientEffects.startMountedLaunch(pitchEntity, localNozzle, localForward, size, ticksRemaining);
        }
    }

    public static void handleLaunchSoundHandoff(byte kind, int sourceEntityId, BlockPos sourcePos,
                                                 int slot, long launchId, int targetEntityId) {
        if (kind == LaunchSoundHandoffPacket.FREE_MISSILE) {
            MissileClientEffects.queueBlockHandoff(sourcePos, targetEntityId);
        } else if (kind == LaunchSoundHandoffPacket.MOUNTED_MISSILE) {
            MissileClientEffects.queueMountedHandoff(sourceEntityId, targetEntityId);
        } else if (kind == LaunchSoundHandoffPacket.ROCKET_POD) {
            RocketClientEffects.queuePodHandoff(sourceEntityId, sourcePos, slot, launchId, targetEntityId);
        }
    }
}
