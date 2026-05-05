package com.happysg.kaboom.block.aerialBombs.baseTypes;

import com.happysg.kaboom.compat.vs2.VS2Utils;
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
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.joml.Vector3dc;

import javax.annotation.Nullable;
import java.util.List;

public class FluidAerialBombBlockEntity extends AerialBombBlockEntity implements IHaveGoggleInformation {

    protected final FluidTank tank;

    public FluidAerialBombBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);

        tank = new SmartFluidTank(getFluidBombCapacity(), this::onFluidStackChanged);
    }
    private int getBombSizeFromBlock() {
        BlockState state = getBlockState();
        if (state.getBlock() instanceof AerialBombBlock bomb)
            return bomb.getBombSize();
        return 1;
    }
    protected int getFluidBombCapacity() {
        int size = Math.max(1,getBombSizeFromBlock());
        return 12000 / size;
    }

    protected void refreshTankCapacity() {
        int newCap = getFluidBombCapacity();
        if (tank.getCapacity() == newCap) return;

        tank.setCapacity(newCap);

        if (tank.getFluidAmount() > newCap) {
            FluidStack f = tank.getFluid().copy();
            f.setAmount(tank.getFluidAmount() - newCap);
            tank.drain(f, FluidAction.EXECUTE);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("FluidContent", tank.writeToNBT(registries, new CompoundTag()));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        tank.readFromNBT(registries, tag.getCompound("FluidContent"));

        refreshTankCapacity();
    }

    public FluidStack getContainedFluidCopy() {
        return tank.getFluid().copy();
    }

    @Nullable
    public IFluidHandler getFluidHandler(@Nullable Direction side) {
        return getFuze().isEmpty() ? tank : null;
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
            MutableComponent name = fluid.getHoverName().copy().withStyle(ChatFormatting.AQUA);
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

        BlockState state = getBlockState();

        AerialBombProjectile projectile = createConfiguredProjectile(state);
        if (projectile == null) return;

        projectile.setPos(VS2Utils.getWorldPos(this).below().getCenter());
        Vector3dc shipVel = VS2Utils.getVelocity(level,this.worldPosition);
        if(shipVel != null) {
            projectile.addDeltaMovement(new Vec3(shipVel.x(), shipVel.y(), shipVel.z()));
        }

        level.addFreshEntity(projectile);

        int count = state.getValue(FluidAerialBombBlock.COUNT);
        if (count > 1) {
            level.setBlock(worldPosition, state.setValue(FluidAerialBombBlock.COUNT, count - 1), 3);
        } else {
            level.destroyBlock(worldPosition, false);
        }
    }

    @Override
    protected AerialBombProjectile createConfiguredProjectile(BlockState state) {
        AerialBombProjectile projectile = super.createConfiguredProjectile(state);
        if (projectile == null) return null;

        projectile.setSize(Math.max(1, getBombSizeFromBlock()));
        projectile.setBombType(AerialBombProjectile.BombType.FLUID);
        projectile.setPayloadFluid(getContainedFluidCopy());
        return projectile;
    }
}
