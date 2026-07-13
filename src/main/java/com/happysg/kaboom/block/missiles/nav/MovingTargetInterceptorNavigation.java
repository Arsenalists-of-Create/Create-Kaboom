package com.happysg.kaboom.block.missiles.nav;

import com.happysg.kaboom.CreateKaboom;
import com.happysg.kaboom.block.missiles.util.MissileGuidanceData;
import com.happysg.kaboom.block.missiles.util.MissileGuidanceType;
import com.happysg.kaboom.config.KaboomConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Locale;

public final class MovingTargetInterceptorNavigation {
    private static final double BOOST_DISTANCE_BLOCKS = 30.0;
    private static final double EPSILON_DIR_SQR = 1.0e-10;
    private static final double NEAR_ZERO_SPEED = 1.0e-4;

    public enum State {
        BOOST,
        INTERCEPT,
        ABORTED
    }

    private State state = State.BOOST;
    private MissileGuidanceType guidanceType = MissileGuidanceType.UNKNOWN;
    @Nullable
    private MissileGuidanceData guidanceData = null;
    private Vec3 launchDirection = new Vec3(0, 1, 0);
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

    public void initialize(Vec3 launchDirection, Vec3 launchPosition, MissileNavigation.FlightAccess access) {
        this.launchDirection = safeNormalize(launchDirection, new Vec3(0, 1, 0));
        this.launchPosition = launchPosition;
        this.previousGuidancePosition = launchPosition;
        this.accumulatedBoostDistance = 0.0;
        this.fuelAtLaunch = access.guidanceFuelMb();
        this.state = State.BOOST;
        this.abortReason = "";
        this.previousMissDistance = Double.NaN;
        this.previousForwardRangeToTarget = Double.NaN;
        this.beganClosingTarget = false;
        syncState(access);
    }

    public void configure(MissileGuidanceData data) {
        guidanceData = data;
        guidanceType = data == null ? MissileGuidanceType.UNKNOWN : data.guidanceType();
    }

    public MissileNavigation.Command tick(MissileNavigation.FlightAccess access, Vec3 pos, Vec3 vel) {
        if (!(access.guidanceLevel() instanceof ServerLevel serverLevel)) {
            lastCommand = MissileNavigation.Command.none("not_server_level");
            return lastCommand;
        }

        if (!guidanceType.isInterceptor()) {
            lastCommand = MissileNavigation.Command.none("not_interceptor_guidance");
            return lastCommand;
        }

        if (state == State.ABORTED) {
            lastCommand = MissileNavigation.Command.none("aborted:" + abortReason);
            debug(access, pos, vel, null, lastCommand, "aborted");
            return lastCommand;
        }

        MovingTargetResolver.TargetData target = MovingTargetResolver.resolve(serverLevel, guidanceData, pos);

        if (state == State.BOOST) {
            accumulateBoostDistance(pos);
            if (accumulatedBoostDistance >= BOOST_DISTANCE_BLOCKS) {
                if (!acceptTarget(serverLevel, target, true)) {
                    abort(access, "no valid target after boost");
                    lastCommand = MissileNavigation.Command.none("abort:" + abortReason);
                    debug(access, pos, vel, target, lastCommand, "boost_abort");
                    return lastCommand;
                }
                transitionTo(access, State.INTERCEPT, "boost completed; entered intercept");
            }

            Vec3 appliedDelta = poweredDeltaAlong(access, launchDirection, configuredThrustAccelerationPerTick());
            lastCommand = new MissileNavigation.Command(appliedDelta, launchDirection, 0.0, appliedDelta, "boost");
            debug(access, pos, vel, target, lastCommand, "boost");
            return lastCommand;
        }

        if (!acceptTarget(serverLevel, target, false)) {
            abort(access, "stale or invalid target data");
            lastCommand = MissileNavigation.Command.none("abort:" + abortReason);
            debug(access, pos, vel, target, lastCommand, "stale_abort");
            return lastCommand;
        }

        if (detectOvershoot(access, pos, vel)) {
            lastCommand = MissileNavigation.Command.none("overshoot:" + abortReason);
            debug(access, pos, vel, target, lastCommand, "overshoot_abort");
            return lastCommand;
        }

        Vec3 aimPoint = computeAimPoint(pos, vel);
        Vec3 desiredDir = safeNormalize(aimPoint.subtract(pos), currentOrLaunchDirection(vel));
        MissileNavigation.Command steered = steerToward(access, vel, desiredDir);
        lastCommand = new MissileNavigation.Command(
                steered.appliedDeltaV(),
                steered.desiredDir(),
                steered.actualTurnDeg(),
                steered.requestedDeltaV(),
                "intercept"
        );
        debug(access, pos, vel, target, lastCommand, "intercept");
        return lastCommand;
    }

