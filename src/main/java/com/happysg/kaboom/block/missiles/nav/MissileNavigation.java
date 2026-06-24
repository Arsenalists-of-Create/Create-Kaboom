package com.happysg.kaboom.block.missiles.nav;

import com.happysg.kaboom.CreateKaboom;
import com.happysg.kaboom.block.missiles.util.MissileGuidanceData;
import com.happysg.kaboom.block.missiles.util.MissileTargetSpec;
import com.happysg.kaboom.config.KaboomConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.UUID;

public final class MissileNavigation {

    private static final double BOOST_DISTANCE_BLOCKS = 30.0;
    private static final double EPSILON_DIR_SQR = 1.0e-10;
    private static final double NEAR_ZERO_SPEED = 1.0e-4;

    public enum State {
        BOOST,
        DIRECT_TERMINAL,
        CLIMB_OR_CRUISE,
        TERMINAL_DIVE,
        ABORTED
    }

    public record Command(Vec3 appliedDeltaV, Vec3 desiredDir, double actualTurnDeg, Vec3 requestedDeltaV, String reason) {
        public static Command none(String reason) {
            return new Command(Vec3.ZERO, Vec3.ZERO, 0.0, Vec3.ZERO, reason);
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
        void guidanceSetFuelMb(int mb);
        void guidanceBurnFuel(int mb);
        void guidanceSyncState(int stateOrdinal);
    }

    private State state = State.BOOST;
    @Nullable
    private Vec3 target = null;
    private Vec3 launchDirection = new Vec3(0, 1, 0);
    private Vec3 previousGuidancePosition = Vec3.ZERO;
    private double accumulatedBoostDistance = 0.0;
    private double previousTargetDistance = Double.NaN;
    private boolean beganClosingTarget = false;
    private String abortReason = "";
    private Command lastCommand = Command.none("uninitialized");

    public void initialize(Vec3 launchDirection, Vec3 launchPosition, FlightAccess access) {
        this.launchDirection = safeNormalize(launchDirection, new Vec3(0, 1, 0));
        this.previousGuidancePosition = launchPosition;
        this.accumulatedBoostDistance = 0.0;
        this.state = State.BOOST;
        this.abortReason = "";
        this.beganClosingTarget = false;
        this.previousTargetDistance = target == null ? Double.NaN : launchPosition.distanceTo(target);
        syncState(access);
    }

    public void configureStationaryTarget(MissileGuidanceData data, Vec3 currentPosition) {
        target = null;

        MissileTargetSpec spec = data.target();
        if (spec == null || spec.type() != MissileTargetSpec.TargetType.POINT) {
            return;
        }

        Vec3 point = spec.point();
        if (point == null || !isFinite(point)) return;

        target = point;
        previousTargetDistance = currentPosition.distanceTo(target);
        beganClosingTarget = false;
        abortReason = "";
    }

    public Command tick(FlightAccess access, Vec3 pos, Vec3 vel) {
        if (target == null) {
            lastCommand = Command.none("no_target");
            return lastCommand;
        }

        if (state == State.ABORTED) {
            lastCommand = Command.none("aborted:" + abortReason);
            return lastCommand;
        }

        if (state == State.BOOST) {
            accumulateBoostDistance(pos);
            if (accumulatedBoostDistance >= BOOST_DISTANCE_BLOCKS) {
                completeBoost(access, pos);
            }
        }

        if (state == State.DIRECT_TERMINAL || state == State.TERMINAL_DIVE) {
            if (detectOvershoot(access, pos)) {
                lastCommand = Command.none("overshoot:" + abortReason);
                debug(access, pos, vel, lastCommand);
                return lastCommand;
            }
        }

        Vec3 aimPoint;
        if (state == State.BOOST) {
            aimPoint = pos.add(launchDirection);
        } else if (state == State.CLIMB_OR_CRUISE) {
            double horizontalRange = horizontalDistance(pos, target);
            if (horizontalRange <= effectiveTerminalDiveStartHorizontalDistance(pos, target)) {
                transitionTo(access, State.TERMINAL_DIVE, "entered terminal dive");
                previousTargetDistance = pos.distanceTo(target);
                beganClosingTarget = false;
                aimPoint = target;
            } else {
                aimPoint = cruiseAimPoint(access, pos, target);
            }
        } else {
            aimPoint = target;
        }

        Vec3 desiredDir = desiredDirection(access, pos, aimPoint);
        Vec3 appliedDelta;
        Vec3 requestedDelta;
        double actualTurnDeg = 0.0;

        if (state == State.BOOST) {
            desiredDir = launchDirection;
            appliedDelta = poweredDeltaAlong(access, desiredDir, configuredThrustAccelerationPerTick());
            requestedDelta = appliedDelta;
        } else {
            Command steered = steerToward(access, vel, desiredDir);
            appliedDelta = steered.appliedDeltaV();
            requestedDelta = steered.requestedDeltaV();
            actualTurnDeg = steered.actualTurnDeg();
            desiredDir = steered.desiredDir();
        }

        Command cmd = new Command(appliedDelta, desiredDir, actualTurnDeg, requestedDelta, state.name().toLowerCase(Locale.ROOT));
        lastCommand = cmd;
        debug(access, pos, vel, cmd);
        return cmd;
    }

