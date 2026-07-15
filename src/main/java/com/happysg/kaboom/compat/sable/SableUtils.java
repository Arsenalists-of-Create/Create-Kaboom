package com.happysg.kaboom.compat.sable;

import com.happysg.kaboom.compat.Mods;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.ClipContextExtension;
import dev.ryanhcode.sable.sublevel.SubLevel;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3fc;

public class SableUtils {
   public record LaunchKinematics(Vec3 position, Vec3 direction, Vec3 carrierVelocity, @Nullable UUID sourceSubLevelId) {
   }

   public static LaunchKinematics getLaunchKinematics(Level level, BlockPos sourcePos, Vec3 localPosition, Vec3 localDirection) {
      Vec3 fallbackDirection = localDirection.lengthSqr() < 1.0E-10
         ? new Vec3(0.0, 1.0, 0.0)
         : localDirection.normalize();
      SubLevelAccess subLevel = getShipManagingPos(level, sourcePos);
      if (subLevel == null) {
         return new LaunchKinematics(localPosition, fallbackDirection, Vec3.ZERO, null);
      }

      Vec3 position = getWorldVec(localPosition, subLevel);
      Vec3 transformedDirection = getWorldVecDirectionTransform(fallbackDirection, subLevel);
      Vec3 direction = transformedDirection.lengthSqr() < 1.0E-10
         ? fallbackDirection
         : transformedDirection.normalize();
      Vector3dc velocity = getVelocity(level, sourcePos);
      Vec3 carrierVelocity = velocity == null
         ? Vec3.ZERO
         : new Vec3(velocity.x(), velocity.y(), velocity.z());
      return new LaunchKinematics(position, direction, carrierVelocity, subLevel.getUniqueId());
   }

   public static void ignoreSubLevel(ClipContext context, @Nullable UUID subLevelId) {
      if (!Mods.SABLE.isLoaded() || subLevelId == null || !(context instanceof ClipContextExtension extension)) {
         return;
      }

      Predicate<SubLevel> existing = extension.sable$getSubLevelIgnoring();
      extension.sable$setSubLevelIgnoring(subLevel -> subLevelId.equals(subLevel.getUniqueId())
         || existing != null && existing.test(subLevel));
   }

   public static BlockPos getWorldPos(Level level, BlockPos pos) {
      if (Mods.SABLE.isLoaded() && isBlockInShipyard(level, pos)) {
         SubLevelAccess subLevel = SableCompanion.INSTANCE.getContaining(level, pos);
         if (subLevel != null) {
            Vector3d vec = subLevel.logicalPose().transformPosition(new Vector3d((double)pos.getX(), (double)pos.getY(), (double)pos.getZ()));
            return new BlockPos((int)vec.x(), (int)vec.y(), (int)vec.z());
         } else {
            return pos;
         }
      } else {
         return pos;
      }
   }

   public static Vec3 getShipVec(Vec3 vec3, BlockEntity be) {
      return !Mods.SABLE.isLoaded() ? vec3 : getShipVec(vec3, getShipManagingPos(be));
   }

   public static Vec3 getShipVec(Vec3 vec3, SubLevelAccess subLevel) {
      if (!Mods.SABLE.isLoaded()) {
         return vec3;
      } else if (subLevel != null) {
         Vector3d vec = subLevel.logicalPose().transformPositionInverse(new Vector3d(vec3.x, vec3.y, vec3.z));
         return new Vec3(vec.x(), vec.y(), vec.z());
      } else {
         return vec3;
      }
   }

   public static Vec3 getWorldVecDirectionTransform(Vec3 vec3, BlockEntity be) {
      return !Mods.SABLE.isLoaded() ? vec3 : getWorldVecDirectionTransform(vec3, getShipManagingPos(be));
   }

   public static Vec3 getWorldVecDirectionTransform(Vec3 vec3, SubLevelAccess subLevel) {
      if (!Mods.SABLE.isLoaded()) {
         return vec3;
      } else if (subLevel != null) {
         Vector3d vec = subLevel.logicalPose().transformNormal(new Vector3d(vec3.x, vec3.y, vec3.z));
         return new Vec3(vec.x(), vec.y(), vec.z());
      } else {
         return vec3;
      }
   }

   public static Vec3 getShipVecDirectionTransform(Vec3 vec3, SubLevelAccess subLevel) {
      if (!Mods.SABLE.isLoaded()) {
         return vec3;
      } else if (subLevel != null) {
         Vector3d vec = subLevel.logicalPose().transformNormalInverse(new Vector3d(vec3.x, vec3.y, vec3.z));
         return new Vec3(vec.x(), vec.y(), vec.z());
      } else {
         return vec3;
      }
   }

   public static BlockPos getWorldPos(BlockEntity blockEntity) {
      return getWorldPos(blockEntity.getLevel(), blockEntity.getBlockPos());
   }

   public static Iterable<SubLevel> getLoadedShips(Level level, AABB aabb) {
      if (!Mods.SABLE.isLoaded()) {
         return List.of();
      } else {
         BoundingBox3dc boundingBox = new BoundingBox3d(aabb.minX, aabb.minY, aabb.minZ, aabb.maxX, aabb.maxY, aabb.maxZ);
         return Objects.requireNonNull(SubLevelContainer.getContainer(level)).queryIntersecting(boundingBox);
      }
   }

