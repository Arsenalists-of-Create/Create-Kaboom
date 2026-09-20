package com.happysg.kaboom.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;
import java.lang.reflect.Method;

public class SableCompatibility {

    public static boolean checkWheelMeshes(Level level, BlockPos pos) {
        try {
            Class<?> containerClass = Class.forName("dev.ryanhcode.sable.api.sublevel.SubLevelContainer");
            Method getContainerMethod = containerClass.getMethod("getContainer", Level.class);
            Object container = getContainerMethod.invoke(null, level);
            if (container == null) return false;

            Method getAllSubLevelsMethod = containerClass.getMethod("getAllSubLevels");
            Iterable<?> subLevels = (Iterable<?>) getAllSubLevelsMethod.invoke(container);
            if (subLevels == null) return false;

            AABB mineBox = new AABB(pos);

            for (Object subLevel : subLevels) {
                Method getPlotMethod = subLevel.getClass().getMethod("getPlot");
                Object plot = getPlotMethod.invoke(subLevel);
                if (plot == null) continue;

                Method getBlockEntityActorsMethod = plot.getClass().getMethod("getBlockEntityActors");
                Iterable<?> actors = (Iterable<?>) getBlockEntityActorsMethod.invoke(plot);
                if (actors == null) continue;

                for (Object actor : actors) {
                    if (actor instanceof net.minecraft.world.level.block.entity.BlockEntity be) {
                        String classNameLower = be.getClass().getSimpleName().toLowerCase();
                        if (classNameLower.contains("wheel") || classNameLower.contains("track") ||
                            classNameLower.contains("tread") || classNameLower.contains("bogie") ||
                            classNameLower.contains("bogey") || classNameLower.contains("suspension") ||
                            classNameLower.contains("roller")) {

                            Method logicalPoseMethod = subLevel.getClass().getMethod("logicalPose");
                            Object pose = logicalPoseMethod.invoke(subLevel);
                            if (pose == null) continue;

                            Method transformMethod = pose.getClass().getMethod("transformPosition", org.joml.Vector3dc.class, org.joml.Vector3d.class);

                            AABB localBox = new AABB(0.0, -1.0, 0.0, 1.0, 0.5, 1.0);

                            double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
                            double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;

                            net.minecraft.core.BlockPos lp = be.getBlockPos();
                            double[][] corners = {
                                {localBox.minX + lp.getX() + 0.5, localBox.minY + lp.getY() + 0.5, localBox.minZ + lp.getZ() + 0.5},
                                {localBox.minX + lp.getX() + 0.5, localBox.minY + lp.getY() + 0.5, localBox.maxZ + lp.getZ() + 0.5},
                                {localBox.minX + lp.getX() + 0.5, localBox.maxY + lp.getY() + 0.5, localBox.minZ + lp.getZ() + 0.5},
                                {localBox.minX + lp.getX() + 0.5, localBox.maxY + lp.getY() + 0.5, localBox.maxZ + lp.getZ() + 0.5},
                                {localBox.maxX + lp.getX() + 0.5, localBox.minY + lp.getY() + 0.5, localBox.minZ + lp.getZ() + 0.5},
                                {localBox.maxX + lp.getX() + 0.5, localBox.minY + lp.getY() + 0.5, localBox.maxZ + lp.getZ() + 0.5},
                                {localBox.maxX + lp.getX() + 0.5, localBox.maxY + lp.getY() + 0.5, localBox.minZ + lp.getZ() + 0.5},
                                {localBox.maxX + lp.getX() + 0.5, localBox.maxY + lp.getY() + 0.5, localBox.maxZ + lp.getZ() + 0.5}
                            };

                            for (double[] corner : corners) {
                                Vector3d localVec = new Vector3d(corner[0], corner[1], corner[2]);
                                Vector3d dest = new Vector3d();
                                transformMethod.invoke(pose, (org.joml.Vector3dc) localVec, dest);

                                if (dest.x < minX) minX = dest.x;
                                if (dest.y < minY) minY = dest.y;
                                if (dest.z < minZ) minZ = dest.z;
                                if (dest.x > maxX) maxX = dest.x;
                                if (dest.y > maxY) maxY = dest.y;
                                if (dest.z > maxZ) maxZ = dest.z;
                            }

                            AABB globalWheelBox = new AABB(minX, minY, minZ, maxX, maxY, maxZ);

                            if (mineBox.intersects(globalWheelBox)) {
                                return true;
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {}
        return false;
    }
}
