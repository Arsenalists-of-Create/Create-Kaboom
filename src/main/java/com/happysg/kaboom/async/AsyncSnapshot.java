package com.happysg.kaboom.async;

import net.minecraft.world.phys.Vec3;

public record AsyncSnapshot() {
    public record MissileSnapshot(Vec3 pos, Vec3 vel, int lifeTicks) {}
    public record TargetSnapshot(Vec3 pos, Vec3 vel, boolean valid) {}

    public record GuidanceCommand(Vec3 desiredAccel) {
        public static final GuidanceCommand NONE = new GuidanceCommand(Vec3.ZERO);
    }
}
