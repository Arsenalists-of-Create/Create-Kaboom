package com.happysg.kaboom.block.missiles.assembly;

import com.happysg.kaboom.block.missiles.MissileContraption;
import com.happysg.kaboom.block.missiles.MissileEntity;
import com.happysg.kaboom.block.missiles.chaining.ChainSystem;
import com.happysg.kaboom.block.missiles.parts.thrust.ThrusterBlockEntity;
import com.happysg.kaboom.block.missiles.util.IMissileGuidanceProvider;
import com.happysg.kaboom.block.missiles.util.MissileGuidanceData;
import com.happysg.kaboom.registry.ModEntities;
import com.simibubi.create.content.contraptions.AssemblyException;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.UUID;

public class MissileLaunchHelper {
    public static boolean assembleAndSpawn(ServerLevel level, BlockPos anyThrusterPos) throws AssemblyException {
        MissileAssemblyResult result = MissileAssembler.scan(level, anyThrusterPos);
        if (!result.isValid()) return false;

        BlockPos controllerPos = result.getControllerPos();

        BlockPos warheadWorldPos = result.getWarhead();
        BlockPos warheadLocalPos = warheadWorldPos.subtract(controllerPos);

        BlockPos guidanceWorldPos = result.guidance();

        MissileGuidanceData guidance = null;
        if (guidanceWorldPos != null) {
            BlockEntity be = level.getBlockEntity(guidanceWorldPos);
            if (be instanceof IMissileGuidanceProvider provider) {
                guidance = provider.exportGuidance();
            }
        }

        if (guidance == null) {
            return false;
        }

        MissileContraption mc = MissileContraptionBuilder.build(level, result, warheadWorldPos);

        mc.guidanceTag = guidance.toTag();

        BlockEntity controllerBE = level.getBlockEntity(controllerPos);
        ChainSystem chainSystem = null;
        if (controllerBE instanceof ThrusterBlockEntity thrusterBE) {
            chainSystem = thrusterBE.getChainSystem();

            chainSystem.breakDanglingChains(controllerPos, level);
            mc.chainSystemTag = chainSystem.save();
        }

        for (int i = result.getBlocks().size() - 1; i >= 0; i--) {
            level.removeBlock(result.getBlocks().get(i), false);
        }

        MissileEntity entity = ModEntities.MISSILE.get().create(level);
        if (entity == null) return false;

        entity.initFromAssembly(mc, controllerPos, warheadLocalPos);

        boolean added = level.addFreshEntity(entity);

        if (added && chainSystem != null) {
            for (UUID mobId : chainSystem.getSecuredMobIds()) {
                Entity mob = level.getEntity(mobId);
                if (mob instanceof Mob m) {
                    m.startRiding(entity, true);
                }
            }
        }

        return added;
    }

    public static void requestLaunch(ServerLevel level, BlockPos triggeringThrusterPos) {

        BlockPos controller = MissileAssembler.findControllerThruster(level, triggeringThrusterPos);

        if (controller == null) {
            return;
        }

        try {
            assembleAndSpawn(level, controller);
        } catch (AssemblyException e) {
            throw new RuntimeException(e);
        }
    }
}
