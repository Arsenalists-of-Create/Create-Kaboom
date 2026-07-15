package com.happysg.kaboom.block.missiles.util;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;

public record MissileGuidanceData(
   MissileGuidanceType guidanceType,
   MissileTargetSpec target,
   MissileFlightProfile profile,
   @Nullable BlockPos networkControllerPos,
   @Nullable BlockPos radarGuidancePos,
   @Nullable UUID radarEmitterId
) {
   public MissileGuidanceData(MissileTargetSpec target, MissileFlightProfile profile) {
      this(MissileGuidanceType.GPS, target, profile, null, null, null);
   }

   public static MissileGuidanceData command(BlockPos networkControllerPos, MissileFlightProfile profile) {
      return new MissileGuidanceData(
         MissileGuidanceType.COMMAND, null, profile, networkControllerPos == null ? null : networkControllerPos.immutable(), null, null
      );
   }

   public static MissileGuidanceData radar(BlockPos radarGuidancePos, MissileFlightProfile profile) {
      return new MissileGuidanceData(MissileGuidanceType.RADAR, null, profile, null, radarGuidancePos == null ? null : radarGuidancePos.immutable(), null);
   }

   public static MissileGuidanceData radar(BlockPos radarGuidancePos, UUID lockedTargetId, MissileFlightProfile profile) {
      return radar(radarGuidancePos, lockedTargetId, profile, null);
   }

   public static MissileGuidanceData radar(BlockPos radarGuidancePos, UUID lockedTargetId, MissileFlightProfile profile, @Nullable UUID radarEmitterId) {
      MissileTargetSpec target = lockedTargetId == null ? null : MissileTargetSpec.entity(lockedTargetId);
      return new MissileGuidanceData(
         MissileGuidanceType.RADAR, target, profile, null, radarGuidancePos == null ? null : radarGuidancePos.immutable(), radarEmitterId
      );
   }

   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("GuidanceType", this.guidanceType.name());
      if (this.target != null) {
         tag.put("Target", this.target.toTag());
      }

      tag.put("Profile", (this.profile == null ? MissileFlightProfile.defaults() : this.profile).toTag());
      if (this.networkControllerPos != null) {
         tag.put("NetworkControllerPos", NbtUtils.writeBlockPos(this.networkControllerPos));
      }

      if (this.radarGuidancePos != null) {
         tag.put("RadarGuidancePos", NbtUtils.writeBlockPos(this.radarGuidancePos));
      }

      if (this.radarEmitterId != null) {
         tag.putUUID("RadarEmitterId", this.radarEmitterId);
      }

      return tag;
   }

   public static MissileGuidanceData fromTag(CompoundTag tag) {
      MissileGuidanceType type = tag.contains("GuidanceType") ? MissileGuidanceType.fromName(tag.getString("GuidanceType")) : MissileGuidanceType.GPS;
      MissileTargetSpec t = tag.contains("Target") ? MissileTargetSpec.fromTag(tag.getCompound("Target")) : null;
      MissileFlightProfile p = tag.contains("Profile") ? MissileFlightProfile.fromTag(tag.getCompound("Profile")) : MissileFlightProfile.defaults();
      BlockPos networkControllerPos = tag.contains("NetworkControllerPos") ? (BlockPos)NbtUtils.readBlockPos(tag, "NetworkControllerPos").orElse(null) : null;
      BlockPos radarGuidancePos = tag.contains("RadarGuidancePos") ? (BlockPos)NbtUtils.readBlockPos(tag, "RadarGuidancePos").orElse(null) : null;
      UUID radarEmitterId = tag.hasUUID("RadarEmitterId") ? tag.getUUID("RadarEmitterId") : null;
      return new MissileGuidanceData(type, t, p, networkControllerPos, radarGuidancePos, radarEmitterId);
   }
}