    public void write(CompoundTag tag) {
        tag.putString("kaboom:GuidanceState", state.name());
        putVec(tag, "kaboom:LaunchDirection", launchDirection);
        putVec(tag, "kaboom:PreviousGuidancePosition", previousGuidancePosition);
        tag.putDouble("kaboom:AccumulatedBoostDistance", accumulatedBoostDistance);
        tag.putDouble("kaboom:PreviousTargetDistance", previousTargetDistance);
        tag.putBoolean("kaboom:BeganClosingTarget", beganClosingTarget);
        tag.putString("kaboom:AbortReason", abortReason == null ? "" : abortReason);
        if (target != null) {
            tag.putBoolean("kaboom:HasNavTarget", true);
            putVec(tag, "kaboom:NavTarget", target);
        } else {
            tag.putBoolean("kaboom:HasNavTarget", false);
        }
    }

    public void read(CompoundTag tag, FlightAccess access, Vec3 currentPosition) {
        if (tag.contains("kaboom:GuidanceState")) {
            try {
                state = State.valueOf(tag.getString("kaboom:GuidanceState"));
            } catch (IllegalArgumentException ignored) {
                state = State.BOOST;
            }
        }
        syncState(access);

        launchDirection = safeNormalize(readVec(tag, "kaboom:LaunchDirection", launchDirection), new Vec3(0, 1, 0));
        previousGuidancePosition = readVec(tag, "kaboom:PreviousGuidancePosition", currentPosition);
        accumulatedBoostDistance = tag.contains("kaboom:AccumulatedBoostDistance") ? tag.getDouble("kaboom:AccumulatedBoostDistance") : 0.0;
        previousTargetDistance = tag.contains("kaboom:PreviousTargetDistance") ? tag.getDouble("kaboom:PreviousTargetDistance") : Double.NaN;
        beganClosingTarget = tag.getBoolean("kaboom:BeganClosingTarget");
        abortReason = tag.contains("kaboom:AbortReason") ? tag.getString("kaboom:AbortReason") : "";
        target = tag.getBoolean("kaboom:HasNavTarget") ? readVec(tag, "kaboom:NavTarget", null) : null;
    }

    public Vec3 launchDirection() {
        return launchDirection;
    }

    public int stateOrdinal() {
        return state.ordinal();
    }

    private void accumulateBoostDistance(Vec3 pos) {
        if (!isFinite(previousGuidancePosition)) {
            previousGuidancePosition = pos;
            return;
        }
        double traveled = previousGuidancePosition.distanceTo(pos);
        if (Double.isFinite(traveled)) {
            accumulatedBoostDistance += traveled;
        }
        previousGuidancePosition = pos;
    }

    private void completeBoost(FlightAccess access, Vec3 pos) {
        double range = pos.distanceTo(target);
        previousTargetDistance = range;
        beganClosingTarget = false;
        logTransition(access, "boost completed range=" + String.format(Locale.ROOT, "%.2f", range));

        if (range <= configuredCruiseActivationDistance()) {
            transitionTo(access, State.DIRECT_TERMINAL, "entered direct terminal");
        } else {
            transitionTo(access, State.CLIMB_OR_CRUISE, "entered climb/cruise");
        }
    }

