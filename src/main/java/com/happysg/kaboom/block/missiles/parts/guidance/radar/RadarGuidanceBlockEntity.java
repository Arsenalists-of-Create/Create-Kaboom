package com.happysg.kaboom.block.missiles.parts.guidance.radar;

import com.happysg.kaboom.block.missiles.assembly.MissileAssemblyResult;
import com.happysg.kaboom.block.missiles.parts.guidance.IPoweredTargetAcquisition;
import com.happysg.kaboom.block.missiles.util.IMissileGuidanceProvider;
import com.happysg.kaboom.block.missiles.util.MissileFlightProfile;
import com.happysg.kaboom.block.missiles.util.MissileGuidanceData;
import com.happysg.kaboom.compat.radars.RadarCompatRegistry;
import com.happysg.kaboom.compat.radars.RadarIntegration;
import com.happysg.kaboom.compat.sable.SableUtils;
import com.happysg.kaboom.config.KaboomConfig;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class RadarGuidanceBlockEntity extends BlockEntity implements IMissileGuidanceProvider, IPoweredTargetAcquisition {
   private static final int LOCK_GRACE_TICKS = 5;
   private static final String TAG_CANDIDATE = "RadarCandidate";
   private static final String TAG_LOCK_TICKS = "RadarCandidateTicks";
   private static final String TAG_LOCKED_TARGET = "RadarLockedTarget";
   private static final String TAG_LAST_ACQUISITION_TICK = "RadarLastAcquisitionTick";
   private static final String TAG_RWR_EMITTER_ID = "RadarRwrEmitterId";
   @Nullable
   private UUID candidateTargetId;
   private int candidateLockTicks;
   private int candidateMissTicks;
   @Nullable
   private UUID lockedTargetId;
   private long lastAcquisitionTick = -1L;
   private UUID rwrEmitterId = UUID.randomUUID();

   public RadarGuidanceBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
      super(type, pos, blockState);
   }

   @Override
   public MissileGuidanceData exportGuidance() {
      return MissileGuidanceData.radar(this.worldPosition, this.lockedTargetId, MissileFlightProfile.defaults(), this.rwrEmitterId);
   }

   @Override
   public boolean tickAcquisition(ServerLevel level, MissileAssemblyResult result) {
      long gameTime = level.getGameTime();
      this.spawnLockParticles(level);
      long acquisitionGap = this.lastAcquisitionTick < 0L ? 1L : gameTime - this.lastAcquisitionTick;
      if (acquisitionGap > 6L) {
         this.candidateTargetId = null;
         this.candidateLockTicks = 0;
         this.candidateMissTicks = 0;
         this.lockedTargetId = null;
      }

      this.lastAcquisitionTick = gameTime;
      RadarTargeting.SensorFrame frame = RadarTargeting.sensorFrame(level, result);
      RadarCompatRegistry.get()
         .updateRadarEmitter(
            level,
            this.rwrEmitterId,
            frame.origin(),
            frame.forward(),
            configuredRange(),
            configuredHalfAngleDegrees(),
            null,
            RadarIntegration.ThreatStage.LOCKED
         );
      RadarTargeting.Candidate candidate = RadarTargeting.acquire(level, frame, configuredRange(), configuredHalfAngleDegrees(), this.candidateTargetId);
      if (candidate == null || this.candidateTargetId != null && !candidate.id().equals(this.candidateTargetId)) {
         if (this.candidateTargetId != null && ++this.candidateMissTicks <= 5) {
            return false;
         }

         this.candidateTargetId = null;
         this.candidateLockTicks = 0;
         this.candidateMissTicks = 0;
         this.lockedTargetId = null;
         this.setChanged();
         if (candidate == null) {
            return false;
         }
      }

      this.candidateMissTicks = 0;
      if (!candidate.id().equals(this.candidateTargetId)) {
         this.candidateTargetId = candidate.id();
         this.candidateLockTicks = 1;
         this.lockedTargetId = null;
      } else {
         this.candidateLockTicks++;
      }

      if (this.candidateLockTicks >= configuredLockTicks()) {
         this.lockedTargetId = candidate.id();
      }

      this.setChanged();
      return this.lockedTargetId != null;
   }

   private void spawnLockParticles(ServerLevel level) {
      Vec3 center = SableUtils.getWorldVec(level, this.worldPosition.getCenter());
      level.sendParticles(DustParticleOptions.REDSTONE, center.x, center.y, center.z, 2, 0.3, 0.3, 0.3, 0.0);
   }

   @Override
   public void resetAcquisition() {
      this.removeRwrEmitter();
      if (this.candidateTargetId != null || this.candidateLockTicks != 0 || this.lockedTargetId != null || this.lastAcquisitionTick != -1L) {
         this.candidateTargetId = null;
         this.candidateLockTicks = 0;
         this.candidateMissTicks = 0;
         this.lockedTargetId = null;
         this.lastAcquisitionTick = -1L;
         this.setChanged();
      }
   }

   @Nullable
   public UUID getLockedTargetId() {
      return this.lockedTargetId;
   }

   public void setRemoved() {
      this.removeRwrEmitter();
      super.setRemoved();
   }

   protected void saveAdditional(CompoundTag tag, Provider registries) {
      super.saveAdditional(tag, registries);
      if (this.candidateTargetId != null) {
         tag.putUUID("RadarCandidate", this.candidateTargetId);
      }

      tag.putInt("RadarCandidateTicks", this.candidateLockTicks);
      if (this.lockedTargetId != null) {
         tag.putUUID("RadarLockedTarget", this.lockedTargetId);
      }

      tag.putLong("RadarLastAcquisitionTick", this.lastAcquisitionTick);
      tag.putUUID("RadarRwrEmitterId", this.rwrEmitterId);
   }

   protected void loadAdditional(CompoundTag tag, Provider registries) {
      super.loadAdditional(tag, registries);
      this.candidateTargetId = tag.hasUUID("RadarCandidate") ? tag.getUUID("RadarCandidate") : null;
      this.candidateLockTicks = Math.max(0, tag.getInt("RadarCandidateTicks"));
      this.lockedTargetId = tag.hasUUID("RadarLockedTarget") ? tag.getUUID("RadarLockedTarget") : null;
      this.lastAcquisitionTick = tag.contains("RadarLastAcquisitionTick") ? tag.getLong("RadarLastAcquisitionTick") : -1L;
      if (tag.hasUUID("RadarRwrEmitterId")) {
         this.rwrEmitterId = tag.getUUID("RadarRwrEmitterId");
      }
   }

   private static double configuredRange() {
      return Math.max(1.0, (double)KaboomConfig.server().radarAcquisitionRangeBlocks.getF());
   }

   private static double configuredHalfAngleDegrees() {
      return Math.max(0.0, Math.min(180.0, (double)KaboomConfig.server().radarAcquisitionHalfAngleDegrees.getF()));
   }

   private static int configuredLockTicks() {
      return Math.max(1, (Integer)KaboomConfig.server().radarLockTicks.get());
   }

   private void removeRwrEmitter() {
      if (this.level instanceof ServerLevel serverLevel) {
         RadarCompatRegistry.get().removeRadarEmitter(serverLevel, this.rwrEmitterId);
      }
   }
}
