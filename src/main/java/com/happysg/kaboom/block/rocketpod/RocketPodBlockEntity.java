package com.happysg.kaboom.block.rocketpod;

import com.happysg.kaboom.items.rocket.RocketItem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class RocketPodBlockEntity extends BlockEntity {
    public static final int SLOT_COUNT = 4;
    private static final String ROCKETS_TAG = "Rockets";
    private static final String SLOT_TAG = "Slot";
    private static final String STACK_TAG = "Stack";

    private final ItemStack[] rockets = new ItemStack[SLOT_COUNT];

    public RocketPodBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        clearRockets();
    }

    public static boolean isRocket(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof RocketItem;
    }

    public ItemStack getRocket(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) {
            return ItemStack.EMPTY;
        }
        ItemStack rocket = this.rockets[slot];
        return rocket.isEmpty() ? ItemStack.EMPTY : rocket.copy();
    }

    public int getFirstEmptySlot() {
        for (int slot = 0; slot < SLOT_COUNT; ++slot) {
            if (this.rockets[slot].isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    public int getFirstLoadedSlot() {
        for (int slot = 0; slot < SLOT_COUNT; ++slot) {
            if (!this.rockets[slot].isEmpty()) {
                return slot;
            }
        }
        return -1;
    }


    public ItemStack insertRocket(ItemStack stack, boolean simulate) {
        int slot = getFirstEmptySlot();
        if (!isRocket(stack) || slot < 0) {
            return stack;
        }

        ItemStack remainder = stack.copy();
        remainder.shrink(1);
        if (!simulate) {
            this.rockets[slot] = stack.copyWithCount(1);
            setChanged();
        }
        return remainder;
    }

    public ItemStack extractNextRocket(boolean simulate) {
        int slot = getFirstLoadedSlot();
        if (slot < 0) {
            return ItemStack.EMPTY;
        }

        return extractRocket(slot, simulate);
    }

    public ItemStack extractRocket(int slot, boolean simulate) {
        if (slot < 0 || slot >= SLOT_COUNT || this.rockets[slot].isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack result = this.rockets[slot].copyWithCount(1);
        if (!simulate) {
            this.rockets[slot] = ItemStack.EMPTY;
            setChanged();
        }
        return result;
    }

    public List<ItemStack> removeAllRockets() {
        List<ItemStack> removed = new ArrayList<>(SLOT_COUNT);
        for (int slot = 0; slot < SLOT_COUNT; ++slot) {
            if (!this.rockets[slot].isEmpty()) {
                removed.add(this.rockets[slot].copyWithCount(1));
                this.rockets[slot] = ItemStack.EMPTY;
            }
        }
        if (!removed.isEmpty()) {
            setChanged();
        }
        return removed;
    }

    private void clearRockets() {
        for (int slot = 0; slot < SLOT_COUNT; ++slot) {
            this.rockets[slot] = ItemStack.EMPTY;
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag rocketsTag = new ListTag();
        for (int slot = 0; slot < SLOT_COUNT; ++slot) {
            ItemStack rocket = this.rockets[slot];
            if (rocket.isEmpty()) {
                continue;
            }
            CompoundTag entry = new CompoundTag();
            entry.putByte(SLOT_TAG, (byte) slot);
            entry.put(STACK_TAG, rocket.saveOptional(registries));
            rocketsTag.add(entry);
        }
        tag.put(ROCKETS_TAG, rocketsTag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        clearRockets();
        if (!tag.contains(ROCKETS_TAG, Tag.TAG_LIST)) {
            return;
        }

        ListTag rocketsTag = tag.getList(ROCKETS_TAG, Tag.TAG_COMPOUND);
        for (int index = 0; index < rocketsTag.size(); ++index) {
            CompoundTag entry = rocketsTag.getCompound(index);
            int slot = entry.getByte(SLOT_TAG) & 255;
            if (slot >= SLOT_COUNT) {
                continue;
            }
            ItemStack rocket = ItemStack.parseOptional(registries, entry.getCompound(STACK_TAG));
            if (isRocket(rocket)) {
                this.rockets[slot] = rocket.copyWithCount(1);
            }
        }
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        loadAdditional(tag, registries);
    }
}
