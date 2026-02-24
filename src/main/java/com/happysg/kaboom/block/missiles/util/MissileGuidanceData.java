package com.happysg.kaboom.block.missiles.util;

import net.minecraft.nbt.CompoundTag;

public record MissileGuidanceData(MissileTargetSpec target, MissileFlightProfile profile) {

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.put("Target", target.toTag());
        tag.put("Profile", profile.toTag());
        return tag;
    }

    public static MissileGuidanceData fromTag(CompoundTag tag) {
        MissileTargetSpec t = MissileTargetSpec.fromTag(tag.getCompound("Target"));
        MissileFlightProfile p = MissileFlightProfile.fromTag(tag.getCompound("Profile"));
        return new MissileGuidanceData(t, p);
    }
}