    private boolean detectOvershoot(FlightAccess access, Vec3 pos) {
        double distance = pos.distanceTo(target);
        double epsilon = configuredOvershootDistanceEpsilon();

        if (!Double.isFinite(previousTargetDistance)) {
            previousTargetDistance = distance;
            return false;
        }

        if (distance < previousTargetDistance - epsilon) {
            beganClosingTarget = true;
            previousTargetDistance = distance;
            return false;
        }

        if (beganClosingTarget && distance > previousTargetDistance + epsilon) {
            abort(access, "overshoot detected prevDistance=" + String.format(Locale.ROOT, "%.3f", previousTargetDistance)
                    + " currentDistance=" + String.format(Locale.ROOT, "%.3f", distance));
            previousTargetDistance = distance;
            return true;
        }

        previousTargetDistance = distance;
        return false;
    }

    private void abort(FlightAccess access, String reason) {
        access.guidanceSetFuelMb(0);
        abortReason = reason;
        transitionTo(access, State.ABORTED, "entered aborted state: " + reason);
    }

    private Vec3 cruiseAimPoint(FlightAccess access, Vec3 pos, Vec3 target) {
        Vec3 horizontal = new Vec3(target.x - pos.x, 0.0, target.z - pos.z);
        double horizontalDistance = horizontal.length();
        Vec3 horizontalDir = horizontalDistance > NEAR_ZERO_SPEED ? horizontal.scale(1.0 / horizontalDistance) : currentOrLaunchDirection(access.guidanceVelocity());

        double lookahead = Math.min(configuredCruiseLookaheadDistance(), Math.max(1.0, horizontalDistance));
        double cruiseY = configuredCruiseAltitudeY(access.guidanceLevel());
        double deadband = configuredCruiseAltitudeDeadband();
        double yError = cruiseY - pos.y;
        double correctedError = Math.abs(yError) <= deadband ? 0.0 : Math.copySign(Math.abs(yError) - deadband, yError);
        double maxVerticalOffset = Math.max(4.0, lookahead * 0.35);
        double verticalOffset = Mth.clamp(correctedError, -maxVerticalOffset, maxVerticalOffset);

        return pos.add(horizontalDir.scale(lookahead)).add(0.0, verticalOffset, 0.0);
    }

    private Command steerToward(FlightAccess access, Vec3 vel, Vec3 desiredDirRaw) {
        Vec3 desiredDir = safeNormalize(desiredDirRaw, launchDirection);
        Vec3 currentDir = currentOrLaunchDirection(vel);
        Vec3 rotatedDir = limitTurnSafe(currentDir, desiredDir, configuredMaxTurnDegreesPerTick());
        double actualTurnDeg = angleDegrees(currentDir, rotatedDir);

        double currentSpeed = vel.length();
        double requestedSpeed = Math.min(configuredMaxSpeed(), currentSpeed + configuredThrustAccelerationPerTick());
        Vec3 requestedVelocity = rotatedDir.scale(requestedSpeed);
        Vec3 requestedDelta = requestedVelocity.subtract(vel);
        Vec3 appliedDelta = clampMagnitude(requestedDelta, configuredMaxAccelerationPerTick());
        appliedDelta = poweredDeltaAlong(access, appliedDelta, appliedDelta.length());

        return new Command(appliedDelta, desiredDir, actualTurnDeg, requestedDelta, "steer");
    }

    private Vec3 poweredDeltaAlong(FlightAccess access, Vec3 directionOrDelta, double magnitude) {
        if (access.guidanceFuelMb() <= 0 || magnitude <= 0.0 || directionOrDelta.lengthSqr() < EPSILON_DIR_SQR) {
            return Vec3.ZERO;
        }

        double max = Math.min(magnitude, configuredMaxAccelerationPerTick());
        Vec3 dir = safeNormalize(directionOrDelta, launchDirection);
        double throttle = configuredThrustAccelerationPerTick() <= 1.0e-9
                ? 0.0
                : Mth.clamp(max / configuredThrustAccelerationPerTick(), 0.0, 1.0);
        double availableThrottle = burnFuelForThrottle(access, throttle);
        return dir.scale(configuredThrustAccelerationPerTick() * availableThrottle);
    }

    private double burnFuelForThrottle(FlightAccess access, double throttleRaw) {
        double throttle = Mth.clamp(throttleRaw, 0.0, 1.0);
        if (access.guidanceFuelMb() <= 0 || throttle <= 0.0) {
            return 0.0;
        }

        int burnAtFull = Math.max(1, KaboomConfig.server().maxFuelBurnPerTick.get());
        int requestedBurn = Math.max(1, (int) Math.ceil(burnAtFull * throttle));
        if (access.guidanceFuelMb() < requestedBurn) {
            double scale = access.guidanceFuelMb() / (double) requestedBurn;
            requestedBurn = access.guidanceFuelMb();
            throttle *= scale;
        }

        access.guidanceBurnFuel(requestedBurn);
        return access.guidanceFuelMb() >= 0 ? throttle : 0.0;
    }

