package com.happysg.kaboom.block.missiles.parts;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class MissilePartShapes {
    public static final VoxelShape FULL = Block.box(0, 0, 0, 16, 16, 16);

    private static final VoxelShape SMALL_X = Block.box(0, 3, 3, 16, 13, 13);
    private static final VoxelShape SMALL_Y = Block.box(3, 0, 3, 13, 16, 13);
    private static final VoxelShape SMALL_Z = Block.box(3, 3, 0, 13, 13, 16);

    // huge_solid_fuel_tank.json is the parent model for all cylindrical huge body parts.
    private static final VoxelShape HUGE_BODY_X = Block.box(0, -4, -4, 16, 20, 20);
    private static final VoxelShape HUGE_BODY_Y = Block.box(-4, 0, -4, 20, 16, 20);
    private static final VoxelShape HUGE_BODY_Z = Block.box(-4, -4, 0, 20, 20, 16);

    // Huge warheads have an 18-wide base for the first 10 units and a 10-wide nose.
    private static final VoxelShape HUGE_WARHEAD_UP = Shapes.or(
            Block.box(-1, 0, -1, 17, 10, 17),
            Block.box(3, 10, 3, 13, 16, 13));
    private static final VoxelShape HUGE_WARHEAD_DOWN = Shapes.or(
            Block.box(-1, 6, -1, 17, 16, 17),
            Block.box(3, 0, 3, 13, 6, 13));
    private static final VoxelShape HUGE_WARHEAD_EAST = Shapes.or(
            Block.box(0, -1, -1, 10, 17, 17),
            Block.box(10, 3, 3, 16, 13, 13));
    private static final VoxelShape HUGE_WARHEAD_WEST = Shapes.or(
            Block.box(6, -1, -1, 16, 17, 17),
            Block.box(0, 3, 3, 6, 13, 13));
    private static final VoxelShape HUGE_WARHEAD_SOUTH = Shapes.or(
            Block.box(-1, -1, 0, 17, 17, 10),
            Block.box(3, 3, 10, 13, 13, 16));
    private static final VoxelShape HUGE_WARHEAD_NORTH = Shapes.or(
            Block.box(-1, -1, 6, 17, 17, 16),
            Block.box(3, 3, 0, 13, 13, 6));

    private MissilePartShapes() {
    }

    public static VoxelShape small(Direction.Axis axis) {
        return switch (axis) {
            case X -> SMALL_X;
            case Y -> SMALL_Y;
            case Z -> SMALL_Z;
        };
    }

    public static VoxelShape hugeBody(Direction.Axis axis) {
        return switch (axis) {
            case X -> HUGE_BODY_X;
            case Y -> HUGE_BODY_Y;
            case Z -> HUGE_BODY_Z;
        };
    }

    public static VoxelShape hugeWarhead(Direction facing) {
        return switch (facing) {
            case UP -> HUGE_WARHEAD_UP;
            case DOWN -> HUGE_WARHEAD_DOWN;
            case EAST -> HUGE_WARHEAD_EAST;
            case WEST -> HUGE_WARHEAD_WEST;
            case SOUTH -> HUGE_WARHEAD_SOUTH;
            case NORTH -> HUGE_WARHEAD_NORTH;
        };
    }
}
