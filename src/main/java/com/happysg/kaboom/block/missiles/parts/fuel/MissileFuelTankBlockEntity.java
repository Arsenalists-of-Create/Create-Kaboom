package com.happysg.kaboom.block.missiles.parts.fuel;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class MissileFuelTankBlockEntity extends BlockEntity implements IHaveGoggleInformation {
    private boolean needsBalance = false;
    private long lastBalanceGameTime = -9999;
    private static final int BALANCE_COOLDOWN_TICKS = 2;
    private final FluidTank tank;
    public static final int CAPACITY = 8000;
    private FluidStack cachedStackFluid = FluidStack.EMPTY;
    private int cachedStackAmount = 0;
    private int cachedStackCapacity = 0;
    private int cachedStackTanks = 1;

    private boolean isValidFuel(FluidStack stack) {
        return !stack.isEmpty();
    }

    public MissileFuelTankBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);

        int cap = MissileFuelTankBlockEntity.CAPACITY;
        if (state.getBlock() instanceof MissileFuelTankBlock b) {
            cap = b.getCapacity();
        }

        this.tank = new FluidTank(cap) {
            @Override
            protected void onContentsChanged() {
                super.onContentsChanged();
                setChanged();

                if (MissileFuelTankBlockEntity.this.level != null && !MissileFuelTankBlockEntity.this.level.isClientSide) {
                    List<MissileFuelTankBlockEntity> stack =
                            collectVerticalStack(MissileFuelTankBlockEntity.this.level, MissileFuelTankBlockEntity.this.worldPosition);

                    updateStackCache(stack);

                    for (MissileFuelTankBlockEntity be : stack) {
                        be.needsBalance = true;
                    }
                }
            }

            @Override
            public boolean isFluidValid(FluidStack stack) {
                return isValidFuel(stack);
            }

        };

        this.cachedStackTanks = 1;
        this.cachedStackCapacity = cap;
        this.cachedStackAmount = this.tank.getFluidAmount();
        this.cachedStackFluid = this.tank.getFluid().isEmpty() ? FluidStack.EMPTY : this.tank.getFluid().copy();
    }

    private void updateStackCache(List<MissileFuelTankBlockEntity> stack) {
        int stackCap = 0;
        int stackAmt = 0;
        FluidStack stackType = FluidStack.EMPTY;

        for (MissileFuelTankBlockEntity be : stack) {
            stackCap += be.tank.getCapacity();

            FluidStack fs = be.tank.getFluid();
            if (!fs.isEmpty() && stackType.isEmpty()) {
                stackType = fs.copy();
                stackType.setAmount(1);
            }
            stackAmt += fs.getAmount();
        }

        for (MissileFuelTankBlockEntity be : stack) {
            be.cachedStackTanks = stack.size();
            be.cachedStackCapacity = stackCap;
            be.cachedStackAmount = stackAmt;
            be.cachedStackFluid = stackType.isEmpty() ? FluidStack.EMPTY : stackType.copy();

            be.setChanged();
            be.markForSync();
        }
    }
    public static void tick(Level level, BlockPos pos, BlockState state, MissileFuelTankBlockEntity be) {
        if (level.isClientSide) return;
        if (!be.needsBalance) return;

        long t = level.getGameTime();
        if (t - be.lastBalanceGameTime < BALANCE_COOLDOWN_TICKS) return;

        be.needsBalance = false;
        be.lastBalanceGameTime = t;

        be.balanceVerticalStack();
    }

    private void balanceVerticalStack() {
        if (level == null) return;

        List<MissileFuelTankBlockEntity> stack = collectVerticalStack(this.level, this.worldPosition);
        if (stack.size() <= 1) return;

        FluidStack fluidType = FluidStack.EMPTY;
        int total = 0;

        for (MissileFuelTankBlockEntity be : stack) {
            FluidStack fs = be.tank.getFluid();
            if (!fs.isEmpty() && fluidType.isEmpty()) {
                fluidType = fs.copy();
                fluidType.setAmount(1);
            }
        }

        for (MissileFuelTankBlockEntity be : stack) {
            FluidStack fs = be.tank.getFluid();
            if (fs.isEmpty()) continue;

            if (fluidType.isEmpty()) {
                updateStackCache(stack);
                return;
            }

            if (!FluidStack.isSameFluidSameComponents(fs, fluidType)) {

                return;
            }

            total += fs.getAmount();
        }

        if (fluidType.isEmpty()) return;

        int per = total / stack.size();
        int rem = total % stack.size();

        for (int i = 0; i < stack.size(); i++) {
            MissileFuelTankBlockEntity be = stack.get(i);

            int amt = per + (i < rem ? 1 : 0);
            FluidStack newStack = fluidType.copy();
            newStack.setAmount(amt);

            FluidStack cur = be.tank.getFluid();
            if (cur.getAmount() != amt || !FluidStack.isSameFluidSameComponents(cur, newStack)) {
                be.tank.setFluid(newStack);
                be.setChanged();
                be.markForSync();
            }
        }

        int stackCap = 0;
        int stackAmt = 0;
        FluidStack stackType = FluidStack.EMPTY;

        for (MissileFuelTankBlockEntity be : stack) {
            stackCap += be.tank.getCapacity();

            FluidStack fs = be.tank.getFluid();
            if (!fs.isEmpty() && stackType.isEmpty()) {
                stackType = fs.copy();
                stackType.setAmount(1);
            }
            stackAmt += fs.getAmount();
        }

        for (MissileFuelTankBlockEntity be : stack) {
            be.cachedStackTanks = stack.size();
            be.cachedStackCapacity = stackCap;
            be.cachedStackAmount = stackAmt;
            be.cachedStackFluid = stackType.isEmpty() ? FluidStack.EMPTY : stackType.copy();

            be.setChanged();
            be.markForSync();
        }
        updateStackCache(stack);

    }
    private static List<MissileFuelTankBlockEntity> collectVerticalStack(Level level, BlockPos start) {
        List<MissileFuelTankBlockEntity> result = new ArrayList<>();
        BlockState required = level.getBlockState(start);

        BlockPos cursor = start;
        while (true) {
            BlockPos below = cursor.below();
            if (!isFuelTank(level, below, required)) break;
            cursor = below;
        }

        while (true) {
            BlockEntity be = level.getBlockEntity(cursor);
            if (be instanceof MissileFuelTankBlockEntity ft) result.add(ft);
            else break;

            BlockPos above = cursor.above();
            if (!isFuelTank(level, above, required)) break;
            cursor = above;
        }

        return result;
    }
    private static boolean isFuelTank(Level level, BlockPos pos, BlockState requiredState) {
        BlockState st = level.getBlockState(pos);
        if (st.getBlock() != requiredState.getBlock()) return false;
        return level.getBlockEntity(pos) instanceof MissileFuelTankBlockEntity;
    }

    private void markForSync() {

        if (level == null) return;
        BlockState state = getBlockState();
        level.sendBlockUpdated(worldPosition, state, state, 3);
    }

    public IFluidHandler getFluidHandler(@Nullable Direction side) {
        return tank;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Tank", tank.writeToNBT(registries, new CompoundTag()));
        if (!cachedStackFluid.isEmpty())
            tag.put("CachedStackFluid", cachedStackFluid.saveOptional(registries));
        tag.putInt("CachedStackAmount", cachedStackAmount);
        tag.putInt("CachedStackCapacity", cachedStackCapacity);
        tag.putInt("CachedStackTanks", cachedStackTanks);
    }
    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Tank")) {
            tank.readFromNBT(registries, tag.getCompound("Tank"));
        }
        if (tag.contains("CachedStackFluid"))
            cachedStackFluid = FluidStack.parseOptional(registries, tag.getCompound("CachedStackFluid"));
        else
            cachedStackFluid = FluidStack.EMPTY;

        cachedStackAmount = tag.getInt("CachedStackAmount");
        cachedStackCapacity = tag.getInt("CachedStackCapacity");
        cachedStackTanks = tag.getInt("CachedStackTanks");
    }

    public record FuelStackInfo(FluidStack fluid, int totalAmount, int totalCapacity, int tanks) {}

    public FluidTank getTank() {
        return tank;
    }
    public FuelStackInfo getCachedStackInfo() {
        int cap = cachedStackCapacity > 0 ? cachedStackCapacity : tank.getCapacity();

        if (cachedStackFluid == null || cachedStackFluid.isEmpty() || cachedStackAmount <= 0) {
            return new FuelStackInfo(FluidStack.EMPTY, 0, cap, cachedStackTanks);
        }

        FluidStack f = cachedStackFluid.copy();
        f.setAmount(cachedStackAmount);
        return new FuelStackInfo(f, cachedStackAmount, cap, cachedStackTanks);
    }
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveWithoutMetadata(registries);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        FuelStackInfo info = getCachedStackInfo();

        int amt = info.totalAmount();
        int cap = info.totalCapacity();
        FluidStack fluid = info.fluid();

        tooltip.add(Component.literal("   ").append(Component.literal("Missile Fuel").withStyle(ChatFormatting.GOLD)));

        if (fluid.isEmpty() || amt <= 0) {
            tooltip.add(Component.literal("  Empty").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("  0 / " + cap + " mB").withStyle(ChatFormatting.DARK_GRAY));
            return true;
        }

        MutableComponent fluidName = fluid.getHoverName().copy().withStyle(ChatFormatting.AQUA);

        tooltip.add(Component.literal("  Fluid: ").withStyle(ChatFormatting.GRAY).append(fluidName));
        tooltip.add(Component.literal("  " + amt + " / " + cap + " mB").withStyle(ChatFormatting.GRAY));

        if (isPlayerSneaking) {
            tooltip.add(Component.literal("  Tanks: " + info.tanks()).withStyle(ChatFormatting.DARK_GRAY));
        }

        return true;
    }
    public static int getAmountFromTag(@Nullable CompoundTag beTag) {
        if (beTag == null || !beTag.contains("Tank")) return 0;
        CompoundTag tankTag = beTag.getCompound("Tank");
        if (!tankTag.contains("Fluid", Tag.TAG_COMPOUND)) return 0;
        return tankTag.getCompound("Fluid").getInt("amount");
    }

    public static FluidStack getFluidFromTag(@Nullable CompoundTag beTag, HolderLookup.Provider registries) {
        if (beTag == null || !beTag.contains("Tank")) return FluidStack.EMPTY;
        CompoundTag tankTag = beTag.getCompound("Tank");
        if (!tankTag.contains("Fluid", Tag.TAG_COMPOUND)) return FluidStack.EMPTY;
        return FluidStack.parseOptional(registries, tankTag.getCompound("Fluid"));
    }

    public static FluidStack getFluidFromTag(@Nullable CompoundTag beTag) {
        return FluidStack.EMPTY;
    }
}
