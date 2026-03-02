package com.happysg.kaboom.block.missiles.chaining.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ChainCollisionField {

    public record CollisionQuery(double distance, Vec3 gradient) {}

    private final List<AABB> boxes = new ArrayList<>();

    public void build(Level level, List<VerletPoint> points) {
        boxes.clear();
        Set<BlockPos> visited = new HashSet<>();

        for (VerletPoint p : points) {
            BlockPos center = BlockPos.containing(p.pos);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos check = center.offset(dx, dy, dz);
                        if (!visited.add(check)) continue;

                        VoxelShape shape = level.getBlockState(check).getCollisionShape(level, check);
                        if (shape.isEmpty()) continue;

                        for (AABB box : shape.toAabbs()) {
                            boxes.add(box.move(check.getX(), check.getY(), check.getZ()));
                        }
                    }
                }
            }
        }
    }

    public CollisionQuery query(Vec3 point, double threshold) {
        double minDist = Double.MAX_VALUE;
        AABB nearest = null;

        for (int i = 0, size = boxes.size(); i < size; i++) {
            AABB box = boxes.get(i);

            // Quick reject: expand box by threshold and skip if point is outside
            if (point.x < box.minX - threshold || point.x > box.maxX + threshold ||
                point.y < box.minY - threshold || point.y > box.maxY + threshold ||
                point.z < box.minZ - threshold || point.z > box.maxZ + threshold) {
                continue;
            }

            double d = signedDistanceToAABB(point, box);
            if (d < minDist) {
                minDist = d;
                nearest = box;
            }
        }

        if (nearest == null || minDist > threshold) {
            return new CollisionQuery(minDist, Vec3.ZERO);
        }

        // Analytical gradient from the nearest box
        Vec3 grad = gradientToAABB(point, nearest);
        return new CollisionQuery(minDist, grad);
    }

    public static double signedDistanceToAABB(Vec3 point, AABB box) {
        double halfX = (box.maxX - box.minX) * 0.5;
        double halfY = (box.maxY - box.minY) * 0.5;
        double halfZ = (box.maxZ - box.minZ) * 0.5;

        double centerX = box.minX + halfX;
        double centerY = box.minY + halfY;
        double centerZ = box.minZ + halfZ;

        double qx = Math.abs(point.x - centerX) - halfX;
        double qy = Math.abs(point.y - centerY) - halfY;
        double qz = Math.abs(point.z - centerZ) - halfZ;

        double outsideX = Math.max(qx, 0);
        double outsideY = Math.max(qy, 0);
        double outsideZ = Math.max(qz, 0);
        double outsideDist = Math.sqrt(outsideX * outsideX + outsideY * outsideY + outsideZ * outsideZ);

        double insideDist = Math.min(Math.max(qx, Math.max(qy, qz)), 0);

        return outsideDist + insideDist;
    }

    private static Vec3 gradientToAABB(Vec3 point, AABB box) {
        double halfX = (box.maxX - box.minX) * 0.5;
        double halfY = (box.maxY - box.minY) * 0.5;
        double halfZ = (box.maxZ - box.minZ) * 0.5;

        double centerX = box.minX + halfX;
        double centerY = box.minY + halfY;
        double centerZ = box.minZ + halfZ;

        double dx = point.x - centerX;
        double dy = point.y - centerY;
        double dz = point.z - centerZ;

        double qx = Math.abs(dx) - halfX;
        double qy = Math.abs(dy) - halfY;
        double qz = Math.abs(dz) - halfZ;

        boolean outside = qx > 0 || qy > 0 || qz > 0;

        if (outside) {
            // Gradient points from nearest surface point to the query point
            double gx = qx > 0 ? Math.signum(dx) * qx : 0;
            double gy = qy > 0 ? Math.signum(dy) * qy : 0;
            double gz = qz > 0 ? Math.signum(dz) * qz : 0;
            double len = Math.sqrt(gx * gx + gy * gy + gz * gz);
            if (len < 1e-10) return new Vec3(0, 1, 0);
            return new Vec3(gx / len, gy / len, gz / len);
        } else {
            // Inside: gradient points toward nearest face
            double distMinX = Math.abs(qx);
            double distMinY = Math.abs(qy);
            double distMinZ = Math.abs(qz);

            // qx, qy, qz are all negative inside; the one closest to 0 is the nearest face
            if (qx > qy && qx > qz) {
                return new Vec3(Math.signum(dx), 0, 0);
            } else if (qy > qz) {
                return new Vec3(0, Math.signum(dy), 0);
            } else {
                return new Vec3(0, 0, Math.signum(dz));
            }
        }
    }
}
