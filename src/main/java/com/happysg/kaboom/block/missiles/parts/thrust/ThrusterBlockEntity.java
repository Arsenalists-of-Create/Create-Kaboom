package com.happysg.kaboom.block.missiles.parts.thrust;

import com.happysg.kaboom.block.missiles.assembly.MissileAssembler;
import com.happysg.kaboom.block.missiles.assembly.MissileLaunchHelper;
import com.mojang.logging.LogUtils;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

import java.util.List;

public class ThrusterBlockEntity extends SmartBlockEntity {
    private static final Logger LOGGER = LogUtils.getLogger();
    // Rising-edge detection (don’t spam-launch while powered)
    private boolean lastPowered = false;

    public ThrusterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }


    /**
     * Called from ThrusterBlock.neighborChanged().
     * Handles rising-edge redstone -> launch.
     */
    public void onRedstoneUpdated() {
        Level level = getLevel();
        if (!(level instanceof ServerLevel server)) return;

        boolean poweredNow = level.hasNeighborSignal(worldPosition);
        LOGGER.warn("[THRUSTER-BE] onRedstoneUpdated @ {} poweredNow={} lastPowered={}", worldPosition, poweredNow, lastPowered);
        // Rising edge only
        if (!lastPowered && poweredNow) {
            LOGGER.warn("[THRUSTER-BE] Rising edge @ {} -> requesting launch", worldPosition);
            MissileLaunchHelper.requestLaunch(server, worldPosition);
        }

        lastPowered = poweredNow;
        setChanged();
    }

    private void tryLaunchIfController(ServerLevel level) throws AssemblyException {
        // Must be bottom-most thruster in this column
        BlockPos controller = MissileAssembler.findControllerThruster(level, worldPosition);
        if (controller == null || !controller.equals(worldPosition))
            return;

        // Assemble + spawn missile (this also removes blocks)
        MissileLaunchHelper.assembleAndSpawn(level, worldPosition);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {

    }
}