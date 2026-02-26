package com.happysg.kaboom.block.missiles.util;// com.happysg.kaboom.targeting.MissileFlightProfile.java


import net.minecraft.nbt.CompoundTag;

public record MissileFlightProfile(
        // NEW: vertical boost stage (no turning)
        double boostAltitude,
        double boostTolerance,
        double boostThrottle,

        double climbAltitude,
        double climbTolerance,
        double climbThrottle,

        double pitchOverSeconds,
        double pitchOverPitchDeg,
        double pitchOverThrottle,

        double assumedSpeed,
        double arriveRadius,
        double terminalThrottle,

        boolean preferHighArc,
        int planCadenceTicks
) {
    public static MissileFlightProfile defaults() {
        return new MissileFlightProfile(
                10, 1.0, .7,     // boost straight up ~40 blocks (tune 20–80)
                128.0, 2.0, 1.0,
                1, 0.0, 1.0,
                20, 6.0, 0,
                true, 1
        );
    }

    public CompoundTag toTag() {
        CompoundTag t = new CompoundTag();

        t.putDouble("BoostAlt", boostAltitude);
        t.putDouble("BoostTol", boostTolerance);
        t.putDouble("BoostThr", boostThrottle);

        t.putDouble("ClimbAlt", climbAltitude);
        t.putDouble("ClimbTol", climbTolerance);
        t.putDouble("ClimbThr", climbThrottle);

        t.putDouble("PitchSec", pitchOverSeconds);
        t.putDouble("PitchDeg", pitchOverPitchDeg);
        t.putDouble("PitchThr", pitchOverThrottle);

        t.putDouble("AssumedSpeed", assumedSpeed);
        t.putDouble("ArriveR", arriveRadius);
        t.putDouble("TermThr", terminalThrottle);

        t.putBoolean("PreferHighArc", preferHighArc);
        t.putInt("Cadence", planCadenceTicks);
        return t;
    }

    public static MissileFlightProfile fromTag(CompoundTag t) {
        MissileFlightProfile d = defaults();
        return new MissileFlightProfile(
                t.contains("BoostAlt") ? t.getDouble("BoostAlt") : d.boostAltitude(),
                t.contains("BoostTol") ? t.getDouble("BoostTol") : d.boostTolerance(),
                t.contains("BoostThr") ? t.getDouble("BoostThr") : d.boostThrottle(),

                t.contains("ClimbAlt") ? t.getDouble("ClimbAlt") : d.climbAltitude(),
                t.contains("ClimbTol") ? t.getDouble("ClimbTol") : d.climbTolerance(),
                t.contains("ClimbThr") ? t.getDouble("ClimbThr") : d.climbThrottle(),

                t.contains("PitchSec") ? t.getDouble("PitchSec") : d.pitchOverSeconds(),
                t.contains("PitchDeg") ? t.getDouble("PitchDeg") : d.pitchOverPitchDeg(),
                t.contains("PitchThr") ? t.getDouble("PitchThr") : d.pitchOverThrottle(),

                t.contains("AssumedSpeed") ? t.getDouble("AssumedSpeed") : d.assumedSpeed(),
                t.contains("ArriveR") ? t.getDouble("ArriveR") : d.arriveRadius(),
                t.contains("TermThr") ? t.getDouble("TermThr") : d.terminalThrottle(),

                t.contains("PreferHighArc") ? t.getBoolean("PreferHighArc") : d.preferHighArc(),
                t.contains("Cadence") ? t.getInt("Cadence") : d.planCadenceTicks()
        );
    }
}