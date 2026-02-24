package com.happysg.kaboom.block.missiles.assembly;

import com.happysg.kaboom.block.missiles.MissileContraption;
import com.happysg.kaboom.block.missiles.util.IMissileGuidanceProvider;
import com.happysg.kaboom.block.missiles.util.MissileGuidanceData;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

public class MissileContraptionBuilder {

    public static MissileContraption build(Level level, MissileAssemblyResult result, BlockPos warheadWorldPos) {
        Logger LOGGER = LogUtils.getLogger();
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

        LOGGER.warn("[MISSILE BUILD] guidancePos={}", guidancePos);

        if (guidancePos != null) {
            BlockEntity be = level.getBlockEntity(guidancePos);
            LOGGER.warn("[MISSILE BUILD] guidanceBE={} isProvider={}",
                    be == null ? "null" : be.getClass().getName(),
                    (be instanceof IMissileGuidanceProvider));

            if (be instanceof IMissileGuidanceProvider provider) {
                MissileGuidanceData data = provider.exportGuidance();
                CompoundTag t = data.toTag();
                LOGGER.warn("[MISSILE BUILD] guidanceTagKeys={}", t.getAllKeys());
                c.guidanceTag = t;
            }
        }

        c.captureFromScan(level, result);
        return c;
    }
}