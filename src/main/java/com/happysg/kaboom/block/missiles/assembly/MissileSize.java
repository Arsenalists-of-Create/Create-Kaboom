package com.happysg.kaboom.block.missiles.assembly;

public enum MissileSize {
    SMALL(8, 3, 0.36, 0.75F, 3.0, 0.16, 1.0),
    LARGE(11, 5, 0.60, 1.0F, 5.0, 0.22, 1.5),
    HUGE(15, 8, 0.96, 1.4F, 8.0, 0.30, 2.0);

    private final int fuelTankLaunchRejectionThreshold;
    private final int launchSmokePerTick;
    private final double minimumLaunchSpeed;
    private final float launchSmokeScale;
    private final double launchSmokeRange;
    private final double launchSmokeClimbSpeed;
    private final double dragMultiplier;

    MissileSize(int fuelTankLaunchRejectionThreshold, int launchSmokePerTick,
                double minimumLaunchSpeed, float launchSmokeScale, double launchSmokeRange,
                double launchSmokeClimbSpeed, double dragMultiplier) {
        this.fuelTankLaunchRejectionThreshold = fuelTankLaunchRejectionThreshold;
        this.launchSmokePerTick = launchSmokePerTick;
        this.minimumLaunchSpeed = minimumLaunchSpeed;
        this.launchSmokeScale = launchSmokeScale;
        this.launchSmokeRange = launchSmokeRange;
        this.launchSmokeClimbSpeed = launchSmokeClimbSpeed;
        this.dragMultiplier = dragMultiplier;
    }

    public int fuelTankLaunchRejectionThreshold() {
        return fuelTankLaunchRejectionThreshold;
    }

    public int launchSmokePerTick() { return this.launchSmokePerTick; }

    public double minimumLaunchSpeed() { return this.minimumLaunchSpeed; }

    public float launchSmokeScale() { return this.launchSmokeScale; }

    public double launchSmokeRange() { return this.launchSmokeRange; }

    public double launchSmokeClimbSpeed() { return this.launchSmokeClimbSpeed; }

    public double dragMultiplier() { return this.dragMultiplier; }

    public int launchDustPerTick() { return this.ordinal() + 1; }

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
