package com.happysg.kaboom.async;

import com.mojang.logging.LogUtils;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

public final class AsyncMissilePlanner {

    /* ----------------------------
     * Data passed from MissileEntity (MAIN THREAD snapshot)
     * ---------------------------- */
    public record Snapshot(
            long tick,
            Vec3 pos,
            Vec3 vel,
            float yawDeg,
            float pitchDeg,
            double fuelMb,
            double maxSpeed,
            double gravity   // positive magnitude (0.08)
    ) {}

    public record TargetSnapshot(
            UUID id,
            Vec3 pos,
            Vec3 vel,
            boolean valid
    ) {}

    public record PlanningInput(Snapshot missile, TargetSnapshot target) {}

    public record Output(
            Vec3 desiredDir,
            double throttleReq,
            boolean detonate,
            boolean done,
            String debug,
            double steerScale   // 0..1
    ) {
        public static Output idle() {
            return new Output(new Vec3(0, 0, 0), 0.0, false, false, "", 0.0);
        }

        public Output withDebug(String s) { return new Output(desiredDir, throttleReq, detonate, done, s, steerScale); }
        public Output markDone() { return new Output(desiredDir, throttleReq, detonate, true, debug, steerScale); }
        public Output withDone(boolean d) { return new Output(desiredDir, throttleReq, detonate, d, debug, steerScale); }
        public Output withDetonate(boolean d) { return new Output(desiredDir, throttleReq, d, done, debug, steerScale); }
        public Output withSteerScale(double s) { return new Output(desiredDir, throttleReq, detonate, done, debug, Mth.clamp(s, 0.0, 1.0)); }

        public static Output cmd(Vec3 dir, double thr, String dbg) {
            return new Output(dir, thr, false, false, dbg, 1.0);
        }
    }

    /** MAIN THREAD ONLY. Must not be called from async compute. */
    public interface WorldView {
        TargetSnapshot getTarget(UUID id);
    }

    /* ----------------------------
     * Maneuver scripting API
     * ---------------------------- */
    private interface Maneuver {
        /** Called on main thread when maneuver becomes active. */
        default void onStart(Snapshot snap) {}
        /** Pure math only; runs async. */
        Output compute(PlanningInput in);
    }

    private interface Targeted {
        UUID targetId();
    }

    private final Executor executor;
    private final AtomicLong seq = new AtomicLong();

    private volatile Output latest = Output.idle();
    private CompletableFuture<Output> inFlight = null;

    private final Deque<Maneuver> queue = new ArrayDeque<>();
    private Maneuver active = null;

    // cadence (optional): compute every N ticks
    private int planEveryNTicks = 1;

    public AsyncMissilePlanner(Executor executor) {
        this.executor = executor;
    }

    public AsyncMissilePlanner setPlanCadence(int nTicks) {
        this.planEveryNTicks = Math.max(1, nTicks);
        return this;
    }

    public AsyncMissilePlanner clearProgram() {
        queue.clear();
        active = null;
        latest = Output.idle();
        inFlight = null;
        LogUtils.getLogger().warn("[PLANNER] clearProgram called");
        return this;
    }

    public AsyncMissilePlanner boostUp(double heightAboveLaunch, double tol, double throttle) {
        queue.addLast(new BoostUpTo(heightAboveLaunch, tol, throttle));
        return this;
    }

    public AsyncMissilePlanner climbTo(double altitudeY, double tolerance, double throttle) {
        queue.addLast(new ClimbTo(altitudeY, tolerance, throttle));
        return this;
    }

