package com.happysg.kaboom.block.missiles.parts;

import com.happysg.kaboom.block.missiles.assembly.IMissileComponent;
import com.happysg.kaboom.registry.ModBlockEntityTypes;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;

public class MissileFuelTankBlock extends RotatedPillarBlock implements IMissileComponent, IBE<MissileFuelTankBlockEntity> {
    private final int capacity;

    public MissileFuelTankBlock(Properties properties, int capacity) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(AXIS, Direction.Axis.Y));
        this.capacity = capacity;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(AXIS, context.getClickedFace().getAxis());
    }
    public int getCapacity() {
        return capacity;
    }
    @Override
    public MissilePartType getPartType() {
        return MissilePartType.FUEL_TANK;
    }

    @Override
    public Class<MissileFuelTankBlockEntity> getBlockEntityClass() {
        return MissileFuelTankBlockEntity.class;
    }


    @Override
    public BlockEntityType<? extends MissileFuelTankBlockEntity> getBlockEntityType() {
        return ModBlockEntityTypes.FUEL_TANK_SMALL.get();
    }
    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : (lvl, p, st, be) -> {
            if (be instanceof MissileFuelTankBlockEntity ft) MissileFuelTankBlockEntity.tick(lvl, p, st, ft);
        };
    }
    @Override
    public int getFuelCapacityMb(BlockState state) {
        return this.capacity;
    }

    @Override
    public int getFuelMb(@Nullable CompoundTag beTag) {
        return MissileFuelTankBlockEntity.getAmountFromTag(beTag);
    }

    @Override
    public FluidStack getFuelFluid(@Nullable CompoundTag beTag) {
        return MissileFuelTankBlockEntity.getFluidFromTag(beTag);
    }

}
