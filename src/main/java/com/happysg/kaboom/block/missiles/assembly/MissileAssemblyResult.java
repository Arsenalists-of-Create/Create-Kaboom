package com.happysg.kaboom.block.missiles.assembly;

import net.minecraft.core.BlockPos;

import java.util.List;

public class MissileAssemblyResult {
    private final boolean valid;
    private final List<BlockPos> blocks;       // absolute world positions, bottom->top
    private final BlockPos controllerPos;      // bottom-most thruster position
    private final BlockPos warhead;

    private MissileAssemblyResult(boolean valid, List<BlockPos> blocks, BlockPos controllerPos, BlockPos wHead) {
        this.valid = valid;
        this.blocks = blocks;
        this.controllerPos = controllerPos;
        this.warhead = wHead;
    }

    public static MissileAssemblyResult invalid() {
        return new MissileAssemblyResult(false, List.of(), null,null);
    }

    public static MissileAssemblyResult valid(List<BlockPos> blocks, BlockPos controllerPos,BlockPos wHead) {
        return new MissileAssemblyResult(true, List.copyOf(blocks), controllerPos,wHead);
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
    public BlockPos getWarhead(){return warhead;}
}