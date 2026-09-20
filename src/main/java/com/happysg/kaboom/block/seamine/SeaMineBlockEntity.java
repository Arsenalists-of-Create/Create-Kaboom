package com.happysg.kaboom.block.seamine;

import com.happysg.kaboom.items.MineFuze;
import com.happysg.kaboom.registry.ModBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class SeaMineBlockEntity extends BlockEntity {

    private ItemStack fuze = ItemStack.EMPTY;
    private ItemStack chainStack = ItemStack.EMPTY;
    private int sensitivity = 50;
    private int chainLength = 16;

    public SeaMineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public void setFuze(ItemStack stack) {
        this.fuze = stack;
        if (!stack.isEmpty() && stack.getItem() instanceof MineFuze) {
            this.sensitivity = MineFuze.getSensitivity(stack);
        }
        sendUpdate();
    }

    public ItemStack getFuze() {
        return this.fuze;
    }

    public void setChainStack(ItemStack stack) {
        this.chainStack = stack;
        sendUpdate();
    }

    public ItemStack getChainStack() {
        return this.chainStack;
    }

    public void setSensitivity(int sensitivity) {
        this.sensitivity = Mth.clamp(sensitivity, 1, 1000);
        if (!this.fuze.isEmpty() && this.fuze.getItem() instanceof MineFuze) {
            MineFuze.setSensitivity(this.fuze, this.sensitivity);
        }
        sendUpdate();
    }

    public int getSensitivity() {
        return this.sensitivity;
    }

    public void setChainLength(int length) {
        this.chainLength = Mth.clamp(length, 0, 64);
        sendUpdate();
    }

    public int getChainLength() {
        return this.chainLength;
    }

    public void clearContents() {
        this.fuze = ItemStack.EMPTY;
        this.chainStack = ItemStack.EMPTY;
        setChanged();
    }

    public void sendUpdate() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public void tryAssemble(Player player) {
        if (level == null || level.isClientSide) return;

        int bottomY = worldPosition.getY();
        int maxWaterY = bottomY;
        for (int y = bottomY + 1; y < level.getMaxBuildHeight(); y++) {
            BlockPos checkPos = new BlockPos(worldPosition.getX(), y, worldPosition.getZ());
            BlockState checkState = level.getBlockState(checkPos);
            if (checkState.getFluidState().is(FluidTags.WATER) || checkState.is(Blocks.WATER)) {
                maxWaterY = y;
            } else {
                break;
            }
        }

        if (maxWaterY <= bottomY) {
            player.displayClientMessage(Component.translatable("create_kaboom.sea_mine.cannot_ascend"), true);
            return;
        }

        int target = Math.max(bottomY + 1, maxWaterY - chainLength);

        BlockPos anchor = worldPosition.immutable();
        ItemStack savedFuze = this.fuze.copy();
        ItemStack savedChains = this.chainStack.copy();
        int savedSensitivity = this.sensitivity;
        int savedChainLength = this.chainLength;

        level.setBlock(worldPosition, Blocks.WATER.defaultBlockState(), 3);

        SeaMineEntity entity = new SeaMineEntity(level, anchor.getX() + 0.5, anchor.getY() + 0.1, anchor.getZ() + 0.5);
        entity.initData(anchor, (float) target, savedSensitivity, savedChainLength, savedFuze, savedChains);
        level.addFreshEntity(entity);

        level.playSound(null, anchor, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.playSound(null, anchor, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.8F, 1.2F);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!fuze.isEmpty()) {
            tag.put("MineFuze", fuze.saveOptional(registries));
        }
        if (!chainStack.isEmpty()) {
            tag.put("ChainStack", chainStack.saveOptional(registries));
        }
        tag.putInt("Sensitivity", sensitivity);
        tag.putInt("ChainLength", chainLength);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("MineFuze")) {
            this.fuze = ItemStack.parseOptional(registries, tag.getCompound("MineFuze"));
        } else {
            this.fuze = ItemStack.EMPTY;
        }
        if (tag.contains("ChainStack")) {
            this.chainStack = ItemStack.parseOptional(registries, tag.getCompound("ChainStack"));
        } else {
            this.chainStack = ItemStack.EMPTY;
        }
        if (tag.contains("Sensitivity")) {
            this.sensitivity = tag.getInt("Sensitivity");
        }
        if (tag.contains("ChainLength")) {
            this.chainLength = tag.getInt("ChainLength");
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection connection, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        CompoundTag tag = packet.getTag();
        if (tag != null) {
            loadAdditional(tag, registries);
            if (level != null) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.handleUpdateTag(tag, registries);
        loadAdditional(tag, registries);
    }
}
