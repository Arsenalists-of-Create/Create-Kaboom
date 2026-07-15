package com.happysg.kaboom.block.missiles.parts;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class MissilePartShapes {
    public static final VoxelShape FULL = Block.box(0, 0, 0, 16, 16, 16);

    private static final VoxelShape SMALL_X = Block.box(0, 3, 3, 16, 13, 13);
    private static final VoxelShape SMALL_Y = Block.box(3, 0, 3, 13, 16, 13);
    private static final VoxelShape SMALL_Z = Block.box(3, 3, 0, 13, 13, 16);

    private static final VoxelShape LARGE_X = Block.box(0, -6, -6, 16, 22, 22);
    private static final VoxelShape LARGE_Y = Block.box(-6, 0, -6, 22, 16, 22);
    private static final VoxelShape LARGE_Z = Block.box(-6, -6, 0, 22, 22, 16);

    private MissilePartShapes() {
    }

    public static VoxelShape small(Direction.Axis axis) {
        return switch (axis) {
            case X -> SMALL_X;
            case Y -> SMALL_Y;
            case Z -> SMALL_Z;
        };
    }

    public static VoxelShape large(Direction.Axis axis) {
        return switch (axis) {
            case X -> LARGE_X;
            case Y -> LARGE_Y;
            case Z -> LARGE_Z;
        };
    }
}
