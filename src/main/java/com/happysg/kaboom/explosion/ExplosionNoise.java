package com.happysg.kaboom.explosion;

final class ExplosionNoise {
    private ExplosionNoise() {
    }

    static double hash01(long x, long y, long z, long seed) {
        long hash = splitMix64(x * 0x9E3779B97F4A7C15L ^ seed);
        hash = splitMix64(hash ^ y * 0xC2B2AE3D27D4EB4FL);
        hash = splitMix64(hash ^ z * 0x165667B19E3779F9L);
        return (hash >>> 11) * (1.0 / (1L << 53));
    }

    static long splitMix64(long value) {
        value += 0x9E3779B97F4A7C15L;
        long z = value;
        z = (z ^ z >>> 30) * 0xBF58476D1CE4E5B9L;
        z = (z ^ z >>> 27) * 0x94D049BB133111EBL;
        return z ^ z >>> 31;
    }

    static final class Random {
        private long state;

        Random(long seed) {
            state = seed;
        }

        double nextDouble() {
            state += 0x9E3779B97F4A7C15L;
            long z = state;
            z = (z ^ z >>> 30) * 0xBF58476D1CE4E5B9L;
            z = (z ^ z >>> 27) * 0x94D049BB133111EBL;
            z ^= z >>> 31;
            return (z >>> 11) * (1.0 / (1L << 53));
        }

        double nextGaussian() {
            double a = Math.max(nextDouble(), 1.0E-12);
            double b = nextDouble();
            return Math.sqrt(-2.0 * Math.log(a)) * Math.cos(Math.PI * 2.0 * b);
        }
    }
}
