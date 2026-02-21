package com.happysg.kaboom.block.missiles.assembly;

import com.happysg.kaboom.block.missiles.MissileContraption;
import com.happysg.kaboom.block.missiles.parts.guidance.MissileGuidanceBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

public class MissileContraptionBuilder {

    public static MissileContraption build(Level level, MissileAssemblyResult result, BlockPos warheadWorldPos) {
        MissileContraption c = new MissileContraption();

        c.warheadState = level.getBlockState(warheadWorldPos);


        BlockPos guidanceWorldPos = result.guidance(); // add this to your result
        Vec3 fallback = new Vec3(0.5, 80.0, 5000.5);      // your hardcode for now

        if (guidanceWorldPos != null) {
            BlockEntity be = level.getBlockEntity(guidanceWorldPos);
            if (be instanceof MissileGuidanceBlockEntity gbe) {
                c.guidanceTargetPoint = gbe.getTargetPoint();
            } else {
                c.guidanceTargetPoint = fallback;
            }
        } else {
            c.guidanceTargetPoint = fallback;
        }

        c.captureFromScan(level, result);
        return c;
    }
}