   public static SubLevelAccess getLoadedSubLevel(ServerLevel level, UUID subLevelId, Vec3 lastKnownPosition) {
      if (Mods.SABLE.isLoaded() && subLevelId != null) {
         SubLevelContainer container = SubLevelContainer.getContainer(level);
         if (container == null) {
            return null;
         } else {
            SubLevel direct = container.getSubLevel(subLevelId);
            if (direct != null && !direct.isRemoved()) {
               return direct;
            } else {
               AABB search = new AABB(lastKnownPosition, lastKnownPosition).inflate(256.0);

               for (SubLevel subLevel : getLoadedShips(level, search)) {
                  if (!subLevel.isRemoved() && subLevelId.equals(subLevel.getUniqueId())) {
                     return subLevel;
                  }
               }

               return null;
            }
         }
      } else {
         return null;
      }
   }

   public static Vec3 getSubLevelPosition(SubLevelAccess subLevel) {
      if (Mods.SABLE.isLoaded() && subLevel != null && subLevel.boundingBox() != null) {
         Vector3d center = subLevel.boundingBox().center(new Vector3d());
         return new Vec3(center.x, center.y, center.z);
      } else {
         return Vec3.ZERO;
      }
   }

   public static Vec3 getSubLevelVelocity(Level level, SubLevelAccess subLevel) {
      if (Mods.SABLE.isLoaded() && subLevel != null && subLevel.boundingBox() != null) {
         Object velocity = SableCompanion.INSTANCE.getVelocity(level, subLevel.boundingBox().center());
         if (velocity instanceof Vector3dc vector) {
            return new Vec3(vector.x(), vector.y(), vector.z());
         } else {
            return velocity instanceof Vector3fc vector ? new Vec3((double)vector.x(), (double)vector.y(), (double)vector.z()) : Vec3.ZERO;
         }
      } else {
         return Vec3.ZERO;
      }
   }

   public static SubLevelAccess getShipManagingPos(Level level, BlockPos pos) {
      return !Mods.SABLE.isLoaded() ? null : SableCompanion.INSTANCE.getContaining(level, pos);
   }

   public static SubLevelAccess getShipManagingPos(BlockEntity blockEntity) {
      return getShipManagingPos(blockEntity.getLevel(), blockEntity.getBlockPos());
   }

   public static Vec3 getWorldVec(Level level, BlockPos pos) {
      if (!Mods.SABLE.isLoaded()) {
         return new Vec3((double)pos.getX(), (double)pos.getY(), (double)pos.getZ());
      } else {
         SubLevelAccess subLevel = SableCompanion.INSTANCE.getContaining(level, pos);
         if (subLevel != null) {
            Vec3 center = pos.getCenter();
            Vector3d vec = subLevel.logicalPose().transformPosition(new Vector3d(center.x, center.y, center.z));
            return new Vec3(vec.x(), vec.y(), vec.z());
         } else {
            return new Vec3((double)pos.getX(), (double)pos.getY(), (double)pos.getZ());
         }
      }
   }

   public static Vec3 getWorldVec(Level level, Vec3 vec3) {
      if (!Mods.SABLE.isLoaded()) {
         return vec3;
      } else {
         SubLevelAccess subLevel = SableCompanion.INSTANCE.getContaining(level, vec3);
         if (subLevel != null) {
            Vector3d vec = subLevel.logicalPose().transformPosition(new Vector3d(vec3.x, vec3.y, vec3.z));
            return new Vec3(vec.x(), vec.y(), vec.z());
         } else {
            return vec3;
         }
      }
   }

   public static Vec3 getWorldVec(Vec3 vec3, SubLevelAccess subLevel) {
      if (Mods.SABLE.isLoaded() && subLevel != null) {
         Vector3d transformed = subLevel.logicalPose().transformPosition(new Vector3d(vec3.x, vec3.y, vec3.z));
         return new Vec3(transformed.x(), transformed.y(), transformed.z());
      } else {
         return vec3;
      }
   }

   public static SubLevelAccess getShipManagingPos(Level level, Vec3 pos) {
      return !Mods.SABLE.isLoaded() ? null : SableCompanion.INSTANCE.getContaining(level, pos);
   }

   public static Vec3 getWorldVec(BlockEntity blockEntity) {
      return !Mods.SABLE.isLoaded() ? blockEntity.getBlockPos().getCenter() : getWorldVec(blockEntity.getLevel(), blockEntity.getBlockPos());
   }

   public static Vec3 getVec3FromVector(Vector3d vector) {
      return new Vec3(vector.x, vector.y, vector.z);
   }

   public static BlockPos getBlockPosFromVec3(Vec3 vec3) {
      return new BlockPos((int)vec3.x, (int)vec3.y, (int)vec3.z);
   }

   public static Vector3d getVector3dFromVec3(Vec3 vec) {
      return new Vector3d(vec.x, vec.y, vec.z);
   }

   public static boolean isBlockInShipyard(Level level, BlockPos blockPos) {
      return !Mods.SABLE.isLoaded() ? false : SableCompanion.INSTANCE.getContaining(level, blockPos) != null;
   }

   public static Vector3dc getVelocity(Level level, BlockPos pos) {
      if (!isBlockInShipyard(level, pos)) {
         return null;
      } else {
         Vector3d velocityMetersPerSecond = new Vector3d();
         SableCompanion.INSTANCE
            .getVelocity(level, new Vector3d((double)pos.getX() + 0.5, (double)pos.getY() + 0.5, (double)pos.getZ() + 0.5), velocityMetersPerSecond);
         return new Vector3d(velocityMetersPerSecond.x / 20.0, velocityMetersPerSecond.y / 20.0, velocityMetersPerSecond.z / 20.0);
      }
   }
}
