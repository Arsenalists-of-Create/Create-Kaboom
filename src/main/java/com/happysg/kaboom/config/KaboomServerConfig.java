package com.happysg.kaboom.config;

import net.createmod.catnip.config.ConfigBase;
import net.createmod.catnip.config.ConfigBase.ConfigBool;
import net.createmod.catnip.config.ConfigBase.ConfigFloat;
import net.createmod.catnip.config.ConfigBase.ConfigInt;

public class KaboomServerConfig extends ConfigBase {
   public final ConfigInt maxMissileSpeed = this.i(10, 1, "maxMissileSpeed", new String[]{"maxMissileSpeed"});
   public final ConfigFloat maxMissileAccel = this.f(0.5F, 0.0F, "maxMissileAccel", new String[]{"maxMissileAccel"});
   public final ConfigInt maxFuelBurnPerTick = this.i(1, 1, "maxFuelBurnPerTick", new String[]{"maxFuelBurnPerTick"});
   public final ConfigBool missileGuidanceDebug = this.b(false, "missileGuidanceDebug", new String[]{"Log missile guidance state and steering every 5 ticks"});
   public final ConfigBool interceptorGuidanceDebug = this.b(
      false, "interceptorGuidanceDebug", new String[]{"Log command/radar interceptor guidance state and steering every 5 ticks"}
   );
   public final ConfigFloat maxTurnDegreesPerTick = this.f(8.0F, 0.0F, "maxTurnDegreesPerTick", new String[]{"Maximum missile steering turn angle per tick"});
   public final ConfigFloat maxAccelerationPerTick = this.f(
      0.5F, 0.0F, "maxAccelerationPerTick", new String[]{"Maximum missile guidance delta-velocity per tick"}
   );
   public final ConfigFloat maxSpeed = this.f(10.0F, 0.0F, "maxSpeed", new String[]{"Maximum missile speed in blocks per tick"});
   public final ConfigFloat thrustAccelerationPerTick = this.f(0.5F, 0.0F, "thrustAccelerationPerTick", new String[]{"Powered missile acceleration per tick"});
   public final ConfigFloat missileEjectionVelocity = this.f(
      0.5F, 0.0F, "missileEjectionVelocity", new String[]{"One-time forward velocity added when a missile launches, in blocks per tick"}
   );
   public final ConfigFloat bombEjectionVelocity = this.f(
      0.25F, 0.0F, "bombEjectionVelocity", new String[]{"One-time local-down velocity added when an aerial bomb releases, in blocks per tick"}
   );
   public final ConfigInt bombCarrierCollisionGraceTicks = this.i(
      20, 0, "bombCarrierCollisionGraceTicks", new String[]{"Ticks that a released aerial bomb ignores only its launching Sable sublevel"}
   );
   public final ConfigInt cruiseAltitudeY = this.i(350, 0, "cruiseAltitudeY", new String[]{"World Y altitude used for missile cruise"});
   public final ConfigFloat cruiseAltitudeDeadband = this.f(12.0F, 0.0F, "cruiseAltitudeDeadband", new String[]{"Altitude error ignored during missile cruise"});
   public final ConfigFloat cruiseActivationDistance = this.f(
      600.0F, 1.0F, "cruiseActivationDistance", new String[]{"Targets farther than this use cruise guidance"}
   );
   public final ConfigFloat diveStartHorizontalDistance = this.f(
      500.0F, 1.0F, "diveStartHorizontalDistance", new String[]{"Horizontal range at which cruising missiles enter terminal dive"}
   );
   public final ConfigFloat terminalDiveAngleDegrees = this.f(
      60.0F, 0.0F, "terminalDiveAngleDegrees", new String[]{"Desired downward terminal dive angle for cruising missiles"}
   );
   public final ConfigFloat minimumGpsLaunchHorizontalDistance = this.f(
      250.0F, 0.0F, "minimumGpsLaunchHorizontalDistance", new String[]{"Minimum horizontal distance from launcher to GPS target required to launch"}
   );
   public final ConfigFloat cruiseLookaheadDistance = this.f(
      150.0F, 1.0F, "cruiseLookaheadDistance", new String[]{"Horizontal lookahead distance for cruise aim point"}
   );
   public final ConfigFloat overshootDistanceEpsilon = this.f(
      0.5F, 0.0F, "overshootDistanceEpsilon", new String[]{"Distance increase needed to abort after overshoot"}
   );
   public final ConfigInt maxLeadTimeTicks = this.i(100, 1, "maxLeadTimeTicks", new String[]{"Maximum command/radar interceptor lead time in ticks"});
   public final ConfigFloat targetVelocitySmoothing = this.f(
      0.35F, 0.0F, "targetVelocitySmoothing", new String[]{"Smoothing factor for command/radar target velocity"}
   );
   public final ConfigFloat interceptPointSmoothing = this.f(
      0.45F, 0.0F, "interceptPointSmoothing", new String[]{"Smoothing factor for command/radar intercept point"}
   );
   public final ConfigInt targetDataTimeoutTicks = this.i(
      20, 0, "targetDataTimeoutTicks", new String[]{"Maximum age in ticks for fallback command/radar target data"}
   );
   public final ConfigFloat radarAcquisitionRangeBlocks = this.f(
      1000.0F, 1.0F, "radarAcquisitionRangeBlocks", new String[]{"Maximum radar-guidance acquisition and tracking range in blocks"}
   );
   public final ConfigFloat radarAcquisitionHalfAngleDegrees = this.f(
      15.0F, 0.0F, "radarAcquisitionHalfAngleDegrees", new String[]{"Radar-guidance acquisition cone half-angle in degrees"}
   );
   public final ConfigInt radarLockTicks = this.i(
      40, 1, "radarLockTicks", new String[]{"Continuous target dwell required before a radar-guided missile launches"}
   );
   public final ConfigFloat radarTrackingConePaddingDegrees = this.f(
      2.5F, 0.0F, "radarTrackingConePaddingDegrees", new String[]{"Additional seeker cone half-angle available after a radar-guided missile launches"}
   );

   public String getName() {
      return "Kaboom Server";
   }
}
