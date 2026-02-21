package com.happysg.kaboom.block.missiles.parts.guidance;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public record MissileTargetSpec(TargetType type, Vec3 point, UUID entityId, boolean highArc) {

    public enum TargetType { POINT, ENTITY }

    public static MissileTargetSpec point(Vec3 p, boolean highArc) {
        return new MissileTargetSpec(TargetType.POINT, p, null, highArc);
    }

    public static MissileTargetSpec entity(UUID id) {
        return new MissileTargetSpec(TargetType.ENTITY, null, id, false);
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", type.name());
        tag.putBoolean("HighArc", highArc);

        if (type == TargetType.POINT && point != null) {
            tag.putDouble("X", point.x);
            tag.putDouble("Y", point.y);
            tag.putDouble("Z", point.z);
        }

        if (type == TargetType.ENTITY && entityId != null) {
            tag.putUUID("Entity", entityId);
        }

        return tag;
    }

    public static MissileTargetSpec fromTag(CompoundTag tag) {
        TargetType type = TargetType.valueOf(tag.getString("Type"));
        boolean highArc = tag.getBoolean("HighArc");

        if (type == TargetType.POINT) {
            Vec3 p = new Vec3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z"));
            return point(p, highArc);
        }

        UUID id = tag.hasUUID("Entity") ? tag.getUUID("Entity") : null;
        return entity(id);
    }
}