package com.happysg.kaboom.explosion;

import java.util.BitSet;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

final class ExplosionSimulation {
    private static final int[] EXPOSED_DX = {1, -1, 0, 0, 0, 0};
    private static final int[] EXPOSED_DZ = {0, 0, 1, -1, 0, 0};
    private static final int[] EXPOSED_DY = {0, 0, 0, 0, 1, -1};
    private static final double CRATER_RADIUS_COEFFICIENT = 2.75;
    private static final double MAX_NATURAL_RADIUS = 36.0;
    private static final double RAY_STEP = 0.45;
    private static final double AIR_ATTENUATION = 0.14;
    private static final double RAY_RESISTANCE_SCALE = 0.30;
    private static final double RAY_JITTER = 0.045;
    private static final double SHOCKWAVE_SHATTER_RATE = 1.30;
    private static final double SHOCKWAVE_DEBRIS_RATE = 0.13;

    private ExplosionSimulation() {
    }

    static SimulationResult simulate(ExplosionVolume volume, Vec3 center, KaboomExplosionProfile profile) {
        double centerX = center.x - volume.minX;
        double centerZ = center.z - volume.minZ;
        double centerY = center.y - volume.minY;
        double baseRadius = baseRadius(profile);
        double craterRadius = Math.max(1.0, baseRadius * profile.craterRadiusScale());
        double craterDepth = Math.max(1.0, baseRadius * profile.craterDepthScale());
        double shockwaveRadius = Math.min(profile.maxCaptureRadius() - 1.0, baseRadius * profile.shockwaveRadiusScale());

        BitSet destroy = new BitSet(volume.size());
        markSmoothCrater(volume, destroy, centerX, centerZ, centerY, craterRadius, craterDepth, profile.seed());
        castRays(volume, destroy, centerX, centerZ, centerY, baseRadius, profile.seed());
        applyDestroyMask(volume, destroy);

        if (profile.shockwave()) {
            applyShockwave(volume, centerX, centerZ, centerY, craterRadius, shockwaveRadius, profile);
        }
        if (profile.scorchSurface()) {
            scorchSurface(volume, centerX, centerZ, centerY, craterRadius, shockwaveRadius, profile.seed());
        }
        if (profile.pruneDetachedFragments()) {
            ExplosionCollapse.pruneDetachedFragments(volume, profile.maxDetachedFragmentBlocks());
        }
        ExplosionCollapse.removeSpecks(volume, 2);

        return new SimulationResult(baseRadius, craterRadius, shockwaveRadius, volume.truncated());
    }

    static double baseRadius(KaboomExplosionProfile profile) {
        return Math.min(MAX_NATURAL_RADIUS, CRATER_RADIUS_COEFFICIENT * Math.cbrt(profile.yield()));
    }

    private static void markSmoothCrater(ExplosionVolume volume, BitSet destroy,
                                         double centerX, double centerZ, double centerY,
                                         double craterRadius, double craterDepth, long seed) {
        int minX = Math.max(0, (int) Math.floor(centerX - craterRadius - 1.0));
        int maxX = Math.min(volume.width - 1, (int) Math.ceil(centerX + craterRadius + 1.0));
        int minZ = Math.max(0, (int) Math.floor(centerZ - craterRadius - 1.0));
        int maxZ = Math.min(volume.depth - 1, (int) Math.ceil(centerZ + craterRadius + 1.0));
        double radiusSquared = craterRadius * craterRadius;

        for (int x = minX; x <= maxX; x++) {
            double dx = x + 0.5 - centerX;
            for (int z = minZ; z <= maxZ; z++) {
                double dz = z + 0.5 - centerZ;
                double radial = (dx * dx + dz * dz) / radiusSquared;
                if (radial >= 1.0) {
                    continue;
                }

                // Smooth parabolic bowl: zero thickness at the rim, deepest at the center.
                double bowl = Math.pow(1.0 - radial, 0.65);
                double noise = ExplosionNoise.hash01(volume.minX + x, volume.minZ + z, volume.minY, seed) - 0.5;
                double lower = centerY - craterDepth * bowl + noise * 0.45;
                double upper = centerY + 0.50 * bowl + noise * 0.20;
                int minY = Math.max(0, (int) Math.floor(lower));
                int maxY = Math.min(volume.height - 1, (int) Math.ceil(upper));

                for (int y = minY; y <= maxY; y++) {
                    int index = volume.index(x, z, y);
                    if (volume.material(index).isDestructible()) {
                        destroy.set(index);
                    }
                }
            }
        }
    }

