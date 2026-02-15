package com.happysg.kaboom.async;

import net.minecraft.world.phys.Vec3;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public class AsyncFlightPlanner {

    private final ExecutorService exec = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "kaboom-missile-planner");
        t.setDaemon(true);
        return t;
    });

    private final AtomicReference<AsyncSnapshot.GuidanceCommand> latest = new AtomicReference<>(null);

    // Optional: drop stale plans if we request too fast
    private final AtomicReference<CompletableFuture<?>> inFlight = new AtomicReference<>(null);

    public void requestPlan(AsyncSnapshot.MissileSnapshot missile, AsyncSnapshot.TargetSnapshot target, long gameTick) {
        if (target == null || !target.valid()) return;

        // Cancel old work if still running (optional)
        CompletableFuture<?> prev = (CompletableFuture<?>) inFlight.getAndSet(null);
        if (prev != null && !prev.isDone()) prev.cancel(true);

        CompletableFuture<Void> f = CompletableFuture
                .supplyAsync(() -> computeCommand(missile, target, gameTick), exec)
                .thenAccept(latest::set)
                .exceptionally(ex -> null);

        inFlight.set(f);
    }

    public AsyncSnapshot.GuidanceCommand pollLatestCommand() {
        return latest.getAndSet(null);
    }

    // --- The actual guidance math (pure, safe off-thread) ---
    private AsyncSnapshot.GuidanceCommand computeCommand(AsyncSnapshot.MissileSnapshot m, AsyncSnapshot.TargetSnapshot t, long tick) {
        // Example: simple predictive pursuit (lead) + proportional steering.
        // Upgrade later to Proportional Navigation if you want “real missile vibes”.

        Vec3 relPos = t.pos().subtract(m.pos());
        Vec3 relVel = t.vel().subtract(m.vel());

        // Predict intercept time (very rough): closing speed along LOS
        double closing = -relVel.dot(relPos.normalize());
        double time = (closing > 0.001) ? (relPos.length() / closing) : 0.5;

        time = clamp(time, 0.0, 40.0); // limit horizon

        Vec3 aimPoint = t.pos().add(t.vel().scale(time));
        Vec3 desiredDir = aimPoint.subtract(m.pos()).normalize();

        // Convert "desired direction" to an acceleration command:
        // steer acceleration toward desiredDir perpendicular component
        Vec3 v = m.vel();
        double speed = Math.max(v.length(), 0.001);
        Vec3 curDir = v.scale(1.0 / speed);

        Vec3 dirError = desiredDir.subtract(curDir); // small-angle approx works well
        Vec3 accel = dirError.scale(0.20);           // gain (tune per missile)

        return new AsyncSnapshot.GuidanceCommand(accel);
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    public void shutdown() {
        exec.shutdownNow();
    }
}