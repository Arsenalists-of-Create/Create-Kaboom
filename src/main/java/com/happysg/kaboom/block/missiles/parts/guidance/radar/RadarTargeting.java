package com.happysg.kaboom.block.missiles.parts.guidance.radar;

import com.happysg.kaboom.block.missiles.assembly.MissileAssemblyResult;
import com.happysg.kaboom.compat.Mods;
import com.happysg.kaboom.compat.sable.SableUtils;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.ClipContextExtension;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class RadarTargeting {
    private static final double SENSOR_APERTURE_EPSILON = 0.05;
    private static final double MIN_DISTANCE_SQR = 1.0e-8;

    public enum TargetKind {
        ENTITY,
        SABLE
    }

    public record SensorFrame(Vec3 origin, Vec3 forward, @Nullable UUID launcherSublevelId) {
    }

    public record Candidate(
            UUID id,
            TargetKind kind,
            Vec3 position,
            Vec3 velocity,
            double alignment,
            double distanceSqr
    ) {
    }

    private RadarTargeting() {
    }

    public static SensorFrame sensorFrame(ServerLevel level, MissileAssemblyResult result) {
        Direction direction = result.getAssemblyDirection();
        Vec3 localForward = Vec3.atLowerCornerOf(direction.getNormal()).normalize();
        Vec3 localOrigin = findNoseAperture(level, result, direction);
        SubLevelAccess launcherSublevel = SableUtils.getShipManagingPos(level, result.getControllerPos());
        Vec3 worldOrigin = SableUtils.getWorldVec(localOrigin, launcherSublevel);
        Vec3 worldForward = SableUtils.getWorldVecDirectionTransform(localForward, launcherSublevel);
        if (worldForward.lengthSqr() < MIN_DISTANCE_SQR) {
            worldForward = localForward;
        } else {
            worldForward = worldForward.normalize();
        }
        UUID launcherId = launcherSublevel == null ? null : launcherSublevel.getUniqueId();
        return new SensorFrame(worldOrigin, worldForward, launcherId);
    }

    @Nullable
    public static Candidate acquire(ServerLevel level, SensorFrame frame, double range, double halfAngleDegrees,
                                    @Nullable UUID stickyTargetId) {
        return acquire(level, frame, range, halfAngleDegrees, stickyTargetId, null);
    }

    @Nullable
    public static Candidate acquire(ServerLevel level, SensorFrame frame, double range, double halfAngleDegrees,
                                    @Nullable UUID stickyTargetId, @Nullable UUID excludedTargetId) {
        return select(level, frame, candidates(level, frame, range,
                halfAngleDegrees, excludedTargetId), stickyTargetId);
    }

    public static List<Candidate> candidates(ServerLevel level, SensorFrame frame,
                                             double range, double halfAngleDegrees,
                                             @Nullable UUID excludedTargetId) {
        double safeRange = Math.max(1.0, range);
        AABB searchBounds = new AABB(frame.origin(), frame.origin()).inflate(safeRange);
        List<Candidate> candidates = new ArrayList<>();

        for (Entity entity : level.getEntities((Entity) null, searchBounds, RadarTargeting::isAircraftLike)) {
            if (excludedTargetId != null && excludedTargetId.equals(entity.getUUID())) continue;
            if (SableUtils.getShipManagingPos(level, entity.position()) != null) continue;
            Vec3 position = entity.getBoundingBox().getCenter();
            Candidate candidate = candidate(frame, entity.getUUID(), TargetKind.ENTITY,
                    position, entity.getDeltaMovement(), safeRange, halfAngleDegrees);
            if (candidate != null) candidates.add(candidate);
        }

        if (Mods.SABLE.isLoaded()) {
            for (SubLevel subLevel : SableUtils.getLoadedShips(level, searchBounds)) {
                if (subLevel == null || subLevel.isRemoved()) continue;
                UUID id = subLevel.getUniqueId();
                if (id == null || id.equals(frame.launcherSublevelId()) || id.equals(excludedTargetId)) continue;
                Vec3 position = SableUtils.getSubLevelPosition(subLevel);
                Vec3 velocity = SableUtils.getSubLevelVelocity(level, subLevel);
                Candidate candidate = candidate(frame, id, TargetKind.SABLE,
                        position, velocity, safeRange, halfAngleDegrees);
                if (candidate != null) candidates.add(candidate);
            }
        }

        return List.copyOf(candidates);
    }

    @Nullable
    public static Candidate select(ServerLevel level, SensorFrame frame,
                                   List<Candidate> candidates,
                                   @Nullable UUID stickyTargetId) {
        List<Candidate> ranked = new ArrayList<>(candidates == null ? List.of() : candidates);
        if (stickyTargetId != null) {
            for (Candidate candidate : ranked) {
                if (stickyTargetId.equals(candidate.id()) && hasLineOfSight(level, frame.origin(), candidate)) {
                    return candidate;
                }
            }
        }

        ranked.sort(Comparator
                .comparingDouble(Candidate::alignment).reversed()
                .thenComparingDouble(Candidate::distanceSqr)
                .thenComparing(candidate -> candidate.id().toString()));
        for (Candidate candidate : ranked) {
            if (hasLineOfSight(level, frame.origin(), candidate)) {
                return candidate;
            }
        }
        return null;
    }

    @Nullable
    public static Candidate candidate(SensorFrame frame, UUID id, TargetKind kind,
                                      Vec3 position, Vec3 velocity,
                                      double range, double halfAngleDegrees) {
        return createCandidate(frame, id, kind, position, velocity,
                range, halfAngleDegrees);
    }

    public static boolean isWithinEnvelope(Vec3 origin, Vec3 forward, Vec3 targetPosition,
                                           double range, double halfAngleDegrees) {
        if (!isFinite(origin) || !isFinite(forward) || !isFinite(targetPosition)) return false;
        Vec3 toTarget = targetPosition.subtract(origin);
        double distanceSqr = toTarget.lengthSqr();
        double safeRange = Math.max(0.0, range);
        if (distanceSqr < MIN_DISTANCE_SQR || distanceSqr > safeRange * safeRange) return false;
        if (forward.lengthSqr() < MIN_DISTANCE_SQR) return false;
        double clampedAngle = Math.max(0.0, Math.min(180.0, halfAngleDegrees));
        double minimumDot = Math.cos(Math.toRadians(clampedAngle));
        return forward.normalize().dot(toTarget.normalize()) >= minimumDot;
    }

    public static boolean hasLineOfSight(ServerLevel level, Vec3 origin, Candidate candidate) {
        UUID targetSublevelId = candidate.kind() == TargetKind.SABLE ? candidate.id() : null;
        return hasLineOfSight(level, origin, candidate.position(), targetSublevelId);
    }

    public static boolean hasLineOfSight(ServerLevel level, Vec3 origin, Vec3 targetPosition,
                                         @Nullable UUID targetSublevelId) {
        return hasLineOfSight(level, origin, targetPosition, targetSublevelId, null);
    }

    /**
     * Block-target variant used by passive seekers. A world radar's own collider is a valid
     * terminal hit; moving Sable targets continue to use the ignored-sublevel ray behavior.
     */
    public static boolean hasLineOfSightToBlock(ServerLevel level, Vec3 origin, Vec3 targetPosition,
                                                BlockPos targetBlockPos, @Nullable UUID targetSublevelId) {
        return hasLineOfSight(level, origin, targetPosition, targetSublevelId, targetBlockPos);
    }

    private static boolean hasLineOfSight(ServerLevel level, Vec3 origin, Vec3 targetPosition,
                                          @Nullable UUID targetSublevelId, @Nullable BlockPos acceptedTargetBlock) {
        if (!isFinite(origin) || !isFinite(targetPosition)) return false;
        ClipContext context = new ClipContext(origin, targetPosition,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, (Entity) null);
        ignoreTargetSublevel(level, context, targetSublevelId);
        BlockHitResult hit = level.clip(context);
        return hit.getType() == HitResult.Type.MISS
                || acceptedTargetBlock != null && acceptedTargetBlock.equals(hit.getBlockPos())
                || hit.getLocation().distanceToSqr(targetPosition) <= SENSOR_APERTURE_EPSILON * SENSOR_APERTURE_EPSILON;
    }

    @Nullable
    private static Candidate createCandidate(SensorFrame frame, UUID id, TargetKind kind, Vec3 position, Vec3 velocity,
                                             double range, double halfAngleDegrees) {
        if (id == null || !isFinite(position) || !isFinite(velocity)) return null;
        Vec3 delta = position.subtract(frame.origin());
        if (!isWithinEnvelope(frame.origin(), frame.forward(), position, range, halfAngleDegrees)) return null;
        return new Candidate(id, kind, position, velocity,
                frame.forward().normalize().dot(delta.normalize()), delta.lengthSqr());
    }

    private static boolean isAircraftLike(Entity entity) {
        if (entity == null || !entity.isAlive() || entity.isSpectator()) return false;
        if (entity instanceof ItemEntity || entity instanceof Projectile) return false;
        if (!(entity instanceof LivingEntity) && !(entity instanceof AbstractContraptionEntity)) return false;
        return !entity.onGround();
    }

    private static Vec3 findNoseAperture(ServerLevel level, MissileAssemblyResult result, Direction direction) {
        Direction.Axis axis = direction.getAxis();
        boolean positive = direction.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        double extreme = positive ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;

        for (BlockPos pos : result.getBlocks()) {
            BlockState state = level.getBlockState(pos);
            VoxelShape shape = state.getCollisionShape(level, pos);
            AABB bounds = shape.isEmpty()
                    ? new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0)
                    : shape.bounds().move(pos);
            double edge = switch (axis) {
                case X -> positive ? bounds.maxX : bounds.minX;
                case Y -> positive ? bounds.maxY : bounds.minY;
                case Z -> positive ? bounds.maxZ : bounds.minZ;
            };
            extreme = positive ? Math.max(extreme, edge) : Math.min(extreme, edge);
        }

        Vec3 center = result.getWarhead().getCenter();
        if (!Double.isFinite(extreme)) {
            extreme = switch (axis) {
                case X -> center.x + direction.getStepX() * 0.5;
                case Y -> center.y + direction.getStepY() * 0.5;
                case Z -> center.z + direction.getStepZ() * 0.5;
            };
        }
        extreme += (positive ? 1.0 : -1.0) * SENSOR_APERTURE_EPSILON;
        return switch (axis) {
            case X -> new Vec3(extreme, center.y, center.z);
            case Y -> new Vec3(center.x, extreme, center.z);
            case Z -> new Vec3(center.x, center.y, extreme);
        };
    }

    private static void ignoreTargetSublevel(ServerLevel level, ClipContext context, @Nullable UUID targetSublevelId) {
        if (!Mods.SABLE.isLoaded() || targetSublevelId == null) return;
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return;
        SubLevel target = container.getSubLevel(targetSublevelId);
        if (target != null && context instanceof ClipContextExtension extension) {
            extension.sable$setIgnoredSubLevel(target);
        }
    }

    private static boolean isFinite(Vec3 value) {
        return value != null
                && Double.isFinite(value.x)
                && Double.isFinite(value.y)
                && Double.isFinite(value.z);
    }
}
