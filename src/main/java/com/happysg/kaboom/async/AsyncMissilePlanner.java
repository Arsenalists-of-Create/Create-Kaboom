package com.happysg.kaboom.async;

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
            String debug
    ) {
        public static Output idle() {
            return new Output(new Vec3(0, 0, 0), 0.0, false, false, "");
        }

        public Output withDebug(String s) {
            return new Output(desiredDir, throttleReq, detonate, done, s);
        }

        public Output markDone() { // renamed from done()
            return new Output(desiredDir, throttleReq, detonate, true, debug);
        }

        public Output withDone(boolean d) {
            return new Output(desiredDir, throttleReq, detonate, d, debug);
        }

        public Output withDetonate(boolean d) {
            return new Output(desiredDir, throttleReq, d, done, debug);
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

    /** Default: just keep going in current direction. */
    public AsyncMissilePlanner coast(double throttle) {
        queue.addLast(new Coast(throttle));
        return this;
    }

    /** Call once per SERVER tick (main thread). Returns latest output (possibly from previous tick). */
    public Output tick(WorldView view, Snapshot snap) {
        // Advance maneuver if none active
        if (active == null) {
            active = queue.pollFirst();
            if (active != null) active.onStart(snap);
        }
        if (active == null) {
            latest = Output.idle();
            return latest;
        }

        // Consume completed async job
        if (inFlight != null && inFlight.isDone()) {
            try {
                Output out = inFlight.getNow(Output.idle());
                latest = out;
                inFlight = null;

                // If the maneuver declared "done", advance next tick
                if (out.done) {
                    active = null;
                    // Keep latest as-is this tick; next tick will start next maneuver
                }
            } catch (Exception ignored) {
                inFlight = null;
            }
        }

        // Launch new job if none in flight and cadence matches
        if (inFlight == null && (snap.tick % planEveryNTicks == 0)) {
            TargetSnapshot tgt = null;

            if (active instanceof Targeted targeted) {
                // Resolve target ONLY on main thread
                tgt = view.getTarget(targeted.targetId());
            }

            PlanningInput input = new PlanningInput(snap, tgt);

            long s = seq.incrementAndGet();
            Maneuver cur = active;

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
            return new Output(dir, clamp(throttle, 0, 1), false, false, "coast");
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
            return new Output(desired, throttle, false, done, "climb dy=" + dy);
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
                    "pitchOver pitch=" + fmt(desiredPitch));
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
                return new Output(fallback, 0.0, false, false, "intercept: no target");
            }

            Vec3 r = t.pos.subtract(m.pos);
            double dist = r.length();
            if (dist <= hitRadius) {
                Vec3 dir = dist > 1e-8 ? r.scale(1.0 / dist) : new Vec3(0, 1, 0);
                return new Output(dir, 0.0, false, true, "intercept: arrived");
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
                    "intercept tgo=" + fmt(tgo) + " align=" + fmt(align));
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
            Snapshot m = in.missile;

            Vec3 to = target.subtract(m.pos);
            double dist = to.length();
            if (dist <= arriveRadius) {
                Vec3 desired = dist > 1e-8 ? to.scale(1.0 / dist) : new Vec3(0, -1, 0);
                return new Output(desired, 0.0, false, true, "arc: arrived");
            }

            double g = Math.max(1e-6, m.gravity);
            Aim aim = aimArc(m.pos, target, assumedSpeed, g, highArc);

            Vec3 desired;
            boolean valid = aim.valid;

            if (valid) {
                desired = aim.dir;
            } else {
                // no real solution (too far / too high at this speed): point generally toward target
                desired = to.normalize();
            }

            // If you want “boost then coast”, set throttle low here; your actuator will still steer while coasting.
            return new Output(desired, throttle, false, false,
                    valid ? ("arc t=" + fmt(aim.timeTicks)) : "arc invalid");
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