    /** Pitch over to a target pitch over a duration (seconds). */
    public AsyncMissilePlanner pitchOver(double seconds, double targetPitchDeg, double throttle) {
        queue.addLast(new PitchOver(seconds, targetPitchDeg, throttle));
        return this;
    }
    public AsyncMissilePlanner pitchOverHoldXZToArc(Vec3 target, double seconds, double assumedSpeed, boolean highArc) {
        queue.addLast(new PitchOverHoldXZToArc(target, seconds, assumedSpeed, highArc));
        return this;
    }
    /** Put missile on a ballistic arc that lands on target (highArc=true gives “go up then come down”). */
    public AsyncMissilePlanner arcTo(Vec3 targetPos, double assumedSpeed, boolean highArc, double arriveRadius, double throttle) {
        queue.addLast(new ArcToPoint(targetPos, assumedSpeed, highArc, arriveRadius, throttle));
        return this;
    }

    /** Lead intercept a moving target (simple constant-speed lead). */
    public AsyncMissilePlanner intercept(UUID targetId, double assumedSpeed, double hitRadius, double throttleMax) {
        queue.addLast(new Intercept(targetId, assumedSpeed, hitRadius, throttleMax));
        return this;
    }
    public AsyncMissilePlanner cruiseTo(
            Vec3 targetPos,
            double cruiseAltitudeY,
            double altitudeTol,
            double arriveRadius,
            double desiredSpeed,
            double throttleMax
    ) {
        queue.addLast(new CruiseToPoint(targetPos, cruiseAltitudeY, altitudeTol, arriveRadius, desiredSpeed, throttleMax));
        return this;
    }

    /** Default: just keep going in current direction. */
    public AsyncMissilePlanner coast(double throttle) {
        queue.addLast(new Coast(throttle));
        return this;
    }

    /** Call once per SERVER tick (main thread). Returns latest output (possibly from previous tick). */
    public Output tick(WorldView view, Snapshot snap) {

        // 1) Consume completed async job first (if any)
        if (inFlight != null && inFlight.isDone()) {
            try {
                Output out = inFlight.getNow(Output.idle());
                latest = out;
                inFlight = null;

                // If the maneuver finished, advance immediately
                if (out.done()) {
                    active = null; // mark finished, we'll pick next below
                }
            } catch (Exception ignored) {
                inFlight = null;
            }
        }

        // 2) Ensure we have an active maneuver (pull next if needed)
        if (active == null) {
            active = queue.pollFirst();
            if (active != null) {
                active.onStart(snap);
            }
        }

        // 3) If still nothing active, behave exactly like before: idle
        if (active == null) {
            latest = Output.idle();
            return latest;
        }

        // 4) Launch compute if nothing in flight and cadence matches
        if (inFlight == null && (snap.tick() % planEveryNTicks == 0)) {

            TargetSnapshot tgt = null;
            if (active instanceof Targeted targeted) {
                tgt = view.getTarget(targeted.targetId()); // MAIN THREAD
            }

            PlanningInput input = new PlanningInput(snap, tgt);

            long s = seq.incrementAndGet();
            Maneuver cur = active; // capture

            inFlight = CompletableFuture
                    .supplyAsync(() -> cur.compute(input), executor)
                    .exceptionally(err -> Output.idle().withDebug("planner err: " + err.getClass().getSimpleName()))
                    .thenApply(out -> (seq.get() == s) ? out : latest);
        }

        return latest;
    }

    /* ----------------------------
     * Maneuvers
     * ---------------------------- */

    private static final class Coast implements Maneuver {
        private final double throttle;
        private Coast(double throttle) { this.throttle = throttle; }

        @Override
        public Output compute(PlanningInput in) {
            Vec3 v = in.missile.vel;
            Vec3 dir = v.lengthSqr() > 1e-8 ? v.normalize() : Vec3.directionFromRotation(in.missile.pitchDeg, in.missile.yawDeg);
            return new Output(dir, clamp(throttle, 0, 1), false, false, "coast",1);
        }
    }

    private static final class ClimbTo implements Maneuver {
        private final double targetY;
        private final double tol;
        private final double throttle;

        private ClimbTo(double targetY, double tol, double throttle) {
            this.targetY = targetY;
            this.tol = Math.max(0.1, tol);
            this.throttle = throttle;
        }

