package com.happysg.kaboom.block.missiles.util;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;

import javax.annotation.Nullable;

public record MissileGuidanceData(
        MissileGuidanceType guidanceType,
        MissileTargetSpec target,
        MissileFlightProfile profile,
        @Nullable BlockPos networkControllerPos,
        @Nullable BlockPos radarGuidancePos
) {

    public MissileGuidanceData(MissileTargetSpec target, MissileFlightProfile profile) {
        this(MissileGuidanceType.GPS, target, profile, null, null);
    }

    public static MissileGuidanceData command(BlockPos networkControllerPos, MissileFlightProfile profile) {
        return new MissileGuidanceData(MissileGuidanceType.COMMAND, null, profile,
                networkControllerPos == null ? null : networkControllerPos.immutable(), null);
    }

    public static MissileGuidanceData radar(BlockPos radarGuidancePos, MissileFlightProfile profile) {
        return new MissileGuidanceData(MissileGuidanceType.RADAR, null, profile, null,
                radarGuidancePos == null ? null : radarGuidancePos.immutable());
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("GuidanceType", guidanceType.name());
        if (target != null) {
            tag.put("Target", target.toTag());
        }
        tag.put("Profile", (profile == null ? MissileFlightProfile.defaults() : profile).toTag());
        if (networkControllerPos != null) {
            tag.put("NetworkControllerPos", NbtUtils.writeBlockPos(networkControllerPos));
        }
        if (radarGuidancePos != null) {
            tag.put("RadarGuidancePos", NbtUtils.writeBlockPos(radarGuidancePos));
        }
        return tag;
    }

    public static MissileGuidanceData fromTag(CompoundTag tag) {
        MissileGuidanceType type = tag.contains("GuidanceType")
                ? MissileGuidanceType.fromName(tag.getString("GuidanceType"))
                : MissileGuidanceType.GPS;
        MissileTargetSpec t = tag.contains("Target") ? MissileTargetSpec.fromTag(tag.getCompound("Target")) : null;
        MissileFlightProfile p = tag.contains("Profile")
                ? MissileFlightProfile.fromTag(tag.getCompound("Profile"))
                : MissileFlightProfile.defaults();
        BlockPos networkControllerPos = tag.contains("NetworkControllerPos")
                ? NbtUtils.readBlockPos(tag, "NetworkControllerPos").orElse(null)
                : null;
        BlockPos radarGuidancePos = tag.contains("RadarGuidancePos")
                ? NbtUtils.readBlockPos(tag, "RadarGuidancePos").orElse(null)
                : null;
        return new MissileGuidanceData(type, t, p, networkControllerPos, radarGuidancePos);
    }
}
