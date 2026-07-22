package com.happysg.kaboom.items.rocket;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;

/** One-tank item fluid handler used by fluid-payload rockets and Create spouts. */
public final class RocketFluidHandler implements IFluidHandlerItem {
    public static final int CAPACITY_MB = 500;

    private final ItemStack container;

    public RocketFluidHandler(ItemStack container) {
        this.container = container;
    }

    @Override
    public ItemStack getContainer() {
        return this.container;
    }

    @Override
    public int getTanks() {
        return 1;
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        return tank == 0 ? RocketItem.getFluidContent(this.container) : FluidStack.EMPTY;
    }

    @Override
    public int getTankCapacity(int tank) {
        return tank == 0 ? CAPACITY_MB : 0;
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return tank == 0
                && !stack.isEmpty()
                && RocketItem.getPayload(this.container) == RocketPayload.FLUID;
    }

    @Override
    public int fill(FluidStack resource, IFluidHandler.FluidAction action) {
        if (!isFluidValid(0, resource)) {
            return 0;
        }

        FluidStack stored = RocketItem.getFluidContent(this.container);
        if (!stored.isEmpty() && !FluidStack.isSameFluidSameComponents(stored, resource)) {
            return 0;
        }

        int filled = Math.min(CAPACITY_MB - stored.getAmount(), resource.getAmount());
        if (filled <= 0) {
            return 0;
        }
        if (action.execute()) {
            FluidStack result = stored.isEmpty() ? resource.copyWithAmount(filled) : stored.copy();
            if (!stored.isEmpty()) {
                result.grow(filled);
            }
            RocketItem.setFluidContent(this.container, result);
        }
        return filled;
    }

    @Override
    public FluidStack drain(FluidStack resource, IFluidHandler.FluidAction action) {
        FluidStack stored = RocketItem.getFluidContent(this.container);
        if (resource.isEmpty() || stored.isEmpty()
                || !FluidStack.isSameFluidSameComponents(stored, resource)) {
            return FluidStack.EMPTY;
        }
        return drain(Math.min(resource.getAmount(), stored.getAmount()), action);
    }

    @Override
    public FluidStack drain(int maxDrain, IFluidHandler.FluidAction action) {
        FluidStack stored = RocketItem.getFluidContent(this.container);
        int drainedAmount = Math.min(Math.max(maxDrain, 0), stored.getAmount());
        if (stored.isEmpty() || drainedAmount <= 0) {
            return FluidStack.EMPTY;
        }

        FluidStack drained = stored.copyWithAmount(drainedAmount);
        if (action.execute()) {
            stored.shrink(drainedAmount);
            RocketItem.setFluidContent(this.container, stored);
        }
        return drained;
    }
}
