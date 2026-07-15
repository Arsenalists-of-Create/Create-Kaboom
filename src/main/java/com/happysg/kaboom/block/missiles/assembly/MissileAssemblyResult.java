package com.happysg.kaboom.block.missiles.assembly;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public class MissileAssemblyResult {
   private final boolean valid;
   private final List<BlockPos> blocks;
   private final BlockPos controllerPos;
   private final BlockPos warhead;
   private final int warheadIndex;
   private final BlockPos guidance;
   private final Direction assemblyDirection;
   @Nullable
   private final MissileSize missileSize;
   private final int fuelTankCount;

   private MissileAssemblyResult(
      boolean valid, List<BlockPos> blocks, BlockPos controllerPos, BlockPos warhead, int warheadIndex, BlockPos guidance,
      Direction assemblyDirection, @Nullable MissileSize missileSize, int fuelTankCount
   ) {
      this.valid = valid;
      this.blocks = blocks;
      this.controllerPos = controllerPos;
      this.warhead = warhead;
      this.warheadIndex = warheadIndex;
      this.guidance = guidance;
      this.assemblyDirection = assemblyDirection;
      this.missileSize = missileSize;
      this.fuelTankCount = Math.max(0, fuelTankCount);
   }

   public static MissileAssemblyResult invalid() {
      return new MissileAssemblyResult(false, List.of(), BlockPos.ZERO, BlockPos.ZERO, -1, BlockPos.ZERO, Direction.UP, null, 0);
   }

   public static MissileAssemblyResult valid(List<BlockPos> blocks, BlockPos controllerPos, BlockPos warhead, BlockPos guidance,
                                             Direction assemblyDirection, MissileSize missileSize, int fuelTankCount) {
      List<BlockPos> copy = List.copyOf(blocks);
      if (!copy.contains(warhead)) {
         throw new IllegalArgumentException("warhead must be contained in blocks");
      } else {
         return new MissileAssemblyResult(true, copy, controllerPos, warhead, copy.size() - 1, guidance,
            assemblyDirection, missileSize, fuelTankCount);
      }
   }

   public int getWarheadIndex() {
      return this.warheadIndex;
   }

   public boolean isValid() {
      return this.valid;
   }

   public List<BlockPos> getBlocks() {
      return this.blocks;
   }

   public BlockPos getControllerPos() {
      return this.controllerPos;
   }

   public BlockPos getWarhead() {
      return this.warhead;
   }

   public BlockPos toLocal(BlockPos worldPos) {
      return worldPos.subtract(this.controllerPos);
   }

   public BlockPos getWarheadLocal() {
      return this.toLocal(this.warhead);
   }

   public BlockPos guidance() {
      return this.guidance;
   }

   public Direction getAssemblyDirection() {
      return this.assemblyDirection;
   }

   @Nullable
   public MissileSize getMissileSize() {
      return this.missileSize;
   }

   public int getFuelTankCount() {
      return this.fuelTankCount;
   }
}
