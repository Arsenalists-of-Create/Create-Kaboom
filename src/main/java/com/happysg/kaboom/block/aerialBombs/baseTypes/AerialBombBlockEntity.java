package com.happysg.kaboom.block.aerialBombs.baseTypes;

import com.happysg.kaboom.compat.sable.SableUtils;
import com.happysg.kaboom.config.KaboomConfig;
import com.happysg.kaboom.registry.ModProjectiles;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import rbasamoyai.createbigcannons.munitions.big_cannon.FuzedBlockEntity;
import rbasamoyai.createbigcannons.munitions.fuzes.FuzeItem;

import java.util.List;

public class AerialBombBlockEntity extends FuzedBlockEntity {
    public static final String FUZES_TAG = "IndividualFuzes";
    public static final String FUZE_SLOT_TAG = "Slot";
    public static final String FUZE_STACK_TAG = "Stack";
    public static final int MAX_FUZE_SLOTS = 9;

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

        Vec3 localReleaseDirection = Vec3.atLowerCornerOf(Direction.DOWN.getNormal());
        SableUtils.LaunchKinematics launch = SableUtils.getLaunchKinematics(
                level, worldPosition, worldPosition.below().getCenter(), localReleaseDirection
        );
        double ejectionVelocity = Math.max(0.0, KaboomConfig.server().bombEjectionVelocity.getF());
        projectile.setPos(launch.position());
        projectile.setDeltaMovement(launch.carrierVelocity().add(launch.direction().scale(ejectionVelocity)));
        projectile.initializeCarrierCollisionGrace(
                launch.sourceSubLevelId(),
                Math.max(0, KaboomConfig.server().bombCarrierCollisionGraceTicks.get())
        );

        level.addFreshEntity(projectile);

        int count = state.getValue(AerialBombBlock.COUNT);
        consumeLaunchedFuze(state);
        if (count > 1) {
            BlockState remainingState = state.setValue(AerialBombBlock.COUNT, count - 1);
            level.setBlock(worldPosition, remainingState
                    .setValue(AerialBombBlock.FUZED, hasActiveFuze(remainingState)), 3);
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
        return AerialBombFuzeLayout.activeSlotCount(state);
    }

    public boolean hasAnyFuze() {
        return hasActiveFuze(getBlockState());
    }

    public boolean hasVisibleFuze() {
        return hasActiveFuze(getBlockState());
    }

    public boolean hasActiveFuze(BlockState state) {
        int slots = getVisibleFuzeSlots(state);
        for (int i = 0; i < slots; i++) {
            if (!fuzes[i].isEmpty()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        if (!getTracer().isEmpty()) {
            CreateLang.builder("tooltip")
                    .translate("createbigcannons.tracer")
                    .forGoggles(tooltip);
        }

        CreateLang.builder("block")
                .translate("create_kaboom.aerial_bomb.tooltip.fuzes")
                .style(ChatFormatting.YELLOW)
                .forGoggles(tooltip);

        int activeSlots = getVisibleFuzeSlots();
        for (int releaseIndex = 0; releaseIndex < activeSlots; releaseIndex++) {
            int slot = activeSlots - releaseIndex - 1;
            ItemStack fuzeStack = getFuze(slot);
            MutableComponent fuzeDescription;
            FuzeItem fuzeItem = null;
            if (fuzeStack.getItem() instanceof FuzeItem item) {
                fuzeItem = item;
                fuzeDescription = item.getDescription().copy().withStyle(ChatFormatting.GREEN);
            } else {
                fuzeDescription = Component.translatable("block.createbigcannons.shell.tooltip.fuze.none")
                        .withStyle(ChatFormatting.DARK_GRAY);
            }

            MutableComponent entry = releaseIndex == 0
                    ? Component.translatable("block.create_kaboom.aerial_bomb.tooltip.fuze.next", fuzeDescription)
                    : Component.translatable("block.create_kaboom.aerial_bomb.tooltip.fuze.later",
                            releaseIndex + 1, fuzeDescription);
            CreateLang.builder()
                    .add(entry.withStyle(ChatFormatting.GRAY))
                    .forGoggles(tooltip, 1);

            if (fuzeItem != null) {
                fuzeItem.addExtraInfo(tooltip, isPlayerSneaking, fuzeStack);
            }
        }
        return true;
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
        return getFuze(AerialBombFuzeLayout.launchSlot(state));
    }

    protected void consumeLaunchedFuze(BlockState state) {
        removeFuze(AerialBombFuzeLayout.launchSlot(state));
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
            fuzeTag.putByte(FUZE_SLOT_TAG, (byte) i);
            fuzeTag.put(FUZE_STACK_TAG, fuzes[i].saveOptional(registries));
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
                int slot = fuzeTag.getByte(FUZE_SLOT_TAG) & 255;
                if (slot < MAX_FUZE_SLOTS) {
                    fuzes[slot] = ItemStack.parseOptional(registries, fuzeTag.getCompound(FUZE_STACK_TAG));
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

    public static CompoundTag singleFuzeTag(ItemStack fuze, HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        if (fuze == null || fuze.isEmpty()) {
            return tag;
        }

        CompoundTag fuzeTag = new CompoundTag();
        fuzeTag.putByte(FUZE_SLOT_TAG, (byte) 0);
        fuzeTag.put(FUZE_STACK_TAG, fuze.copyWithCount(1).saveOptional(registries));
        ListTag list = new ListTag();
        list.add(fuzeTag);
        tag.put(FUZES_TAG, list);
        return tag;
    }

    public static ItemStack readFuze(CompoundTag tag, int requestedSlot, HolderLookup.Provider registries) {
        if (tag.contains(FUZES_TAG, Tag.TAG_LIST)) {
            ListTag list = tag.getList(FUZES_TAG, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag fuzeTag = list.getCompound(i);
                int slot = fuzeTag.getByte(FUZE_SLOT_TAG) & 255;
                if (slot == requestedSlot) {
                    return ItemStack.parseOptional(registries, fuzeTag.getCompound(FUZE_STACK_TAG));
                }
            }
        }

        if (requestedSlot == 0 && tag.contains("Fuze", Tag.TAG_COMPOUND)) {
            return ItemStack.parseOptional(registries, tag.getCompound("Fuze"));
        }
        return ItemStack.EMPTY;
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
