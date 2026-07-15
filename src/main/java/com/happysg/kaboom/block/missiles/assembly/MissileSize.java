package com.happysg.kaboom.block.missiles.assembly;

public enum MissileSize {
    SMALL(5),
    LARGE(7),
    HUGE(10);

    private final int fuelTankLaunchRejectionThreshold;

    MissileSize(int fuelTankLaunchRejectionThreshold) {
        this.fuelTankLaunchRejectionThreshold = fuelTankLaunchRejectionThreshold;
    }

    public int fuelTankLaunchRejectionThreshold() {
        return fuelTankLaunchRejectionThreshold;
    }

    public boolean isOverweight(int fuelTankCount) {
        return fuelTankCount >= fuelTankLaunchRejectionThreshold;
    }

    public double accelerationMultiplier(int fuelTankCount) {
        if (fuelTankCount <= 1) {
            return 1.0;
        }

        double multiplier = (double) (fuelTankLaunchRejectionThreshold - fuelTankCount)
                / (double) (fuelTankLaunchRejectionThreshold - 1);
        return Math.max(0.0, Math.min(1.0, multiplier));
    }
}