        @Override
        public Output compute(PlanningInput in) {
            Snapshot m = in.missile;

            boolean done = m.pos().y >= targetY - tol;
            // forward basis: along velocity or current look
            Vec3 forward = m.vel().lengthSqr() > 1e-8
                    ? m.vel().normalize()
                    : Vec3.directionFromRotation(m.pitchDeg(), m.yawDeg());

            Vec3 horiz = new Vec3(forward.x, 0, forward.z);
            if (horiz.lengthSqr() < 1e-8) horiz = new Vec3(0, 0, 1);
            horiz = horiz.normalize();

            double dy = targetY - m.pos().y;
            double up = Mth.clamp(dy * 0.02, -0.8, 0.8);

            Vec3 desired = new Vec3(horiz.x, up, horiz.z).normalize();
            return new Output(desired, throttle, false, done, "climb dy=" + dy,1);
        }
    }


    private static final class BoostUpTo implements Maneuver {
        private final double boostHeight;
        private final double tol;
        private final double throttle;

        private double startY = Double.NaN;

        private BoostUpTo(double boostHeight, double tol, double throttle) {
            this.boostHeight = Math.max(0.0, boostHeight);
            this.tol = Math.max(0.1, tol);
            this.throttle = clamp(throttle, 0.0, 1.0);
        }

        @Override
        public void onStart(Snapshot snap) {
            startY = snap.pos().y; // capture launch altitude
        }

        @Override
        public Output compute(PlanningInput in) {
            Snapshot m = in.missile();
            double targetY = startY + boostHeight;
            boolean done = m.pos().y >= targetY - tol;

            return new Output(new Vec3(0, 1, 0), throttle, false, done,
                    "boostUp toY=" + fmt(targetY),1);
        }
    }
    private static final class PitchOver implements Maneuver {
        private final long durationTicks;
        private final double targetPitchDeg;
        private final double throttle;

        private long startTick = -1;
        private float startPitch = 0;

        private PitchOver(double seconds, double targetPitchDeg, double throttle) {
            this.durationTicks = Math.max(1, (long) Math.ceil(seconds * 20.0));
            this.targetPitchDeg = targetPitchDeg;
            this.throttle = throttle;
        }

        @Override
        public void onStart(Snapshot snap) {
            startTick = snap.tick;
            startPitch = snap.pitchDeg;
        }

        @Override
        public Output compute(PlanningInput in) {
            Snapshot m = in.missile;
            long t = Math.max(0, m.tick - startTick);
            double a = clamp(t / (double) durationTicks, 0.0, 1.0);

            float desiredPitch = (float) Mth.lerp((float) a, startPitch, (float) targetPitchDeg);

            // Keep yaw from current velocity direction if possible
            float yaw = m.yawDeg;
            if (m.vel.lengthSqr() > 1e-8) yaw = yawFromVec(m.vel);

            Vec3 dir = Vec3.directionFromRotation(desiredPitch, yaw).normalize();

            boolean done = a >= 1.0;
            return new Output(dir, clamp(throttle, 0, 1), false, done,
                    "pitchOver pitch=" + fmt(desiredPitch),1);
        }
    }

    private static final class Intercept implements Maneuver, Targeted {
        private final UUID targetId;
        private final double assumedSpeed;
        private final double hitRadius;
        private final double throttleMax;

        private Intercept(UUID targetId, double assumedSpeed, double hitRadius, double throttleMax) {
            this.targetId = targetId;
            this.assumedSpeed = Math.max(0.1, assumedSpeed);
            this.hitRadius = Math.max(0.25, hitRadius);
            this.throttleMax = clamp(throttleMax, 0.0, 1.0);
        }

        @Override public UUID targetId() { return targetId; }

