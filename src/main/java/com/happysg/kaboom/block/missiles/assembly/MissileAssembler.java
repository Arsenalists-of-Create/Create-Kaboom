package com.happysg.kaboom.block.missiles.assembly;

import com.happysg.kaboom.block.missiles.parts.thrust.ThrusterBlock;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import rbasamoyai.createbigcannons.munitions.big_cannon.FuzedProjectileBlock;

public class MissileAssembler {
   public static final int MAX_SCAN_LENGTH = 256;
   @Deprecated(
      forRemoval = false
   )
   public static final int MAX_VERTICAL_SCAN = 256;
   private static final DirectionProperty FACING = BlockStateProperties.FACING;
   private static final DirectionProperty HORIZONTAL_FACING = BlockStateProperties.HORIZONTAL_FACING;
   private static final EnumProperty<Axis> AXIS = BlockStateProperties.AXIS;
   private static final EnumProperty<Axis> HORIZONTAL_AXIS = BlockStateProperties.HORIZONTAL_AXIS;

   public static BlockPos findControllerThruster(Level level, BlockPos startPos) {
      if (!(level.getBlockState(startPos).getBlock() instanceof ThrusterBlock)) {
         return null;
      } else {
         Direction facing = getDirectionalFacing(level.getBlockState(startPos));
         if (facing == null) {
            return null;
         } else {
            BlockPos cursor = startPos;

            for (int i = 0; i < 256; i++) {
               BlockPos behind = cursor.relative(facing.getOpposite());
               BlockState behindState = level.getBlockState(behind);
               if (!(behindState.getBlock() instanceof ThrusterBlock) || getDirectionalFacing(behindState) != facing) {
                  break;
               }

               cursor = behind;
            }

            return cursor;
         }
      }
   }

   public static MissileAssemblyResult scan(Level level, BlockPos anyThrusterPos) {
      BlockPos guidance = null;
      BlockPos controllerPos = findControllerThruster(level, anyThrusterPos);
      if (controllerPos == null) {
         return MissileAssemblyResult.invalid();
      } else {
         BlockState controllerState = level.getBlockState(controllerPos);
         if (controllerState.getBlock() instanceof IMissileComponent controllerPart && controllerPart.isThruster()) {
            Direction controllerFacing = getDirectionalFacing(controllerState);
            if (controllerFacing == null) {
               return MissileAssemblyResult.invalid();
            }

            Axis controllerAxis = controllerFacing.getAxis();
            List<BlockPos> collected = new ArrayList<>();
            collected.add(controllerPos);
            boolean foundFuel = false;
            boolean foundGuidance = false;
            boolean foundFuzedProjectile = false;
            BlockPos warhead = null;
            BlockPos cursor = controllerPos.relative(controllerFacing);

            for (int i = 0; i < 256; i++) {
               BlockState state = level.getBlockState(cursor);
               Block block = state.getBlock();
               if (!(block instanceof IMissileComponent part)) {
                  if (block instanceof FuzedProjectileBlock) {
                     foundFuzedProjectile = true;
                     collected.add(cursor);
                     warhead = cursor;
                  }
                  break;
               }

               if (!matchesOrientation(state, part, controllerFacing, controllerAxis)) {
                  return MissileAssemblyResult.invalid();
               }

               if (part.isFuelTank()) {
                  foundFuel = true;
                  collected.add(cursor);
                  cursor = cursor.relative(controllerFacing);
               } else {
                  if (part.isThruster() || !part.isGuidance()) {
                     break;
                  }

                  foundGuidance = true;
                  if (guidance == null) {
                     guidance = cursor.immutable();
                  }

                  collected.add(cursor);
                  cursor = cursor.relative(controllerFacing);
               }
            }

            if (foundFuel && foundGuidance && foundFuzedProjectile) {
               return MissileAssemblyResult.valid(collected, controllerPos, warhead, guidance, controllerFacing);
            }

            return MissileAssemblyResult.invalid();
         }

         return MissileAssemblyResult.invalid();
      }
   }

   public static BlockPos findControllerFromComponent(Level level, BlockPos componentPos) {
      if (!isMissileStructureBlock(level.getBlockState(componentPos))) {
         return null;
      } else if (level.getBlockState(componentPos).getBlock() instanceof ThrusterBlock) {
         return findControllerThruster(level, componentPos);
      } else {
         for (Direction direction : Direction.values()) {
            BlockPos cursor = componentPos;

            for (int i = 0; i < 256; i++) {
               cursor = cursor.relative(direction);
               BlockState state = level.getBlockState(cursor);
               if (state.getBlock() instanceof ThrusterBlock) {
                  return findControllerThruster(level, cursor);
               }

               if (!isMissileStructureBlock(state)) {
                  break;
               }
            }
         }

         return null;
      }
   }

   public static boolean isMissileStructureBlock(BlockState state) {
      Block block = state.getBlock();
      return block instanceof IMissileComponent || block instanceof FuzedProjectileBlock;
   }

   private static boolean matchesOrientation(BlockState state, IMissileComponent part, Direction controllerFacing, Axis controllerAxis) {
      if (!part.isFuelTank() && !part.isGuidance()) {
         Direction facing = getDirectionalFacing(state);
         return facing != null && facing == controllerFacing;
      } else {
         Axis axis = getAxialAxis(state);
         return axis != null && axis == controllerAxis;
      }
   }

   private static Direction getDirectionalFacing(BlockState state) {
      if (state.hasProperty(FACING)) {
         return (Direction)state.getValue(FACING);
      } else {
         return state.hasProperty(HORIZONTAL_FACING) ? (Direction)state.getValue(HORIZONTAL_FACING) : null;
      }
   }

   private static Axis getAxialAxis(BlockState state) {
      if (state.hasProperty(AXIS)) {
         return (Axis)state.getValue(AXIS);
      } else {
         return state.hasProperty(HORIZONTAL_AXIS) ? (Axis)state.getValue(HORIZONTAL_AXIS) : null;
      }
   }
}
