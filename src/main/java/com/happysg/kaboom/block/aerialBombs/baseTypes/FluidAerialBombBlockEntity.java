package com.happysg.kaboom.block.aerialBombs.baseTypes;

import com.happysg.kaboom.compat.vs2.VS2Utils;
import com.happysg.kaboom.registry.ModProjectiles;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.fluids.transfer.GenericItemEmptying;
import com.simibubi.create.content.fluids.transfer.GenericItemFilling;
import com.simibubi.create.foundation.fluid.SmartFluidTank;
import net.createmod.catnip.data.Pair;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.fluids.capability.templates.FluidTank;

import javax.annotation.Nullable;
import java.util.List;

public class FluidAerialBombBlockEntity extends AerialBombBlockEntity implements IHaveGoggleInformation {

    protected final FluidTank tank;
    private LazyOptional<IFluidHandler> fluidOptional;

    public FluidAerialBombBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);

        // NOTE: this runs before load(), so size is probably default=1 here.
        // We'll refresh capacity in load() after size is read from NBT.
        tank = new SmartFluidTank(getFluidBombCapacity(), this::onFluidStackChanged);
    }
    private int getBombSizeFromBlock() {
        BlockState state = getBlockState();
        if (state.getBlock() instanceof AerialBombBlock bomb)
            return bomb.getBombSize();
        return 1;
    }
    protected int getFluidBombCapacity() {
        int size = Math.max(1,getBombSizeFromBlock()); // getSize() comes from AerialBombBlockEntity
        return 12000 / size;
    }

    /** Call this after size changes or after loading NBT. */
    protected void refreshTankCapacity() {
        int newCap = getFluidBombCapacity();
        if (tank.getCapacity() == newCap) return;

        // SmartFluidTank supports setCapacity in Create; if not available in your mappings,
        // you can replace tank with a custom tank implementation that supports resizing.
        tank.setCapacity(newCap);

        // Ensure we don't exceed capacity
        if (tank.getFluidAmount() > newCap) {
            FluidStack f = tank.getFluid().copy();
            f.setAmount(tank.getFluidAmount() - newCap);
            tank.drain(f, FluidAction.EXECUTE);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("FluidContent", tank.writeToNBT(new CompoundTag()));
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        tank.readFromNBT(tag.getCompound("FluidContent"));

        // size is now loaded by the super (AerialBombBlockEntity), so update capacity after load
        refreshTankCapacity();
    }

    public FluidStack getContainedFluidCopy() {
        return tank.getFluid().copy();
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.FLUID_HANDLER && getFuze().isEmpty()) {
            return getFluidOptional().cast();
        }
        return super.getCapability(cap, side);
    }

    public boolean tryEmptyItemIntoTE(Level worldIn, Player player, InteractionHand handIn, ItemStack heldItem) {
        if (getFuze().isEmpty() && GenericItemEmptying.canItemBeEmptied(worldIn, heldItem)) {
            if (worldIn.isClientSide) return true;

            Pair<FluidStack, ItemStack> emptyingResult = GenericItemEmptying.emptyItem(worldIn, heldItem, true);
            FluidStack fluidStack = emptyingResult.getFirst();

            if (fluidStack.getAmount() != tank.fill(fluidStack, FluidAction.SIMULATE))
                return false;

            ItemStack copyOfHeld = heldItem.copy();
            emptyingResult = GenericItemEmptying.emptyItem(worldIn, copyOfHeld, false);
            tank.fill(fluidStack, FluidAction.EXECUTE);

            if (!player.isCreative()) {
                if (copyOfHeld.isEmpty()) {
                    player.setItemInHand(handIn, emptyingResult.getSecond());
                } else {
                    player.setItemInHand(handIn, copyOfHeld);
                    player.getInventory().placeItemBackInInventory(emptyingResult.getSecond());
                }
            }

            notifyUpdate();
            return true;
        }
        return false;
    }

    public boolean tryFillItemFromTE(Level level, Player player, InteractionHand handIn, ItemStack heldItem) {
        if (getFuze().isEmpty() && GenericItemFilling.canItemBeFilled(level, heldItem)) {

            if (level.isClientSide) return true;

            FluidStack fluid = tank.getFluid();
            if (fluid.isEmpty()) return false;

            int required = GenericItemFilling.getRequiredAmountForItem(level, heldItem, fluid.copy());
            if (required == -1 || required > fluid.getAmount()) return false;

            if (player.isCreative())
                heldItem = heldItem.copy();

            ItemStack out = GenericItemFilling.fillItem(level, required, heldItem, fluid.copy());

            FluidStack copy = fluid.copy();
            copy.setAmount(required);
            tank.drain(copy, FluidAction.EXECUTE);

            if (!player.isCreative())
                player.getInventory().placeItemBackInInventory(out);

            notifyUpdate();
            return true;
        }
        return false;
    }

    public LazyOptional<IFluidHandler> getFluidOptional() {
        if (fluidOptional == null) {
            fluidOptional = LazyOptional.of(() -> tank);
        }
        return fluidOptional;
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        if (fluidOptional != null) fluidOptional.invalidate();
    }

    protected void onFluidStackChanged(FluidStack newStack) {
        if (getLevel() != null && !getLevel().isClientSide) notifyUpdate();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        boolean added = super.addToGoggleTooltip(tooltip, isPlayerSneaking);

        FluidStack fluid = tank.getFluid();
        int cap = tank.getCapacity();

        tooltip.add(Component.literal("Fluid Payload").withStyle(ChatFormatting.GOLD));

        if (fluid.isEmpty()) {
            tooltip.add(Component.literal(" - Empty").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            MutableComponent name = fluid.getDisplayName().copy().withStyle(ChatFormatting.AQUA);
            tooltip.add(Component.literal(" - ").append(name));

            tooltip.add(Component.literal(" - ")
                    .append(Component.literal(fluid.getAmount() + " mB").withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(" / " + cap + " mB").withStyle(ChatFormatting.GRAY)));
        }

        return added || true;
    }

    @Override
    public void activate() {
        if (level == null || level.isClientSide) return;

        // payload snapshot
        FluidStack payload = getContainedFluidCopy();

        BlockState state = getBlockState();

        var projectile = ModProjectiles.AERIAL_BOMB_PROJECTILE.create(level);
        if (projectile == null) return;

        // size now comes from BE
        int size = Math.max(1, getBombSizeFromBlock());
        projectile.setSize(size);

        projectile.setPos(VS2Utils.getWorldPos(this).below().getCenter());

        // give projectile the state for visuals, but don't mutate it weirdly here
        projectile.setState(state);

        projectile.setFuzeStack(getFuze().copy());
        projectile.setBombType(AerialBombProjectile.BombType.FLUID);
        projectile.setPayloadFluid(payload);

        level.addFreshEntity(projectile);

        // Decrement COUNT (ammo remaining), not SIZE.
        int count = state.getValue(FluidAerialBombBlock.COUNT);
        if (count > 1) {
            level.setBlock(worldPosition, state.setValue(FluidAerialBombBlock.COUNT, count - 1), 3);
        } else {
            level.destroyBlock(worldPosition, false);
        }
    }
}