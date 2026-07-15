package com.happysg.kaboom.block.missiles;

import com.happysg.kaboom.block.missiles.assembly.IMissileComponent;
import com.happysg.kaboom.block.missiles.assembly.MissileAssemblyResult;
import com.happysg.kaboom.block.missiles.assembly.MissileSize;
import com.happysg.kaboom.block.missiles.util.IMissileGuidanceProvider;
import com.simibubi.create.content.contraptions.mounted.MountedContraption;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;

public class MissileContraption extends MountedContraption {
   public BlockState warheadState;
   public BlockPos controllerWorldPos = BlockPos.ZERO;
   private BlockPos startPos;
   private Direction initialOrientation;
   public Direction assemblyDirection = Direction.UP;
   public int fuelAmountMb = 0;
   public int fuelCapacityMb = 0;
   public MissileSize missileSize = MissileSize.SMALL;
   public int fuelTankCount = 1;
   public CompoundTag fuelFluidTag = null;
   public BlockPos warheadLocalPos = null;
   public BlockPos capLocalPos = BlockPos.ZERO;
   public BlockPos endLocalPos = BlockPos.ZERO;
   public Vec3 guidanceTargetPoint = null;
   @Nullable
   public CompoundTag guidanceTag = null;
   @Nullable
   public CompoundTag chainSystemTag = null;

   public void captureFromScan(Level level, MissileAssemblyResult result) {
      this.controllerWorldPos = result.getControllerPos();
      this.assemblyDirection = result.getAssemblyDirection();
      this.missileSize = result.getMissileSize() == null ? MissileSize.SMALL : result.getMissileSize();
      this.fuelTankCount = Math.max(1, result.getFuelTankCount());
      this.warheadLocalPos = result.getWarheadLocal();
      this.capLocalPos = this.warheadLocalPos;

      for (BlockPos worldPos : result.getBlocks()) {
         BlockState state = level.getBlockState(worldPos);
         BlockEntity be = level.getBlockEntity(worldPos);
         if (this.guidanceTag == null || this.guidanceTag.isEmpty()) {
            if (be instanceof IMissileGuidanceProvider provider) {
               this.guidanceTag = provider.exportGuidance().toTag();
            } else if (be != null && state.getBlock() instanceof IMissileComponent part && part.isGuidance()) {
               this.guidanceTag = be.saveWithoutMetadata(level.registryAccess());
            }
         }

         CompoundTag tag = be != null ? be.saveWithFullMetadata(level.registryAccess()) : null;
         BlockPos localPos = worldPos.subtract(this.controllerWorldPos);
         this.getBlocks().put(localPos, new StructureBlockInfo(localPos, state, tag));
      }

      this.computeFuelFromCapturedBlocks(level);
      this.anchor = BlockPos.ZERO;
      this.startPos = BlockPos.ZERO;
      this.capLocalPos = this.warheadLocalPos;
      this.endLocalPos = this.warheadLocalPos;
      this.initialOrientation = this.assemblyDirection;
      this.bounds = this.computeAabbFromLocalBlocks();
   }

