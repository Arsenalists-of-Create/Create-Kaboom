package com.happysg.kaboom.block.missiles.assembly;

import com.happysg.kaboom.block.missiles.MissileContraption;
import com.happysg.kaboom.block.missiles.util.IMissileGuidanceProvider;
import com.happysg.kaboom.block.missiles.util.MissileGuidanceData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class MissileContraptionBuilder {

    public static MissileContraption build(Level level, MissileAssemblyResult result, BlockPos warheadWorldPos) {
        MissileContraption c = new MissileContraption();
        c.warheadState = level.getBlockState(warheadWorldPos);

        BlockPos guidancePos = result.guidance();
        if (guidancePos != null) {
            BlockEntity be = level.getBlockEntity(guidancePos);
            if (be instanceof IMissileGuidanceProvider provider) {
                MissileGuidanceData data = provider.exportGuidance();
                c.guidanceTag = data.toTag();
                c.guidanceTargetPoint = data.target().point();
            }
        }

        c.captureFromScan(level, result);
        return c;
    }
}