    public void write(CompoundTag tag) {
        tag.putString("kaboom:InterceptorState", state.name());
        tag.putString("kaboom:InterceptorGuidanceType", guidanceType.name());
        if (guidanceData != null) {
            tag.put("kaboom:InterceptorGuidanceData", guidanceData.toTag());
        }
        putVec(tag, "kaboom:InterceptorLaunchPosition", launchPosition);
        putVec(tag, "kaboom:InterceptorLaunchDirection", launchDirection);
        putVec(tag, "kaboom:InterceptorPreviousPosition", previousGuidancePosition);
        tag.putDouble("kaboom:InterceptorBoostDistance", accumulatedBoostDistance);
        tag.putInt("kaboom:InterceptorFuelAtLaunch", fuelAtLaunch);
        tag.putString("kaboom:InterceptorTargetId", targetId);
        tag.putString("kaboom:InterceptorTargetCategory", targetCategory);
        if (latestTargetPosition != null) putVec(tag, "kaboom:InterceptorTargetPosition", latestTargetPosition);
        putVec(tag, "kaboom:InterceptorRawTargetVelocity", rawTargetVelocity);
        putVec(tag, "kaboom:InterceptorFilteredTargetVelocity", filteredTargetVelocity);
        tag.putLong("kaboom:InterceptorTargetTimestamp", targetTimestamp);
        tag.putInt("kaboom:InterceptorTargetAge", targetAgeTicks);
        tag.putDouble("kaboom:InterceptorPreviousMissDistance", previousMissDistance);
        tag.putDouble("kaboom:InterceptorPreviousForwardRange", previousForwardRangeToTarget);
        tag.putBoolean("kaboom:InterceptorBeganClosing", beganClosingTarget);
        tag.putDouble("kaboom:InterceptorTime", interceptTimeTicks);
        if (rawInterceptPoint != null) putVec(tag, "kaboom:InterceptorRawPoint", rawInterceptPoint);
        if (smoothedInterceptPoint != null) putVec(tag, "kaboom:InterceptorSmoothedPoint", smoothedInterceptPoint);
        tag.putString("kaboom:InterceptorAbortReason", abortReason == null ? "" : abortReason);
    }

    public void read(CompoundTag tag, MissileNavigation.FlightAccess access, Vec3 currentPosition) {
        if (tag.contains("kaboom:InterceptorState")) {
            try {
                state = State.valueOf(tag.getString("kaboom:InterceptorState"));
            } catch (IllegalArgumentException ignored) {
                state = State.BOOST;
            }
        }
        if (tag.contains("kaboom:InterceptorGuidanceType")) {
            guidanceType = MissileGuidanceType.fromName(tag.getString("kaboom:InterceptorGuidanceType"));
        }
        if (tag.contains("kaboom:InterceptorGuidanceData")) {
            guidanceData = MissileGuidanceData.fromTag(tag.getCompound("kaboom:InterceptorGuidanceData"));
            guidanceType = guidanceData.guidanceType();
        }
        launchPosition = readVec(tag, "kaboom:InterceptorLaunchPosition", currentPosition);
        launchDirection = safeNormalize(readVec(tag, "kaboom:InterceptorLaunchDirection", launchDirection), new Vec3(0, 1, 0));
        previousGuidancePosition = readVec(tag, "kaboom:InterceptorPreviousPosition", currentPosition);
        accumulatedBoostDistance = tag.contains("kaboom:InterceptorBoostDistance") ? tag.getDouble("kaboom:InterceptorBoostDistance") : 0.0;
        fuelAtLaunch = tag.contains("kaboom:InterceptorFuelAtLaunch") ? tag.getInt("kaboom:InterceptorFuelAtLaunch") : access.guidanceFuelMb();
        targetId = tag.getString("kaboom:InterceptorTargetId");
        targetCategory = tag.getString("kaboom:InterceptorTargetCategory");
        latestTargetPosition = readVec(tag, "kaboom:InterceptorTargetPosition", null);
        rawTargetVelocity = readVec(tag, "kaboom:InterceptorRawTargetVelocity", Vec3.ZERO);
        filteredTargetVelocity = readVec(tag, "kaboom:InterceptorFilteredTargetVelocity", Vec3.ZERO);
        targetTimestamp = tag.contains("kaboom:InterceptorTargetTimestamp") ? tag.getLong("kaboom:InterceptorTargetTimestamp") : -1L;
        targetAgeTicks = tag.contains("kaboom:InterceptorTargetAge") ? tag.getInt("kaboom:InterceptorTargetAge") : Integer.MAX_VALUE;
        previousMissDistance = tag.contains("kaboom:InterceptorPreviousMissDistance") ? tag.getDouble("kaboom:InterceptorPreviousMissDistance") : Double.NaN;
        previousForwardRangeToTarget = tag.contains("kaboom:InterceptorPreviousForwardRange") ? tag.getDouble("kaboom:InterceptorPreviousForwardRange") : Double.NaN;
        beganClosingTarget = tag.getBoolean("kaboom:InterceptorBeganClosing");
        interceptTimeTicks = tag.contains("kaboom:InterceptorTime") ? tag.getDouble("kaboom:InterceptorTime") : Double.NaN;
        rawInterceptPoint = readVec(tag, "kaboom:InterceptorRawPoint", null);
        smoothedInterceptPoint = readVec(tag, "kaboom:InterceptorSmoothedPoint", null);
        abortReason = tag.contains("kaboom:InterceptorAbortReason") ? tag.getString("kaboom:InterceptorAbortReason") : "";
        syncState(access);
    }

