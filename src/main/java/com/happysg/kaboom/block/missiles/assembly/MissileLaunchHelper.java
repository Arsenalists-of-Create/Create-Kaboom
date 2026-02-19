package com.happysg.kaboom.block.missiles.assembly;

import com.happysg.kaboom.block.missiles.MissileEntity;
import com.happysg.kaboom.block.missiles.assembly.MissileAssemblyResult;
import com.happysg.kaboom.registry.ModEntities;
import com.mojang.logging.LogUtils;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.Contraption;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

public class MissileLaunchHelper {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static boolean assembleAndSpawn(ServerLevel level, BlockPos anyThrusterPos) throws AssemblyException {
        MissileAssemblyResult result = MissileAssembler.scan(level, anyThrusterPos);
        if (!result.isValid()) return false;

        Contraption contraption = MissileContraptionBuilder.build(level, result);

        // remove blocks from world (top-down is a bit safer)
        for (int i = result.getBlocks().size() - 1; i >= 0; i--) {
            level.removeBlock(result.getBlocks().get(i), false);
        }

        MissileEntity entity = ModEntities.MISSILE.get().create(level);
        if (entity == null) return false;

        entity.initFromAssembly(contraption, result.getControllerPos());
        level.addFreshEntity(entity);
        boolean added = level.addFreshEntity(entity);
        LOGGER.warn("[LAUNCH] addFreshEntity -> {}  id={}  pos={}", added, entity, entity.position());

        return true;
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