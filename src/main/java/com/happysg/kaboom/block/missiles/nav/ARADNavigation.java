package com.happysg.kaboom.block.missiles.nav;

import com.happysg.kaboom.CreateKaboom;
import com.happysg.kaboom.block.missiles.util.ARADTargetReference;
import com.happysg.kaboom.block.missiles.util.MissileGuidanceData;
import com.happysg.kaboom.block.missiles.util.MissileGuidanceType;
import com.happysg.kaboom.block.missiles.util.MissileTargetSpec;
import com.happysg.kaboom.config.KaboomConfig;
import com.happysg.kaboom.compat.radars.RadarCompatRegistry;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class ARADNavigation {
    private static final double BOOST_DISTANCE_BLOCKS = 30.0;
    private static final double EARLY_TARGET_LOSS_DISTANCE_BLOCKS = 100.0;
    private static final double RUNAWAY_DISTANCE_BLOCKS = 500.0;
    private static final double DIVE_START_HORIZONTAL_DISTANCE = 100.0;
    private static final double MIN_CRUISE_OFFSET = 20.0;
    private static final double MAX_CRUISE_OFFSET = 50.0;
    private static final double MIN_OFFSET_HORIZONTAL_DISTANCE = 100.0;
    private static final double MAX_OFFSET_HORIZONTAL_DISTANCE = 500.0;
    private static final double CRUISE_LOOKAHEAD_BLOCKS = 80.0;
    private static final double CRUISE_ALTITUDE_DEADBAND = 1.0;
    private static final double TERMINAL_OVERSHOOT_EPSILON = 0.5;
    private static final double EPSILON_DIR_SQR = 1.0E-10;
    private static final double NEAR_ZERO_SPEED = 1.0E-4;

    private State state = State.BOOST;
    @Nullable
    private Vec3 target;
    @Nullable
    private ARADTargetReference targetReference;
    private Vec3 launchPosition = Vec3.ZERO;
    private Vec3 launchDirection = new Vec3(0.0, 1.0, 0.0);
    private Vec3 previousGuidancePosition = Vec3.ZERO;
    private double accumulatedBoostDistance;
    private Vec3 runawayDirection = new Vec3(0.0, 1.0, 0.0);
    private Vec3 previousRunawayPosition = Vec3.ZERO;
    private double accumulatedRunawayDistance;
    private boolean runawayDetonationRequested;
    private double cruiseAltitudeY;
    @Nullable
    private Vec3 terminalAxis;
    private double previousTerminalForwardRange = Double.NaN;
    private double previousTerminalDistance = Double.NaN;
    @Nullable
    private Vec3 previousMovingTargetPosition;
    private String abortReason = "";
    private MissileNavigation.Command lastCommand = MissileNavigation.Command.none("uninitialized");

    public void initialize(Vec3 launchDirection, Vec3 launchPosition, MissileNavigation.FlightAccess access) {
        this.launchDirection = safeNormalize(launchDirection, new Vec3(0.0, 1.0, 0.0));
        this.launchPosition = launchPosition;
        this.previousGuidancePosition = launchPosition;
        this.accumulatedBoostDistance = 0.0;
        this.runawayDirection = this.launchDirection;
        this.previousRunawayPosition = launchPosition;
        this.accumulatedRunawayDistance = 0.0;
        this.runawayDetonationRequested = false;
        this.cruiseAltitudeY = launchPosition.y;
        this.terminalAxis = null;
        this.previousTerminalForwardRange = Double.NaN;
        this.previousTerminalDistance = Double.NaN;
        this.previousMovingTargetPosition = null;
        this.abortReason = "";
        this.state = State.BOOST;
        syncState(access);
    }

    public void configure(MissileGuidanceData data, Vec3 currentPosition, MissileNavigation.FlightAccess access) {
        this.target = null;
        this.targetReference = null;
        if (data == null || data.guidanceType() != MissileGuidanceType.ARAD) {
            return;
        }

        ARADTargetReference reference = data.aradTargetReference();
        if (reference != null && reference.isValid()) {
            this.targetReference = reference;
            this.target = resolveTrackedTarget(access);
            if (!isFinite(this.target)) {
                this.target = reference.noisyWorldPosition();
            }
        } else {
            MissileTargetSpec targetSpec = data.target();
            if (targetSpec == null || targetSpec.type() != MissileTargetSpec.TargetType.POINT || !isFinite(targetSpec.point())) {
                return;
            }
            this.target = targetSpec.point();
        }
        if (!isFinite(this.launchPosition)) {
            this.launchPosition = currentPosition;
        }
        this.cruiseAltitudeY = calculateCruiseAltitude(access, this.launchPosition, this.launchDirection, this.target);
        this.abortReason = "";
    }

    public MissileNavigation.Command tick(MissileNavigation.FlightAccess access, Vec3 pos, Vec3 vel) {
        if (this.state == State.RUNAWAY) {
            return tickRunaway(access, pos);
        }
        if (this.state == State.ABORTED) {
            this.lastCommand = MissileNavigation.Command.none("aborted:" + this.abortReason);
            return this.lastCommand;
        }
        if (this.targetReference != null) {
            Vec3 resolvedTarget = resolveTrackedTarget(access);
            if (!isFinite(resolvedTarget)) {
                if (isEarlyTargetLoss(pos)) {
                    enterRunaway(access, pos, vel, "designated radar was lost near launch");
                    return tickRunaway(access, pos);
                }
                abort(access, "designated radar is unavailable or no longer running");
                this.lastCommand = MissileNavigation.Command.none("aborted:" + this.abortReason);
                debug(access, pos, vel, this.lastCommand);
                return this.lastCommand;
            }
            this.target = resolvedTarget;
        }
        if (this.target == null) {
            if (isEarlyTargetLoss(pos)) {
                enterRunaway(access, pos, vel, "ARAD target metadata was missing near launch");
                return tickRunaway(access, pos);
            }
            abort(access, "ARAD target metadata is unavailable");
            this.lastCommand = MissileNavigation.Command.none("aborted:" + this.abortReason);
            debug(access, pos, vel, this.lastCommand);
            return this.lastCommand;
        }

        if (this.state == State.BOOST) {
            accumulateBoostDistance(pos);
            if (this.accumulatedBoostDistance >= BOOST_DISTANCE_BLOCKS) {
                if (horizontalDistance(pos, this.target) <= DIVE_START_HORIZONTAL_DISTANCE) {
                    enterDive(access, pos, "boost completed inside terminal range");
                } else {
                    transitionTo(access, State.CRUISE, "boost completed; entered cruise");
                }
            }
        }

        if (this.state == State.CRUISE && horizontalDistance(pos, this.target) <= DIVE_START_HORIZONTAL_DISTANCE) {
            enterDive(access, pos, "entered terminal dive");
        }

        if (this.state == State.DIVE && detectTerminalOvershoot(access, pos, vel)) {
            this.lastCommand = MissileNavigation.Command.none("overshoot:" + this.abortReason);
            debug(access, pos, vel, this.lastCommand);
            return this.lastCommand;
        }

        Vec3 desiredDirection;
        Vec3 appliedDelta;
        Vec3 requestedDelta;
        double actualTurnDegrees = 0.0;
        if (this.state == State.BOOST) {
            desiredDirection = this.launchDirection;
            appliedDelta = poweredDeltaAlong(access, desiredDirection, effectiveThrustAccelerationPerTick(access));
            requestedDelta = appliedDelta;
        } else {
            Vec3 aimPoint = this.state == State.CRUISE ? cruiseAimPoint(pos, vel) : this.target;
            MissileNavigation.Command steered = steerToward(access, vel, aimPoint.subtract(pos));
            desiredDirection = steered.desiredDir();
            appliedDelta = steered.appliedDeltaV();
            requestedDelta = steered.requestedDeltaV();
            actualTurnDegrees = steered.actualTurnDeg();
        }

        this.lastCommand = new MissileNavigation.Command(
                appliedDelta,
                desiredDirection,
                actualTurnDegrees,
                requestedDelta,
                this.state.name().toLowerCase(Locale.ROOT)
        );
        debug(access, pos, vel, this.lastCommand);
        return this.lastCommand;
    }

    public boolean isBoosting() {
        return this.state == State.BOOST || this.state == State.RUNAWAY;
    }

    public boolean shouldDetonateAfterRunaway() {
        return this.runawayDetonationRequested;
    }

    public boolean isRunaway() {
        return this.state == State.RUNAWAY;
    }

    public boolean isAborted() {
        return this.state == State.ABORTED;
    }

    @Nullable
    public Vec3 targetPosition() {
        return this.target;
    }

    public void write(CompoundTag tag) {
        tag.putString("kaboom:AradState", this.state.name());
        putVec(tag, "kaboom:AradLaunchPosition", this.launchPosition);
        putVec(tag, "kaboom:AradLaunchDirection", this.launchDirection);
        putVec(tag, "kaboom:AradPreviousGuidancePosition", this.previousGuidancePosition);
        tag.putDouble("kaboom:AradBoostDistance", this.accumulatedBoostDistance);
        putVec(tag, "kaboom:AradRunawayDirection", this.runawayDirection);
        putVec(tag, "kaboom:AradPreviousRunawayPosition", this.previousRunawayPosition);
        tag.putDouble("kaboom:AradRunawayDistance", this.accumulatedRunawayDistance);
        tag.putDouble("kaboom:AradCruiseAltitudeY", this.cruiseAltitudeY);
        tag.putDouble("kaboom:AradPreviousTerminalForwardRange", this.previousTerminalForwardRange);
        tag.putDouble("kaboom:AradPreviousTerminalDistance", this.previousTerminalDistance);
        tag.putString("kaboom:AradAbortReason", this.abortReason == null ? "" : this.abortReason);
        if (this.target != null) {
            tag.putBoolean("kaboom:AradHasTarget", true);
            putVec(tag, "kaboom:AradTarget", this.target);
        } else {
            tag.putBoolean("kaboom:AradHasTarget", false);
        }
        if (this.terminalAxis != null) {
            tag.putBoolean("kaboom:AradHasTerminalAxis", true);
            putVec(tag, "kaboom:AradTerminalAxis", this.terminalAxis);
        } else {
            tag.putBoolean("kaboom:AradHasTerminalAxis", false);
        }
        if (this.previousMovingTargetPosition != null) {
            tag.putBoolean("kaboom:AradHasPreviousMovingTarget", true);
            putVec(tag, "kaboom:AradPreviousMovingTarget", this.previousMovingTargetPosition);
        } else {
            tag.putBoolean("kaboom:AradHasPreviousMovingTarget", false);
        }
    }

    public void read(CompoundTag tag, MissileNavigation.FlightAccess access, Vec3 currentPosition) {
        if (tag.contains("kaboom:AradState")) {
            try {
                this.state = State.valueOf(tag.getString("kaboom:AradState"));
            } catch (IllegalArgumentException ignored) {
                this.state = State.BOOST;
            }
        }

        this.launchPosition = readVec(tag, "kaboom:AradLaunchPosition", currentPosition);
        this.launchDirection = safeNormalize(
                readVec(tag, "kaboom:AradLaunchDirection", this.launchDirection),
                new Vec3(0.0, 1.0, 0.0)
        );
        this.previousGuidancePosition = readVec(tag, "kaboom:AradPreviousGuidancePosition", currentPosition);
        this.accumulatedBoostDistance = tag.contains("kaboom:AradBoostDistance")
                ? Math.max(0.0, tag.getDouble("kaboom:AradBoostDistance"))
                : 0.0;
        this.runawayDirection = safeNormalize(
                readVec(tag, "kaboom:AradRunawayDirection", this.launchDirection),
                this.launchDirection
        );
        this.previousRunawayPosition = readVec(tag, "kaboom:AradPreviousRunawayPosition", currentPosition);
        this.accumulatedRunawayDistance = tag.contains("kaboom:AradRunawayDistance")
                ? Math.max(0.0, tag.getDouble("kaboom:AradRunawayDistance"))
                : 0.0;
        this.runawayDetonationRequested = false;
        if (tag.contains("kaboom:AradCruiseAltitudeY")) {
            this.cruiseAltitudeY = tag.getDouble("kaboom:AradCruiseAltitudeY");
        } else if (this.target != null) {
            this.cruiseAltitudeY = calculateCruiseAltitude(access, this.launchPosition, this.launchDirection, this.target);
        }
        this.previousTerminalForwardRange = tag.contains("kaboom:AradPreviousTerminalForwardRange")
                ? tag.getDouble("kaboom:AradPreviousTerminalForwardRange")
                : Double.NaN;
        this.previousTerminalDistance = tag.contains("kaboom:AradPreviousTerminalDistance")
                ? tag.getDouble("kaboom:AradPreviousTerminalDistance")
                : Double.NaN;
        this.abortReason = tag.contains("kaboom:AradAbortReason") ? tag.getString("kaboom:AradAbortReason") : "";
        if (tag.contains("kaboom:AradHasTarget")) {
            this.target = tag.getBoolean("kaboom:AradHasTarget")
                    ? readVec(tag, "kaboom:AradTarget", this.target)
                    : null;
        }
        this.terminalAxis = tag.getBoolean("kaboom:AradHasTerminalAxis")
                ? safeNormalize(readVec(tag, "kaboom:AradTerminalAxis", null), null)
                : null;
        this.previousMovingTargetPosition = tag.getBoolean("kaboom:AradHasPreviousMovingTarget")
                ? readVec(tag, "kaboom:AradPreviousMovingTarget", null)
                : null;
        if (this.state == State.RUNAWAY) {
            this.target = null;
            this.targetReference = null;
        }
        syncState(access);
    }

    private boolean isEarlyTargetLoss(Vec3 pos) {
        return this.state == State.BOOST
                || (isFinite(this.launchPosition)
                && this.launchPosition.distanceToSqr(pos)
                <= EARLY_TARGET_LOSS_DISTANCE_BLOCKS * EARLY_TARGET_LOSS_DISTANCE_BLOCKS);
    }

    private void enterRunaway(
            MissileNavigation.FlightAccess access,
            Vec3 pos,
            Vec3 velocity,
            String reason
    ) {
        this.runawayDirection = currentOrLaunchDirection(velocity);
        this.previousRunawayPosition = pos;
        this.accumulatedRunawayDistance = 0.0;
        this.runawayDetonationRequested = false;
        this.target = null;
        this.targetReference = null;
        this.abortReason = reason;
        transitionTo(access, State.RUNAWAY, "entered early target-loss runaway: " + reason);
    }

    private MissileNavigation.Command tickRunaway(MissileNavigation.FlightAccess access, Vec3 pos) {
        if (isFinite(this.previousRunawayPosition)) {
            double traveled = this.previousRunawayPosition.distanceTo(pos);
            if (Double.isFinite(traveled)) {
                this.accumulatedRunawayDistance += traveled;
            }
        }
        this.previousRunawayPosition = pos;

        if (this.accumulatedRunawayDistance >= RUNAWAY_DISTANCE_BLOCKS) {
            access.guidanceSetFuelMb(0);
            if (!access.guidanceHasFuze()) {
                abort(access, "runaway completed without a fuze");
                this.lastCommand = MissileNavigation.Command.none("aborted:" + this.abortReason);
                debug(access, pos, access.guidanceVelocity(), this.lastCommand);
                return this.lastCommand;
            }
            this.runawayDetonationRequested = true;
            this.lastCommand = MissileNavigation.Command.none("runaway_detonation");
            debug(access, pos, access.guidanceVelocity(), this.lastCommand);
            return this.lastCommand;
        }

        Vec3 appliedDelta = poweredDeltaAlong(
                access,
                this.runawayDirection,
                effectiveThrustAccelerationPerTick(access)
        );
        this.lastCommand = new MissileNavigation.Command(
                appliedDelta,
                this.runawayDirection,
                0.0,
                appliedDelta,
                "runaway"
        );
        debug(access, pos, access.guidanceVelocity(), this.lastCommand);
        return this.lastCommand;
    }

    private void accumulateBoostDistance(Vec3 pos) {
        if (!isFinite(this.previousGuidancePosition)) {
            this.previousGuidancePosition = pos;
            return;
        }

        double traveled = this.previousGuidancePosition.distanceTo(pos);
        if (Double.isFinite(traveled)) {
            this.accumulatedBoostDistance += traveled;
        }
        this.previousGuidancePosition = pos;
    }

    private Vec3 cruiseAimPoint(Vec3 pos, Vec3 vel) {
        Vec3 horizontalDelta = new Vec3(this.target.x - pos.x, 0.0, this.target.z - pos.z);
        double horizontalRange = horizontalDelta.length();
        Vec3 fallback = horizontalDirection(currentOrLaunchDirection(vel), this.launchDirection);
        Vec3 horizontalDirection = horizontalRange > NEAR_ZERO_SPEED
                ? horizontalDelta.scale(1.0 / horizontalRange)
                : fallback;
        double lookahead = Math.min(CRUISE_LOOKAHEAD_BLOCKS, Math.max(1.0, horizontalRange));
        double altitudeError = this.cruiseAltitudeY - pos.y;
        double correctedAltitudeError = Math.abs(altitudeError) <= CRUISE_ALTITUDE_DEADBAND
                ? 0.0
                : Math.copySign(Math.abs(altitudeError) - CRUISE_ALTITUDE_DEADBAND, altitudeError);
        double maxVerticalOffset = Math.max(4.0, lookahead * 0.35);
        double verticalOffset = Mth.clamp(correctedAltitudeError, -maxVerticalOffset, maxVerticalOffset);
        return pos.add(horizontalDirection.scale(lookahead)).add(0.0, verticalOffset, 0.0);
    }

    private void enterDive(MissileNavigation.FlightAccess access, Vec3 pos, String message) {
        this.terminalAxis = safeNormalize(this.target.subtract(pos), this.launchDirection);
        this.previousTerminalForwardRange = this.target.subtract(pos).dot(this.terminalAxis);
        this.previousTerminalDistance = this.target.distanceTo(pos);
        this.previousMovingTargetPosition = this.targetReference == null ? null : this.target;
        transitionTo(access, State.DIVE, message);
    }

    private boolean detectTerminalOvershoot(MissileNavigation.FlightAccess access, Vec3 pos, Vec3 vel) {
        if (this.targetReference != null) {
            return detectMovingTerminalOvershoot(access, pos, vel);
        }
        if (this.terminalAxis == null) {
            this.terminalAxis = safeNormalize(this.target.subtract(pos), this.launchDirection);
        }
        double forwardRange = this.target.subtract(pos).dot(this.terminalAxis);
        boolean crossedTargetPlane = Double.isFinite(this.previousTerminalForwardRange)
                && this.previousTerminalForwardRange >= 0.0
                && forwardRange < -TERMINAL_OVERSHOOT_EPSILON;
        this.previousTerminalForwardRange = forwardRange;
        if (crossedTargetPlane) {
            abort(access, "crossed target plane");
        }
        return crossedTargetPlane;
    }

    private boolean detectMovingTerminalOvershoot(MissileNavigation.FlightAccess access, Vec3 pos, Vec3 vel) {
        double distance = this.target.distanceTo(pos);
        Vec3 targetVelocity = this.previousMovingTargetPosition == null
                ? Vec3.ZERO
                : this.target.subtract(this.previousMovingTargetPosition);
        Vec3 toTarget = this.target.subtract(pos);
        Vec3 relativeVelocity = vel.subtract(targetVelocity);
        boolean movingAway = toTarget.lengthSqr() > EPSILON_DIR_SQR && relativeVelocity.dot(toTarget) <= 0.0;
        boolean passedClosestApproach = Double.isFinite(this.previousTerminalDistance)
                && distance > this.previousTerminalDistance + TERMINAL_OVERSHOOT_EPSILON;
        this.previousTerminalDistance = distance;
        this.previousMovingTargetPosition = this.target;
        if (passedClosestApproach && movingAway) {
            abort(access, "passed moving target closest approach");
            return true;
        }
        return false;
    }

    @Nullable
    private Vec3 resolveTrackedTarget(MissileNavigation.FlightAccess access) {
        if (this.targetReference == null || !(access.guidanceLevel() instanceof ServerLevel serverLevel)) {
            return null;
        }
        return RadarCompatRegistry.get().resolveAradTarget(serverLevel, this.targetReference);
    }

    private MissileNavigation.Command steerToward(
            MissileNavigation.FlightAccess access,
            Vec3 velocity,
            Vec3 desiredDirectionRaw
    ) {
        Vec3 desiredDirection = safeNormalize(desiredDirectionRaw, this.launchDirection);
        Vec3 currentDirection = currentOrLaunchDirection(velocity);
        Vec3 rotatedDirection = limitTurnSafe(currentDirection, desiredDirection, MissileNavigation.configuredTurnRateDegreesPerTick(velocity));
        double actualTurnDegrees = angleDegrees(currentDirection, rotatedDirection);
        double currentSpeed = velocity.length();
        double requestedSpeed = Math.min(configuredMaxSpeed(), currentSpeed + effectiveThrustAccelerationPerTick(access));
        Vec3 requestedVelocity = rotatedDirection.scale(requestedSpeed);
        Vec3 requestedDelta = requestedVelocity.subtract(velocity);
        Vec3 limitedDelta = clampMagnitude(requestedDelta, configuredMaxAccelerationPerTick());
        Vec3 appliedDelta = poweredDeltaAlong(access, limitedDelta, limitedDelta.length());
        return new MissileNavigation.Command(appliedDelta, desiredDirection, actualTurnDegrees, requestedDelta, "arad_steer");
    }

    private Vec3 poweredDeltaAlong(MissileNavigation.FlightAccess access, Vec3 directionOrDelta, double magnitude) {
        double thrustAcceleration = effectiveThrustAccelerationPerTick(access);
        if (access.guidanceFuelMb() <= 0
                || thrustAcceleration <= 1.0E-9
                || magnitude <= 0.0
                || directionOrDelta.lengthSqr() < EPSILON_DIR_SQR) {
            return Vec3.ZERO;
        }

        double limitedMagnitude = Math.min(magnitude, configuredMaxAccelerationPerTick());
        Vec3 direction = safeNormalize(directionOrDelta, this.launchDirection);
        double throttle = Mth.clamp(limitedMagnitude / thrustAcceleration, 0.0, 1.0);
        double availableThrottle = burnFuelForThrottle(access, throttle);
        return direction.scale(thrustAcceleration * availableThrottle);
    }

    private double burnFuelForThrottle(MissileNavigation.FlightAccess access, double throttleRaw) {
        double throttle = Mth.clamp(throttleRaw, 0.0, 1.0);
        if (access.guidanceFuelMb() <= 0 || throttle <= 0.0) {
            return 0.0;
        }

        int burnAtFull = Math.max(1, KaboomConfig.server().maxFuelBurnPerTick.get());
        int requestedBurn = Math.max(1, (int) Math.ceil(burnAtFull * throttle));
        if (access.guidanceFuelMb() < requestedBurn) {
            throttle *= (double) access.guidanceFuelMb() / requestedBurn;
            requestedBurn = access.guidanceFuelMb();
        }
        access.guidanceBurnFuel(requestedBurn);
        return throttle;
    }

    private Vec3 currentOrLaunchDirection(Vec3 velocity) {
        return velocity.length() > NEAR_ZERO_SPEED
                ? safeNormalize(velocity, this.launchDirection)
                : this.launchDirection;
    }

    private void abort(MissileNavigation.FlightAccess access, String reason) {
        access.guidanceSetFuelMb(0);
        this.abortReason = reason;
        transitionTo(access, State.ABORTED, "entered aborted state: " + reason);
    }

    private void transitionTo(MissileNavigation.FlightAccess access, State next, String message) {
        if (this.state == next) {
            return;
        }
        this.state = next;
        syncState(access);
        if (KaboomConfig.server().missileGuidanceDebug.get()) {
            CreateKaboom.getLogger().info(
                    "ARADNavigation transition id={} uuid={} state={} {}",
                    access.guidanceEntityId(),
                    access.guidanceUuid(),
                    this.state,
                    message
            );
        }
    }

    private void syncState(MissileNavigation.FlightAccess access) {
        access.guidanceSyncState(this.state.ordinal());
    }

    private void debug(
            MissileNavigation.FlightAccess access,
            Vec3 pos,
            Vec3 vel,
            MissileNavigation.Command command
    ) {
        if (!KaboomConfig.server().missileGuidanceDebug.get() || access.guidanceTickCount() % 5 != 0) {
            return;
        }
        double horizontalRange = this.target == null ? Double.NaN : horizontalDistance(pos, this.target);
        CreateKaboom.getLogger().info(
                "ARADNavigation id={} uuid={} state={} pos={} target={} vel={} launchDir={} boostDistance={} runawayDistance={} cruiseY={} horizontalRange={} terminalForwardRange={} desiredDir={} turnDeg={} appliedDelta={} fuel={} abortReason={}",
                access.guidanceEntityId(),
                access.guidanceUuid(),
                this.state,
                format(pos),
                this.target == null ? "none" : format(this.target),
                format(vel),
                format(this.launchDirection),
                String.format(Locale.ROOT, "%.3f", this.accumulatedBoostDistance),
                String.format(Locale.ROOT, "%.3f", this.accumulatedRunawayDistance),
                String.format(Locale.ROOT, "%.3f", this.cruiseAltitudeY),
                String.format(Locale.ROOT, "%.3f", horizontalRange),
                String.format(Locale.ROOT, "%.3f", this.previousTerminalForwardRange),
                format(command.desiredDir()),
                String.format(Locale.ROOT, "%.3f", command.actualTurnDeg()),
                format(command.appliedDeltaV()),
                access.guidanceFuelMb(),
                this.abortReason
        );
    }

    static double cruiseOffsetForHorizontalDistance(double horizontalDistance) {
        double interpolation = Mth.clamp(
                (horizontalDistance - MIN_OFFSET_HORIZONTAL_DISTANCE)
                        / (MAX_OFFSET_HORIZONTAL_DISTANCE - MIN_OFFSET_HORIZONTAL_DISTANCE),
                0.0,
                1.0
        );
        return Mth.lerp(interpolation, MIN_CRUISE_OFFSET, MAX_CRUISE_OFFSET);
    }

    private static double calculateCruiseAltitude(
            MissileNavigation.FlightAccess access,
            Vec3 launchPosition,
            Vec3 launchDirection,
            Vec3 target
    ) {
        double cruiseAltitude = launchPosition.y;
        if (launchDirection.y > 0.0 || target.y > launchPosition.y) {
            cruiseAltitude = target.y + cruiseOffsetForHorizontalDistance(horizontalDistance(launchPosition, target));
        }
        int minimumY = access.guidanceLevel().getMinBuildHeight() + 2;
        int maximumY = access.guidanceLevel().getMaxBuildHeight() - 2;
        return Mth.clamp(cruiseAltitude, minimumY, maximumY);
    }

    private static Vec3 horizontalDirection(Vec3 direction, Vec3 fallback) {
        Vec3 horizontal = new Vec3(direction.x, 0.0, direction.z);
        if (horizontal.lengthSqr() >= EPSILON_DIR_SQR) {
            return horizontal.normalize();
        }
        Vec3 fallbackHorizontal = new Vec3(fallback.x, 0.0, fallback.z);
        return fallbackHorizontal.lengthSqr() >= EPSILON_DIR_SQR
                ? fallbackHorizontal.normalize()
                : new Vec3(1.0, 0.0, 0.0);
    }

    private static double horizontalDistance(Vec3 first, Vec3 second) {
        double deltaX = second.x - first.x;
        double deltaZ = second.z - first.z;
        return Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
    }

    private static Vec3 clampMagnitude(Vec3 vector, double maximum) {
        double length = vector.length();
        return length > maximum && length > 1.0E-9 ? vector.scale(maximum / length) : vector;
    }

    private static double angleDegrees(Vec3 first, Vec3 second) {
        if (first.lengthSqr() < EPSILON_DIR_SQR || second.lengthSqr() < EPSILON_DIR_SQR) {
            return 0.0;
        }
        double dot = Mth.clamp(first.normalize().dot(second.normalize()), -1.0, 1.0);
        return Math.toDegrees(Math.acos(dot));
    }

    private static Vec3 limitTurnSafe(Vec3 currentDirection, Vec3 desiredDirection, double maximumTurnDegrees) {
        if (currentDirection.lengthSqr() < EPSILON_DIR_SQR || desiredDirection.lengthSqr() < EPSILON_DIR_SQR) {
            return desiredDirection;
        }

        Vec3 current = currentDirection.normalize();
        Vec3 desired = desiredDirection.normalize();
        double dot = Mth.clamp(current.dot(desired), -1.0, 1.0);
        double maximumRadians = Math.toRadians(maximumTurnDegrees);
        if (dot < -0.9995) {
            Vec3 axis = current.cross(new Vec3(0.0, 1.0, 0.0));
            if (axis.lengthSqr() < EPSILON_DIR_SQR) {
                axis = current.cross(new Vec3(1.0, 0.0, 0.0));
            }
            axis = axis.normalize();
            return current.scale(Math.cos(maximumRadians))
                    .add(axis.cross(current).scale(Math.sin(maximumRadians)))
                    .normalize();
        }

        double angle = Math.acos(dot);
        if (angle < 1.0E-6 || angle <= maximumRadians) {
            return desired;
        }

        double interpolation = maximumRadians / angle;
        double sinAngle = Math.sin(angle);
        double currentWeight = Math.sin((1.0 - interpolation) * angle) / sinAngle;
        double desiredWeight = Math.sin(interpolation * angle) / sinAngle;
        return current.scale(currentWeight).add(desired.scale(desiredWeight)).normalize();
    }

    private static Vec3 safeNormalize(@Nullable Vec3 vector, @Nullable Vec3 fallback) {
        if (isFinite(vector) && vector.lengthSqr() >= EPSILON_DIR_SQR) {
            return vector.normalize();
        }
        if (isFinite(fallback) && fallback.lengthSqr() >= EPSILON_DIR_SQR) {
            return fallback.normalize();
        }
        return new Vec3(0.0, 1.0, 0.0);
    }

    private static boolean isFinite(@Nullable Vec3 vector) {
        return vector != null
                && Double.isFinite(vector.x)
                && Double.isFinite(vector.y)
                && Double.isFinite(vector.z);
    }

    private static void putVec(CompoundTag tag, String key, Vec3 value) {
        CompoundTag vectorTag = new CompoundTag();
        vectorTag.putDouble("X", value.x);
        vectorTag.putDouble("Y", value.y);
        vectorTag.putDouble("Z", value.z);
        tag.put(key, vectorTag);
    }

    @Nullable
    private static Vec3 readVec(CompoundTag tag, String key, @Nullable Vec3 fallback) {
        if (!tag.contains(key)) {
            return fallback;
        }
        CompoundTag vectorTag = tag.getCompound(key);
        Vec3 value = new Vec3(vectorTag.getDouble("X"), vectorTag.getDouble("Y"), vectorTag.getDouble("Z"));
        return isFinite(value) ? value : fallback;
    }

    private static String format(@Nullable Vec3 vector) {
        return vector == null ? "null" : String.format(Locale.ROOT, "(%.3f, %.3f, %.3f)", vector.x, vector.y, vector.z);
    }

    private static double configuredMaxAccelerationPerTick() {
        return Math.max(0.0, KaboomConfig.server().maxAccelerationPerTick.getF());
    }

    private static double configuredMaxSpeed() {
        return Math.max(0.0, KaboomConfig.server().maxSpeed.getF());
    }

    private static double configuredThrustAccelerationPerTick() {
        return Math.max(0.0, KaboomConfig.server().thrustAccelerationPerTick.getF());
    }

    private static double effectiveThrustAccelerationPerTick(MissileNavigation.FlightAccess access) {
        return Math.max(0.0, access.guidanceAccelerationPerTick());
    }

    public enum State {
        BOOST,
        CRUISE,
        DIVE,
        ABORTED,
        RUNAWAY
    }
}