    public Vec3 launchDirection() {
        return launchDirection;
    }

    public int stateOrdinal() {
        return state.ordinal();
    }

    private boolean acceptTarget(ServerLevel level, @Nullable MovingTargetResolver.TargetData target, boolean resetIfChanged) {
        if (target == null || !isFinite(target.position()) || !isFinite(target.velocity())) return false;

        int age = target.ageTicks(level);
        if (!target.live() && age > configuredTargetDataTimeoutTicks()) {
            return false;
        }

        if (!target.id().equals(targetId)) {
            resetTarget(target);
            if (!resetIfChanged) {
                logTransition(null, "target switch/reset id=" + target.id());
            }
        }

        latestTargetPosition = target.position();
        rawTargetVelocity = target.velocity();
        targetTimestamp = target.scannedTime();
        targetAgeTicks = age;

        double velocityAlpha = configuredTargetVelocitySmoothing();
        filteredTargetVelocity = filteredTargetVelocity.lengthSqr() < EPSILON_DIR_SQR
                ? rawTargetVelocity
                : lerp(filteredTargetVelocity, rawTargetVelocity, velocityAlpha);
        return true;
    }

    private void resetTarget(MovingTargetResolver.TargetData target) {
        targetId = target.id();
        targetCategory = target.category();
        filteredTargetVelocity = target.velocity();
        rawTargetVelocity = target.velocity();
        latestTargetPosition = target.position();
        rawInterceptPoint = target.position();
        smoothedInterceptPoint = target.position();
        previousMissDistance = Double.NaN;
        previousForwardRangeToTarget = Double.NaN;
        beganClosingTarget = false;
    }

    private Vec3 computeAimPoint(Vec3 pos, Vec3 vel) {
        if (latestTargetPosition == null) {
            interceptTimeTicks = Double.NaN;
            rawInterceptPoint = pos.add(currentOrLaunchDirection(vel).scale(16.0));
            return rawInterceptPoint;
        }

        Vec3 relative = latestTargetPosition.subtract(pos);
        double speed = Math.max(vel.length(), configuredMaxSpeed() * 0.5);
        double t = solveLeadTime(relative, filteredTargetVelocity, speed);
        if (!Double.isFinite(t)) {
            t = Math.min(configuredMaxLeadTimeTicks(), Math.max(1.0, relative.length() / Math.max(speed, 1.0e-6)));
            rawInterceptPoint = latestTargetPosition;
            interceptTimeTicks = Double.NaN;
            logTransition(null, "lead solution unavailable; using bounded direct pursuit");
        } else {
            rawInterceptPoint = latestTargetPosition.add(filteredTargetVelocity.scale(t));
            interceptTimeTicks = t;
        }

        if (!isFinite(rawInterceptPoint) || rawInterceptPoint.distanceToSqr(pos) > 1.0e10) {
            rawInterceptPoint = latestTargetPosition;
            interceptTimeTicks = Double.NaN;
        }

        smoothedInterceptPoint = smoothedInterceptPoint == null
                ? rawInterceptPoint
                : lerp(smoothedInterceptPoint, rawInterceptPoint, configuredInterceptPointSmoothing());
        return smoothedInterceptPoint;
    }