    private static void castRays(ExplosionVolume volume, BitSet destroy,
                                 double centerX, double centerZ, double centerY,
                                 double baseRadius, long seed) {
        ExplosionNoise.Random random = new ExplosionNoise.Random(seed ^ 0xC0FFEE4B1D1E5L);
        double initialEnergy = Math.max(1.0, baseRadius / 1.25);

        for (int ray = 0; ray < ExplosionRayDirections.X.length; ray++) {
            double dx = ExplosionRayDirections.X[ray] + random.nextGaussian() * RAY_JITTER;
            double dz = ExplosionRayDirections.Y[ray] + random.nextGaussian() * RAY_JITTER;
            double dy = ExplosionRayDirections.Z[ray] + random.nextGaussian() * RAY_JITTER;
            double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (length <= 1.0E-8) {
                continue;
            }
            dx /= length;
            dy /= length;
            dz /= length;

            double energy = initialEnergy * (0.65 + random.nextDouble() * 0.70);
            double x = centerX;
            double z = centerZ;
            double y = centerY;
            int lastIndex = -1;

            while (energy > 0.0) {
                int blockX = (int) Math.floor(x);
                int blockZ = (int) Math.floor(z);
                int blockY = (int) Math.floor(y);
                if (!volume.inBounds(blockX, blockZ, blockY)) {
                    break;
                }

                int index = volume.index(blockX, blockZ, blockY);
                if (index != lastIndex) {
                    ExplosionMaterial material = volume.material(index);
                    if (material != ExplosionMaterial.AIR) {
                        energy -= (volume.resistance(index) + 0.30) * RAY_RESISTANCE_SCALE;
                        if (energy > 0.0 && material.isDestructible()) {
                            destroy.set(index);
                        }
                    }
                    lastIndex = index;
                }

                x += dx * RAY_STEP;
                z += dz * RAY_STEP;
                y += dy * RAY_STEP;
                energy -= AIR_ATTENUATION * RAY_STEP;
            }
        }
    }

    private static void applyDestroyMask(ExplosionVolume volume, BitSet destroy) {
        for (int index = destroy.nextSetBit(0); index >= 0; index = destroy.nextSetBit(index + 1)) {
            volume.clear(index);
        }
    }