    private Vec3 desiredDirection(FlightAccess access, Vec3 pos, Vec3 aimPoint) {
        return safeNormalize(aimPoint.subtract(pos), currentOrLaunchDirection(access.guidanceVelocity()));
    }

    private Vec3 currentOrLaunchDirection(Vec3 vel) {
        return vel.length() > NEAR_ZERO_SPEED ? safeNormalize(vel, launchDirection) : launchDirection;
    }

    private void transitionTo(FlightAccess access, State next, String message) {
        if (state == next) return;
        state = next;
        syncState(access);
        logTransition(access, message);
    }

    private void syncState(FlightAccess access) {
        access.guidanceSyncState(state.ordinal());
    }

    private void debug(FlightAccess access, Vec3 pos, Vec3 vel, Command cmd) {
        if (!KaboomConfig.server().missileGuidanceDebug.get() || access.guidanceTickCount() % 5 != 0) return;

        double range = target == null ? Double.NaN : pos.distanceTo(target);
        double horizontalRange = target == null ? Double.NaN : horizontalDistance(pos, target);
        double diveStartHorizontalRange = target == null ? Double.NaN : effectiveTerminalDiveStartHorizontalDistance(pos, target);
        CreateKaboom.getLogger().info(
                "MissileGuidance id={} uuid={} state={} pos={} target={} vel={} speed={} launchDir={} boostDistance={} range={} horizontalRange={} diveStartHorizontalRange={} terminalDiveAngleDeg={} desiredDir={} turnDeg={} requestedDelta={} appliedDelta={} fuel={} beganClosing={} prevDistance={} abortReason={}",
                access.guidanceEntityId(),
                access.guidanceUuid(),
                state,
                fmt(pos),
                target == null ? "none" : fmt(target),
                fmt(vel),
                String.format(Locale.ROOT, "%.3f", vel.length()),
                fmt(launchDirection),
                String.format(Locale.ROOT, "%.3f", accumulatedBoostDistance),
                String.format(Locale.ROOT, "%.3f", range),
                String.format(Locale.ROOT, "%.3f", horizontalRange),
                String.format(Locale.ROOT, "%.3f", diveStartHorizontalRange),
                String.format(Locale.ROOT, "%.3f", configuredTerminalDiveAngleDegrees()),
                fmt(cmd.desiredDir()),
                String.format(Locale.ROOT, "%.3f", cmd.actualTurnDeg()),
                fmt(cmd.requestedDeltaV()),
                fmt(cmd.appliedDeltaV()),
                access.guidanceFuelMb(),
                beganClosingTarget,
                String.format(Locale.ROOT, "%.3f", previousTargetDistance),
                abortReason
        );
    }

    private void logTransition(FlightAccess access, String message) {
        if (KaboomConfig.server().missileGuidanceDebug.get()) {
            CreateKaboom.getLogger().info("MissileGuidance transition id={} uuid={} state={} {}", access.guidanceEntityId(), access.guidanceUuid(), state, message);
        }
    }

    private static String fmt(Vec3 v) {
        if (v == null) return "null";
        return String.format(Locale.ROOT, "(%.3f, %.3f, %.3f)", v.x, v.y, v.z);
    }

    private static Vec3 clampMagnitude(Vec3 v, double max) {
        double len = v.length();
        if (len > max && len > 1.0e-9) return v.scale(max / len);
        return v;
    }

    private static double angleDegrees(Vec3 a, Vec3 b) {
        if (a.lengthSqr() < EPSILON_DIR_SQR || b.lengthSqr() < EPSILON_DIR_SQR) return 0.0;
        double dot = Mth.clamp(a.normalize().dot(b.normalize()), -1.0, 1.0);
        return Math.toDegrees(Math.acos(dot));
    }

