package com.happysg.kaboom.block.missiles.assembly;

import com.happysg.kaboom.block.missiles.parts.thrust.ThrusterBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import rbasamoyai.createbigcannons.munitions.big_cannon.FuzedProjectileBlock;

import java.util.ArrayList;
import java.util.List;

public class MissileAssembler {

    public static final int MAX_VERTICAL_SCAN = 256;

    private static final DirectionProperty FACING = BlockStateProperties.FACING;
    private static final DirectionProperty HORIZONTAL_FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;
    private static final EnumProperty<Direction.Axis> HORIZONTAL_AXIS = BlockStateProperties.HORIZONTAL_AXIS;

    public static BlockPos findControllerThruster(Level level, BlockPos startPos) {
        if (!(level.getBlockState(startPos).getBlock() instanceof ThrusterBlock))
            return null;

        BlockPos cursor = startPos;

        for (int i = 0; i < MAX_VERTICAL_SCAN; i++) {
            BlockPos below = cursor.below();
            if (level.getBlockState(below).getBlock() instanceof ThrusterBlock) {
                cursor = below;
                continue;
            }
            break;
        }

        return cursor;
    }

    public static MissileAssemblyResult scan(Level level, BlockPos anyThrusterPos) {
        BlockPos guidance = null;
        BlockPos controllerPos = findControllerThruster(level, anyThrusterPos);

        if (controllerPos == null) {
            return MissileAssemblyResult.invalid();
        }

        BlockState controllerState = level.getBlockState(controllerPos);

        if (!(controllerState.getBlock() instanceof IMissileComponent controllerPart) || !controllerPart.isThruster()) {
            return MissileAssemblyResult.invalid();
        }

        Direction controllerFacing = getDirectionalFacing(controllerState);
        if (controllerFacing == null) {
            return MissileAssemblyResult.invalid();
        }

        Direction.Axis controllerAxis = controllerFacing.getAxis();

        List<BlockPos> collected = new ArrayList<>();
        collected.add(controllerPos);

        boolean foundFuel = false;
        boolean foundGuidance = false;
        boolean foundFuzedProjectile = false;
        BlockPos warhead = null;
        BlockPos cursor = controllerPos.above();

        for (int i = 0; i < MAX_VERTICAL_SCAN; i++) {

            BlockState state = level.getBlockState(cursor);
            Block block = state.getBlock();

            if (block instanceof IMissileComponent part) {

                if (!matchesOrientation(state, part, controllerFacing, controllerAxis)) {
                    return MissileAssemblyResult.invalid();
                }

                if (part.isFuelTank()) {
                    foundFuel = true;
                    collected.add(cursor);
                    cursor = cursor.above();
                    continue;
                }

                if (part.isThruster()) {
                    break;
                }

                if (part.isGuidance()) {
                    foundGuidance = true;
                    if (guidance == null)
                        guidance = cursor.immutable();
                    collected.add(cursor);
                    cursor = cursor.above();
                    continue;
                }

                break;
            }

            if (block instanceof FuzedProjectileBlock<?, ?>) {
                foundFuzedProjectile = true;
                collected.add(cursor);
                warhead = cursor;
                break;
            }

            break;
        }

        if (!foundFuel || !foundGuidance || !foundFuzedProjectile) {
            return MissileAssemblyResult.invalid();
        }

        return MissileAssemblyResult.valid(collected, controllerPos, warhead, guidance);
    }

    private static boolean matchesOrientation(BlockState state, IMissileComponent part,
                                              Direction controllerFacing, Direction.Axis controllerAxis) {
        if (part.isFuelTank() || part.isGuidance()) {
            Direction.Axis axis = getAxialAxis(state);
            return axis != null && axis == controllerAxis;
        }

        Direction facing = getDirectionalFacing(state);
        return facing != null && facing == controllerFacing;
    }

    private static Direction getDirectionalFacing(BlockState state) {
        if (state.hasProperty(FACING))
            return state.getValue(FACING);
        if (state.hasProperty(HORIZONTAL_FACING))
            return state.getValue(HORIZONTAL_FACING);
        return null;
    }

    private static Direction.Axis getAxialAxis(BlockState state) {
        if (state.hasProperty(AXIS))
            return state.getValue(AXIS);
        if (state.hasProperty(HORIZONTAL_AXIS))
            return state.getValue(HORIZONTAL_AXIS);
        return null;
    }
}
