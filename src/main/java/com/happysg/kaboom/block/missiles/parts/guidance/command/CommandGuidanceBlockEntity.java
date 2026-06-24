package com.happysg.kaboom.block.missiles.parts.guidance.command;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

public class CommandGuidanceBlockEntity extends BlockEntity {
    public static final String TAG_NETWORK_CONTROLLER_POS = "NetworkControllerPos";

    @Nullable
    private BlockPos networkControllerPos;

    public CommandGuidanceBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    public void setNetworkControllerPos(BlockPos pos) {
        networkControllerPos = pos.immutable();
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Nullable
    public BlockPos getNetworkControllerPos() {
        return networkControllerPos;
    }

    public boolean hasNetworkControllerPos() {
        return networkControllerPos != null;
    }

    public static CompoundTag tagForNetworkController(BlockPos pos) {
        CompoundTag tag = new CompoundTag();
        tag.put(TAG_NETWORK_CONTROLLER_POS, NbtUtils.writeBlockPos(pos));
        return tag;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (networkControllerPos != null) {
            tag.put(TAG_NETWORK_CONTROLLER_POS, NbtUtils.writeBlockPos(networkControllerPos));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(TAG_NETWORK_CONTROLLER_POS)) {
            networkControllerPos = NbtUtils.readBlockPos(tag, TAG_NETWORK_CONTROLLER_POS).orElse(null);
        } else {
            networkControllerPos = null;
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        loadAdditional(tag, registries);
    }
}