        @Override
        public Output compute(PlanningInput in) {
            Snapshot m = in.missile;
            TargetSnapshot t = in.target;

            if (t == null || !t.valid) {
                Vec3 fallback = m.vel.lengthSqr() > 1e-8 ? m.vel.normalize() : Vec3.directionFromRotation(m.pitchDeg, m.yawDeg);
                return new Output(fallback, 0.0, false, false, "intercept: no target",1);
            }

            Vec3 r = t.pos.subtract(m.pos);
            double dist = r.length();
            if (dist <= hitRadius) {
                Vec3 dir = dist > 1e-8 ? r.scale(1.0 / dist) : new Vec3(0, 1, 0);
                return new Output(dir, 0.0, false, true, "intercept: arrived",1);
            }

            // Constant-speed lead solution: solve |r + vRel*t| = s*t
            Vec3 vRel = t.vel.subtract(m.vel);
            double s = assumedSpeed;

            double a = vRel.lengthSqr() - s * s;
            double b = 2.0 * r.dot(vRel);
            double c = r.lengthSqr();

            double tgo = smallestPositiveRoot(a, b, c);
            if (!(tgo > 0)) tgo = dist / s;

            Vec3 lead = r.add(vRel.scale(tgo));
            Vec3 desired = lead.lengthSqr() > 1e-8 ? lead.normalize() : r.normalize();

            // Throttle more when misaligned
            Vec3 vHat = m.vel.lengthSqr() > 1e-8 ? m.vel.normalize() : desired;
            double align = clamp(desired.dot(vHat), -1, 1);
            double throttle = clamp((1.0 - align) * 0.8, 0.0, throttleMax);

            return new Output(desired, throttle, false, false,
                    "intercept tgo=" + fmt(tgo) + " align=" + fmt(align),1);
        }
    }

    private static final class ArcToPoint implements Maneuver {
        private final Vec3 target;
        private final double assumedSpeed;
        private final boolean highArc;
        private final double arriveRadius;
        private final double throttle;

        private ArcToPoint(Vec3 target, double assumedSpeed, boolean highArc, double arriveRadius, double throttle) {
            this.target = target;
            this.assumedSpeed = Math.max(0.1, assumedSpeed);
            this.highArc = highArc;
            this.arriveRadius = Math.max(0.5, arriveRadius);
            this.throttle = clamp(throttle, 0.0, 1.0);
        }

        @Override
        public Output compute(PlanningInput in) {
            Snapshot m = in.missile();

            Vec3 to = target.subtract(m.pos());
            double dist = to.length();
            if (dist <= arriveRadius) {
                Vec3 desired = dist > 1e-8 ? to.scale(1.0 / dist) : new Vec3(0, -1, 0);
                return new Output(desired, 0.0, false, true, "arc: arrived",1);
            }

            // Horizontal range (what arc math mostly cares about)
            double R = Math.sqrt(to.x * to.x + to.z * to.z);
            double g = Math.max(1e-6, m.gravity());

            // Pick a "goal speed" for the arc solver: at least what is required, capped by missile maxSpeed
            double vGoal = Math.min(m.maxSpeed(), Math.max(assumedSpeed, 0.1));
            double vMin = Math.sqrt(g * Math.max(0.0, R)); // minimum speed for ANY same-height solution
            if (vGoal < vMin) vGoal = Math.min(m.maxSpeed(), vMin);

            double vNow = m.vel().length();

            // 1) BOOST: if we're well below the speed the arc math assumes, don't command a steep arc yet
            // Push mostly horizontally toward the target while building speed.
            if (vNow < vGoal * 0.90) {
                Vec3 horiz = new Vec3(to.x, 0, to.z);
                if (horiz.lengthSqr() < 1e-8) horiz = new Vec3(0, 0, 1);
                horiz = horiz.normalize();

                // small up bias so we don't lawn-dart while boosting
                double upBias = 0.15; // tune 0.05..0.25
                Vec3 desired = new Vec3(horiz.x, upBias, horiz.z).normalize();

                double thr = Math.max(throttle, 1.0); // full throttle while boosting (fuel permitting)
                return new Output(desired, thr, false, false, "arc: boosting v=" + fmt(vNow) + "->" + fmt(vGoal),1);
            }

            // 2) Solve arc (try both) and choose based on pitch cap
            Aim low  = aimArc(m.pos(), target, vGoal, g, false);
            Aim high = aimArc(m.pos(), target, vGoal, g, true);

            Vec3 desired;
            boolean valid = low.valid || high.valid;

            if (!valid) {
                // unreachable even at vGoal (e.g., too far with maxSpeed): just fly toward it
                desired = to.normalize();
                return new Output(desired, 1.0, false, false, "arc: unreachable -> flyTo",1);
            }

            // Prefer high arc only if it isn't ridiculously steep, otherwise fall back to low
            final double MAX_PITCH_DEG = 60.0; // tune
            Vec3 pick = low.valid ? low.dir : high.dir;

            if (highArc && high.valid) {
                double pitchHigh = Math.toDegrees(Math.asin(Mth.clamp(high.dir.y, -1.0, 1.0)));
                if (pitchHigh <= MAX_PITCH_DEG) pick = high.dir;
            }

            // Hard clamp pitch anyway (safety net)
            desired = clampPitch(pick, MAX_PITCH_DEG);

            return new Output(desired, throttle, false, false,
                    "arc: v=" + fmt(vNow) + " goal=" + fmt(vGoal),1);
        }

