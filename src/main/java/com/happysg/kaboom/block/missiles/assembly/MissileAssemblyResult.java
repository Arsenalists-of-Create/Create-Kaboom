package com.happysg.kaboom.block.missiles.assembly;

import net.minecraft.core.BlockPos;

import java.util.List;

public class MissileAssemblyResult {
    private final boolean valid;
    private final List<BlockPos> blocks;
    private final BlockPos controllerPos;
    private final BlockPos warhead;
    private final int warheadIndex;
    private final BlockPos guidance;

    private MissileAssemblyResult(boolean valid, List<BlockPos> blocks, BlockPos controllerPos, BlockPos warhead, int warheadIndex,BlockPos guidance) {
        this.valid = valid;
        this.blocks = blocks;
        this.controllerPos = controllerPos;
        this.warhead = warhead;
        this.warheadIndex = warheadIndex;
        this.guidance= guidance;
    }

    public static MissileAssemblyResult invalid() {
        return new MissileAssemblyResult(false, List.of(), BlockPos.ZERO, BlockPos.ZERO, -1,BlockPos.ZERO);
    }

    public static MissileAssemblyResult valid(List<BlockPos> blocks, BlockPos controllerPos, BlockPos warhead,BlockPos guidance) {
        List<BlockPos> copy = List.copyOf(blocks);
        if (!copy.contains(warhead))
            throw new IllegalArgumentException("warhead must be contained in blocks");
        return new MissileAssemblyResult(true, copy, controllerPos, warhead, copy.size() - 1,guidance);
    }

    public int getWarheadIndex() { return warheadIndex; }
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
    public BlockPos toLocal(BlockPos worldPos) {
        return worldPos.subtract(controllerPos);
    }

    public BlockPos getWarheadLocal() {
        return toLocal(warhead);
    }
    public BlockPos guidance() { return guidance; }

}