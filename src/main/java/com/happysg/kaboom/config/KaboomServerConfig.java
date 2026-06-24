package com.happysg.kaboom.config;

import net.createmod.catnip.config.ConfigBase;

public class KaboomServerConfig extends ConfigBase {
    @Override
    public String getName() {
        return "Kaboom Server";
    }
    public final ConfigInt maxMissileSpeed = i(10,1,"maxMissileSpeed", "maxMissileSpeed");
    public final ConfigFloat maxMissileAccel =f(0.5f,0,"maxMissileAccel","maxMissileAccel");
    public final ConfigInt maxFuelBurnPerTick = i(1,1,"maxFuelBurnPerTick","maxFuelBurnPerTick");
    public final ConfigBool missileGuidanceDebug = b(false, "missileGuidanceDebug", "Log missile guidance state and steering every 5 ticks");
    public final ConfigFloat maxTurnDegreesPerTick = f(8.0f, 0.0f, "maxTurnDegreesPerTick", "Maximum missile steering turn angle per tick");
    public final ConfigFloat maxAccelerationPerTick = f(0.5f, 0.0f, "maxAccelerationPerTick", "Maximum missile guidance delta-velocity per tick");
    public final ConfigFloat maxSpeed = f(10.0f, 0.0f, "maxSpeed", "Maximum missile speed in blocks per tick");
    public final ConfigFloat thrustAccelerationPerTick = f(0.5f, 0.0f, "thrustAccelerationPerTick", "Powered missile acceleration per tick");
    public final ConfigInt cruiseAltitudeY = i(350, 0, "cruiseAltitudeY", "World Y altitude used for missile cruise");
    public final ConfigFloat cruiseAltitudeDeadband = f(12.0f, 0.0f, "cruiseAltitudeDeadband", "Altitude error ignored during missile cruise");
    public final ConfigFloat cruiseActivationDistance = f(600.0f, 1.0f, "cruiseActivationDistance", "Targets farther than this use cruise guidance");
    public final ConfigFloat diveStartHorizontalDistance = f(500.0f, 1.0f, "diveStartHorizontalDistance", "Horizontal range at which cruising missiles enter terminal dive");
    public final ConfigFloat terminalDiveAngleDegrees = f(60.0f, 0.0f, "terminalDiveAngleDegrees", "Desired downward terminal dive angle for cruising missiles");
    public final ConfigFloat minimumGpsLaunchHorizontalDistance = f(250.0f, 0.0f, "minimumGpsLaunchHorizontalDistance", "Minimum horizontal distance from launcher to GPS target required to launch");
    public final ConfigFloat cruiseLookaheadDistance = f(150.0f, 1.0f, "cruiseLookaheadDistance", "Horizontal lookahead distance for cruise aim point");
    public final ConfigFloat overshootDistanceEpsilon = f(0.5f, 0.0f, "overshootDistanceEpsilon", "Distance increase needed to abort after overshoot");
}