    private static Vec3 limitTurnSafe(Vec3 currentDir, Vec3 desiredDir, double maxTurnDeg) {
        if (currentDir.lengthSqr() < 1e-8 || desiredDir.lengthSqr() < 1e-8) return desiredDir;
        Vec3 a = currentDir.normalize();
        Vec3 b = desiredDir.normalize();

        double dot = Mth.clamp(a.dot(b), -1.0, 1.0);

        if (dot < -0.9995) {
            Vec3 axis = a.cross(new Vec3(0, 1, 0));
            if (axis.lengthSqr() < 1e-8) axis = a.cross(new Vec3(1, 0, 0));
            axis = axis.normalize();

            double maxRad = Math.toRadians(maxTurnDeg);
            Vec3 rotated = a.scale(Math.cos(maxRad))
                    .add(axis.cross(a).scale(Math.sin(maxRad)))
                    .add(axis.scale(axis.dot(a) * (1.0 - Math.cos(maxRad))));
            return rotated.normalize();
        }

        double angle = Math.acos(dot);
        if (angle < 1e-6) return b;

        double maxRad = Math.toRadians(maxTurnDeg);
        if (angle <= maxRad) return b;

        double t = maxRad / angle;
        double sinAngle = Math.sin(angle);

        double w1 = Math.sin((1.0 - t) * angle) / sinAngle;
        double w2 = Math.sin(t * angle) / sinAngle;

        return a.scale(w1).add(b.scale(w2)).normalize();
    }

    private static double horizontalDistance(Vec3 a, Vec3 b) {
        double dx = b.x - a.x;
        double dz = b.z - a.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static double effectiveTerminalDiveStartHorizontalDistance(Vec3 pos, Vec3 target) {
        double fixedCap = configuredDiveStartHorizontalDistance();
        double altitudeDelta = Math.max(0.0, pos.y - target.y);
        if (altitudeDelta <= 1.0e-6) return fixedCap;

        double angleRad = Math.toRadians(configuredTerminalDiveAngleDegrees());
        double adaptiveRange = altitudeDelta / Math.tan(angleRad);
        return Mth.clamp(adaptiveRange, 1.0, fixedCap);
    }

    private static Vec3 safeNormalize(Vec3 v, Vec3 fallback) {
        if (!isFinite(v) || v.lengthSqr() < EPSILON_DIR_SQR) {
            return fallback.lengthSqr() < EPSILON_DIR_SQR ? new Vec3(0, 1, 0) : fallback.normalize();
        }
        return v.normalize();
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
        if (!tag.contains(key)) return fallback;
        CompoundTag vec = tag.getCompound(key);
        Vec3 value = new Vec3(vec.getDouble("X"), vec.getDouble("Y"), vec.getDouble("Z"));
        return isFinite(value) ? value : fallback;
    }

    public static double configuredMaxAccelerationPerTick() {
        return Math.max(0.0, KaboomConfig.server().maxAccelerationPerTick.getF());
    }

    public static double configuredMaxSpeed() {
        double configured = KaboomConfig.server().maxSpeed.getF();
        if (configured <= 0.0) configured = KaboomConfig.server().maxMissileSpeed.get();
        return Math.max(0.0, configured);
    }

    private static double configuredMaxTurnDegreesPerTick() {
        return Math.max(0.0, KaboomConfig.server().maxTurnDegreesPerTick.getF());
    }

    private static double configuredThrustAccelerationPerTick() {
        double configured = KaboomConfig.server().thrustAccelerationPerTick.getF();
        if (configured <= 0.0) configured = KaboomConfig.server().maxMissileAccel.getF();
        return Math.max(0.0, configured);
    }

    private static double configuredCruiseAltitudeY(Level level) {
        int minY = level.getMinBuildHeight() + 2;
        int maxY = level.getMaxBuildHeight() - 2;
        return Mth.clamp(KaboomConfig.server().cruiseAltitudeY.get(), minY, maxY);
    }

    private static double configuredCruiseAltitudeDeadband() {
        return Math.max(0.0, KaboomConfig.server().cruiseAltitudeDeadband.getF());
    }

    private static double configuredCruiseActivationDistance() {
        double dive = configuredDiveStartHorizontalDistance();
        return Math.max(dive + 1.0, KaboomConfig.server().cruiseActivationDistance.getF());
    }

    private static double configuredDiveStartHorizontalDistance() {
        return Math.max(1.0, KaboomConfig.server().diveStartHorizontalDistance.getF());
    }

    private static double configuredTerminalDiveAngleDegrees() {
        return Mth.clamp(KaboomConfig.server().terminalDiveAngleDegrees.getF(), 15.0, 85.0);
    }

    private static double configuredCruiseLookaheadDistance() {
        return Math.max(1.0, KaboomConfig.server().cruiseLookaheadDistance.getF());
    }

    private static double configuredOvershootDistanceEpsilon() {
        return Math.max(0.0, KaboomConfig.server().overshootDistanceEpsilon.getF());
    }
}
