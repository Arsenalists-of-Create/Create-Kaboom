package com.happysg.kaboom.explosion;

/**
 * Immutable, per-warhead explosion tuning. Build one profile per warhead type and reuse it.
 * A seed of {@link #AUTO_SEED} asks the engine to make a fresh deterministic seed at detonation time.
 */
public final class KaboomExplosionProfile {
    public static final long AUTO_SEED = Long.MIN_VALUE;

    private final float yield;
    private final long seed;
    private final boolean terrainDamage;
    private final boolean entityDamage;
    private final boolean shockwave;
    private final boolean debris;
    private final boolean scorchSurface;
    private final boolean pruneDetachedFragments;
    private final float craterRadiusScale;
    private final float craterDepthScale;
    private final float shockwaveRadiusScale;
    private final int maxCaptureRadius;
    private final int maxBlockChanges;
    private final int maxDetachedFragmentBlocks;

    private KaboomExplosionProfile(Builder builder) {
        yield = clamp(builder.yield, 0.1f, 4_096.0f);
        seed = builder.seed;
        terrainDamage = builder.terrainDamage;
        entityDamage = builder.entityDamage;
        shockwave = builder.shockwave;
        debris = builder.debris;
        scorchSurface = builder.scorchSurface;
        pruneDetachedFragments = builder.pruneDetachedFragments;
        craterRadiusScale = clamp(builder.craterRadiusScale, 0.15f, 1.50f);
        craterDepthScale = clamp(builder.craterDepthScale, 0.10f, 1.25f);
        shockwaveRadiusScale = clamp(builder.shockwaveRadiusScale, 1.0f, 5.0f);
        maxCaptureRadius = clamp(builder.maxCaptureRadius, 8, 64);
        maxBlockChanges = clamp(builder.maxBlockChanges, 1_000, 250_000);
        maxDetachedFragmentBlocks = clamp(builder.maxDetachedFragmentBlocks, 32, 100_000);
    }

    public static Builder builder(float yield) {
        return new Builder(yield);
    }

    public static KaboomExplosionProfile defaultWarhead(float yield) {
        return builder(yield).build();
    }

    public KaboomExplosionProfile withResolvedSeed(long resolvedSeed) {
        return builder(yield)
            .seed(resolvedSeed)
            .terrainDamage(terrainDamage)
            .entityDamage(entityDamage)
            .shockwave(shockwave)
            .debris(debris)
            .scorchSurface(scorchSurface)
            .pruneDetachedFragments(pruneDetachedFragments)
            .craterRadiusScale(craterRadiusScale)
            .craterDepthScale(craterDepthScale)
            .shockwaveRadiusScale(shockwaveRadiusScale)
            .maxCaptureRadius(maxCaptureRadius)
            .maxBlockChanges(maxBlockChanges)
            .maxDetachedFragmentBlocks(maxDetachedFragmentBlocks)
            .build();
    }

    public float yield() {
        return yield;
    }

    public long seed() {
        return seed;
    }

    public boolean terrainDamage() {
        return terrainDamage;
    }

    public boolean entityDamage() {
        return entityDamage;
    }

    public boolean shockwave() {
        return shockwave;
    }

    public boolean debris() {
        return debris;
    }

    public boolean scorchSurface() {
        return scorchSurface;
    }

    public boolean pruneDetachedFragments() {
        return pruneDetachedFragments;
    }

    public float craterRadiusScale() {
        return craterRadiusScale;
    }

    public float craterDepthScale() {
        return craterDepthScale;
    }

    public float shockwaveRadiusScale() {
        return shockwaveRadiusScale;
    }

    public int maxCaptureRadius() {
        return maxCaptureRadius;
    }

    public int maxBlockChanges() {
        return maxBlockChanges;
    }

    public int maxDetachedFragmentBlocks() {
        return maxDetachedFragmentBlocks;
    }

    public static final class Builder {
        private float yield;
        private long seed = AUTO_SEED;
        private boolean terrainDamage = true;
        private boolean entityDamage = true;
        private boolean shockwave = true;
        private boolean debris = true;
        private boolean scorchSurface = true;
        private boolean pruneDetachedFragments = true;

        // These defaults intentionally favor a smaller, smoother crater and wider surface damage.
        private float craterRadiusScale = 0.66f;
        private float craterDepthScale = 0.42f;
        private float shockwaveRadiusScale = 3.25f;
        private int maxCaptureRadius = 56;
        private int maxBlockChanges = 80_000;
        private int maxDetachedFragmentBlocks = 20_000;

        private Builder(float yield) {
            this.yield = yield;
        }

        public Builder yield(float value) {
            yield = value;
            return this;
        }

        public Builder seed(long value) {
            seed = value;
            return this;
        }

        public Builder terrainDamage(boolean value) {
            terrainDamage = value;
            return this;
        }

        public Builder entityDamage(boolean value) {
            entityDamage = value;
            return this;
        }

        public Builder shockwave(boolean value) {
            shockwave = value;
            return this;
        }

        public Builder debris(boolean value) {
            debris = value;
            return this;
        }

        public Builder scorchSurface(boolean value) {
            scorchSurface = value;
            return this;
        }

        public Builder pruneDetachedFragments(boolean value) {
            pruneDetachedFragments = value;
            return this;
        }

        public Builder craterRadiusScale(float value) {
            craterRadiusScale = value;
            return this;
        }

        public Builder craterDepthScale(float value) {
            craterDepthScale = value;
            return this;
        }

        public Builder shockwaveRadiusScale(float value) {
            shockwaveRadiusScale = value;
            return this;
        }

        public Builder maxCaptureRadius(int value) {
            maxCaptureRadius = value;
            return this;
        }

        public Builder maxBlockChanges(int value) {
            maxBlockChanges = value;
            return this;
        }

        public Builder maxDetachedFragmentBlocks(int value) {
            maxDetachedFragmentBlocks = value;
            return this;
        }

        public KaboomExplosionProfile build() {
            return new KaboomExplosionProfile(this);
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