        // Keeps direction but limits elevation angle
        private static Vec3 clampPitch(Vec3 dir, double maxPitchDeg) {
            Vec3 d = dir.normalize();
            double maxY = Math.sin(Math.toRadians(maxPitchDeg));

            if (d.y <= maxY) return d;

            Vec3 h = new Vec3(d.x, 0, d.z);
            if (h.lengthSqr() < 1e-8) return new Vec3(0, maxY, 0);

            h = h.normalize();
            double horizMag = Math.cos(Math.asin(maxY));
            return new Vec3(h.x * horizMag, maxY, h.z * horizMag).normalize();
        }
    }
    private static final class PitchOverHoldXZToArc implements Maneuver {
        private final Vec3 target;
        private final double seconds;
        private final double assumedSpeed;
        private final boolean highArc;

        private long startTick = -1;
        private Vec3 finalDir = new Vec3(0, 1, 0);

        private PitchOverHoldXZToArc(Vec3 target, double seconds, double assumedSpeed, boolean highArc) {
            this.target = target;
            this.seconds = Math.max(0.05, seconds);
            this.assumedSpeed = Math.max(0.1, assumedSpeed);
            this.highArc = highArc;
        }

        @Override
        public void onStart(Snapshot snap) {
            startTick = snap.tick();

            // Compute the pitch that arcTo would want FROM THIS POSITION
            double g = Math.max(1e-6, snap.gravity());
            Aim aim = aimArc(snap.pos(), target, assumedSpeed, g, highArc);

            // If invalid, fall back to pointing toward target (still safe)
            Vec3 to = target.subtract(snap.pos());
            finalDir = (aim.valid ? aim.dir : (to.lengthSqr() > 1e-8 ? to.normalize() : new Vec3(0, 1, 0)));
        }

        @Override
        public Output compute(PlanningInput in) {
            Snapshot m = in.missile();

            double t = (m.tick() - startTick) / (seconds * 20.0);
            t = clamp(t, 0.0, 1.0);
            t = t * t * (3.0 - 2.0 * t); // smoothstep

            Vec3 up = new Vec3(0, 1, 0);
            Vec3 desired = slerpDir(up, finalDir, t);

            boolean done = (t >= 1.0);

            // CRITICAL: hold X/Z by disabling steering; also cut throttle during this pose
            return new Output(desired, 0.0, false, done, "pitchHold t=" + fmt(t), 0.0);
        }

