package com.happysg.kaboom.block.missiles.assembly;

import com.happysg.kaboom.block.missiles.parts.ThrusterBlock;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import rbasamoyai.createbigcannons.munitions.big_cannon.SimpleShellBlock;

import java.util.ArrayList;
import java.util.List;

public class MissileAssembler {

    public static final int MAX_VERTICAL_SCAN = 256;

    // Vanilla properties (many mods reuse these)
    private static final DirectionProperty FACING = BlockStateProperties.FACING;
    private static final DirectionProperty HORIZONTAL_FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;
    private static final EnumProperty<Direction.Axis> HORIZONTAL_AXIS = BlockStateProperties.HORIZONTAL_AXIS;

    /**
     * Find the bottom-most ThrusterBlock in the vertical column starting at startPos.
     * Returns null if startPos isn't a thruster.
     */
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

    /**
     * Scan upward from the bottom-most thruster and validate:
     * - same orientation (facing/axis) for all parts
     * - structure rules: thruster -> fuel tanks (1+) -> guidance (terminator)
     */
    public static MissileAssemblyResult scan(Level level, BlockPos anyThrusterPos) {
        BlockPos guidance = null; // NEW
        if (!(level instanceof ServerLevel server))
            return MissileAssemblyResult.invalid();

        LogUtils.getLogger().warn("=== Missile Assembly Start ===");

        BlockPos controllerPos = findControllerThruster(level, anyThrusterPos);

        if (controllerPos == null) {
            log(server, "FAIL: No thruster found at position.");
            return MissileAssemblyResult.invalid();
        }

        if (!controllerPos.equals(anyThrusterPos)) {
            log(server, "Controller resolved to bottom thruster at: " + controllerPos);
        }

        BlockState controllerState = level.getBlockState(controllerPos);

        if (!(controllerState.getBlock() instanceof IMissileComponent controllerPart) || !controllerPart.isThruster()) {
            log(server, "FAIL: Controller is not a valid thruster IMissilePart.");
            return MissileAssemblyResult.invalid();
        }

        Direction controllerFacing = getDirectionalFacing(controllerState);
        if (controllerFacing == null) {
            log(server, "FAIL: Controller thruster has no FACING property.");
            return MissileAssemblyResult.invalid();
        }

        Direction.Axis controllerAxis = controllerFacing.getAxis();

        log(server, "Controller facing: " + controllerFacing);
        log(server, "Controller axis: " + controllerAxis);

        List<BlockPos> collected = new ArrayList<>();
        collected.add(controllerPos);

        boolean foundFuel = false;
        boolean foundGuidance = false;
        boolean foundFuzedProjectile = false;
        BlockPos warhead =null;
        BlockPos cursor = controllerPos.above();

        for (int i = 0; i < MAX_VERTICAL_SCAN; i++) {

            BlockState state = level.getBlockState(cursor);
            Block block = state.getBlock();
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);

            log(server, "Checking block at " + cursor + ": " + block.getName().getString());

            // 1) Normal missile parts path
            if (block instanceof IMissileComponent part) {

                if (!matchesOrientation(state, part, controllerFacing, controllerAxis)) {
                    log(server, "FAIL: Orientation mismatch at " + cursor);
                    log(server, "Block state: " + state);
                    return MissileAssemblyResult.invalid();
                }

                if (part.isFuelTank()) {
                    foundFuel = true;
                    log(server, "Fuel tank accepted.");
                    collected.add(cursor);
                    cursor = cursor.above();
                    continue;
                }

                if (part.isThruster()) {
                    log(server, "Additional thruster found above — stopping.");
                    break;
                }

                if (part.isGuidance()) {
                    foundGuidance = true;
                    if (guidance == null) guidance = cursor.immutable(); // NEW
                    log(server, "Guidance unit accepted @ " + guidance);
                    collected.add(cursor);
                    cursor = cursor.above();
                    continue;

                }

                // Known IMissileComponent but not one you care about -> stop
                log(server, "Unknown missile IMissileComponent type — stopping.");
                break;
            }


            if (block instanceof SimpleShellBlock<?>) {
                foundFuzedProjectile = true;
                log(server, "Warhead accepted: " + block.getName().getString());
                LogUtils.getLogger().warn("[MISSILE SCAN] warhead={} class={}", id, block.getClass().getName());
                collected.add(cursor);
                warhead = cursor;
                break;
            }

            // 3) Truly invalid block
            log(server, "Stopped scan at non-missile block: " + block.getName().getString());
            LogUtils.getLogger().warn("[MISSILE SCAN] top={} class={}", id, block.getClass().getName());
            break;
        }

        if (!foundFuel) {
            log(server, "FAIL: No fuel tank found.");
            return MissileAssemblyResult.invalid();
        }

        if (!foundGuidance) {
            log(server, "FAIL: No guidance unit found.");
            return MissileAssemblyResult.invalid();
        }

        if (!foundFuzedProjectile) {
            log(server, "FAIL: No FuzedBigCannonProjectile found.");
            return MissileAssemblyResult.invalid();
        }

        log(server, "SUCCESS: Missile assembly valid. Blocks: " + collected.size());
        log(server, "=== Missile Assembly End ===");

        return MissileAssemblyResult.valid(collected, controllerPos,warhead,guidance);
    }
    private static void log(ServerLevel server, String message) {
       LogUtils.getLogger().warn("[MISSILE] " + message);
    }

    private static boolean matchesOrientation(BlockState state, IMissileComponent part,
                                              Direction controllerFacing, Direction.Axis controllerAxis) {
        if (part.isFuelTank()) {
            Direction.Axis axis = getAxialAxis(state);
            return axis != null && axis == controllerAxis;
        } else {
            Direction facing = getDirectionalFacing(state);
            return facing != null && facing == controllerFacing;
        }
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