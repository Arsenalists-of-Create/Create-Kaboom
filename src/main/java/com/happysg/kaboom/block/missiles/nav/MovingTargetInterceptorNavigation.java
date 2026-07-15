package com.happysg.kaboom.block.missiles.nav;

import com.happysg.kaboom.CreateKaboom;
import com.happysg.kaboom.block.missiles.parts.guidance.radar.RadarTargeting;
import com.happysg.kaboom.block.missiles.util.MissileGuidanceData;
import com.happysg.kaboom.block.missiles.util.MissileGuidanceType;
import com.happysg.kaboom.block.missiles.util.MissileTargetSpec;
import com.happysg.kaboom.compat.radars.RadarCompatRegistry;
import com.happysg.kaboom.compat.radars.RadarIntegration;
import com.happysg.kaboom.config.KaboomConfig;
import java.util.Locale;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class MovingTargetInterceptorNavigation {
   private static final double BOOST_DISTANCE_BLOCKS = 30.0;
   private static final double EPSILON_DIR_SQR = 1.0E-10;
   private static final double NEAR_ZERO_SPEED = 1.0E-4;
   private static final int RWR_ENGAGEMENT_REFRESH_TICKS = 10;
   private MovingTargetInterceptorNavigation.State state = MovingTargetInterceptorNavigation.State.BOOST;
   private MissileGuidanceType guidanceType = MissileGuidanceType.UNKNOWN;
   @Nullable
   private MissileGuidanceData guidanceData = null;
   private Vec3 launchDirection = new Vec3(0.0, 1.0, 0.0);
   private Vec3 launchPosition = Vec3.ZERO;
   private Vec3 previousGuidancePosition = Vec3.ZERO;
   private double accumulatedBoostDistance = 0.0;
   private int fuelAtLaunch = 0;
   private String targetId = "";
   private String targetCategory = "";
   @Nullable
   private Vec3 latestTargetPosition = null;
   private Vec3 rawTargetVelocity = Vec3.ZERO;
   private Vec3 filteredTargetVelocity = Vec3.ZERO;
   private long targetTimestamp = -1L;
   private int targetAgeTicks = Integer.MAX_VALUE;
   private double previousMissDistance = Double.NaN;
   private double previousForwardRangeToTarget = Double.NaN;
   private boolean beganClosingTarget = false;
   private double interceptTimeTicks = Double.NaN;
   @Nullable
   private Vec3 rawInterceptPoint = null;
   @Nullable
   private Vec3 smoothedInterceptPoint = null;
   private String abortReason = "";
   private MissileNavigation.Command lastCommand = MissileNavigation.Command.none("uninitialized");
   private String lastRwrEngagedTargetId = "";
   private long nextRwrEngagementRefreshTime = Long.MIN_VALUE;
   private int radarLockLossTicks = 0;
   private long chaffSuppressedUntilTick = 0L;
   private String chaffTargetId = "";
   private boolean chaffActiveLastTick = false;
   private RadarIntegration.ThreatStage radarRwrThreatStage = RadarIntegration.ThreatStage.ENGAGED;

   public void initialize(Vec3 launchDirection, Vec3 launchPosition, MissileNavigation.FlightAccess access) {
      this.launchDirection = safeNormalize(launchDirection, new Vec3(0.0, 1.0, 0.0));
      this.launchPosition = launchPosition;
      this.previousGuidancePosition = launchPosition;
      this.accumulatedBoostDistance = 0.0;
      this.fuelAtLaunch = access.guidanceFuelMb();
      this.state = MovingTargetInterceptorNavigation.State.BOOST;
      this.abortReason = "";
      this.previousMissDistance = Double.NaN;
      this.previousForwardRangeToTarget = Double.NaN;
      this.beganClosingTarget = false;
      this.lastRwrEngagedTargetId = "";
      this.nextRwrEngagementRefreshTime = Long.MIN_VALUE;
      this.radarLockLossTicks = 0;
      this.chaffSuppressedUntilTick = 0L;
      this.chaffTargetId = "";
      this.chaffActiveLastTick = false;
      this.radarRwrThreatStage = RadarIntegration.ThreatStage.ENGAGED;
      this.syncState(access);
   }

   public void configure(MissileGuidanceData data) {
      this.guidanceData = data;
      this.guidanceType = data == null ? MissileGuidanceType.UNKNOWN : data.guidanceType();
   }

   public boolean isBoosting() {
      return this.state == MovingTargetInterceptorNavigation.State.BOOST;
   }

   public MissileNavigation.Command tick(MissileNavigation.FlightAccess access, Vec3 pos, Vec3 vel) {
      if (access.guidanceLevel() instanceof ServerLevel serverLevel) {
         if (!this.guidanceType.isInterceptor()) {
            this.lastCommand = MissileNavigation.Command.none("not_interceptor_guidance");
            return this.lastCommand;
         } else if (this.state == MovingTargetInterceptorNavigation.State.ABORTED) {
            this.clearRadarRwrEmitter(access);
            this.lastCommand = MissileNavigation.Command.none("aborted:" + this.abortReason);
            this.debug(access, pos, vel, null, this.lastCommand, "aborted");
            return this.lastCommand;
         } else if (this.guidanceType == MissileGuidanceType.COMMAND && !RadarCompatRegistry.isAvailable()) {
            this.abort(access, "Create Radar compatibility unavailable");
            this.lastCommand = MissileNavigation.Command.none("abort:" + this.abortReason);
            this.debug(access, pos, vel, null, this.lastCommand, "compat_unavailable");
            return this.lastCommand;
         } else {
            MovingTargetResolver.TargetData target = MovingTargetResolver.resolve(serverLevel, this.guidanceData, pos);
            this.updateCommandChaffState(serverLevel, target);
            if (serverLevel.getGameTime() < this.chaffSuppressedUntilTick) {
               if (!this.chaffActiveLastTick && this.hasLockedRadarTarget()) {
                  this.radarRwrThreatStage = this.radarRwrThreatStage.downgraded();
               }

               this.chaffActiveLastTick = true;
               this.publishRadarRwrEmitter(access, pos, this.currentOrLaunchDirection(vel), this.radarRwrThreatStage);
               if (this.state == MovingTargetInterceptorNavigation.State.BOOST) {
                  this.accumulateBoostDistance(pos);
               }

               MissileNavigation.Command straight = this.steerToward(access, vel, this.currentOrLaunchDirection(vel));
               this.lastCommand = new MissileNavigation.Command(
                  straight.appliedDeltaV(), this.currentOrLaunchDirection(vel), 0.0, straight.requestedDeltaV(), "chaff_suppressed"
               );
               this.debug(access, pos, vel, target, this.lastCommand, "chaff_suppressed");
               return this.lastCommand;
            } else {
               if (this.chaffActiveLastTick) {
                  this.chaffActiveLastTick = false;
                  MovingTargetResolver.TargetData exitTarget = target;
                  if (this.guidanceType == MissileGuidanceType.COMMAND && target == null) {
                     exitTarget = MovingTargetResolver.resolveSuppressedCommandTarget(serverLevel, this.guidanceData, this.chaffTargetId);
                  }

                  String chaffExitFailure = this.validateChaffExit(serverLevel, pos, vel, exitTarget);
                  if (chaffExitFailure != null) {
                     this.abort(access, chaffExitFailure);
                     this.lastCommand = MissileNavigation.Command.none("chaff_exit_abort:" + this.abortReason);
                     this.debug(access, pos, vel, exitTarget, this.lastCommand, "chaff_exit_abort");
                     return this.lastCommand;
                  }

                  this.resetTarget(exitTarget);
                  target = exitTarget;
                  this.radarLockLossTicks = 0;
                  this.chaffSuppressedUntilTick = 0L;
                  this.chaffTargetId = "";
                  this.radarRwrThreatStage = RadarIntegration.ThreatStage.ENGAGED;
                  this.logTransition(access, "chaff expired; target reacquired");
               }

               boolean lockedRadarGuidance = this.hasLockedRadarTarget();
               if (lockedRadarGuidance) {
                  target = this.applyRadarSeekerEnvelope(serverLevel, pos, vel, target);
                  if (this.radarLockLossTicks > configuredTargetDataTimeoutTicks()) {
                     this.abort(access, "radar lock lost outside seeker envelope");
                     this.lastCommand = MissileNavigation.Command.none("abort:" + this.abortReason);
                     this.debug(access, pos, vel, target, this.lastCommand, "radar_lock_abort");
                     return this.lastCommand;
                  }

                  if (this.radarLockLossTicks == 0) {
                     this.radarRwrThreatStage = RadarIntegration.ThreatStage.ENGAGED;
                     this.publishRadarRwrEmitter(access, pos, this.currentOrLaunchDirection(vel), this.radarRwrThreatStage);
                  } else {
                     this.clearRadarRwrEmitter(access);
                  }
               }

               this.refreshRwrEngagement(serverLevel, target);
               if (this.state == MovingTargetInterceptorNavigation.State.BOOST) {
                  if (target != null) {
                     this.acceptTarget(serverLevel, target, true);
                  }

                  this.accumulateBoostDistance(pos);
                  if (this.accumulatedBoostDistance >= 30.0) {
                     if (!this.acceptTarget(serverLevel, target, true)) {
                        this.abort(access, "no valid target after boost");
                        this.lastCommand = MissileNavigation.Command.none("abort:" + this.abortReason);
                        this.debug(access, pos, vel, target, this.lastCommand, "boost_abort");
                        return this.lastCommand;
                     }

                     this.transitionTo(access, MovingTargetInterceptorNavigation.State.INTERCEPT, "boost completed; entered intercept");
                  }

                  Vec3 appliedDelta = this.poweredDeltaAlong(access, this.launchDirection, effectiveThrustAccelerationPerTick(access));
                  this.lastCommand = new MissileNavigation.Command(appliedDelta, this.launchDirection, 0.0, appliedDelta, "boost");
                  this.debug(access, pos, vel, target, this.lastCommand, "boost");
                  return this.lastCommand;
               } else if (!this.acceptTarget(serverLevel, target, false)) {
                  this.abort(access, "stale or invalid target data");
                  this.lastCommand = MissileNavigation.Command.none("abort:" + this.abortReason);
                  this.debug(access, pos, vel, target, this.lastCommand, "stale_abort");
                  return this.lastCommand;
               } else if (this.detectOvershoot(access, pos, vel)) {
                  this.lastCommand = MissileNavigation.Command.none("overshoot:" + this.abortReason);
                  this.debug(access, pos, vel, target, this.lastCommand, "overshoot_abort");
                  return this.lastCommand;
               } else {
                  Vec3 aimPoint = this.computeAimPoint(pos, vel);
                  Vec3 desiredDir = safeNormalize(aimPoint.subtract(pos), this.currentOrLaunchDirection(vel));
                  MissileNavigation.Command steered = this.steerToward(access, vel, desiredDir);
                  this.lastCommand = new MissileNavigation.Command(
                     steered.appliedDeltaV(), steered.desiredDir(), steered.actualTurnDeg(), steered.requestedDeltaV(), "intercept"
                  );
                  this.debug(access, pos, vel, target, this.lastCommand, "intercept");
                  return this.lastCommand;
               }
            }
         }
      } else {
         this.lastCommand = MissileNavigation.Command.none("not_server_level");
         return this.lastCommand;
      }
   }

   public void write(CompoundTag tag) {
      tag.putString("kaboom:InterceptorState", this.state.name());
      tag.putString("kaboom:InterceptorGuidanceType", this.guidanceType.name());
      if (this.guidanceData != null) {
         tag.put("kaboom:InterceptorGuidanceData", this.guidanceData.toTag());
      }

      putVec(tag, "kaboom:InterceptorLaunchPosition", this.launchPosition);
      putVec(tag, "kaboom:InterceptorLaunchDirection", this.launchDirection);
      putVec(tag, "kaboom:InterceptorPreviousPosition", this.previousGuidancePosition);
      tag.putDouble("kaboom:InterceptorBoostDistance", this.accumulatedBoostDistance);
      tag.putInt("kaboom:InterceptorFuelAtLaunch", this.fuelAtLaunch);
      tag.putString("kaboom:InterceptorTargetId", this.targetId);
      tag.putString("kaboom:InterceptorTargetCategory", this.targetCategory);
      if (this.latestTargetPosition != null) {
         putVec(tag, "kaboom:InterceptorTargetPosition", this.latestTargetPosition);
      }

      putVec(tag, "kaboom:InterceptorRawTargetVelocity", this.rawTargetVelocity);
      putVec(tag, "kaboom:InterceptorFilteredTargetVelocity", this.filteredTargetVelocity);
      tag.putLong("kaboom:InterceptorTargetTimestamp", this.targetTimestamp);
      tag.putInt("kaboom:InterceptorTargetAge", this.targetAgeTicks);
      tag.putDouble("kaboom:InterceptorPreviousMissDistance", this.previousMissDistance);
      tag.putDouble("kaboom:InterceptorPreviousForwardRange", this.previousForwardRangeToTarget);
      tag.putBoolean("kaboom:InterceptorBeganClosing", this.beganClosingTarget);
      tag.putDouble("kaboom:InterceptorTime", this.interceptTimeTicks);
      if (this.rawInterceptPoint != null) {
         putVec(tag, "kaboom:InterceptorRawPoint", this.rawInterceptPoint);
      }

      if (this.smoothedInterceptPoint != null) {
         putVec(tag, "kaboom:InterceptorSmoothedPoint", this.smoothedInterceptPoint);
      }

      tag.putString("kaboom:InterceptorAbortReason", this.abortReason == null ? "" : this.abortReason);
      tag.putInt("kaboom:RadarLockLossTicks", this.radarLockLossTicks);
      tag.putLong("kaboom:ChaffSuppressedUntil", this.chaffSuppressedUntilTick);
      tag.putString("kaboom:ChaffTargetId", this.chaffTargetId);
      tag.putBoolean("kaboom:ChaffActiveLastTick", this.chaffActiveLastTick);
      tag.putString("kaboom:RadarRwrThreatStage", this.radarRwrThreatStage.name());
   }

   public void read(CompoundTag tag, MissileNavigation.FlightAccess access, Vec3 currentPosition) {
      if (tag.contains("kaboom:InterceptorState")) {
         try {
            this.state = MovingTargetInterceptorNavigation.State.valueOf(tag.getString("kaboom:InterceptorState"));
         } catch (IllegalArgumentException var6) {
            this.state = MovingTargetInterceptorNavigation.State.BOOST;
         }
      }

      if (tag.contains("kaboom:InterceptorGuidanceType")) {
         this.guidanceType = MissileGuidanceType.fromName(tag.getString("kaboom:InterceptorGuidanceType"));
      }

      if (tag.contains("kaboom:InterceptorGuidanceData")) {
         this.guidanceData = MissileGuidanceData.fromTag(tag.getCompound("kaboom:InterceptorGuidanceData"));
         this.guidanceType = this.guidanceData.guidanceType();
      }

      this.launchPosition = readVec(tag, "kaboom:InterceptorLaunchPosition", currentPosition);
      this.launchDirection = safeNormalize(readVec(tag, "kaboom:InterceptorLaunchDirection", this.launchDirection), new Vec3(0.0, 1.0, 0.0));
      this.previousGuidancePosition = readVec(tag, "kaboom:InterceptorPreviousPosition", currentPosition);
      this.accumulatedBoostDistance = tag.contains("kaboom:InterceptorBoostDistance") ? tag.getDouble("kaboom:InterceptorBoostDistance") : 0.0;
      this.fuelAtLaunch = tag.contains("kaboom:InterceptorFuelAtLaunch") ? tag.getInt("kaboom:InterceptorFuelAtLaunch") : access.guidanceFuelMb();
      this.targetId = tag.getString("kaboom:InterceptorTargetId");
      this.targetCategory = tag.getString("kaboom:InterceptorTargetCategory");
      this.latestTargetPosition = readVec(tag, "kaboom:InterceptorTargetPosition", null);
      this.rawTargetVelocity = readVec(tag, "kaboom:InterceptorRawTargetVelocity", Vec3.ZERO);
      this.filteredTargetVelocity = readVec(tag, "kaboom:InterceptorFilteredTargetVelocity", Vec3.ZERO);
      this.targetTimestamp = tag.contains("kaboom:InterceptorTargetTimestamp") ? tag.getLong("kaboom:InterceptorTargetTimestamp") : -1L;
      this.targetAgeTicks = tag.contains("kaboom:InterceptorTargetAge") ? tag.getInt("kaboom:InterceptorTargetAge") : Integer.MAX_VALUE;
      this.previousMissDistance = tag.contains("kaboom:InterceptorPreviousMissDistance") ? tag.getDouble("kaboom:InterceptorPreviousMissDistance") : Double.NaN;
      this.previousForwardRangeToTarget = tag.contains("kaboom:InterceptorPreviousForwardRange")
         ? tag.getDouble("kaboom:InterceptorPreviousForwardRange")
         : Double.NaN;
      this.beganClosingTarget = tag.getBoolean("kaboom:InterceptorBeganClosing");
      this.interceptTimeTicks = tag.contains("kaboom:InterceptorTime") ? tag.getDouble("kaboom:InterceptorTime") : Double.NaN;
      this.rawInterceptPoint = readVec(tag, "kaboom:InterceptorRawPoint", null);
      this.smoothedInterceptPoint = readVec(tag, "kaboom:InterceptorSmoothedPoint", null);
      this.abortReason = tag.contains("kaboom:InterceptorAbortReason") ? tag.getString("kaboom:InterceptorAbortReason") : "";
      this.radarLockLossTicks = tag.contains("kaboom:RadarLockLossTicks") ? Math.max(0, tag.getInt("kaboom:RadarLockLossTicks")) : 0;
      this.chaffSuppressedUntilTick = tag.contains("kaboom:ChaffSuppressedUntil") ? Math.max(0L, tag.getLong("kaboom:ChaffSuppressedUntil")) : 0L;
      this.chaffTargetId = tag.getString("kaboom:ChaffTargetId");
      this.chaffActiveLastTick = tag.getBoolean("kaboom:ChaffActiveLastTick");
      if (tag.contains("kaboom:RadarRwrThreatStage")) {
         try {
            this.radarRwrThreatStage = RadarIntegration.ThreatStage.valueOf(tag.getString("kaboom:RadarRwrThreatStage"));
         } catch (IllegalArgumentException var5) {
            this.radarRwrThreatStage = RadarIntegration.ThreatStage.ENGAGED;
         }
      } else {
         this.radarRwrThreatStage = this.chaffActiveLastTick ? RadarIntegration.ThreatStage.LOCKED : RadarIntegration.ThreatStage.ENGAGED;
      }

      this.lastRwrEngagedTargetId = "";
      this.nextRwrEngagementRefreshTime = Long.MIN_VALUE;
      this.syncState(access);
   }

   public Vec3 launchDirection() {
      return this.launchDirection;
   }

   public int stateOrdinal() {
      return this.state.ordinal();
   }

   @Nullable
   public String getRadarChaffTargetId() {
      if (this.state != MovingTargetInterceptorNavigation.State.ABORTED && this.hasLockedRadarTarget()) {
         MissileTargetSpec target = this.guidanceData.target();
         return target.entityId().toString();
      } else {
         return null;
      }
   }

   public void applyRadarChaffSuppression(String affectedTargetId, long untilTick) {
      String lockedTargetId = this.getRadarChaffTargetId();
      if (lockedTargetId != null && lockedTargetId.equals(affectedTargetId)) {
         this.chaffTargetId = affectedTargetId;
         this.chaffSuppressedUntilTick = Math.max(this.chaffSuppressedUntilTick, untilTick);
      }
   }

   public void onRadarMissileLaunched(MissileNavigation.FlightAccess access, Vec3 position, Vec3 forward) {
      if (this.hasLockedRadarTarget()) {
         this.radarRwrThreatStage = RadarIntegration.ThreatStage.ENGAGED;
         this.publishRadarRwrEmitter(access, position, forward, this.radarRwrThreatStage);
      }
   }

   public void clearRadarRwrEmitter(MissileNavigation.FlightAccess access) {
      if (access.guidanceLevel() instanceof ServerLevel level) {
         UUID emitterId = this.radarEmitterId(access);
         if (emitterId != null) {
            RadarCompatRegistry.get().removeRadarEmitter(level, emitterId);
         }
      }
   }

   private void updateCommandChaffState(ServerLevel level, @Nullable MovingTargetResolver.TargetData resolvedTarget) {
      if (this.guidanceType == MissileGuidanceType.COMMAND && this.guidanceData != null) {
         RadarIntegration.ChaffSuppression suppression = RadarCompatRegistry.get().getCommandChaffSuppression(level, this.guidanceData.networkControllerPos());
         if (suppression != null) {
            this.chaffTargetId = suppression.targetId();
            this.chaffSuppressedUntilTick = Math.max(this.chaffSuppressedUntilTick, suppression.untilTick());
         } else {
            if (level.getGameTime() < this.chaffSuppressedUntilTick && resolvedTarget != null && !resolvedTarget.id().equals(this.chaffTargetId)) {
               this.chaffSuppressedUntilTick = 0L;
               this.chaffTargetId = "";
               this.chaffActiveLastTick = false;
               this.resetTarget(resolvedTarget);
               this.logTransition(null, "command target changed; chaff suppression cancelled");
            }
         }
      }
   }

   @Nullable
   private String validateChaffExit(ServerLevel level, Vec3 missilePosition, Vec3 missileVelocity, @Nullable MovingTargetResolver.TargetData exitTarget) {
      if (!isUsableTarget(level, exitTarget)) {
         return "chaff expired without a valid target";
      } else {
         Vec3 forward = this.currentOrLaunchDirection(missileVelocity);
         if (this.guidanceType == MissileGuidanceType.COMMAND) {
            double forwardRange = exitTarget.position().subtract(missilePosition).dot(forward);
            return forwardRange < -configuredOvershootDistanceEpsilon()
               ? "command target overshot during chaff forwardRange=" + String.format(Locale.ROOT, "%.3f", forwardRange)
               : null;
         } else {
            if (this.guidanceType == MissileGuidanceType.RADAR) {
               Vec3 seekerForward = this.state == MovingTargetInterceptorNavigation.State.BOOST ? this.launchDirection : forward;
               double trackingHalfAngle = configuredRadarAcquisitionHalfAngleDegrees() + configuredRadarTrackingConePaddingDegrees();
               if (!RadarTargeting.isWithinEnvelope(missilePosition, seekerForward, exitTarget.position(), configuredRadarRange(), trackingHalfAngle)) {
                  return "radar target outside seeker envelope when chaff expired";
               }
            }

            return null;
         }
      }
   }

   private boolean acceptTarget(ServerLevel level, @Nullable MovingTargetResolver.TargetData target, boolean resetIfChanged) {
      if (!isUsableTarget(level, target)) {
         return false;
      } else {
         int age = target.ageTicks(level);
         if (!target.id().equals(this.targetId)) {
            this.resetTarget(target);
            if (!resetIfChanged) {
               this.logTransition(null, "target switch/reset id=" + target.id());
            }
         }

         this.latestTargetPosition = target.position();
         this.rawTargetVelocity = target.velocity();
         this.targetTimestamp = target.scannedTime();
         this.targetAgeTicks = age;
         double velocityAlpha = configuredTargetVelocitySmoothing();
         this.filteredTargetVelocity = this.filteredTargetVelocity.lengthSqr() < 1.0E-10
            ? this.rawTargetVelocity
            : lerp(this.filteredTargetVelocity, this.rawTargetVelocity, velocityAlpha);
         return true;
      }
   }

   @Nullable
   private MovingTargetResolver.TargetData applyRadarSeekerEnvelope(
      ServerLevel level, Vec3 missilePosition, Vec3 missileVelocity, @Nullable MovingTargetResolver.TargetData resolvedTarget
   ) {
      boolean valid = isUsableTarget(level, resolvedTarget);
      if (valid) {
         Vec3 forward = this.state == MovingTargetInterceptorNavigation.State.BOOST ? this.launchDirection : this.currentOrLaunchDirection(missileVelocity);
         double trackingHalfAngle = configuredRadarAcquisitionHalfAngleDegrees() + configuredRadarTrackingConePaddingDegrees();
         valid = RadarTargeting.isWithinEnvelope(missilePosition, forward, resolvedTarget.position(), configuredRadarRange(), trackingHalfAngle);
         if (valid) {
            UUID targetSublevelId = "sable".equalsIgnoreCase(resolvedTarget.category()) ? parseShipId(resolvedTarget.id()) : null;
            valid = RadarTargeting.hasLineOfSight(level, missilePosition, resolvedTarget.position(), targetSublevelId);
         }
      }

      if (valid) {
         this.radarLockLossTicks = 0;
         return resolvedTarget;
      } else {
         this.radarLockLossTicks++;
         if (this.latestTargetPosition != null && this.targetId != null && !this.targetId.isBlank()) {
            Vec3 coastPosition = this.latestTargetPosition.add(this.filteredTargetVelocity);
            return new MovingTargetResolver.TargetData(
               this.targetId,
               this.targetCategory,
               coastPosition,
               this.filteredTargetVelocity,
               level.getGameTime() - (long)this.radarLockLossTicks,
               false,
               "radar_coast"
            );
         } else {
            return null;
         }
      }
   }

   private boolean hasLockedRadarTarget() {
      if (this.guidanceType == MissileGuidanceType.RADAR && this.guidanceData != null) {
         MissileTargetSpec target = this.guidanceData.target();
         return target != null && target.type() == MissileTargetSpec.TargetType.ENTITY && target.entityId() != null;
      } else {
         return false;
      }
   }

   private void publishRadarRwrEmitter(MissileNavigation.FlightAccess access, Vec3 position, Vec3 forward, RadarIntegration.ThreatStage stage) {
      if (access.guidanceLevel() instanceof ServerLevel level && this.hasLockedRadarTarget()) {
         UUID emitterId = this.radarEmitterId(access);
         MissileTargetSpec target = this.guidanceData.target();
         if (emitterId != null && target != null && target.entityId() != null) {
            RadarCompatRegistry.get()
               .updateRadarEmitter(
                  level,
                  emitterId,
                  position,
                  safeNormalize(forward, this.launchDirection),
                  configuredRadarRange(),
                  configuredRadarAcquisitionHalfAngleDegrees() + configuredRadarTrackingConePaddingDegrees(),
                  target.entityId(),
                  stage
               );
            return;
         }

         return;
      }
   }

   @Nullable
   private UUID radarEmitterId(MissileNavigation.FlightAccess access) {
      if (this.guidanceData != null && this.guidanceType == MissileGuidanceType.RADAR) {
         return this.guidanceData.radarEmitterId() == null ? access.guidanceUuid() : this.guidanceData.radarEmitterId();
      } else {
         return null;
      }
   }

   private void refreshRwrEngagement(ServerLevel level, @Nullable MovingTargetResolver.TargetData target) {
      if (this.guidanceType == MissileGuidanceType.COMMAND && isUsableTarget(level, target)) {
         long gameTime = level.getGameTime();
         boolean targetChanged = !target.id().equals(this.lastRwrEngagedTargetId);
         if (targetChanged || gameTime >= this.nextRwrEngagementRefreshTime) {
            RadarCompatRegistry.get().markCommandEngaged(level, this.guidanceData == null ? null : this.guidanceData.networkControllerPos(), target);
            this.lastRwrEngagedTargetId = target.id();
            this.nextRwrEngagementRefreshTime = gameTime + 10L;
         }
      }
   }

   private static boolean isUsableTarget(ServerLevel level, @Nullable MovingTargetResolver.TargetData target) {
      return target != null && isFinite(target.position()) && isFinite(target.velocity())
         ? target.live() || target.ageTicks(level) <= configuredTargetDataTimeoutTicks()
         : false;
   }

   @Nullable
   private static UUID parseShipId(String id) {
      if (id != null && !id.isBlank()) {
         try {
            return UUID.fromString(id);
         } catch (IllegalArgumentException var2) {
            return null;
         }
      } else {
         return null;
      }
   }

   private void resetTarget(MovingTargetResolver.TargetData target) {
      this.targetId = target.id();
      this.targetCategory = target.category();
      this.filteredTargetVelocity = target.velocity();
      this.rawTargetVelocity = target.velocity();
      this.latestTargetPosition = target.position();
      this.rawInterceptPoint = target.position();
      this.smoothedInterceptPoint = target.position();
      this.previousMissDistance = Double.NaN;
      this.previousForwardRangeToTarget = Double.NaN;
      this.beganClosingTarget = false;
   }

   private Vec3 computeAimPoint(Vec3 pos, Vec3 vel) {
      if (this.latestTargetPosition == null) {
         this.interceptTimeTicks = Double.NaN;
         this.rawInterceptPoint = pos.add(this.currentOrLaunchDirection(vel).scale(16.0));
         return this.rawInterceptPoint;
      } else {
         Vec3 relative = this.latestTargetPosition.subtract(pos);
         double speed = Math.max(vel.length(), configuredMaxSpeed() * 0.5);
         double t = this.solveLeadTime(relative, this.filteredTargetVelocity, speed);
         if (!Double.isFinite(t)) {
            t = Math.min((double)configuredMaxLeadTimeTicks(), Math.max(1.0, relative.length() / Math.max(speed, 1.0E-6)));
            this.rawInterceptPoint = this.latestTargetPosition;
            this.interceptTimeTicks = Double.NaN;
            this.logTransition(null, "lead solution unavailable; using bounded direct pursuit");
         } else {
            this.rawInterceptPoint = this.latestTargetPosition.add(this.filteredTargetVelocity.scale(t));
            this.interceptTimeTicks = t;
         }

         if (!isFinite(this.rawInterceptPoint) || this.rawInterceptPoint.distanceToSqr(pos) > 1.0E10) {
            this.rawInterceptPoint = this.latestTargetPosition;
            this.interceptTimeTicks = Double.NaN;
         }

         this.smoothedInterceptPoint = this.smoothedInterceptPoint == null
            ? this.rawInterceptPoint
            : lerp(this.smoothedInterceptPoint, this.rawInterceptPoint, configuredInterceptPointSmoothing());
         return this.smoothedInterceptPoint;
      }
   }

   private double solveLeadTime(Vec3 relative, Vec3 targetVelocity, double missileSpeed) {
      double a = targetVelocity.dot(targetVelocity) - missileSpeed * missileSpeed;
      double b = 2.0 * relative.dot(targetVelocity);
      double c = relative.dot(relative);
      double t;
      if (Math.abs(a) < 1.0E-8) {
         if (Math.abs(b) < 1.0E-8) {
            return Double.NaN;
         }

         t = -c / b;
      } else {
         double discriminant = b * b - 4.0 * a * c;
         if (discriminant < 0.0) {
            return Double.NaN;
         }

         double sqrt = Math.sqrt(discriminant);
         double t1 = (-b - sqrt) / (2.0 * a);
         double t2 = (-b + sqrt) / (2.0 * a);
         t = this.smallestPositive(t1, t2);
      }

      double max = (double)configuredMaxLeadTimeTicks();
      return Double.isFinite(t) && !(t <= 0.0) ? Mth.clamp(t, 1.0, max) : Double.NaN;
   }

   private double smallestPositive(double a, double b) {
      boolean aOk = Double.isFinite(a) && a > 0.0;
      boolean bOk = Double.isFinite(b) && b > 0.0;
      if (aOk && bOk) {
         return Math.min(a, b);
      } else if (aOk) {
         return a;
      } else {
         return bOk ? b : Double.NaN;
      }
   }

   private boolean detectOvershoot(MissileNavigation.FlightAccess access, Vec3 pos, Vec3 vel) {
      if (this.latestTargetPosition == null) {
         return false;
      } else {
         double distance = pos.distanceTo(this.latestTargetPosition);
         double forwardRange = this.latestTargetPosition.subtract(pos).dot(this.currentOrLaunchDirection(vel));
         double epsilon = configuredOvershootDistanceEpsilon();
         if (!Double.isFinite(this.previousMissDistance)) {
            this.previousMissDistance = distance;
         } else if (distance < this.previousMissDistance - epsilon) {
            this.beganClosingTarget = true;
            this.previousMissDistance = distance;
         } else {
            this.previousMissDistance = distance;
         }

         if (!Double.isFinite(this.previousForwardRangeToTarget)) {
            this.previousForwardRangeToTarget = forwardRange;
            return false;
         } else if (this.previousForwardRangeToTarget >= -epsilon && forwardRange < -epsilon) {
            double previousForwardRange = this.previousForwardRangeToTarget;
            this.previousForwardRangeToTarget = forwardRange;
            this.abort(
               access,
               "overshoot detected prevForwardRange="
                  + String.format(Locale.ROOT, "%.3f", previousForwardRange)
                  + " currentForwardRange="
                  + String.format(Locale.ROOT, "%.3f", forwardRange)
            );
            return true;
         } else {
            this.previousForwardRangeToTarget = forwardRange;
            return false;
         }
      }
   }

   private MissileNavigation.Command steerToward(MissileNavigation.FlightAccess access, Vec3 vel, Vec3 desiredDirRaw) {
      Vec3 desiredDir = safeNormalize(desiredDirRaw, this.launchDirection);
      Vec3 currentDir = this.currentOrLaunchDirection(vel);
      Vec3 rotatedDir = limitTurnSafe(currentDir, desiredDir, configuredMaxTurnDegreesPerTick());
      double actualTurnDeg = angleDegrees(currentDir, rotatedDir);
      double currentSpeed = vel.length();
      double requestedSpeed = Math.min(configuredMaxSpeed(), currentSpeed + effectiveThrustAccelerationPerTick(access));
      Vec3 requestedVelocity = rotatedDir.scale(requestedSpeed);
      Vec3 requestedDelta = requestedVelocity.subtract(vel);
      Vec3 appliedDelta = clampMagnitude(requestedDelta, configuredMaxAccelerationPerTick());
      appliedDelta = this.poweredDeltaAlong(access, appliedDelta, appliedDelta.length());
      return new MissileNavigation.Command(appliedDelta, desiredDir, actualTurnDeg, requestedDelta, "steer");
   }

   private Vec3 poweredDeltaAlong(MissileNavigation.FlightAccess access, Vec3 directionOrDelta, double magnitude) {
      double thrustAcceleration = effectiveThrustAccelerationPerTick(access);
      if (access.guidanceFuelMb() > 0 && thrustAcceleration > 1.0E-9 && !(magnitude <= 0.0) && !(directionOrDelta.lengthSqr() < 1.0E-10)) {
         double max = Math.min(magnitude, configuredMaxAccelerationPerTick());
         Vec3 dir = safeNormalize(directionOrDelta, this.launchDirection);
         double throttle = Mth.clamp(max / thrustAcceleration, 0.0, 1.0);
         double availableThrottle = this.burnFuelForThrottle(access, throttle);
         return dir.scale(thrustAcceleration * availableThrottle);
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

   private Vec3 currentOrLaunchDirection(Vec3 vel) {
      return vel.length() > 1.0E-4 ? safeNormalize(vel, this.launchDirection) : this.launchDirection;
   }

   private void abort(MissileNavigation.FlightAccess access, String reason) {
      this.clearRadarRwrEmitter(access);
      access.guidanceSetFuelMb(0);
      this.abortReason = reason;
      this.transitionTo(access, MovingTargetInterceptorNavigation.State.ABORTED, "entered aborted state: " + reason);
   }

   private void transitionTo(MissileNavigation.FlightAccess access, MovingTargetInterceptorNavigation.State next, String message) {
      if (this.state != next) {
         this.state = next;
         this.syncState(access);
         this.logTransition(access, message);
      }
   }

   private void syncState(MissileNavigation.FlightAccess access) {
      access.guidanceSyncState(this.state.ordinal());
   }

   private void debug(
      MissileNavigation.FlightAccess access, Vec3 pos, Vec3 vel, @Nullable MovingTargetResolver.TargetData target, MissileNavigation.Command cmd, String phase
   ) {
      if ((Boolean)KaboomConfig.server().interceptorGuidanceDebug.get() && access.guidanceTickCount() % 5 == 0) {
         CreateKaboom.getLogger()
            .info(
               "InterceptorGuidance id={} uuid={} type={} state={} phase={} pos={} vel={} speed={} targetId={} category={} targetSource={} targetPos={} targetVel={} filteredVel={} targetAge={} interceptTime={} rawPoint={} smoothedPoint={} miss={} forwardRange={} closing={} desiredDir={} turnDeg={} requestedDelta={} appliedDelta={} boostDistance={} fuel={} fuelAtLaunch={} chaffTarget={} chaffUntil={} chaffPendingExit={} abortReason={}",
               new Object[]{
                  access.guidanceEntityId(),
                  access.guidanceUuid(),
                  this.guidanceType,
                  this.state,
                  phase,
                  fmt(pos),
                  fmt(vel),
                  String.format(Locale.ROOT, "%.3f", vel.length()),
                  this.targetId,
                  this.targetCategory,
                  target == null ? "none" : target.source(),
                  this.latestTargetPosition == null ? "none" : fmt(this.latestTargetPosition),
                  fmt(this.rawTargetVelocity),
                  fmt(this.filteredTargetVelocity),
                  this.targetAgeTicks,
                  String.format(Locale.ROOT, "%.3f", this.interceptTimeTicks),
                  this.rawInterceptPoint == null ? "none" : fmt(this.rawInterceptPoint),
                  this.smoothedInterceptPoint == null ? "none" : fmt(this.smoothedInterceptPoint),
                  String.format(Locale.ROOT, "%.3f", this.previousMissDistance),
                  String.format(Locale.ROOT, "%.3f", this.previousForwardRangeToTarget),
                  this.beganClosingTarget,
                  fmt(cmd.desiredDir()),
                  String.format(Locale.ROOT, "%.3f", cmd.actualTurnDeg()),
                  fmt(cmd.requestedDeltaV()),
                  fmt(cmd.appliedDeltaV()),
                  String.format(Locale.ROOT, "%.3f", this.accumulatedBoostDistance),
                  access.guidanceFuelMb(),
                  this.fuelAtLaunch,
                  this.chaffTargetId,
                  this.chaffSuppressedUntilTick,
                  this.chaffActiveLastTick,
                  this.abortReason
               }
            );
      }
   }

   private void logTransition(@Nullable MissileNavigation.FlightAccess access, String message) {
      if ((Boolean)KaboomConfig.server().interceptorGuidanceDebug.get()) {
         if (access == null) {
            CreateKaboom.getLogger()
               .info("InterceptorGuidance transition type={} state={} targetId={} {}", new Object[]{this.guidanceType, this.state, this.targetId, message});
         } else {
            CreateKaboom.getLogger()
               .info(
                  "InterceptorGuidance transition id={} uuid={} type={} state={} targetId={} {}",
                  new Object[]{access.guidanceEntityId(), access.guidanceUuid(), this.guidanceType, this.state, this.targetId, message}
               );
         }
      }
   }

   private static Vec3 lerp(Vec3 from, Vec3 to, double alphaRaw) {
      double alpha = Mth.clamp(alphaRaw, 0.0, 1.0);
      return from.scale(1.0 - alpha).add(to.scale(alpha));
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
            return a.scale(Math.cos(maxRad)).add(axis.cross(a).scale(Math.sin(maxRad))).add(axis.scale(axis.dot(a) * (1.0 - Math.cos(maxRad)))).normalize();
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
                  return a.scale(Math.sin((1.0 - t) * angle) / sinAngle).add(b.scale(Math.sin(t * angle) / sinAngle)).normalize();
               }
            }
         }
      } else {
         return desiredDir;
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

   private static String fmt(Vec3 v) {
      return v == null ? "null" : String.format(Locale.ROOT, "(%.3f, %.3f, %.3f)", v.x, v.y, v.z);
   }

   private static double configuredMaxAccelerationPerTick() {
      return Math.max(0.0, (double)KaboomConfig.server().maxAccelerationPerTick.getF());
   }

   private static double configuredMaxSpeed() {
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

   private static double effectiveThrustAccelerationPerTick(MissileNavigation.FlightAccess access) {
      double multiplier = Mth.clamp(access.guidanceAccelerationMultiplier(), 0.0, 1.0);
      return configuredThrustAccelerationPerTick() * multiplier;
   }

   private static double configuredOvershootDistanceEpsilon() {
      return Math.max(0.0, (double)KaboomConfig.server().overshootDistanceEpsilon.getF());
   }

   private static int configuredMaxLeadTimeTicks() {
      return Math.max(1, (Integer)KaboomConfig.server().maxLeadTimeTicks.get());
   }

   private static double configuredTargetVelocitySmoothing() {
      return Mth.clamp((double)KaboomConfig.server().targetVelocitySmoothing.getF(), 0.0, 1.0);
   }

   private static double configuredInterceptPointSmoothing() {
      return Mth.clamp((double)KaboomConfig.server().interceptPointSmoothing.getF(), 0.0, 1.0);
   }

   private static int configuredTargetDataTimeoutTicks() {
      return Math.max(0, (Integer)KaboomConfig.server().targetDataTimeoutTicks.get());
   }

   private static double configuredRadarRange() {
      return Math.max(1.0, (double)KaboomConfig.server().radarAcquisitionRangeBlocks.getF());
   }

   private static double configuredRadarAcquisitionHalfAngleDegrees() {
      return Math.max(0.0, Math.min(180.0, (double)KaboomConfig.server().radarAcquisitionHalfAngleDegrees.getF()));
   }

   private static double configuredRadarTrackingConePaddingDegrees() {
      return Math.max(0.0, (double)KaboomConfig.server().radarTrackingConePaddingDegrees.getF());
   }

   public static enum State {
      BOOST,
      INTERCEPT,
      ABORTED;
   }
}
