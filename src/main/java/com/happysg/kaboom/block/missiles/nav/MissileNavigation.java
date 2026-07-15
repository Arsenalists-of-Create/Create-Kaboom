package com.happysg.kaboom.block.missiles.nav;

import com.happysg.kaboom.CreateKaboom;
import com.happysg.kaboom.block.missiles.util.MissileGuidanceData;
import com.happysg.kaboom.block.missiles.util.MissileTargetSpec;
import com.happysg.kaboom.config.KaboomConfig;
import java.util.Locale;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class MissileNavigation {
   private static final double BOOST_DISTANCE_BLOCKS = 30.0;
   private static final double EPSILON_DIR_SQR = 1.0E-10;
   private static final double NEAR_ZERO_SPEED = 1.0E-4;
   private MissileNavigation.State state = MissileNavigation.State.BOOST;
   @Nullable
   private Vec3 target = null;
   private Vec3 launchDirection = new Vec3(0.0, 1.0, 0.0);
   private Vec3 previousGuidancePosition = Vec3.ZERO;
   private double accumulatedBoostDistance = 0.0;
   private double previousTargetDistance = Double.NaN;
   private boolean beganClosingTarget = false;
   private String abortReason = "";
   private MissileNavigation.Command lastCommand = MissileNavigation.Command.none("uninitialized");

   public void initialize(Vec3 launchDirection, Vec3 launchPosition, MissileNavigation.FlightAccess access) {
      this.launchDirection = safeNormalize(launchDirection, new Vec3(0.0, 1.0, 0.0));
      this.previousGuidancePosition = launchPosition;
      this.accumulatedBoostDistance = 0.0;
      this.state = MissileNavigation.State.BOOST;
      this.abortReason = "";
      this.beganClosingTarget = false;
      this.previousTargetDistance = this.target == null ? Double.NaN : launchPosition.distanceTo(this.target);
      this.syncState(access);
   }

   public void configureStationaryTarget(MissileGuidanceData data, Vec3 currentPosition) {
      this.target = null;
      MissileTargetSpec spec = data.target();
      if (spec != null && spec.type() == MissileTargetSpec.TargetType.POINT) {
         Vec3 point = spec.point();
         if (point != null && isFinite(point)) {
            this.target = point;
            this.previousTargetDistance = currentPosition.distanceTo(this.target);
            this.beganClosingTarget = false;
            this.abortReason = "";
         }
      }
   }

   public MissileNavigation.Command tick(MissileNavigation.FlightAccess access, Vec3 pos, Vec3 vel) {
      if (this.target == null) {
         this.lastCommand = MissileNavigation.Command.none("no_target");
         return this.lastCommand;
      } else if (this.state == MissileNavigation.State.ABORTED) {
         this.lastCommand = MissileNavigation.Command.none("aborted:" + this.abortReason);
         return this.lastCommand;
      } else {
         if (this.state == MissileNavigation.State.BOOST) {
            this.accumulateBoostDistance(pos);
            if (this.accumulatedBoostDistance >= 30.0) {
               this.completeBoost(access, pos);
            }
         }

         if ((this.state == MissileNavigation.State.DIRECT_TERMINAL || this.state == MissileNavigation.State.TERMINAL_DIVE)
            && this.detectOvershoot(access, pos)) {
            this.lastCommand = MissileNavigation.Command.none("overshoot:" + this.abortReason);
            this.debug(access, pos, vel, this.lastCommand);
            return this.lastCommand;
         } else {
            Vec3 aimPoint;
            if (this.state == MissileNavigation.State.BOOST) {
               aimPoint = pos.add(this.launchDirection);
            } else if (this.state == MissileNavigation.State.CLIMB_OR_CRUISE) {
               double horizontalRange = horizontalDistance(pos, this.target);
               if (horizontalRange <= effectiveTerminalDiveStartHorizontalDistance(pos, this.target)) {
                  this.transitionTo(access, MissileNavigation.State.TERMINAL_DIVE, "entered terminal dive");
                  this.previousTargetDistance = pos.distanceTo(this.target);
                  this.beganClosingTarget = false;
                  aimPoint = this.target;
               } else {
                  aimPoint = this.cruiseAimPoint(access, pos, this.target);
               }
            } else {
               aimPoint = this.target;
            }

            Vec3 desiredDir = this.desiredDirection(access, pos, aimPoint);
            double actualTurnDeg = 0.0;
            Vec3 appliedDelta;
            Vec3 requestedDelta;
            if (this.state == MissileNavigation.State.BOOST) {
               desiredDir = this.launchDirection;
               appliedDelta = this.poweredDeltaAlong(access, desiredDir, configuredThrustAccelerationPerTick());
               requestedDelta = appliedDelta;
            } else {
               MissileNavigation.Command steered = this.steerToward(access, vel, desiredDir);
               appliedDelta = steered.appliedDeltaV();
               requestedDelta = steered.requestedDeltaV();
               actualTurnDeg = steered.actualTurnDeg();
               desiredDir = steered.desiredDir();
            }

            MissileNavigation.Command cmd = new MissileNavigation.Command(
               appliedDelta, desiredDir, actualTurnDeg, requestedDelta, this.state.name().toLowerCase(Locale.ROOT)
            );
            this.lastCommand = cmd;
            this.debug(access, pos, vel, cmd);
            return cmd;
         }
      }
   }

   public void write(CompoundTag tag) {
      tag.putString("kaboom:GuidanceState", this.state.name());
      putVec(tag, "kaboom:LaunchDirection", this.launchDirection);
      putVec(tag, "kaboom:PreviousGuidancePosition", this.previousGuidancePosition);
      tag.putDouble("kaboom:AccumulatedBoostDistance", this.accumulatedBoostDistance);
      tag.putDouble("kaboom:PreviousTargetDistance", this.previousTargetDistance);
      tag.putBoolean("kaboom:BeganClosingTarget", this.beganClosingTarget);
      tag.putString("kaboom:AbortReason", this.abortReason == null ? "" : this.abortReason);
      if (this.target != null) {
         tag.putBoolean("kaboom:HasNavTarget", true);
         putVec(tag, "kaboom:NavTarget", this.target);
      } else {
         tag.putBoolean("kaboom:HasNavTarget", false);
      }
   }

   public void read(CompoundTag tag, MissileNavigation.FlightAccess access, Vec3 currentPosition) {
      if (tag.contains("kaboom:GuidanceState")) {
         try {
            this.state = MissileNavigation.State.valueOf(tag.getString("kaboom:GuidanceState"));
         } catch (IllegalArgumentException var5) {
            this.state = MissileNavigation.State.BOOST;
         }
      }

      this.syncState(access);
      this.launchDirection = safeNormalize(readVec(tag, "kaboom:LaunchDirection", this.launchDirection), new Vec3(0.0, 1.0, 0.0));
      this.previousGuidancePosition = readVec(tag, "kaboom:PreviousGuidancePosition", currentPosition);
      this.accumulatedBoostDistance = tag.contains("kaboom:AccumulatedBoostDistance") ? tag.getDouble("kaboom:AccumulatedBoostDistance") : 0.0;
      this.previousTargetDistance = tag.contains("kaboom:PreviousTargetDistance") ? tag.getDouble("kaboom:PreviousTargetDistance") : Double.NaN;
      this.beganClosingTarget = tag.getBoolean("kaboom:BeganClosingTarget");
      this.abortReason = tag.contains("kaboom:AbortReason") ? tag.getString("kaboom:AbortReason") : "";
      this.target = tag.getBoolean("kaboom:HasNavTarget") ? readVec(tag, "kaboom:NavTarget", null) : null;
   }

   public Vec3 launchDirection() {
      return this.launchDirection;
   }

   public int stateOrdinal() {
      return this.state.ordinal();
   }

   public boolean isBoosting() {
      return this.state == MissileNavigation.State.BOOST;
   }

   private void accumulateBoostDistance(Vec3 pos) {
      if (!isFinite(this.previousGuidancePosition)) {
         this.previousGuidancePosition = pos;
      } else {
         double traveled = this.previousGuidancePosition.distanceTo(pos);
         if (Double.isFinite(traveled)) {
            this.accumulatedBoostDistance += traveled;
         }

         this.previousGuidancePosition = pos;
      }
   }

   private void completeBoost(MissileNavigation.FlightAccess access, Vec3 pos) {
      double range = pos.distanceTo(this.target);
      this.previousTargetDistance = range;
      this.beganClosingTarget = false;
      this.logTransition(access, "boost completed range=" + String.format(Locale.ROOT, "%.2f", range));
      if (range <= configuredCruiseActivationDistance()) {
         this.transitionTo(access, MissileNavigation.State.DIRECT_TERMINAL, "entered direct terminal");
      } else {
         this.transitionTo(access, MissileNavigation.State.CLIMB_OR_CRUISE, "entered climb/cruise");
      }
   }

   private boolean detectOvershoot(MissileNavigation.FlightAccess access, Vec3 pos) {
      double distance = pos.distanceTo(this.target);
      double epsilon = configuredOvershootDistanceEpsilon();
      if (!Double.isFinite(this.previousTargetDistance)) {
         this.previousTargetDistance = distance;
         return false;
      } else if (distance < this.previousTargetDistance - epsilon) {
         this.beganClosingTarget = true;
         this.previousTargetDistance = distance;
         return false;
      } else if (this.beganClosingTarget && distance > this.previousTargetDistance + epsilon) {
         this.abort(
            access,
            "overshoot detected prevDistance="
               + String.format(Locale.ROOT, "%.3f", this.previousTargetDistance)
               + " currentDistance="
               + String.format(Locale.ROOT, "%.3f", distance)
         );
         this.previousTargetDistance = distance;
         return true;
      } else {
         this.previousTargetDistance = distance;
         return false;
      }
   }

   private void abort(MissileNavigation.FlightAccess access, String reason) {
      access.guidanceSetFuelMb(0);
      this.abortReason = reason;
      this.transitionTo(access, MissileNavigation.State.ABORTED, "entered aborted state: " + reason);
   }

   private Vec3 cruiseAimPoint(MissileNavigation.FlightAccess access, Vec3 pos, Vec3 target) {
      Vec3 horizontal = new Vec3(target.x - pos.x, 0.0, target.z - pos.z);
      double horizontalDistance = horizontal.length();
      Vec3 horizontalDir = horizontalDistance > 1.0E-4 ? horizontal.scale(1.0 / horizontalDistance) : this.currentOrLaunchDirection(access.guidanceVelocity());
      double lookahead = Math.min(configuredCruiseLookaheadDistance(), Math.max(1.0, horizontalDistance));
      double cruiseY = configuredCruiseAltitudeY(access.guidanceLevel());
      double deadband = configuredCruiseAltitudeDeadband();
      double yError = cruiseY - pos.y;
      double correctedError = Math.abs(yError) <= deadband ? 0.0 : Math.copySign(Math.abs(yError) - deadband, yError);
      double maxVerticalOffset = Math.max(4.0, lookahead * 0.35);
      double verticalOffset = Mth.clamp(correctedError, -maxVerticalOffset, maxVerticalOffset);
      return pos.add(horizontalDir.scale(lookahead)).add(0.0, verticalOffset, 0.0);
   }

   private MissileNavigation.Command steerToward(MissileNavigation.FlightAccess access, Vec3 vel, Vec3 desiredDirRaw) {
      Vec3 desiredDir = safeNormalize(desiredDirRaw, this.launchDirection);
      Vec3 currentDir = this.currentOrLaunchDirection(vel);
      Vec3 rotatedDir = limitTurnSafe(currentDir, desiredDir, configuredMaxTurnDegreesPerTick());
      double actualTurnDeg = angleDegrees(currentDir, rotatedDir);
      double currentSpeed = vel.length();
      double requestedSpeed = Math.min(configuredMaxSpeed(), currentSpeed + configuredThrustAccelerationPerTick());
      Vec3 requestedVelocity = rotatedDir.scale(requestedSpeed);
      Vec3 requestedDelta = requestedVelocity.subtract(vel);
      Vec3 appliedDelta = clampMagnitude(requestedDelta, configuredMaxAccelerationPerTick());
      appliedDelta = this.poweredDeltaAlong(access, appliedDelta, appliedDelta.length());
      return new MissileNavigation.Command(appliedDelta, desiredDir, actualTurnDeg, requestedDelta, "steer");
   }

   private Vec3 poweredDeltaAlong(MissileNavigation.FlightAccess access, Vec3 directionOrDelta, double magnitude) {
      if (access.guidanceFuelMb() > 0 && !(magnitude <= 0.0) && !(directionOrDelta.lengthSqr() < 1.0E-10)) {
         double max = Math.min(magnitude, configuredMaxAccelerationPerTick());
         Vec3 dir = safeNormalize(directionOrDelta, this.launchDirection);
         double throttle = configuredThrustAccelerationPerTick() <= 1.0E-9 ? 0.0 : Mth.clamp(max / configuredThrustAccelerationPerTick(), 0.0, 1.0);
         double availableThrottle = this.burnFuelForThrottle(access, throttle);
         return dir.scale(configuredThrustAccelerationPerTick() * availableThrottle);
      } else {
         return Vec3.ZERO;
      }
   }

   private double burnFuelForThrottle(MissileNavigation.FlightAccess access, double throttleRaw) {
      double throttle = Mth.clamp(throttleRaw, 0.0, 1.0);
      if (access.guidanceFuelMb() > 0 && !(throttle <= 0.0)) {
         int burnAtFull = Math.max(1, (Integer)KaboomConfig.server().maxFuelBurnPerTick.get());
         int requestedBurn = Math.max(1, (int)Math.ceil((double)burnAtFull * throttle));
         if (access.guidanceFuelMb() < requestedBurn) {
            double scale = (double)access.guidanceFuelMb() / (double)requestedBurn;
            requestedBurn = access.guidanceFuelMb();
            throttle *= scale;
         }

         access.guidanceBurnFuel(requestedBurn);
         return access.guidanceFuelMb() >= 0 ? throttle : 0.0;
      } else {
         return 0.0;
      }
   }

   private Vec3 desiredDirection(MissileNavigation.FlightAccess access, Vec3 pos, Vec3 aimPoint) {
      return safeNormalize(aimPoint.subtract(pos), this.currentOrLaunchDirection(access.guidanceVelocity()));
   }

   private Vec3 currentOrLaunchDirection(Vec3 vel) {
      return vel.length() > 1.0E-4 ? safeNormalize(vel, this.launchDirection) : this.launchDirection;
   }

   private void transitionTo(MissileNavigation.FlightAccess access, MissileNavigation.State next, String message) {
      if (this.state != next) {
         this.state = next;
         this.syncState(access);
         this.logTransition(access, message);
      }
   }

   private void syncState(MissileNavigation.FlightAccess access) {
      access.guidanceSyncState(this.state.ordinal());
   }

   private void debug(MissileNavigation.FlightAccess access, Vec3 pos, Vec3 vel, MissileNavigation.Command cmd) {
      if ((Boolean)KaboomConfig.server().missileGuidanceDebug.get() && access.guidanceTickCount() % 5 == 0) {
         double range = this.target == null ? Double.NaN : pos.distanceTo(this.target);
         double horizontalRange = this.target == null ? Double.NaN : horizontalDistance(pos, this.target);
         double diveStartHorizontalRange = this.target == null ? Double.NaN : effectiveTerminalDiveStartHorizontalDistance(pos, this.target);
         CreateKaboom.getLogger()
            .info(
               "MissileGuidance id={} uuid={} state={} pos={} target={} vel={} speed={} launchDir={} boostDistance={} range={} horizontalRange={} diveStartHorizontalRange={} terminalDiveAngleDeg={} desiredDir={} turnDeg={} requestedDelta={} appliedDelta={} fuel={} beganClosing={} prevDistance={} abortReason={}",
               new Object[]{
                  access.guidanceEntityId(),
                  access.guidanceUuid(),
                  this.state,
                  fmt(pos),
                  this.target == null ? "none" : fmt(this.target),
                  fmt(vel),
                  String.format(Locale.ROOT, "%.3f", vel.length()),
                  fmt(this.launchDirection),
                  String.format(Locale.ROOT, "%.3f", this.accumulatedBoostDistance),
                  String.format(Locale.ROOT, "%.3f", range),
                  String.format(Locale.ROOT, "%.3f", horizontalRange),
                  String.format(Locale.ROOT, "%.3f", diveStartHorizontalRange),
                  String.format(Locale.ROOT, "%.3f", configuredTerminalDiveAngleDegrees()),
                  fmt(cmd.desiredDir()),
                  String.format(Locale.ROOT, "%.3f", cmd.actualTurnDeg()),
                  fmt(cmd.requestedDeltaV()),
                  fmt(cmd.appliedDeltaV()),
                  access.guidanceFuelMb(),
                  this.beganClosingTarget,
                  String.format(Locale.ROOT, "%.3f", this.previousTargetDistance),
                  this.abortReason
               }
            );
      }
   }

   private void logTransition(MissileNavigation.FlightAccess access, String message) {
      if ((Boolean)KaboomConfig.server().missileGuidanceDebug.get()) {
         CreateKaboom.getLogger()
            .info("MissileGuidance transition id={} uuid={} state={} {}", new Object[]{access.guidanceEntityId(), access.guidanceUuid(), this.state, message});
      }
   }

   private static String fmt(Vec3 v) {
      return v == null ? "null" : String.format(Locale.ROOT, "(%.3f, %.3f, %.3f)", v.x, v.y, v.z);
   }

   private static Vec3 clampMagnitude(Vec3 v, double max) {
      double len = v.length();
      return len > max && len > 1.0E-9 ? v.scale(max / len) : v;
   }

   private static double angleDegrees(Vec3 a, Vec3 b) {
      if (!(a.lengthSqr() < 1.0E-10) && !(b.lengthSqr() < 1.0E-10)) {
         double dot = Mth.clamp(a.normalize().dot(b.normalize()), -1.0, 1.0);
         return Math.toDegrees(Math.acos(dot));
      } else {
         return 0.0;
      }
   }

   private static Vec3 limitTurnSafe(Vec3 currentDir, Vec3 desiredDir, double maxTurnDeg) {
      if (!(currentDir.lengthSqr() < 1.0E-8) && !(desiredDir.lengthSqr() < 1.0E-8)) {
         Vec3 a = currentDir.normalize();
         Vec3 b = desiredDir.normalize();
         double dot = Mth.clamp(a.dot(b), -1.0, 1.0);
         if (dot < -0.9995) {
            Vec3 axis = a.cross(new Vec3(0.0, 1.0, 0.0));
            if (axis.lengthSqr() < 1.0E-8) {
               axis = a.cross(new Vec3(1.0, 0.0, 0.0));
            }

            axis = axis.normalize();
            double maxRad = Math.toRadians(maxTurnDeg);
            Vec3 rotated = a.scale(Math.cos(maxRad)).add(axis.cross(a).scale(Math.sin(maxRad))).add(axis.scale(axis.dot(a) * (1.0 - Math.cos(maxRad))));
            return rotated.normalize();
         } else {
            double angle = Math.acos(dot);
            if (angle < 1.0E-6) {
               return b;
            } else {
               double maxRad = Math.toRadians(maxTurnDeg);
               if (angle <= maxRad) {
                  return b;
               } else {
                  double t = maxRad / angle;
                  double sinAngle = Math.sin(angle);
                  double w1 = Math.sin((1.0 - t) * angle) / sinAngle;
                  double w2 = Math.sin(t * angle) / sinAngle;
                  return a.scale(w1).add(b.scale(w2)).normalize();
               }
            }
         }
      } else {
         return desiredDir;
      }
   }

   private static double horizontalDistance(Vec3 a, Vec3 b) {
      double dx = b.x - a.x;
      double dz = b.z - a.z;
      return Math.sqrt(dx * dx + dz * dz);
   }

   private static double effectiveTerminalDiveStartHorizontalDistance(Vec3 pos, Vec3 target) {
      double fixedCap = configuredDiveStartHorizontalDistance();
      double altitudeDelta = Math.max(0.0, pos.y - target.y);
      if (altitudeDelta <= 1.0E-6) {
         return fixedCap;
      } else {
         double angleRad = Math.toRadians(configuredTerminalDiveAngleDegrees());
         double adaptiveRange = altitudeDelta / Math.tan(angleRad);
         return Mth.clamp(adaptiveRange, 1.0, fixedCap);
      }
   }

   private static Vec3 safeNormalize(Vec3 v, Vec3 fallback) {
      if (isFinite(v) && !(v.lengthSqr() < 1.0E-10)) {
         return v.normalize();
      } else {
         return fallback.lengthSqr() < 1.0E-10 ? new Vec3(0.0, 1.0, 0.0) : fallback.normalize();
      }
   }

   private static boolean isFinite(Vec3 v) {
      return v != null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
   }

   private static void putVec(CompoundTag tag, String key, Vec3 value) {
      CompoundTag vec = new CompoundTag();
      vec.putDouble("X", value.x);
      vec.putDouble("Y", value.y);
      vec.putDouble("Z", value.z);
      tag.put(key, vec);
   }

   @Nullable
   private static Vec3 readVec(CompoundTag tag, String key, @Nullable Vec3 fallback) {
      if (!tag.contains(key)) {
         return fallback;
      } else {
         CompoundTag vec = tag.getCompound(key);
         Vec3 value = new Vec3(vec.getDouble("X"), vec.getDouble("Y"), vec.getDouble("Z"));
         return isFinite(value) ? value : fallback;
      }
   }

   public static double configuredMaxAccelerationPerTick() {
      return Math.max(0.0, (double)KaboomConfig.server().maxAccelerationPerTick.getF());
   }

   public static double configuredMaxSpeed() {
      double configured = (double)KaboomConfig.server().maxSpeed.getF();
      if (configured <= 0.0) {
         configured = (double)((Integer)KaboomConfig.server().maxMissileSpeed.get()).intValue();
      }

      return Math.max(0.0, configured);
   }

   private static double configuredMaxTurnDegreesPerTick() {
      return Math.max(0.0, (double)KaboomConfig.server().maxTurnDegreesPerTick.getF());
   }

   private static double configuredThrustAccelerationPerTick() {
      double configured = (double)KaboomConfig.server().thrustAccelerationPerTick.getF();
      if (configured <= 0.0) {
         configured = (double)KaboomConfig.server().maxMissileAccel.getF();
      }

      return Math.max(0.0, configured);
   }

   private static double configuredCruiseAltitudeY(Level level) {
      int minY = level.getMinBuildHeight() + 2;
      int maxY = level.getMaxBuildHeight() - 2;
      return (double)Mth.clamp((Integer)KaboomConfig.server().cruiseAltitudeY.get(), minY, maxY);
   }

   private static double configuredCruiseAltitudeDeadband() {
      return Math.max(0.0, (double)KaboomConfig.server().cruiseAltitudeDeadband.getF());
   }

   private static double configuredCruiseActivationDistance() {
      double dive = configuredDiveStartHorizontalDistance();
      return Math.max(dive + 1.0, (double)KaboomConfig.server().cruiseActivationDistance.getF());
   }

   private static double configuredDiveStartHorizontalDistance() {
      return Math.max(1.0, (double)KaboomConfig.server().diveStartHorizontalDistance.getF());
   }

   private static double configuredTerminalDiveAngleDegrees() {
      return Mth.clamp((double)KaboomConfig.server().terminalDiveAngleDegrees.getF(), 15.0, 85.0);
   }

   private static double configuredCruiseLookaheadDistance() {
      return Math.max(1.0, (double)KaboomConfig.server().cruiseLookaheadDistance.getF());
   }

   private static double configuredOvershootDistanceEpsilon() {
      return Math.max(0.0, (double)KaboomConfig.server().overshootDistanceEpsilon.getF());
   }

   public static record Command(Vec3 appliedDeltaV, Vec3 desiredDir, double actualTurnDeg, Vec3 requestedDeltaV, String reason) {
      public static MissileNavigation.Command none(String reason) {
         return new MissileNavigation.Command(Vec3.ZERO, Vec3.ZERO, 0.0, Vec3.ZERO, reason);
      }
   }

   public interface FlightAccess {
      Level guidanceLevel();

      int guidanceTickCount();

      int guidanceEntityId();

      UUID guidanceUuid();

      Vec3 guidanceVelocity();

      int guidanceFuelMb();

      int guidanceFuelCapacityMb();

      void guidanceSetFuelMb(int var1);

      void guidanceBurnFuel(int var1);

      void guidanceSyncState(int var1);
   }

   public static enum State {
      BOOST,
      DIRECT_TERMINAL,
      CLIMB_OR_CRUISE,
      TERMINAL_DIVE,
      ABORTED;
   }
}