        // Slerp between unit vectors a->b
        private static Vec3 slerpDir(Vec3 a, Vec3 b, double t) {
            Vec3 an = a.normalize();
            Vec3 bn = b.normalize();

            double dot = Mth.clamp(an.dot(bn), -1.0, 1.0);

            // handle near-identical
            if (dot > 0.9999) return an.scale(1.0 - t).add(bn.scale(t)).normalize();

            // handle near-opposite (avoid divide by 0)
            if (dot < -0.9999) {
                Vec3 axis = an.cross(new Vec3(0, 1, 0));
                if (axis.lengthSqr() < 1e-8) axis = an.cross(new Vec3(1, 0, 0));
                axis = axis.normalize();

                double ang = Math.PI * t;
                Vec3 rotated = an.scale(Math.cos(ang))
                        .add(axis.cross(an).scale(Math.sin(ang)))
                        .add(axis.scale(axis.dot(an) * (1.0 - Math.cos(ang))));
                return rotated.normalize();
            }

            double angle = Math.acos(dot);
            double sin = Math.sin(angle);

            double w1 = Math.sin((1.0 - t) * angle) / sin;
            double w2 = Math.sin(t * angle) / sin;

            return an.scale(w1).add(bn.scale(w2)).normalize();
        }
    }
    private static final class CruiseToPoint implements Maneuver {
        private final Vec3 target;
        private final double cruiseY;
        private final double altTol;
        private final double arriveRadius;
        private final double desiredSpeed;
        private final double throttleMax;

        // Tuning knobs (feel free to tweak)
        private static final double KP_ALT = 0.02;   // altitude error -> pitch
        private static final double KD_VY  = 0.06;   // vertical velocity damping
        private static final double MAX_PITCH_DEG = 25.0; // cruise shouldn't point at the moon
        private static final double THR_KP = 0.35;   // speed error -> throttle

        private CruiseToPoint(Vec3 target, double cruiseY, double altTol, double arriveRadius, double desiredSpeed, double throttleMax) {
            this.target = target;
            this.cruiseY = cruiseY;
            this.altTol = Math.max(0.25, altTol);
            this.arriveRadius = Math.max(0.5, arriveRadius);
            this.desiredSpeed = Math.max(0.1, desiredSpeed);
            this.throttleMax = clamp(throttleMax, 0.0, 1.0);
        }

        @Override
        public Output compute(PlanningInput in) {
            Snapshot m = in.missile();

            Vec3 pos = m.pos();
            Vec3 vel = m.vel();

            // Horizontal vector to target
            Vec3 to = target.subtract(pos);
            double R = Math.sqrt(to.x * to.x + to.z * to.z);

            boolean done = R <= arriveRadius;
            if (done) {
                // keep heading at target; next maneuver can take over (arcTo / intercept)
                Vec3 desired = (to.lengthSqr() > 1e-8) ? to.normalize() : new Vec3(0, 0, 1);
                desired = ArcToPoint.clampPitch(desired, MAX_PITCH_DEG);
                return new Output(desired, 0.0, false, true, "cruise: arrived R=" + fmt(R),1);
            }

            Vec3 horiz = new Vec3(to.x, 0, to.z);
            if (horiz.lengthSqr() < 1e-8) horiz = new Vec3(0, 0, 1);
            horiz = horiz.normalize();

            // Altitude hold: proportional on altitude error, derivative on vertical speed
            double altErr = cruiseY - pos.y;
            double yCmd = (altErr * KP_ALT) - (vel.y * KD_VY);

            // Don't overreact; cruise should be gentle
            yCmd = clamp(yCmd, -0.35, 0.35);

            Vec3 desired = new Vec3(horiz.x, yCmd, horiz.z).normalize();
            desired = ArcToPoint.clampPitch(desired, MAX_PITCH_DEG);

            // Throttle: accelerate up to desiredSpeed, then taper
            double speed = vel.length();
            double spErr = desiredSpeed - speed;

            double throttle = clamp(spErr * THR_KP, 0.0, throttleMax);

            // Optional: tiny sustaining throttle to fight drag (keeps cruise from bleeding speed)
            if (speed > desiredSpeed * 0.95) {
                throttle = Math.min(throttle, 0.15);
            } else {
                throttle = Math.max(throttle, 0.20);
            }
            throttle = Math.min(throttle, throttleMax);

            return new Output(desired, throttle, false, false,
                    "cruise R=" + fmt(R) + " altErr=" + fmt(altErr) + " sp=" + fmt(speed),1);
        }
    }