    private static void applyShockwave(ExplosionVolume volume,
                                       double centerX, double centerZ, double centerY,
                                       double craterRadius, double shockwaveRadius,
                                       KaboomExplosionProfile profile) {
        if (shockwaveRadius <= craterRadius) {
            return;
        }

        int minX = Math.max(0, (int) Math.floor(centerX - shockwaveRadius));
        int maxX = Math.min(volume.width - 1, (int) Math.ceil(centerX + shockwaveRadius));
        int minZ = Math.max(0, (int) Math.floor(centerZ - shockwaveRadius));
        int maxZ = Math.min(volume.depth - 1, (int) Math.ceil(centerZ + shockwaveRadius));
        int minY = Math.max(0, (int) Math.floor(centerY - shockwaveRadius));
        int maxY = Math.min(volume.height - 1, (int) Math.ceil(centerY + shockwaveRadius));
        double span = shockwaveRadius - craterRadius;
        long debrisSeed = profile.seed() ^ 0x51A7E5EEDL;

        for (int x = minX; x <= maxX; x++) {
            double dx = x + 0.5 - centerX;
            for (int z = minZ; z <= maxZ; z++) {
                double dz = z + 0.5 - centerZ;
                for (int y = minY; y <= maxY; y++) {
                    double dy = y + 0.5 - centerY;
                    double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (distance <= craterRadius * 0.40 || distance >= shockwaveRadius) {
                        continue;
                    }

                    int index = volume.index(x, z, y);
                    ExplosionMaterial material = volume.material(index);
                    if (!material.isDestructible() || !isExposed(volume, x, z, y)) {
                        continue;
                    }

                    double intensity = 1.0 - (distance - craterRadius) / span;
                    double random = ExplosionNoise.hash01(volume.minX + x, volume.minZ + z, volume.minY + y, debrisSeed);
                    if (material == ExplosionMaterial.PLANT || material == ExplosionMaterial.BRITTLE) {
                        if (random < intensity * SHOCKWAVE_SHATTER_RATE) {
                            volume.clear(index);
                        }
                    } else if (profile.debris() && material == ExplosionMaterial.SOLID) {
                        double toughness = Math.min(1.0, 6.0 / Math.max(0.1, volume.resistance(index)));
                        if (random < intensity * toughness * SHOCKWAVE_DEBRIS_RATE) {
                            volume.setLooseDebris(index);
                        }
                    }
                }
            }
        }
    }

    private static void scorchSurface(ExplosionVolume volume,
                                      double centerX, double centerZ, double centerY,
                                      double craterRadius, double shockwaveRadius, long seed) {
        int minX = Math.max(0, (int) Math.floor(centerX - shockwaveRadius));
        int maxX = Math.min(volume.width - 1, (int) Math.ceil(centerX + shockwaveRadius));
        int minZ = Math.max(0, (int) Math.floor(centerZ - shockwaveRadius));
        int maxZ = Math.min(volume.depth - 1, (int) Math.ceil(centerZ + shockwaveRadius));
        int minY = Math.max(0, (int) Math.floor(centerY - shockwaveRadius * 0.55));
        int maxY = Math.min(volume.height - 1, (int) Math.ceil(centerY + shockwaveRadius * 0.55));
        double span = Math.max(1.0, shockwaveRadius - craterRadius);

        for (int x = minX; x <= maxX; x++) {
            double dx = x + 0.5 - centerX;
            for (int z = minZ; z <= maxZ; z++) {
                double dz = z + 0.5 - centerZ;
                for (int y = minY; y <= maxY; y++) {
                    int index = volume.index(x, z, y);
                    BlockState state = volume.state(index);
                    if (!state.is(Blocks.GRASS_BLOCK) || !isExposed(volume, x, z, y)) {
                        continue;
                    }

                    double dy = y + 0.5 - centerY;
                    double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (distance <= craterRadius || distance >= shockwaveRadius) {
                        continue;
                    }

                    double intensity = 1.0 - (distance - craterRadius) / span;
                    double random = ExplosionNoise.hash01(volume.minX + x, volume.minZ + z, volume.minY + y, seed ^ 0x5C0A11EDL);
                    if (random < intensity * 0.55) {
                        volume.setState(index, Blocks.DIRT.defaultBlockState());
                    }
                }
            }
        }
    }

    private static boolean isExposed(ExplosionVolume volume, int x, int z, int y) {
        for (int direction = 0; direction < 6; direction++) {
            int nextX = x + EXPOSED_DX[direction];
            int nextZ = z + EXPOSED_DZ[direction];
            int nextY = y + EXPOSED_DY[direction];
            if (!volume.inBounds(nextX, nextZ, nextY)) {
                return true;
            }
            if (volume.material(volume.index(nextX, nextZ, nextY)).isOpenSpace()) {
                return true;
            }
        }
        return false;
    }

    record SimulationResult(double baseRadius, double craterRadius, double shockwaveRadius, boolean truncated) {
    }
}
