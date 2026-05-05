package com.happysg.kaboom.block.missiles.parts.thrust;

import com.happysg.kaboom.block.missiles.assembly.MissileAssembler;
import com.happysg.kaboom.block.missiles.assembly.MissileLaunchHelper;
import com.happysg.kaboom.block.missiles.chaining.ChainSystem;
import com.happysg.kaboom.block.missiles.chaining.client.ChainRenderer;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public class ThrusterBlockEntity extends SmartBlockEntity {
    private boolean lastPowered = false;
    private final ChainSystem chainSystem = new ChainSystem();
    private int chainSyncTimer = 0;

    public static final List<PendingEnforcement> PENDING_ENFORCEMENTS = new ArrayList<>();

    public record PendingEnforcement(BlockPos pos, ServerLevel level) {}

    public ThrusterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public ChainSystem getChainSystem() {
        return chainSystem;
    }

    public void onRedstoneUpdated() {
        Level level = getLevel();
        if (!(level instanceof ServerLevel server)) return;

        boolean poweredNow = level.hasNeighborSignal(worldPosition);

        if (!lastPowered && poweredNow) {
            MissileLaunchHelper.requestLaunch(server, worldPosition);
        }

        lastPowered = poweredNow;
        setChanged();
    }

    @Override
    public void tick() {
        super.tick();
        Level level = getLevel();
        if (level == null || level.isClientSide) return;
        if (!(level instanceof ServerLevel serverLevel)) return;

        BlockPos controller = MissileAssembler.findControllerThruster(level, worldPosition);
        if (controller != null && controller.equals(worldPosition)) {
            chainSystem.tickFromBlock(worldPosition, serverLevel);

            PENDING_ENFORCEMENTS.add(new PendingEnforcement(worldPosition.immutable(), serverLevel));
            chainSyncTimer++;
            if (chainSyncTimer >= 10) {
                chainSyncTimer = 0;
                chainSystem.populateEntityIds(serverLevel);
                notifyUpdate();
            }
        }
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.put("kaboom:ChainSystem", chainSystem.save());
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (tag.contains("kaboom:ChainSystem")) {
            chainSystem.load(tag.getCompound("kaboom:ChainSystem"));
        }
        if (clientPacket && !chainSystem.getAnchors().isEmpty()) {
            ChainRenderer.TRACKED_THRUSTERS.add(worldPosition);
        }
    }

    private void tryLaunchIfController(ServerLevel level) throws AssemblyException {
        BlockPos controller = MissileAssembler.findControllerThruster(level, worldPosition);
        if (controller == null || !controller.equals(worldPosition))
            return;
        MissileLaunchHelper.assembleAndSpawn(level, worldPosition);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }
}