    /* ----------------------------
     * Ballistic arc solver (no drag)
     * ---------------------------- */

    private record Aim(Vec3 dir, double timeTicks, boolean valid) {}

    private static Aim aimArc(Vec3 start, Vec3 target, double speed, double gravity, boolean highArc) {
        Vec3 d = target.subtract(start);

        double dx = d.x;
        double dz = d.z;
        double dy = d.y;

        double R = Math.sqrt(dx * dx + dz * dz);
        if (R < 1e-8) {
            Vec3 upDown = dy >= 0 ? new Vec3(0, 1, 0) : new Vec3(0, -1, 0);
            return new Aim(upDown, 0, false);
        }

        double v2 = speed * speed;
        double g = gravity;

        // discriminant: v^4 - g (g R^2 + 2 dy v^2)
        double disc = v2 * v2 - g * (g * R * R + 2.0 * dy * v2);
        if (disc < 0) return new Aim(new Vec3(dx / R, 0, dz / R), 0, false);

        double sqrt = Math.sqrt(disc);
        double tanLow  = (v2 - sqrt) / (g * R);
        double tanHigh = (v2 + sqrt) / (g * R);

        double tan = highArc ? tanHigh : tanLow;
        double pitch = Math.atan(tan);

        double cos = Math.cos(pitch);
        double sin = Math.sin(pitch);

        double hx = dx / R;
        double hz = dz / R;

        Vec3 dir = new Vec3(hx * cos, sin, hz * cos).normalize();
        double time = R / (speed * Math.max(1e-6, cos));
        return new Aim(dir, time, true);
    }

    /* ----------------------------
     * Helpers
     * ---------------------------- */

    private static double smallestPositiveRoot(double a, double b, double c) {
        if (Math.abs(a) < 1e-9) {
            if (Math.abs(b) < 1e-9) return Double.NaN;
            double t = -c / b;
            return t > 0 ? t : Double.NaN;
        }
        double disc = b * b - 4 * a * c;
        if (disc < 0) return Double.NaN;

        double s = Math.sqrt(disc);
        double t1 = (-b - s) / (2 * a);
        double t2 = (-b + s) / (2 * a);

        double best = Double.NaN;
        if (t1 > 0) best = t1;
        if (t2 > 0) best = Double.isNaN(best) ? t2 : Math.min(best, t2);
        return best;
    }

    private static float yawFromVec(Vec3 v) {
        // Minecraft-ish yaw degrees from direction vector
        return (float) (Mth.atan2(v.z, v.x) * (180.0 / Math.PI)) - 90.0f;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }
    public Output prime(WorldView view, Snapshot snap) {
        // Ensure we have an active maneuver
        if (active == null) {
            active = queue.pollFirst();
            if (active != null) active.onStart(snap);
        }
        if (active == null) {
            latest = Output.idle();
            return latest;
        }

        TargetSnapshot tgt = null;
        if (active instanceof Targeted targeted) {
            tgt = view.getTarget(targeted.targetId());
        }

        PlanningInput input = new PlanningInput(snap, tgt);

        // Compute immediately on the main thread (safe: pure math)
        latest = active.compute(input);

        // Optional: clear any in-flight job so we don't accept stale output
        inFlight = null;
        seq.incrementAndGet();

        return latest;
    }
}