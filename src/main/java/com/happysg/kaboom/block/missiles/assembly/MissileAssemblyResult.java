package com.happysg.kaboom.block.missiles.assembly;

import net.minecraft.core.BlockPos;

import java.util.List;

public class MissileAssemblyResult {
    private final boolean valid;
    private final List<BlockPos> blocks;       // absolute world positions, bottom->top
    private final BlockPos controllerPos;      // bottom-most thruster position

    private MissileAssemblyResult(boolean valid, List<BlockPos> blocks, BlockPos controllerPos) {
        this.valid = valid;
        this.blocks = blocks;
        this.controllerPos = controllerPos;
    }

    public static MissileAssemblyResult invalid() {
        return new MissileAssemblyResult(false, List.of(), null);
    }

    public static MissileAssemblyResult valid(List<BlockPos> blocks, BlockPos controllerPos) {
        return new MissileAssemblyResult(true, List.copyOf(blocks), controllerPos);
    }

    public boolean isValid() {
        return valid;
    }

    public List<BlockPos> getBlocks() {
        return blocks;
    }

    public BlockPos getControllerPos() {
        return controllerPos;
    }
}