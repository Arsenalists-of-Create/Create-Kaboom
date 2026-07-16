package com.happysg.kaboom.block.missiles.parts.thrust;

import com.happysg.kaboom.CreateKaboom;
import com.happysg.kaboom.block.missiles.assembly.MissileAssembler;
import com.happysg.kaboom.block.missiles.assembly.MissileAssemblyResult;
import com.happysg.kaboom.block.missiles.assembly.MissileLaunchHelper;
import com.happysg.kaboom.block.missiles.chaining.ChainSystem;
import com.happysg.kaboom.block.missiles.chaining.client.ChainRenderer;
import com.happysg.kaboom.block.missiles.parts.guidance.IPoweredTargetAcquisition;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class ThrusterBlockEntity extends SmartBlockEntity {
   private boolean lastAssemblyPowered = false;
   private boolean lastGuidanceUsedPoweredAcquisition = false;
   private boolean poweredLaunchRejected = false;
   private BlockPos lastGuidancePos;
   private BlockPos activeAcquisitionGuidancePos;
   private List<BlockPos> lastAssemblyBlocks = List.of();
   private final ChainSystem chainSystem = new ChainSystem();
   private int chainSyncTimer = 0;
   public static final List<ThrusterBlockEntity.PendingEnforcement> PENDING_ENFORCEMENTS = new ArrayList<>();

   public ThrusterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
      super(type, pos, state);
   }

   public ChainSystem getChainSystem() {
      return this.chainSystem;
   }

   public void tick() {
      super.tick();
      Level level = this.getLevel();
      if (level != null && !level.isClientSide) {
         if (level instanceof ServerLevel serverLevel) {
            BlockPos controller = MissileAssembler.findControllerThruster(level, this.worldPosition);
            if (controller != null && controller.equals(this.worldPosition)) {
               if (this.tickLaunchControl(serverLevel)) {
                  return;
               }

               this.chainSystem.tickFromBlock(this.worldPosition, serverLevel);
               PENDING_ENFORCEMENTS.add(new ThrusterBlockEntity.PendingEnforcement(this.worldPosition.immutable(), serverLevel));
               this.chainSyncTimer++;
               if (this.chainSyncTimer >= 10) {
                  this.chainSyncTimer = 0;
                  this.chainSystem.populateEntityIds(serverLevel);
                  this.notifyUpdate();
               }
            }
         }
      }
   }

   private boolean tickLaunchControl(ServerLevel level) {
      MissileAssemblyResult result = MissileAssembler.scan(level, this.worldPosition);
      if (!result.isValid()) {
         this.resetTrackedAcquisition(level);
         this.lastAssemblyPowered = this.hasAssemblyPower(level, this.lastAssemblyBlocks);
         if (!this.lastAssemblyPowered) {
            this.poweredLaunchRejected = false;
         }
         this.lastGuidanceUsedPoweredAcquisition = false;
         this.lastGuidancePos = null;
         return false;
      } else {
         this.lastAssemblyBlocks = List.copyOf(result.getBlocks());
         BlockPos guidancePos = result.guidance();
         if (this.lastGuidancePos != null && !this.lastGuidancePos.equals(guidancePos)) {
            this.resetTrackedAcquisition(level);
         }

         this.lastGuidancePos = guidancePos == null ? null : guidancePos.immutable();
         boolean powered = this.hasAssemblyPower(level, this.lastAssemblyBlocks);

         BlockEntity guidance = guidancePos == null ? null : level.getBlockEntity(guidancePos);
         IPoweredTargetAcquisition acquisition = guidance instanceof IPoweredTargetAcquisition candidate
               && candidate.isPoweredAcquisitionEnabled()
               ? candidate
               : null;
         boolean usesPoweredAcquisition = acquisition != null;
         if (usesPoweredAcquisition != this.lastGuidanceUsedPoweredAcquisition) {
            this.resetTrackedAcquisition(level);
         }

         this.lastGuidanceUsedPoweredAcquisition = usesPoweredAcquisition;
         if (acquisition != null) {
            this.activeAcquisitionGuidancePos = guidancePos.immutable();
         }
         boolean risingEdge = powered && !this.lastAssemblyPowered;
         this.lastAssemblyPowered = powered;
         if (!powered) {
            this.poweredLaunchRejected = false;
            this.resetTrackedAcquisition(level);
            return false;
         }

         if (acquisition != null) {
            boolean acquired = acquisition.tickAcquisition(level, result);
            if (!this.poweredLaunchRejected && acquired) {
               return this.tryLatchedLaunch(level);
            }
         } else {
            this.resetTrackedAcquisition(level);
            return risingEdge && !this.poweredLaunchRejected && this.tryLatchedLaunch(level);
         }
         return false;
      }
   }

   private boolean tryLaunch(ServerLevel level) {
      try {
         return MissileLaunchHelper.assembleAndSpawn(level, this.worldPosition);
      } catch (AssemblyException var3) {
         CreateKaboom.getLogger().error("Failed to assemble missile at {}", this.worldPosition, var3);
         return false;
      }
   }

   private boolean tryLatchedLaunch(ServerLevel level) {
      boolean launched = this.tryLaunch(level);
      if (!launched) {
         this.poweredLaunchRejected = true;
      }
      return launched;
   }

   private boolean hasAssemblyPower(ServerLevel level, List<BlockPos> componentPositions) {
      if (componentPositions.isEmpty()) {
         return level.hasNeighborSignal(this.worldPosition);
      }
      for (BlockPos componentPos : componentPositions) {
         if (level.hasNeighborSignal(componentPos)) {
            return true;
         }
      }
      return false;
   }

   private void resetTrackedAcquisition(ServerLevel level) {
      if (this.activeAcquisitionGuidancePos != null
            && level.getBlockEntity(this.activeAcquisitionGuidancePos) instanceof IPoweredTargetAcquisition acquisition) {
         acquisition.resetAcquisition();
      }

      this.activeAcquisitionGuidancePos = null;
   }

   protected void write(CompoundTag tag, Provider registries, boolean clientPacket) {
      super.write(tag, registries, clientPacket);
      tag.put("kaboom:ChainSystem", this.chainSystem.save());
   }

   protected void read(CompoundTag tag, Provider registries, boolean clientPacket) {
      super.read(tag, registries, clientPacket);
      if (tag.contains("kaboom:ChainSystem")) {
         this.chainSystem.load(tag.getCompound("kaboom:ChainSystem"));
      }

      if (clientPacket && !this.chainSystem.getAnchors().isEmpty()) {
         ChainRenderer.TRACKED_THRUSTERS.add(this.worldPosition);
      }
   }

   public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
   }

   public static record PendingEnforcement(BlockPos pos, ServerLevel level) {
   }
}
