package com.happysg.kaboom.explosion;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Server-authoritative entry point for Create: Kaboom warheads.
 * Call {@link #tick(MinecraftServer)} once per server tick from your existing NeoForge server event class.
 */
public final class KaboomExplosionEngine {
    private static final Logger LOGGER = LoggerFactory.getLogger("Create:Kaboom Explosion Engine");
    private static final int WORKER_THREADS = Math.max(1, Math.min(2, Runtime.getRuntime().availableProcessors() / 4));
    private static final int QUEUED_JOBS = WORKER_THREADS * 2;
    private static final Semaphore JOB_SLOTS = new Semaphore(WORKER_THREADS + QUEUED_JOBS);
    private static final AtomicLong LIFECYCLE_EPOCH = new AtomicLong();

    private static final ThreadFactory WORKER_FACTORY = runnable -> {
        Thread thread = new Thread(runnable, "create-kaboom-explosion-worker");
        thread.setDaemon(true);
        return thread;
    };

    private static final ThreadPoolExecutor WORKERS = new ThreadPoolExecutor(
        WORKER_THREADS,
        WORKER_THREADS,
        30L,
        TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(QUEUED_JOBS),
        WORKER_FACTORY,
        new ThreadPoolExecutor.AbortPolicy());

    private KaboomExplosionEngine() {
    }

    /**
     * Must be called from the server thread. It immediately handles sound, particles, and entity effects,
     * then queues terrain work if worker capacity and loaded chunks are available.
     */
    public static Submission explode(ServerLevel level, BlockPos impact, KaboomExplosionProfile profile, Entity cause) {
        return explode(level, Vec3.atCenterOf(impact), profile, cause);
    }

    /** Must be called from the server thread. */
    public static Submission explode(ServerLevel level, Vec3 impact, KaboomExplosionProfile requestedProfile, Entity cause) {
        KaboomExplosionProfile profile = requestedProfile.seed() == KaboomExplosionProfile.AUTO_SEED
            ? requestedProfile.withResolvedSeed(level.getRandom().nextLong())
            : requestedProfile;
        double baseRadius = ExplosionSimulation.baseRadius(profile);

        applyImmediateEffects(level, impact, baseRadius, profile, cause);
        if (!profile.terrainDamage()) {
            return Submission.effectsOnly();
        }

        if (!JOB_SLOTS.tryAcquire()) {
            LOGGER.debug("Skipping terrain simulation for a Kaboom explosion because all worker slots are busy.");
            return Submission.rejectedBusy();
        }

        int halfExtent = captureHalfExtent(profile, baseRadius);
        ExplosionSnapshot snapshot;
        try {
            snapshot = ExplosionSnapshot.capture(level, impact, halfExtent);
        } catch (Throwable throwable) {
            JOB_SLOTS.release();
            LOGGER.error("Kaboom explosion snapshot failed", throwable);
            return Submission.rejectedSnapshot();
        }

        if (!snapshot.hasLoadedChunks()) {
            JOB_SLOTS.release();
            return Submission.rejectedNoLoadedChunks();
        }

        MinecraftServer server = level.getServer();
        long epoch = LIFECYCLE_EPOCH.get();
        try {
            WORKERS.execute(() -> runTerrainJob(server, level, impact, profile, cause, snapshot, epoch));
            return Submission.queued(halfExtent);
        } catch (RejectedExecutionException exception) {
            JOB_SLOTS.release();
            LOGGER.debug("Kaboom terrain job rejected by the worker executor", exception);
            return Submission.rejectedBusy();
        }
    }

    /** Call once from ServerTickEvent.Post. */
    public static void tick(MinecraftServer server) {
        KaboomExplosionQueue.tick(server);
    }

    /** Call during server shutdown. Safe to call more than once. */
    public static void onServerStopping() {
        LIFECYCLE_EPOCH.incrementAndGet();
        KaboomExplosionQueue.clearAll();
    }

    private static void runTerrainJob(MinecraftServer server, ServerLevel level, Vec3 impact,
                                      KaboomExplosionProfile profile, Entity cause,
                                      ExplosionSnapshot snapshot, long epoch) {
        try {
            ExplosionVolume volume = snapshot.decode(profile);
            ExplosionSimulation.SimulationResult result = ExplosionSimulation.simulate(volume, impact, profile);
            ExplosionEditBatch batch = ExplosionEditBatch.create(volume, impact, cause);

            if (result.truncated()) {
                LOGGER.debug("Kaboom explosion at {} reached its {} block terrain-edit cap.", impact, profile.maxBlockChanges());
            }
            if (batch.isComplete()) {
                return;
            }

            server.execute(() -> {
                if (LIFECYCLE_EPOCH.get() != epoch || level.getServer() != server) {
                    return;
                }
                KaboomExplosionQueue.enqueue(level, batch);
            });
        } catch (Throwable throwable) {
            LOGGER.error("Kaboom terrain simulation failed", throwable);
        } finally {
            JOB_SLOTS.release();
        }
    }

    private static int captureHalfExtent(KaboomExplosionProfile profile, double baseRadius) {
        double requested = Math.max(
            baseRadius * profile.shockwaveRadiusScale() + 3.0,
            baseRadius * profile.craterRadiusScale() + 6.0);
        return Math.max(8, Math.min(profile.maxCaptureRadius(), (int) Math.ceil(requested)));
    }

    private static void applyImmediateEffects(ServerLevel level, Vec3 impact, double baseRadius,
                                              KaboomExplosionProfile profile, Entity cause) {
        level.playSound(null, impact.x, impact.y, impact.z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 4.0f, 0.90f);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, impact.x, impact.y, impact.z, 1, 0.0, 0.0, 0.0, 0.0);

        if (!profile.entityDamage()) {
            return;
        }

        double radius = Math.max(2.0, baseRadius * 1.45);
        double radiusSquared = radius * radius;
        AABB bounds = new AABB(impact, impact).inflate(radius);
        DamageSource source = level.damageSources().explosion(null, cause);

        for (Entity entity : level.getEntities((Entity) null, bounds)) {
            double distanceSquared = entity.distanceToSqr(impact);
            if (distanceSquared > radiusSquared) {
                continue;
            }

            double distance = Math.sqrt(distanceSquared);
            double exposure = Explosion.getSeenPercent(impact, entity);
            double impactFactor = (1.0 - distance / radius) * exposure;
            if (impactFactor <= 0.0) {
                continue;
            }

            float damage = (float) (((impactFactor * impactFactor + impactFactor) * 0.5) * 7.0 * radius + 1.0);
            entity.hurt(source, damage);

            Vec3 push = entity.position().subtract(impact);
            if (push.lengthSqr() < 1.0E-8) {
                push = new Vec3(0.0, 1.0, 0.0);
            }
            push = push.normalize().add(0.0, 0.45, 0.0).normalize().scale(impactFactor * 2.25);
            entity.setDeltaMovement(entity.getDeltaMovement().add(push));
            entity.hurtMarked = true;
        }
    }

    public record Submission(Status status, int captureHalfExtent) {
        public static Submission queued(int captureHalfExtent) {
            return new Submission(Status.QUEUED, captureHalfExtent);
        }

        public static Submission effectsOnly() {
            return new Submission(Status.EFFECTS_ONLY, 0);
        }

        public static Submission rejectedBusy() {
            return new Submission(Status.REJECTED_BUSY, 0);
        }

        public static Submission rejectedSnapshot() {
            return new Submission(Status.REJECTED_SNAPSHOT_FAILURE, 0);
        }

        public static Submission rejectedNoLoadedChunks() {
            return new Submission(Status.REJECTED_NO_LOADED_CHUNKS, 0);
        }

        public boolean terrainQueued() {
            return status == Status.QUEUED;
        }
    }

    public enum Status {
        QUEUED,
        EFFECTS_ONLY,
        REJECTED_BUSY,
        REJECTED_SNAPSHOT_FAILURE,
        REJECTED_NO_LOADED_CHUNKS
    }
}
