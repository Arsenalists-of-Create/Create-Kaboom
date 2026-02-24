package com.happysg.kaboom.block.missiles.assembly;

import com.happysg.kaboom.block.missiles.MissileContraption;
import com.happysg.kaboom.block.missiles.MissileEntity;
import com.happysg.kaboom.block.missiles.util.IMissileGuidanceProvider;
import com.happysg.kaboom.block.missiles.util.MissileGuidanceData;
import com.happysg.kaboom.registry.ModEntities;
import com.mojang.logging.LogUtils;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.Contraption;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

public class MissileLaunchHelper {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static boolean assembleAndSpawn(ServerLevel level, BlockPos anyThrusterPos) throws AssemblyException {
        MissileAssemblyResult result = MissileAssembler.scan(level, anyThrusterPos);
        if (!result.isValid()) return false;

        BlockPos controllerPos = result.getControllerPos();

        BlockPos warheadWorldPos = result.getWarhead();
        BlockPos warheadLocalPos = warheadWorldPos.subtract(controllerPos);

        BlockPos guidanceWorldPos = result.guidance();

        // 1) Read guidance from BE BEFORE removing blocks
        MissileGuidanceData guidance = null;
        if (guidanceWorldPos != null) {
            BlockEntity be = level.getBlockEntity(guidanceWorldPos);
            if (be instanceof IMissileGuidanceProvider provider) {
                guidance = provider.exportGuidance();
            }
        }

        if (guidance == null) {
            LOGGER.warn("[LAUNCH] No guidance data found at {} (guidance block missing or not a provider).", guidanceWorldPos);
            return false; // or allow dumb mode if you want
        }

        // 2) Build a MissileContraption (strongly typed)
        MissileContraption mc = MissileContraptionBuilder.build(level, result, warheadWorldPos);

        // 3) Store the guidance blob on the contraption (this is your “full system” handoff)
        mc.guidanceTag = guidance.toTag();

        // 4) Remove blocks (BE is gone after this)
        for (int i = result.getBlocks().size() - 1; i >= 0; i--) {
            level.removeBlock(result.getBlocks().get(i), false);
        }

        // 5) Spawn missile entity
        MissileEntity entity = ModEntities.MISSILE.get().create(level);
        if (entity == null) return false;

        entity.initFromAssembly(mc, controllerPos, warheadLocalPos);

        boolean added = level.addFreshEntity(entity);

        LOGGER.warn("[LAUNCH] addFreshEntity -> {} id={} pos={}", added, entity.getId(), entity.position());
        LOGGER.warn("[MISSILE] controllerWorld={} warheadWorld={} warheadLocal={} guidanceWorld={} guidanceTag={}",
                controllerPos, warheadWorldPos, warheadLocalPos, guidanceWorldPos, mc.guidanceTag);

        return added;
    }
    public static void requestLaunch(ServerLevel level, BlockPos triggeringThrusterPos) {
        // Resolve controller from *any* thruster position
        BlockPos controller = MissileAssembler.findControllerThruster(level, triggeringThrusterPos);

        if (controller == null) {
            LOGGER.warn("[LAUNCH] requestLaunch: no controller found from {}", triggeringThrusterPos);
            return;
        }

        LOGGER.warn("[LAUNCH] requestLaunch: trigger={} controller={}", triggeringThrusterPos, controller);

        // Only controller actually spawns, but it doesn't need to be the one powered
        try {
            assembleAndSpawn(level, controller);
        } catch (AssemblyException e) {
            throw new RuntimeException(e);
        }
    }
}