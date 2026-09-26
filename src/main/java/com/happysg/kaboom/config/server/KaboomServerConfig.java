package com.happysg.kaboom.config.server;

import com.happysg.kaboom.block.missiles.assembly.MissileSize;
import net.createmod.catnip.config.ConfigBase;

public class KaboomServerConfig extends ConfigBase {
    public final ConfigGroup rocketMovementConfig = group(1, "rocketMovement", "Configs for rocket movement");
    public final ConfigFloat rocketBoostDistance = f(120.0F, 0.0F, "rocketBoostDistance", "Distance rockets accelerate before their motors burn out, in blocks");
    public final ConfigFloat rocketInitialVelocity = f(0.3F, 0.0F, "rocketInitialVelocity", "Rocket launch velocity in blocks per tick");
    public final ConfigFloat rocketAccelerationPerTick = f(0.2F, 0.0F, "rocketAccelerationPerTick", "Rocket acceleration added per tick while the motor is burning");
    public final ConfigFloat rocketPodBaseInaccuracyMultiplier = f(3.0F, 0.0F, "rocketPodBaseInaccuracyMultiplier", "Rocket pod spread for the shortest valid two-part tube relative to a vanilla bow; longer tubes use inverse fourth-power falloff");
    public final ConfigBool shoulderFiredRocketsEnabled = b(true, "shoulderFiredRocketsEnabled", "Allow players to ignite and launch rockets from their shoulder");
    public final ConfigFloat shoulderRocketInaccuracyMultiplier = f(1.5F, 0.0F, "shoulderRocketInaccuracyMultiplier", "Shoulder-fired rocket spread relative to a vanilla bow, where 1.0 equals bow inaccuracy");
    public final ConfigBool shoulderRocketElytraPenalties = b(true, "shoulderRocketElytraPenalties", "Apply longer ignition, extra inaccuracy, and mishap durability damage to shoulder-rocket users with elytra");

    public final ConfigGroup missileMovementConfig = group(1, "missileMovement", "Configs for missile movement and fuel consumption");
    public final ConfigInt maxFuelBurnPerTick = i(1, 1, "maxFuelBurnPerTick", "Maximum missile fuel consumed per tick");
    public final ConfigFloat maxTurnDegreesPerTick = f(8.0F, 0.0F, "maxTurnDegreesPerTick", "Maximum missile steering turn angle per tick");
    public final ConfigFloat turnReferenceSpeedMetersPerSecond = f(50.0F, 0.001F, "turnReferenceSpeedMetersPerSecond", "Missile speed at or below which the maximum steering turn angle is available, in meters per second");
    public final ConfigFloat turnRateSpeedExponent = f(0.7F, 0.0F, "turnRateSpeedExponent", "Exponent controlling how missile steering turn rate decreases above the reference speed");
    public final ConfigFloat maxAccelerationPerTick = f(0.5F, 0.0F, "maxAccelerationPerTick", "Maximum missile guidance delta-velocity per tick");
    public final ConfigFloat maxSpeed = f(10.0F, 0.0F, "maxSpeed", "Maximum missile speed in blocks per tick");
    public final ConfigFloat thrustAccelerationPerTick = f(0.3F, 0.0F, "thrustAccelerationPerTick", "Powered missile acceleration per tick");
    public final ConfigFloat missileDragCoefficient = f(0.002F, 0.0F, "missileDragCoefficient", "Quadratic missile drag coefficient x in 0.5 * x * speed^2, in inverse meters");

    public final ConfigGroup launchAndReleaseConfig = group(1, "launchAndRelease", "Configs for missile launches and aerial bomb releases");
    public final ConfigFloat rocketLaunchDelaySeconds = f(0.0F, 0.0F, "rocketLaunchDelaySeconds", "Delay before rockets leave rocket pods, in seconds");
    public final ConfigFloat smallMissileLaunchDelaySeconds = f(0.25F, 0.0F, "smallMissileLaunchDelaySeconds", "Delay before small missiles leave their launcher, in seconds");
    public final ConfigFloat largeMissileLaunchDelaySeconds = f(0.5F, 0.0F, "largeMissileLaunchDelaySeconds", "Delay before large missiles leave their launcher, in seconds");
    public final ConfigFloat hugeMissileLaunchDelaySeconds = f(2.0F, 0.0F, "hugeMissileLaunchDelaySeconds", "Delay before huge missiles leave their launcher, in seconds");
    public final ConfigFloat missileEjectionVelocity = f(0.5F, 0.0F, "missileEjectionVelocity", "One-time forward velocity added when a missile launches, in blocks per tick");
    public final ConfigFloat bombEjectionVelocity = f(0.25F, 0.0F, "bombEjectionVelocity", "One-time local-down velocity added when an aerial bomb releases, in blocks per tick");
    public final ConfigInt bombCarrierCollisionGraceTicks = i(20, 0, "bombCarrierCollisionGraceTicks", "Ticks that a released aerial bomb ignores only its launching Sable sublevel");
    public final ConfigFloat minimumGpsLaunchHorizontalDistance = f(250.0F, 0.0F, "minimumGpsLaunchHorizontalDistance", "Minimum horizontal distance from launcher to GPS target required to launch");

