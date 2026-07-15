package com.happysg.kaboom.block.missiles.parts.warhead;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.fluids.transfer.GenericItemEmptying;
import com.simibubi.create.content.fluids.transfer.GenericItemFilling;
import com.simibubi.create.foundation.fluid.SmartFluidTank;
import net.createmod.catnip.data.Pair;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import rbasamoyai.createbigcannons.munitions.big_cannon.FuzedBlockEntity;

import javax.annotation.Nullable;
import java.util.List;

public class MissileWarheadBlockEntity extends FuzedBlockEntity implements IHaveGoggleInformation {
    public static final String FLUID_CONTENT_TAG = "FluidContent";

    private final FluidTank tank;

    public MissileWarheadBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.tank = new SmartFluidTank(Math.max(1, getConfiguredCapacity(state)), this::onFluidStackChanged);
    }

    private static int getConfiguredCapacity(BlockState state) {
        if (state.getBlock() instanceof AbstractMissileWarhead warhead && warhead.isFluidWarhead()) {
            return warhead.getFluidCapacityMb();
        }
        return 1;
    }

    public boolean isFluidWarhead() {
        return getBlockState().getBlock() instanceof AbstractMissileWarhead warhead && warhead.isFluidWarhead();
    }

    public FluidStack getContainedFluidCopy() {
        return isFluidWarhead() ? tank.getFluid().copy() : FluidStack.EMPTY;
    }

    public static FluidStack readFluidPayload(@Nullable CompoundTag blockEntityTag,
                                              HolderLookup.Provider registries,
                                              int capacity) {
        if (blockEntityTag == null || !blockEntityTag.contains(FLUID_CONTENT_TAG)) {
            return FluidStack.EMPTY;
        }

        FluidTank savedTank = new FluidTank(Math.max(1, capacity));
        savedTank.readFromNBT(registries, blockEntityTag.getCompound(FLUID_CONTENT_TAG));
        return savedTank.getFluid().copy();
    }

    @Nullable
    public IFluidHandler getFluidHandler(@Nullable Direction side) {
        return isFluidWarhead() && getFuze().isEmpty() ? tank : null;
    }

    public boolean tryEmptyItemIntoTank(Level level, Player player, InteractionHand hand, ItemStack heldItem) {
        if (!isFluidWarhead() || !getFuze().isEmpty() || !GenericItemEmptying.canItemBeEmptied(level, heldItem)) {
            return false;
        }
        if (level.isClientSide) {
            return true;
        }

        Pair<FluidStack, ItemStack> simulated = GenericItemEmptying.emptyItem(level, heldItem, true);
        FluidStack fluid = simulated.getFirst();
        if (fluid.isEmpty() || fluid.getAmount() != tank.fill(fluid, FluidAction.SIMULATE)) {
            return false;
        }

        ItemStack heldCopy = heldItem.copy();
        Pair<FluidStack, ItemStack> result = GenericItemEmptying.emptyItem(level, heldCopy, false);
        tank.fill(result.getFirst(), FluidAction.EXECUTE);

        if (!player.isCreative()) {
            if (heldCopy.isEmpty()) {
                player.setItemInHand(hand, result.getSecond());
            } else {
                player.setItemInHand(hand, heldCopy);
                player.getInventory().placeItemBackInInventory(result.getSecond());
            }
        }

        notifyUpdate();
        return true;
    }

    public boolean tryFillItemFromTank(Level level, Player player, InteractionHand hand, ItemStack heldItem) {
        if (!isFluidWarhead() || !getFuze().isEmpty() || !GenericItemFilling.canItemBeFilled(level, heldItem)) {
            return false;
        }
        if (level.isClientSide) {
            return true;
        }

        FluidStack fluid = tank.getFluid();
        if (fluid.isEmpty()) {
            return false;
        }

        int required = GenericItemFilling.getRequiredAmountForItem(level, heldItem, fluid.copy());
        if (required == -1 || required > fluid.getAmount()) {
            return false;
        }

        ItemStack input = player.isCreative() ? heldItem.copy() : heldItem;
        ItemStack output = GenericItemFilling.fillItem(level, required, input, fluid.copy());
        FluidStack drained = fluid.copy();
        drained.setAmount(required);
        tank.drain(drained, FluidAction.EXECUTE);

        if (!player.isCreative()) {
            player.getInventory().placeItemBackInInventory(output);
        }

        notifyUpdate();
        return true;
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (isFluidWarhead()) {
            tag.put(FLUID_CONTENT_TAG, tank.writeToNBT(registries, new CompoundTag()));
        }
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (isFluidWarhead() && tag.contains(FLUID_CONTENT_TAG)) {
            tank.readFromNBT(registries, tag.getCompound(FLUID_CONTENT_TAG));
        }
    }

    private void onFluidStackChanged(FluidStack stack) {
        if (getLevel() != null && !getLevel().isClientSide) {
            notifyUpdate();
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        boolean added = super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        if (!isFluidWarhead()) {
            return added;
        }

        FluidStack fluid = tank.getFluid();
        tooltip.add(Component.literal("Fluid Payload").withStyle(ChatFormatting.GOLD));
        if (fluid.isEmpty()) {
            tooltip.add(Component.literal(" - Empty").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            MutableComponent name = fluid.getHoverName().copy().withStyle(ChatFormatting.AQUA);
            tooltip.add(Component.literal(" - ").append(name));
            tooltip.add(Component.literal(" - ")
                    .append(Component.literal(fluid.getAmount() + " mB").withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(" / " + tank.getCapacity() + " mB").withStyle(ChatFormatting.GRAY)));
        }
        return true;
    }
}