    private double solveLeadTime(Vec3 relative, Vec3 targetVelocity, double missileSpeed) {
        double a = targetVelocity.dot(targetVelocity) - missileSpeed * missileSpeed;
        double b = 2.0 * relative.dot(targetVelocity);
        double c = relative.dot(relative);

        double t;
        if (Math.abs(a) < 1.0e-8) {
            if (Math.abs(b) < 1.0e-8) return Double.NaN;
            t = -c / b;
        } else {
            double discriminant = b * b - 4.0 * a * c;
            if (discriminant < 0.0) return Double.NaN;
            double sqrt = Math.sqrt(discriminant);
            double t1 = (-b - sqrt) / (2.0 * a);
            double t2 = (-b + sqrt) / (2.0 * a);
            t = smallestPositive(t1, t2);
        }

        double max = configuredMaxLeadTimeTicks();
        if (!Double.isFinite(t) || t <= 0.0) return Double.NaN;
        return Mth.clamp(t, 1.0, max);
    }

    private double smallestPositive(double a, double b) {
        boolean aOk = Double.isFinite(a) && a > 0.0;
        boolean bOk = Double.isFinite(b) && b > 0.0;
        if (aOk && bOk) return Math.min(a, b);
        if (aOk) return a;
        if (bOk) return b;
        return Double.NaN;
    }

    private boolean detectOvershoot(MissileNavigation.FlightAccess access, Vec3 pos, Vec3 vel) {
        if (latestTargetPosition == null) return false;
        double distance = pos.distanceTo(latestTargetPosition);
        double forwardRange = latestTargetPosition.subtract(pos).dot(currentOrLaunchDirection(vel));
        double epsilon = configuredOvershootDistanceEpsilon();

        if (!Double.isFinite(previousMissDistance)) {
            previousMissDistance = distance;
        } else if (distance < previousMissDistance - epsilon) {
            beganClosingTarget = true;
            previousMissDistance = distance;
        } else {
            previousMissDistance = distance;
        }

        if (!Double.isFinite(previousForwardRangeToTarget)) {
            previousForwardRangeToTarget = forwardRange;
            return false;
        }

        if (previousForwardRangeToTarget >= -epsilon && forwardRange < -epsilon) {
            double previousForwardRange = previousForwardRangeToTarget;
            previousForwardRangeToTarget = forwardRange;
            abort(access, "overshoot detected prevForwardRange=" + String.format(Locale.ROOT, "%.3f", previousForwardRange)
                    + " currentForwardRange=" + String.format(Locale.ROOT, "%.3f", forwardRange));
            return true;
        }

        previousForwardRangeToTarget = forwardRange;
        return false;
    }

    private MissileNavigation.Command steerToward(MissileNavigation.FlightAccess access, Vec3 vel, Vec3 desiredDirRaw) {
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

        return new MissileNavigation.Command(appliedDelta, desiredDir, actualTurnDeg, requestedDelta, "steer");
    }

