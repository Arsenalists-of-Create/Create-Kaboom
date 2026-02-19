package com.happysg.kaboom.async;

import net.minecraft.world.phys.Vec3;

public final class MissileAutopilotPlanner {

    private MissileAutopilotPlanner() {}

    public record Snapshot(Vec3 pos, Vec3 vel, Phase phase, Vec3 target) {}

    /** Keep this enum local so the async module doesn't need your entity class. */
    public enum Phase { ASCENT, CRUISE, IMPACT }

    public record Config(double ascentSpeed, double cruiseSpeed) {}

    public record Command(Vec3 desiredVel) {
        public static final Command NONE = new Command(Vec3.ZERO);
    }

    /** Pure math. Safe to run async. */
    public static Command plan(Snapshot s, Config cfg) {
        if (s.phase == Phase.IMPACT) return Command.NONE;

        Vec3 toTarget = s.target.subtract(s.pos);
        Vec3 dir = toTarget.lengthSqr() < 1e-10 ? Vec3.ZERO : toTarget.normalize();

        double speed = (s.phase == Phase.ASCENT) ? cfg.ascentSpeed : cfg.cruiseSpeed;

        Vec3 desiredVel = (s.phase == Phase.ASCENT)
                ? new Vec3(0, speed, 0)
                : dir.scale(speed);

        return new Command(desiredVel);
    }
}