    public final ConfigGroup cruiseGuidanceConfig = group(1, "cruiseGuidance", "Configs for GPS missile cruise guidance");
    public final ConfigInt cruiseAltitudeY = i(350, 0, "cruiseAltitudeY", "World Y altitude used for missile cruise");
    public final ConfigFloat cruiseAltitudeDeadband = f(12.0F, 0.0F, "cruiseAltitudeDeadband", "Altitude error ignored during missile cruise");
    public final ConfigFloat cruiseActivationDistance = f(600.0F, 1.0F, "cruiseActivationDistance", "Targets farther than this use cruise guidance");
    public final ConfigFloat diveStartHorizontalDistance = f(500.0F, 1.0F, "diveStartHorizontalDistance", "Horizontal range at which cruising missiles enter terminal dive");
    public final ConfigFloat terminalDiveAngleDegrees = f(60.0F, 0.0F, "terminalDiveAngleDegrees", "Desired downward terminal dive angle for cruising missiles");
    public final ConfigFloat cruiseLookaheadDistance = f(150.0F, 1.0F, "cruiseLookaheadDistance", "Horizontal lookahead distance for cruise aim point");
    public final ConfigFloat overshootDistanceEpsilon = f(0.5F, 0.0F, "overshootDistanceEpsilon", "Distance increase needed to abort after overshoot");

    public final ConfigGroup interceptionConfig = group(1, "interception", "Configs for moving-target interception");
    public final ConfigInt maxLeadTimeTicks = i(100, 1, "maxLeadTimeTicks", "Maximum command/radar interceptor lead time in ticks");
    public final ConfigFloat targetVelocitySmoothing = f(0.35F, 0.0F, "targetVelocitySmoothing", "Smoothing factor for command/radar target velocity");
    public final ConfigFloat interceptPointSmoothing = f(0.45F, 0.0F, "interceptPointSmoothing", "Smoothing factor for command/radar intercept point");
    public final ConfigInt targetDataTimeoutTicks = i(20, 0, "targetDataTimeoutTicks", "Maximum age in ticks for fallback command/radar target data");

    public final ConfigGroup ordnanceInterceptionConfig = group(1, "ordnanceInterception", "Configs for intercepting missiles, rockets, and aerial bombs");
    public final ConfigFloat ordnanceHealth = f(40.0F, 1.0F, "ordnanceHealth", "Raw interception damage required to destroy newly spawned ordnance");
    public final ConfigFloat interceptedDetonationChance = f(0.3F, 0.0F, 1.0F, "interceptedDetonationChance", "Chance that destroyed ordnance detonates its payload instead of disappearing");

    public final ConfigGroup radarSeekerConfig = group(1, "radarSeeker", "Configs for radar seeker acquisition and tracking");
    public final ConfigFloat radarAcquisitionRangeBlocks = f(1000.0F, 1.0F, "radarAcquisitionRangeBlocks", "Maximum radar-guidance acquisition and tracking range in blocks");
    public final ConfigFloat radarAcquisitionHalfAngleDegrees = f(15.0F, 0.0F, "radarAcquisitionHalfAngleDegrees", "Radar-guidance acquisition cone half-angle in degrees");
    public final ConfigInt radarLockTicks = i(40, 1, "radarLockTicks", "Continuous target dwell required before a radar-guided missile launches");
    public final ConfigFloat radarTrackingConePaddingDegrees = f(2.5F, 0.0F, "radarTrackingConePaddingDegrees", "Additional seeker cone half-angle available after a radar-guided missile launches");
    public final ConfigFloat radarRocketMinimumFireToleranceDegrees = f(10.0F, 0.0F, 45.0F, "radarRocketMinimumFireToleranceDegrees", "Minimum pitch and yaw tolerance Create: Radar may use when firing radar-guided rockets from a pod");

    public final ConfigGroup diagnosticsConfig = group(1, "diagnostics", "Configs for guidance diagnostics and logging");
    public final ConfigBool missileGuidanceDebug = b(false, "missileGuidanceDebug", "Log missile guidance state and steering every 5 ticks");
    public final ConfigBool interceptorGuidanceDebug = b(false, "interceptorGuidanceDebug", "Log command/radar interceptor guidance state and steering every 5 ticks");

    @Override
    public String getName() {
        return "Kaboom Server";
    }

    public int rocketLaunchDelayTicks() {
        return secondsToTicks(rocketLaunchDelaySeconds.getF());
    }

    public int missileLaunchDelayTicks(MissileSize size) {
        MissileSize safeSize = size == null ? MissileSize.SMALL : size;
        float seconds = switch (safeSize) {
            case SMALL -> smallMissileLaunchDelaySeconds.getF();
            case LARGE -> largeMissileLaunchDelaySeconds.getF();
            case HUGE -> hugeMissileLaunchDelaySeconds.getF();
        };
        return secondsToTicks(seconds);
    }

    private static int secondsToTicks(float seconds) {
        if (!Float.isFinite(seconds) || seconds <= 0.0F) {
            return 0;
        }
        return (int)Math.ceil(seconds * 20.0F);
    }
}