    private Vec3 poweredDeltaAlong(MissileNavigation.FlightAccess access, Vec3 directionOrDelta, double magnitude) {
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

    private double burnFuelForThrottle(MissileNavigation.FlightAccess access, double throttleRaw) {
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

    private Vec3 currentOrLaunchDirection(Vec3 vel) {
        return vel.length() > NEAR_ZERO_SPEED ? safeNormalize(vel, launchDirection) : launchDirection;
    }

    private void abort(MissileNavigation.FlightAccess access, String reason) {
        access.guidanceSetFuelMb(0);
        abortReason = reason;
        transitionTo(access, State.ABORTED, "entered aborted state: " + reason);
    }

    private void transitionTo(MissileNavigation.FlightAccess access, State next, String message) {
        if (state == next) return;
        state = next;
        syncState(access);
        logTransition(access, message);
    }

    private void syncState(MissileNavigation.FlightAccess access) {
        access.guidanceSyncState(state.ordinal());
    }

    private void debug(MissileNavigation.FlightAccess access, Vec3 pos, Vec3 vel, @Nullable MovingTargetResolver.TargetData target,
                       MissileNavigation.Command cmd, String phase) {
        if (!KaboomConfig.server().interceptorGuidanceDebug.get() || access.guidanceTickCount() % 5 != 0) return;

        CreateKaboom.getLogger().info(
                "InterceptorGuidance id={} uuid={} type={} state={} phase={} pos={} vel={} speed={} targetId={} category={} targetSource={} targetPos={} targetVel={} filteredVel={} targetAge={} interceptTime={} rawPoint={} smoothedPoint={} miss={} forwardRange={} closing={} desiredDir={} turnDeg={} requestedDelta={} appliedDelta={} boostDistance={} fuel={} fuelAtLaunch={} abortReason={}",
                access.guidanceEntityId(),
                access.guidanceUuid(),
                guidanceType,
                state,
                phase,
                fmt(pos),
                fmt(vel),
                String.format(Locale.ROOT, "%.3f", vel.length()),
                targetId,
                targetCategory,
                target == null ? "none" : target.source(),
                latestTargetPosition == null ? "none" : fmt(latestTargetPosition),
                fmt(rawTargetVelocity),
                fmt(filteredTargetVelocity),
                targetAgeTicks,
                String.format(Locale.ROOT, "%.3f", interceptTimeTicks),
                rawInterceptPoint == null ? "none" : fmt(rawInterceptPoint),
                smoothedInterceptPoint == null ? "none" : fmt(smoothedInterceptPoint),
                String.format(Locale.ROOT, "%.3f", previousMissDistance),
                String.format(Locale.ROOT, "%.3f", previousForwardRangeToTarget),
                beganClosingTarget,
                fmt(cmd.desiredDir()),
                String.format(Locale.ROOT, "%.3f", cmd.actualTurnDeg()),
                fmt(cmd.requestedDeltaV()),
                fmt(cmd.appliedDeltaV()),
                String.format(Locale.ROOT, "%.3f", accumulatedBoostDistance),
                access.guidanceFuelMb(),
                fuelAtLaunch,
                abortReason
        );
    }

    private void logTransition(@Nullable MissileNavigation.FlightAccess access, String message) {
        if (!KaboomConfig.server().interceptorGuidanceDebug.get()) return;
        if (access == null) {
            CreateKaboom.getLogger().info("InterceptorGuidance transition type={} state={} targetId={} {}", guidanceType, state, targetId, message);
        } else {
            CreateKaboom.getLogger().info("InterceptorGuidance transition id={} uuid={} type={} state={} targetId={} {}",
                    access.guidanceEntityId(), access.guidanceUuid(), guidanceType, state, targetId, message);
        }
    }

    private static Vec3 lerp(Vec3 from, Vec3 to, double alphaRaw) {
        double alpha = Mth.clamp(alphaRaw, 0.0, 1.0);
        return from.scale(1.0 - alpha).add(to.scale(alpha));
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
            return a.scale(Math.cos(maxRad))
                    .add(axis.cross(a).scale(Math.sin(maxRad)))
                    .add(axis.scale(axis.dot(a) * (1.0 - Math.cos(maxRad))))
                    .normalize();
        }

        double angle = Math.acos(dot);
        if (angle < 1e-6) return b;
        double maxRad = Math.toRadians(maxTurnDeg);
        if (angle <= maxRad) return b;

        double t = maxRad / angle;
        double sinAngle = Math.sin(angle);
        return a.scale(Math.sin((1.0 - t) * angle) / sinAngle)
                .add(b.scale(Math.sin(t * angle) / sinAngle))
                .normalize();
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

    private static String fmt(Vec3 v) {
        if (v == null) return "null";
        return String.format(Locale.ROOT, "(%.3f, %.3f, %.3f)", v.x, v.y, v.z);
    }

    private static double configuredMaxAccelerationPerTick() {
        return Math.max(0.0, KaboomConfig.server().maxAccelerationPerTick.getF());
    }

    private static double configuredMaxSpeed() {
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

    private static double configuredOvershootDistanceEpsilon() {
        return Math.max(0.0, KaboomConfig.server().overshootDistanceEpsilon.getF());
    }

    private static int configuredMaxLeadTimeTicks() {
        return Math.max(1, KaboomConfig.server().maxLeadTimeTicks.get());
    }

    private static double configuredTargetVelocitySmoothing() {
        return Mth.clamp(KaboomConfig.server().targetVelocitySmoothing.getF(), 0.0, 1.0);
    }

    private static double configuredInterceptPointSmoothing() {
        return Mth.clamp(KaboomConfig.server().interceptPointSmoothing.getF(), 0.0, 1.0);
    }

    private static int configuredTargetDataTimeoutTicks() {
        return Math.max(0, KaboomConfig.server().targetDataTimeoutTicks.get());
    }
}
