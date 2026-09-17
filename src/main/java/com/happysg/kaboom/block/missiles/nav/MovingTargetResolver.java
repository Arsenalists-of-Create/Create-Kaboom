package com.happysg.kaboom.block.missiles.nav;

import com.happysg.kaboom.block.missiles.util.MissileGuidanceData;
import com.happysg.kaboom.block.missiles.util.MissileGuidanceType;
import com.happysg.kaboom.block.missiles.util.MissileTargetSpec;
import com.happysg.kaboom.compat.radars.RadarCompatRegistry;
import com.happysg.kaboom.compat.sable.SableUtils;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import java.util.UUID;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class MovingTargetResolver {
   private MovingTargetResolver() {
   }

   @Nullable
   public static MovingTargetResolver.TargetData resolve(ServerLevel level, MissileGuidanceData guidance, Vec3 missilePosition) {
      if (guidance == null) {
         return null;
      } else if (guidance.guidanceType() == MissileGuidanceType.COMMAND) {
         return RadarCompatRegistry.isAvailable() ? RadarCompatRegistry.get().resolveCommandTarget(level, guidance.networkControllerPos(), null, false) : null;
      } else if (guidance.guidanceType() != MissileGuidanceType.RADAR) {
         return null;
      } else {
         MissileTargetSpec target = guidance.target();
         boolean hasLockedTarget = target != null && target.type() == MissileTargetSpec.TargetType.ENTITY && target.entityId() != null;
         return hasLockedTarget
            ? lockedRadarTarget(level, target.entityId(), missilePosition)
            : (
               RadarCompatRegistry.isAvailable()
                  ? RadarCompatRegistry.get().resolveLegacyRadarTarget(level, guidance.radarGuidancePos(), missilePosition)
                  : null
            );
      }
   }

   @Nullable
   public static MovingTargetResolver.TargetData resolveSuppressedCommandTarget(ServerLevel level, MissileGuidanceData guidance, @Nullable String targetId) {
      return guidance != null && guidance.guidanceType() == MissileGuidanceType.COMMAND && RadarCompatRegistry.isAvailable()
         ? RadarCompatRegistry.get().resolveCommandTarget(level, guidance.networkControllerPos(), targetId, true)
         : null;
   }

   @Nullable
   private static MovingTargetResolver.TargetData lockedRadarTarget(ServerLevel level, UUID targetId, Vec3 missilePosition) {
      Entity entity = level.getEntity(targetId);
      if (entity != null && entity.isAlive()) {
         return new MovingTargetResolver.TargetData(
            targetId.toString(), "entity", entity.getBoundingBox().getCenter(), entity.getDeltaMovement(), level.getGameTime(), true, "radar_entity_lock"
         );
      } else {
         SubLevelAccess subLevel = SableUtils.getLoadedSubLevel(level, targetId, missilePosition);
         if (subLevel != null) {
            Vec3 position = SableUtils.getSubLevelPosition(subLevel);
            Vec3 velocity = SableUtils.getSubLevelVelocity(level, subLevel);
            if (isFinite(position) && isFinite(velocity)) {
               return new MovingTargetResolver.TargetData(targetId.toString(), "sable", position, velocity, level.getGameTime(), true, "radar_sable_lock");
            }
         }

         return null;
      }
   }

   private static boolean isFinite(Vec3 value) {
      return value != null && Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
   }

   @Nullable
   public static TargetData applyRadarJamming(ServerLevel level,
                                              MissileGuidanceData guidance,
                                              Vec3 sensorPosition,
                                              Vec3 sensorForward,
                                              double range,
                                              double halfAngleDegrees,
                                              @Nullable TargetData rawTarget) {
      if (guidance == null || guidance.guidanceType() != MissileGuidanceType.RADAR
         || guidance.radarEmitterId() == null || !RadarCompatRegistry.isAvailable()) {
         return rawTarget;
      }
      List<TargetData> reported = RadarCompatRegistry.get().reportRadarTracks(
         level, guidance.radarEmitterId(), sensorPosition, sensorForward,
         range, halfAngleDegrees, rawTarget == null ? List.of() : List.of(rawTarget));
      if (reported.isEmpty()) return null;
      String preferred = rawTarget == null ? null : rawTarget.id();
      if (preferred != null) {
         for (TargetData observation : reported) {
            if (preferred.equals(observation.id())) return observation;
         }
      }
      Vec3 forward = sensorForward.lengthSqr() > 1.0E-8 ? sensorForward.normalize() : new Vec3(0.0, 1.0, 0.0);
      return reported.stream().min(java.util.Comparator
         .comparingDouble((TargetData observation) -> -forward.dot(observation.position().subtract(sensorPosition).normalize()))
         .thenComparingDouble(observation -> observation.position().distanceToSqr(sensorPosition))
         .thenComparing(TargetData::id)).orElse(null);
   }

   public static record TargetData(String id, String category, Vec3 position, Vec3 velocity,
                                   long scannedTime, boolean live, String source,
                                   boolean jammed, boolean synthetic) {
      public TargetData(String id, String category, Vec3 position, Vec3 velocity,
                        long scannedTime, boolean live, String source) {
         this(id, category, position, velocity, scannedTime, live, source, false, false);
      }

      public int ageTicks(ServerLevel level) {
         return (int)Math.max(0L, level.getGameTime() - this.scannedTime);
      }
   }
}
