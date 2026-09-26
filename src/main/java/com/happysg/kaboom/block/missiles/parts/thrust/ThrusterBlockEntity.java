package com.happysg.kaboom.block.missiles.parts.thrust;

import com.happysg.kaboom.CreateKaboom;
import com.happysg.kaboom.block.missiles.assembly.MissileAssembler;
import com.happysg.kaboom.block.missiles.assembly.MissileAssemblyExceptionDisplay;
import com.happysg.kaboom.block.missiles.assembly.MissileAssemblyResult;
import com.happysg.kaboom.block.missiles.assembly.MissileLaunchHelper;
import com.happysg.kaboom.block.missiles.assembly.MissileSize;
import com.happysg.kaboom.block.missiles.chaining.ChainSystem;
import com.happysg.kaboom.block.missiles.chaining.client.ChainRenderer;
import com.happysg.kaboom.block.missiles.parts.fuel.MissileFuelTankBlockEntity;
import com.happysg.kaboom.block.missiles.parts.guidance.IPoweredTargetAcquisition;
import com.happysg.kaboom.client.MissileClientEffects;
import com.happysg.kaboom.client.MissileLaunchEffects;
import com.happysg.kaboom.compat.sable.SableUtils;
import com.happysg.kaboom.config.KaboomConfig;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public class ThrusterBlockEntity extends SmartBlockEntity implements MissileAssemblyExceptionDisplay {
   private boolean lastAssemblyPowered = false;
   private boolean lastGuidanceUsedPoweredAcquisition = false;
   private boolean poweredLaunchRejected = false;
   private BlockPos lastGuidancePos;
   private BlockPos activeAcquisitionGuidancePos;
   private List<BlockPos> lastAssemblyBlocks = List.of();
   private final ChainSystem chainSystem = new ChainSystem();
   private int chainSyncTimer = 0;
   private int launchTicksRemaining;
   private MissileSize pendingLaunchSize = MissileSize.SMALL;
   private final Map<BlockPos, BlockState> pendingLaunchBlocks = new LinkedHashMap<>();
   private transient boolean clientLaunchSoundStarted;
   private AssemblyException lastAssemblyException;
   public static final List<ThrusterBlockEntity.PendingEnforcement> PENDING_ENFORCEMENTS = new ArrayList<>();

   public ThrusterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
      super(type, pos, state);
   }

   public ChainSystem getChainSystem() {
      return this.chainSystem;
   }

   public AssemblyException getStoredAssemblyException() {
      return this.lastAssemblyException;
   }

   public void tick() {
      super.tick();
      Level level = this.getLevel();
      if (level != null && level.isClientSide) {
         this.tickClientLaunchEffects(level);
      } else if (level != null) {
         if (level instanceof ServerLevel serverLevel) {
            if (this.launchTicksRemaining > 0) {
               this.tickPendingLaunch(serverLevel);
               if (!this.isRemoved()) {
                  this.chainSystem.tickFromBlock(this.worldPosition, serverLevel);
                  PENDING_ENFORCEMENTS.add(new PendingEnforcement(this.worldPosition.immutable(), serverLevel));
               }
               return;
            }
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
         boolean powered = this.hasAssemblyPower(level, this.lastAssemblyBlocks);
         boolean risingEdge = powered && !this.lastAssemblyPowered;
         this.lastAssemblyPowered = powered;
         if (risingEdge) {
            this.setLaunchDiagnostic(MissileLaunchHelper.diagnoseFreeLaunch(level, this.worldPosition));
         }
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
            if (risingEdge && !acquired) {
               this.setLaunchDiagnostic(MissileLaunchHelper.diagnoseFreeLaunch(level, this.worldPosition));
            }
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
         MissileLaunchHelper.PreparedFreeLaunch prepared =
                 MissileLaunchHelper.prepareFreeLaunch(level, this.worldPosition);
         if (prepared == null) return false;
         int launchDelayTicks = KaboomConfig.server().missileLaunchDelayTicks(prepared.size());
         if (launchDelayTicks <= 0) {
            return MissileLaunchHelper.finishFreeLaunch(level, this.worldPosition, prepared.blocks());
         }
         this.pendingLaunchBlocks.clear();
         this.pendingLaunchBlocks.putAll(prepared.blocks());
         this.pendingLaunchSize = prepared.size();
         this.launchTicksRemaining = launchDelayTicks;
         this.clientLaunchSoundStarted = false;
         this.setChanged();
         this.notifyUpdate();
         return true;
      } catch (AssemblyException var3) {
         CreateKaboom.getLogger().error("Failed to assemble missile at {}", this.worldPosition, var3);
         return false;
      }
   }

   private void tickPendingLaunch(ServerLevel level) {
      this.burnPendingFuel(Math.max(1, KaboomConfig.server().maxFuelBurnPerTick.get()));
      --this.launchTicksRemaining;
      this.setChanged();
      if (this.launchTicksRemaining > 0) return;

      boolean launched = false;
      try {
         launched = MissileLaunchHelper.finishFreeLaunch(level, this.worldPosition,
                 Map.copyOf(this.pendingLaunchBlocks));
      } catch (AssemblyException exception) {
         CreateKaboom.getLogger().error("Failed to finish missile launch at {}", this.worldPosition, exception);
      }
      if (!launched && !this.isRemoved()) {
         this.pendingLaunchBlocks.clear();
         this.poweredLaunchRejected = true;
         this.notifyUpdate();
      }
   }

   private void burnPendingFuel(int requested) {
      int remaining = requested;
      Level level = this.getLevel();
      if (level == null) return;
      for (BlockPos pos : this.pendingLaunchBlocks.keySet()) {
         if (remaining <= 0) break;
         if (level.getBlockEntity(pos) instanceof MissileFuelTankBlockEntity tank) {
            remaining -= tank.getTank().drain(remaining, IFluidHandler.FluidAction.EXECUTE).getAmount();
         }
      }
   }

   private void tickClientLaunchEffects(Level level) {
      if (this.launchTicksRemaining <= 0) return;
      Direction forward = this.getBlockState().hasProperty(ThrusterBlock.FACING)
              ? this.getBlockState().getValue(ThrusterBlock.FACING) : Direction.UP;
      Vec3 localDirection = Vec3.atLowerCornerOf(forward.getNormal());
      SableUtils.LaunchKinematics pose = SableUtils.getLaunchKinematics(
              level, this.worldPosition, this.worldPosition.getCenter(), localDirection);
      if (!this.clientLaunchSoundStarted) {
         this.clientLaunchSoundStarted = true;
         MissileClientEffects.startBlockLaunch(this, this.launchTicksRemaining);
      }
      if (level instanceof net.minecraft.client.multiplayer.ClientLevel clientLevel) {
         MissileLaunchEffects.emit(clientLevel, pose.position(),
                 pose.direction(), pose.carrierVelocity(), this.pendingLaunchSize);
      }
      --this.launchTicksRemaining;
   }

   public int getLaunchTicksRemaining() {
      return this.launchTicksRemaining;
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

   public void setLaunchDiagnostic(@Nullable AssemblyException diagnostic) {
      Object previous = this.lastAssemblyException == null ? null : this.lastAssemblyException.component;
      Object next = diagnostic == null ? null : diagnostic.component;
      if (Objects.equals(previous, next)) {
         return;
      }
      this.lastAssemblyException = diagnostic;
      this.setChanged();
      this.notifyUpdate();
   }

   protected void write(CompoundTag tag, Provider registries, boolean clientPacket) {
      super.write(tag, registries, clientPacket);
      tag.put("kaboom:ChainSystem", this.chainSystem.save());
      tag.putInt("kaboom:LaunchTicksRemaining", this.launchTicksRemaining);
      tag.putString("kaboom:PendingLaunchSize", this.pendingLaunchSize.name());
      ListTag blocks = new ListTag();
      for (Map.Entry<BlockPos, BlockState> entry : this.pendingLaunchBlocks.entrySet()) {
         CompoundTag block = new CompoundTag();
         block.putLong("Pos", entry.getKey().asLong());
         block.putInt("State", Block.getId(entry.getValue()));
         blocks.add(block);
      }
      tag.put("kaboom:PendingLaunchBlocks", blocks);
      AssemblyException.write(tag, registries, this.lastAssemblyException);
   }

   protected void read(CompoundTag tag, Provider registries, boolean clientPacket) {
      super.read(tag, registries, clientPacket);
      if (tag.contains("kaboom:ChainSystem")) {
         this.chainSystem.load(tag.getCompound("kaboom:ChainSystem"));
      }
      this.launchTicksRemaining = Math.max(0, tag.getInt("kaboom:LaunchTicksRemaining"));
      try {
         this.pendingLaunchSize = MissileSize.valueOf(tag.getString("kaboom:PendingLaunchSize"));
      } catch (IllegalArgumentException ignored) {
         this.pendingLaunchSize = MissileSize.SMALL;
      }
      this.pendingLaunchBlocks.clear();
      ListTag blocks = tag.getList("kaboom:PendingLaunchBlocks", Tag.TAG_COMPOUND);
      for (int i = 0; i < blocks.size(); ++i) {
         CompoundTag block = blocks.getCompound(i);
         this.pendingLaunchBlocks.put(BlockPos.of(block.getLong("Pos")), Block.stateById(block.getInt("State")));
      }
      this.clientLaunchSoundStarted = false;
      this.lastAssemblyException = AssemblyException.read(tag, registries);

      if (clientPacket && !this.chainSystem.getAnchors().isEmpty()) {
         ChainRenderer.TRACKED_THRUSTERS.add(this.worldPosition);
      }
   }

   public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
   }

   public static record PendingEnforcement(BlockPos pos, ServerLevel level) {
   }
}
