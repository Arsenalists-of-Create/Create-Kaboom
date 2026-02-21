package com.happysg.kaboom.block.missiles.assembly;

import com.happysg.kaboom.block.missiles.MissileContraption;
import com.happysg.kaboom.block.missiles.MissileEntity;
import com.happysg.kaboom.block.missiles.assembly.MissileAssemblyResult;
import com.happysg.kaboom.block.missiles.parts.MissileGuidanceBlockEntity;
import com.happysg.kaboom.registry.ModEntities;
import com.mojang.logging.LogUtils;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.Contraption;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile;
import rbasamoyai.createbigcannons.munitions.big_cannon.AbstractBigCannonProjectile;
import rbasamoyai.createbigcannons.munitions.big_cannon.SimpleShellBlock;

public class MissileLaunchHelper {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static boolean assembleAndSpawn(ServerLevel level, BlockPos anyThrusterPos) throws AssemblyException {
        MissileAssemblyResult result = MissileAssembler.scan(level, anyThrusterPos);
        if (!result.isValid()) return false;

        BlockPos controllerPos = result.getControllerPos();

        BlockPos warheadWorldPos = result.getWarhead();
        BlockPos warheadLocalPos = warheadWorldPos.subtract(controllerPos);

        // NEW: guidance world pos from scan
        BlockPos guidanceWorldPos = result.guidance();

        // Build contraption (prefer returning MissileContraption if you can)
        Contraption contraption = MissileContraptionBuilder.build(level, result, warheadWorldPos);

        // NEW: copy guidance settings BEFORE removing blocks
        Vec3 targetPoint = null;
        if (guidanceWorldPos != null) {
            BlockEntity be = level.getBlockEntity(guidanceWorldPos);
            if (be instanceof MissileGuidanceBlockEntity gbe) {
                targetPoint = gbe.getTargetPoint();
            }
        }

        // If your builder returns a MissileContraption, store it there (best)
        if (contraption instanceof MissileContraption mc) {
            mc.guidanceTargetPoint = targetPoint != null ? targetPoint : new Vec3(0.5, 80.0, 5000.5);
        }

        // Now remove blocks (BE will be gone after this)
        for (int i = result.getBlocks().size() - 1; i >= 0; i--) {
            level.removeBlock(result.getBlocks().get(i), false);
        }

        MissileEntity entity = ModEntities.MISSILE.get().create(level);
        if (entity == null) return false;

        entity.initFromAssembly(contraption, controllerPos, warheadLocalPos);
        boolean added = level.addFreshEntity(entity);

        LOGGER.warn("[LAUNCH] addFreshEntity -> {} id={} pos={}", added, entity.getId(), entity.position());
        LOGGER.warn("[MISSILE] controllerWorld={} warheadWorld={} warheadLocal={} guidanceWorld={} target={}",
                controllerPos, warheadWorldPos, warheadLocalPos, guidanceWorldPos, targetPoint);

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