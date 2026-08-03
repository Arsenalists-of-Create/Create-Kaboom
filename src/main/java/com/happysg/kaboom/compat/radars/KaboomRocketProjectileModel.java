package com.happysg.kaboom.compat.radars;

import com.happysg.radar.targeting.ProjectileDynamics;
import com.happysg.radar.targeting.ProjectileModel;
import com.happysg.radar.targeting.ProjectileStep;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import rbasamoyai.createbigcannons.munitions.config.FluidDragHandler;

/**
 * Nominal centerline model matching the rocket projectile's powered and
 * unpowered CBC integration.
 */
public record KaboomRocketProjectileModel(
        double muzzleSpeed,
        double boostAcceleration,
        double boostDistance,
        double gravity,
        double drag,
        boolean quadraticDrag,
        double dragDensity,
        int maxFlightTicks
) implements ProjectileModel {
    public KaboomRocketProjectileModel {
        muzzleSpeed = finiteNonNegative(muzzleSpeed);
        boostAcceleration = finiteNonNegative(boostAcceleration);
        boostDistance = finiteNonNegative(boostDistance);
        gravity = Double.isFinite(gravity) ? gravity : 0.0;
        drag = finiteNonNegative(drag);
        dragDensity = Double.isFinite(dragDensity)
                ? Math.max(0.0, dragDensity) : 1.0;
        maxFlightTicks = Math.max(1, maxFlightTicks);
    }

    @Override
    public boolean cbcPhysics() {
        return true;
    }

    @Override
    public boolean usesCustomDynamics() {
        return true;
    }

    @Override
    public ProjectileDynamics createDynamics(
            Vec3 startPosition,
            Vec3 aimDirection,
            Vec3 inheritedVelocity
    ) {
        Vec3 boostDirection = aimDirection != null
                && finite(aimDirection)
                && aimDirection.lengthSqr() > 1.0e-12
                ? aimDirection.normalize()
                : new Vec3(0.0, 1.0, 0.0);
        return new ProjectileDynamics() {
            private double traveledDistance;

            @Override
            public void step(
                    int tick,
                    double positionX,
                    double positionY,
                    double positionZ,
                    double velocityX,
                    double velocityY,
                    double velocityZ,
                    Level level,
                    ProjectileStep output
            ) {
                double accelerationX;
                double accelerationY;
                double accelerationZ;
                if (boostDistance > 0.0
                        && traveledDistance < boostDistance) {
                    accelerationX = boostDirection.x * boostAcceleration;
                    accelerationY = boostDirection.y * boostAcceleration;
                    accelerationZ = boostDirection.z * boostAcceleration;
                } else {
                    double speed = Math.sqrt(
                            velocityX * velocityX
                                    + velocityY * velocityY
                                    + velocityZ * velocityZ);
                    double density = dragDensity;
                    if (level != null) {
                        density += FluidDragHandler.getFluidDrag(
                                level.getFluidState(BlockPos.containing(
                                        positionX, positionY, positionZ)));
                    }

                    double dragForce = 0.0;
                    if (speed > 1.0e-8 && drag > 0.0 && density > 0.0) {
                        dragForce = drag * density * speed;
                        if (quadraticDrag) {
                            dragForce *= speed;
                        }
                        dragForce = Math.min(dragForce, speed);
                    }
                    double inverseSpeed = speed > 1.0e-8 ? 1.0 / speed : 0.0;
                    accelerationX = -velocityX * inverseSpeed * dragForce;
                    accelerationY = gravity
                            - velocityY * inverseSpeed * dragForce;
                    accelerationZ = -velocityZ * inverseSpeed * dragForce;
                }

                double nextX = positionX + velocityX + accelerationX * 0.5;
                double nextY = positionY + velocityY + accelerationY * 0.5;
                double nextZ = positionZ + velocityZ + accelerationZ * 0.5;
                double dx = nextX - positionX;
                double dy = nextY - positionY;
                double dz = nextZ - positionZ;
                double traveled = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (Double.isFinite(traveled)) {
                    traveledDistance += traveled;
                }

                output.set(
                        nextX, nextY, nextZ,
                        velocityX + accelerationX,
                        velocityY + accelerationY,
                        velocityZ + accelerationZ
                );
            }
        };
    }

    @Override
    public double estimateFlightTicks(double distance) {
        if (!Double.isFinite(distance) || distance <= 0.0) {
            return 0.0;
        }
        double poweredDistance = Math.min(distance, boostDistance);
        double poweredTicks;
        if (boostAcceleration > 1.0e-8) {
            poweredTicks = (-muzzleSpeed + Math.sqrt(
                    muzzleSpeed * muzzleSpeed
                            + 2.0 * boostAcceleration * poweredDistance))
                    / boostAcceleration;
        } else {
            poweredTicks = poweredDistance
                    / Math.max(1.0e-6, muzzleSpeed);
        }
        if (distance <= boostDistance) {
            return Math.min(maxFlightTicks, poweredTicks);
        }
        double burnoutSpeed = Math.max(
                1.0e-6, muzzleSpeed + boostAcceleration * poweredTicks);
        return Math.min(
                maxFlightTicks,
                poweredTicks + (distance - boostDistance) / burnoutSpeed);
    }

    private static double finiteNonNegative(double value) {
        return Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
    }

    private static boolean finite(Vec3 value) {
        return value != null
                && Double.isFinite(value.x)
                && Double.isFinite(value.y)
                && Double.isFinite(value.z);
    }
}
