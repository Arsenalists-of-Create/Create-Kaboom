package com.happysg.kaboom.block.missiles.assembly;

import com.happysg.kaboom.block.missiles.MissileContraption;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public class MissileContraptionBuilder {

    public static MissileContraption build(Level level, MissileAssemblyResult result, BlockPos blockpos ) {
        MissileContraption c = new MissileContraption();
        c.warheadState = level.getBlockState(blockpos);
        c.captureFromScan(level, result);
        return c;
    }
}