   private AABB computeAabbFromLocalBlocks() {
      return new AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);
   }

   private void computeFuelFromCapturedBlocks(Level level) {
      this.fuelAmountMb = 0;
      this.fuelCapacityMb = 0;
      this.fuelFluidTag = null;
      FluidStack chosen = FluidStack.EMPTY;

      for (StructureBlockInfo info : this.getBlocks().values()) {
         BlockState state = info.state();
         Block beTag = state.getBlock();
         if (beTag instanceof IMissileComponent) {
            IMissileComponent part = (IMissileComponent)beTag;
            if (part.isFuelTank()) {
               CompoundTag beTagx = info.nbt();
               this.fuelCapacityMb = this.fuelCapacityMb + part.getFuelCapacityMb(state);
               int amt = part.getFuelMb(beTagx);
               this.fuelAmountMb += amt;
               FluidStack fs = part.getFuelFluid(beTagx, level.registryAccess());
               if (!fs.isEmpty() && chosen.isEmpty()) {
                  chosen = fs.copy();
               }
            }
         }
      }

      if (!chosen.isEmpty()) {
         this.fuelFluidTag = (CompoundTag)chosen.saveOptional(level.registryAccess());
      }
   }

   public CompoundTag writeNBT(Provider registries, boolean clientData) {
      if (this.anchor == null) {
         this.anchor = BlockPos.ZERO;
      }

      if (this.startPos == null) {
         this.startPos = BlockPos.ZERO;
      }

      if (this.initialOrientation == null) {
         this.initialOrientation = Direction.UP;
      }

      if (this.assemblyDirection == null) {
         this.assemblyDirection = Direction.UP;
      }

      CompoundTag tag = super.writeNBT(registries, clientData);
      tag.putInt("kaboom:FuelAmountMb", this.fuelAmountMb);
      tag.putInt("kaboom:FuelCapacityMb", this.fuelCapacityMb);
      tag.putString("kaboom:MissileSize", this.missileSize.name());
      tag.putInt("kaboom:FuelTankCount", this.fuelTankCount);
      tag.putInt("kaboom:AssemblyDirection", this.assemblyDirection.get3DDataValue());
      if (this.fuelFluidTag != null) {
         tag.put("kaboom:FuelFluid", this.fuelFluidTag);
      }

      tag.putLong("kaboom:CapLocalPos", this.capLocalPos.asLong());
      tag.putLong("kaboom:EndLocalPos", this.endLocalPos.asLong());
      if (this.warheadLocalPos != null) {
         tag.putLong("kaboom:WarheadLocalPos", this.warheadLocalPos.asLong());
      }

      if (this.guidanceTargetPoint != null) {
         tag.putDouble("GuidanceX", this.guidanceTargetPoint.x);
         tag.putDouble("GuidanceY", this.guidanceTargetPoint.y);
         tag.putDouble("GuidanceZ", this.guidanceTargetPoint.z);
      }

      if (this.guidanceTag != null && !this.guidanceTag.isEmpty()) {
         tag.put("Guidance", this.guidanceTag);
      }

      if (this.chainSystemTag != null && !this.chainSystemTag.isEmpty()) {
         tag.put("kaboom:ChainSystem", this.chainSystemTag);
      }

      return tag;
   }

   public void readNBT(Level level, CompoundTag tag, boolean clientData) {
      super.readNBT(level, tag, clientData);
      this.restoreWeightMetadata(tag);
      this.fuelAmountMb = tag.getInt("kaboom:FuelAmountMb");
      this.fuelCapacityMb = tag.getInt("kaboom:FuelCapacityMb");
      this.assemblyDirection = tag.contains("kaboom:AssemblyDirection") ? Direction.from3DDataValue(tag.getInt("kaboom:AssemblyDirection")) : Direction.UP;
      this.fuelFluidTag = tag.contains("kaboom:FuelFluid") ? tag.getCompound("kaboom:FuelFluid") : null;
      if (tag.contains("kaboom:CapLocalPos")) {
         this.capLocalPos = BlockPos.of(tag.getLong("kaboom:CapLocalPos"));
      }

      if (tag.contains("kaboom:EndLocalPos")) {
         this.endLocalPos = BlockPos.of(tag.getLong("kaboom:EndLocalPos"));
      }

      this.warheadLocalPos = tag.contains("kaboom:WarheadLocalPos") ? BlockPos.of(tag.getLong("kaboom:WarheadLocalPos")) : null;
      if (this.fuelCapacityMb < 0) {
         this.fuelCapacityMb = 0;
      }

      if (this.fuelAmountMb < 0) {
         this.fuelAmountMb = 0;
      }

      if (this.fuelAmountMb > this.fuelCapacityMb) {
         this.fuelAmountMb = this.fuelCapacityMb;
      }

      if (tag.contains("GuidanceX")) {
         this.guidanceTargetPoint = new Vec3(tag.getDouble("GuidanceX"), tag.getDouble("GuidanceY"), tag.getDouble("GuidanceZ"));
      } else {
         this.guidanceTargetPoint = null;
      }

      this.guidanceTag = tag.contains("Guidance") ? tag.getCompound("Guidance") : null;
      this.chainSystemTag = tag.contains("kaboom:ChainSystem") ? tag.getCompound("kaboom:ChainSystem") : null;
   }

   private void restoreWeightMetadata(CompoundTag tag) {
      MissileSize derivedSize = null;
      int derivedFuelTankCount = 0;
      for (StructureBlockInfo info : this.getBlocks().values()) {
         if (info.state().getBlock() instanceof IMissileComponent part) {
            if (derivedSize == null && part.isThruster()) {
               derivedSize = part.getMissileSize();
            }
            if (part.isFuelTank()) {
               derivedFuelTankCount++;
            }
         }
      }

      this.missileSize = derivedSize == null ? MissileSize.SMALL : derivedSize;
      if (tag.contains("kaboom:MissileSize")) {
         try {
            this.missileSize = MissileSize.valueOf(tag.getString("kaboom:MissileSize"));
         } catch (IllegalArgumentException ignored) {
         }
      }

      this.fuelTankCount = tag.contains("kaboom:FuelTankCount")
         ? Math.max(1, tag.getInt("kaboom:FuelTankCount"))
         : Math.max(1, derivedFuelTankCount);
   }
}
