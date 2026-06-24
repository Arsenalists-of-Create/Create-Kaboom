package com.happysg.kaboom.block.aerialBombs.baseTypes;

import com.happysg.kaboom.compat.sable.SableUtils;
import com.happysg.kaboom.registry.ModProjectiles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3dc;
import rbasamoyai.createbigcannons.munitions.big_cannon.FuzedBlockEntity;

public class AerialBombBlockEntity extends FuzedBlockEntity {
    private static final String FUZES_TAG = "IndividualFuzes";
    private static final int MAX_FUZE_SLOTS = 9;

    private final ItemStack[] fuzes = new ItemStack[MAX_FUZE_SLOTS];

    public AerialBombBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        clearFuzeSlots();
    }

    public void activate() {
        if (level == null || level.isClientSide) return;

        BlockState state = getBlockState();

        AerialBombProjectile projectile = createConfiguredProjectile(state);
        if (projectile == null) return;

        projectile.setPos(worldPosition.below().getCenter());
        Vector3dc shipVel = SableUtils.getVelocity(level,this.worldPosition);
        if(shipVel != null) {
            projectile.setDeltaMovement(new Vec3(shipVel.x(), shipVel.y(), shipVel.z()));
        }

        level.addFreshEntity(projectile);

        int count = state.getValue(AerialBombBlock.COUNT);
        consumeLaunchedFuze(state);
        if (count > 1) {
            level.setBlock(worldPosition, state
                    .setValue(AerialBombBlock.COUNT, count - 1)
                    .setValue(AerialBombBlock.FUZED, hasAnyFuze()), 3);
            notifyUpdate();
        } else {
            level.destroyBlock(worldPosition, false);
        }
    }

    public void detonateOnSpot(Direction signalDirection) {
        if (level == null || level.isClientSide) return;

        BlockState state = getBlockState();
        AerialBombProjectile projectile = createConfiguredProjectile(state);
        if (projectile == null) return;

        projectile.setPos(Vec3.atCenterOf(worldPosition));
        projectile.setDeltaMovement(new Vec3(signalDirection.step()).scale(0.5D));

        level.setBlock(worldPosition, Blocks.AIR.defaultBlockState(), 3);
        projectile.detonate(projectile.position());
    }

    protected AerialBombProjectile createConfiguredProjectile(BlockState state) {
        AerialBombProjectile projectile = ModProjectiles.AERIAL_BOMB_PROJECTILE.create(level);
        if (projectile == null) return null;

        projectile.setState(state);

        ItemStack fuzeStack = getFuzeForLaunch(state);
        projectile.setFuzeStack(fuzeStack.isEmpty() ? ItemStack.EMPTY : fuzeStack.copy());

        AerialBombProjectile.BombType bombType = AerialBombProjectile.BombType.HE;
        int bombSize = 1;

        if (state.getBlock() instanceof AerialBombBlock bomb) {
            bombType = bomb.getBombType();
            bombSize = bomb.getBombSize();
        }

        projectile.setBombType(bombType);
        projectile.setSize(bombSize);
        return projectile;
    }

    @Override
    public ItemStack getFuze() {
        ItemStack first = getFirstVisibleFuze();
        return first.isEmpty() ? super.getFuze() : first;
    }

    @Override
    public void setFuze(ItemStack stack) {
        super.setFuze(stack);
        clearFuzeSlots();
        if (stack != null && !stack.isEmpty()) {
            fuzes[0] = stack.copyWithCount(1);
        }
    }

    public ItemStack getFuze(int index) {
        if (index < 0 || index >= MAX_FUZE_SLOTS) {
            return ItemStack.EMPTY;
        }
        return fuzes[index];
    }

    public int getVisibleFuzeSlots() {
        return getVisibleFuzeSlots(getBlockState());
    }

    public int getVisibleFuzeSlots(BlockState state) {
        int count = state.hasProperty(AerialBombBlock.COUNT) ? state.getValue(AerialBombBlock.COUNT) : 1;
        if (state.getBlock() instanceof AerialBombBlock bomb) {
            return switch (bomb.getBombSize()) {
                case 1 -> 1;
                case 2 -> Math.min(count, 4);
                default -> Math.min(count, 9);
            };
        }
        return Math.min(count, MAX_FUZE_SLOTS);
    }

    public boolean hasAnyFuze() {
        for (ItemStack fuze : fuzes) {
            if (!fuze.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public boolean hasVisibleFuze() {
        int slots = getVisibleFuzeSlots();
        for (int i = 0; i < slots; i++) {
            if (!fuzes[i].isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public int firstEmptyVisibleFuzeSlot() {
        int slots = getVisibleFuzeSlots();
        for (int i = 0; i < slots; i++) {
            if (fuzes[i].isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    public int lastFilledVisibleFuzeSlot() {
        for (int i = getVisibleFuzeSlots() - 1; i >= 0; i--) {
            if (!fuzes[i].isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    public void setFuze(int index, ItemStack stack) {
        if (index < 0 || index >= MAX_FUZE_SLOTS) {
            return;
        }
        fuzes[index] = stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        syncCoarseFuze();
        setChanged();
    }

    public ItemStack removeFuze(int index) {
        if (index < 0 || index >= MAX_FUZE_SLOTS) {
            return ItemStack.EMPTY;
        }
        ItemStack removed = fuzes[index];
        fuzes[index] = ItemStack.EMPTY;
        syncCoarseFuze();
        setChanged();
        return removed;
    }

    public ItemStack getFirstVisibleFuze() {
        int slots = getVisibleFuzeSlots();
        for (int i = 0; i < slots; i++) {
            if (!fuzes[i].isEmpty()) {
                return fuzes[i];
            }
        }
        return ItemStack.EMPTY;
    }

    protected ItemStack getFuzeForLaunch(BlockState state) {
        int launchSlot = Math.max(0, getVisibleFuzeSlots(state) - 1);
        return getFuze(launchSlot);
    }

    protected void consumeLaunchedFuze(BlockState state) {
        int launchSlot = Math.max(0, getVisibleFuzeSlots(state) - 1);
        removeFuze(launchSlot);
    }

    protected void syncCoarseFuze() {
        super.setFuze(getFirstVisibleFuze());
    }

    protected void clearFuzeSlots() {
        for (int i = 0; i < MAX_FUZE_SLOTS; i++) {
            fuzes[i] = ItemStack.EMPTY;
        }
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);

        ListTag list = new ListTag();
        for (int i = 0; i < MAX_FUZE_SLOTS; i++) {
            if (fuzes[i].isEmpty()) {
                continue;
            }

            CompoundTag fuzeTag = new CompoundTag();
            fuzeTag.putByte("Slot", (byte) i);
            fuzeTag.put("Stack", fuzes[i].saveOptional(registries));
            list.add(fuzeTag);
        }
        tag.put(FUZES_TAG, list);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);

        clearFuzeSlots();
        if (tag.contains(FUZES_TAG, Tag.TAG_LIST)) {
            ListTag list = tag.getList(FUZES_TAG, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag fuzeTag = list.getCompound(i);
                int slot = fuzeTag.getByte("Slot") & 255;
                if (slot < MAX_FUZE_SLOTS) {
                    fuzes[slot] = ItemStack.parseOptional(registries, fuzeTag.getCompound("Stack"));
                }
            }
        } else {
            ItemStack legacyFuze = super.getFuze();
            if (!legacyFuze.isEmpty()) {
                fuzes[0] = legacyFuze.copyWithCount(1);
            }
        }

        syncCoarseFuze();
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
