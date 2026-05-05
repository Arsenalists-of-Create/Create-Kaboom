package com.happysg.kaboom.compat.vs2;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Vector3dc;

public class VS2Utils {

    public static BlockPos getWorldPos(Level level, BlockPos pos) {
        return pos;
    }

    public static BlockPos getWorldPos(BlockEntity blockEntity) {
        return getWorldPos(blockEntity.getLevel(), blockEntity.getBlockPos());
    }

    public static Vector3dc getVelocity(Level level, BlockPos pos) {
        return null;
    }
}
