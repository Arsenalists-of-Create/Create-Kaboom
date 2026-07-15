package com.happysg.kaboom.block.missiles.parts.thrust;

import com.happysg.kaboom.CreateKaboom;
import com.happysg.kaboom.block.missiles.assembly.MissileAssembler;
import com.happysg.kaboom.block.missiles.assembly.MissileAssemblyResult;
import com.happysg.kaboom.block.missiles.assembly.MissileLaunchHelper;
import com.happysg.kaboom.block.missiles.chaining.ChainSystem;
import com.happysg.kaboom.block.missiles.chaining.client.ChainRenderer;
import com.happysg.kaboom.block.missiles.parts.guidance.radar.RadarGuidanceBlockEntity;
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
   private boolean lastGuidanceWasRadar = false;
   private boolean radarLaunchRejected = false;
   private BlockPos lastGuidancePos;
   private BlockPos activeRadarGuidancePos;
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
         this.resetTrackedRadar(level);
         this.lastAssemblyPowered = false;
         this.lastGuidanceWasRadar = false;
         this.lastGuidancePos = null;
         return false;
      } else {
         BlockPos guidancePos = result.guidance();
         if (this.lastGuidancePos != null && !this.lastGuidancePos.equals(guidancePos)) {
            this.resetTrackedRadar(level);
            this.lastAssemblyPowered = false;
         }

         this.lastGuidancePos = guidancePos == null ? null : guidancePos.immutable();
         boolean powered = false;

         for (BlockPos componentPos : result.getBlocks()) {
            if (level.hasNeighborSignal(componentPos)) {
               powered = true;
               break;
            }
         }

         BlockEntity guidance = guidancePos == null ? null : level.getBlockEntity(guidancePos);
         boolean radarGuidance = guidance instanceof RadarGuidanceBlockEntity;
         if (radarGuidance != this.lastGuidanceWasRadar) {
            this.lastAssemblyPowered = false;
            this.radarLaunchRejected = false;
         }

         this.lastGuidanceWasRadar = radarGuidance;
         if (guidance instanceof RadarGuidanceBlockEntity radar) {
            this.activeRadarGuidancePos = guidancePos.immutable();
            this.lastAssemblyPowered = powered;
            if (!powered) {
               this.radarLaunchRejected = false;
               radar.resetAcquisition();
               return false;
            } else {
               boolean locked = radar.tickAcquisition(level, result);
               if (!this.radarLaunchRejected && locked) {
                  boolean launched = this.tryLaunch(level);
                  if (!launched) {
                     this.radarLaunchRejected = true;
                  }

                  return launched;
               } else {
                  return false;
               }
            }
         } else {
            this.resetTrackedRadar(level);
            boolean risingEdge = powered && !this.lastAssemblyPowered;
            this.lastAssemblyPowered = powered;
            return risingEdge && this.tryLaunch(level);
         }
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

   private void resetTrackedRadar(ServerLevel level) {
      this.radarLaunchRejected = false;
      if (this.activeRadarGuidancePos != null && level.getBlockEntity(this.activeRadarGuidancePos) instanceof RadarGuidanceBlockEntity radar) {
         radar.resetAcquisition();
      }

      this.activeRadarGuidancePos = null;
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
