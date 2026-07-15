package com.happysg.kaboom.block.missiles.parts.fuel;

import com.happysg.kaboom.block.missiles.assembly.IMissileComponent;
import com.happysg.kaboom.block.missiles.assembly.MissileSize;
import com.happysg.kaboom.block.missiles.parts.MissilePartShapes;
import com.happysg.kaboom.registry.ModBlockEntityTypes;
import com.happysg.kaboom.registry.ModBlocks;
import com.simibubi.create.content.fluids.transfer.GenericItemEmptying;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

import javax.annotation.Nullable;

public class MissileFuelTankBlock extends RotatedPillarBlock implements IMissileComponent, IBE<MissileFuelTankBlockEntity> {
    private final int capacity;
    private final MissileSize missileSize;

    public MissileFuelTankBlock(Properties properties, int capacity, MissileSize missileSize) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(AXIS, Direction.Axis.Y));
        this.capacity = capacity;
        this.missileSize = missileSize;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        Direction.Axis axis = state.getValue(AXIS);
        if (this == ModBlocks.MISSILE_FUEL_SMALL.get()) return MissilePartShapes.small(axis);
        if (this == ModBlocks.MISSILE_FUEL.get()) return MissilePartShapes.FULL;
        return MissilePartShapes.large(axis);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return getShape(s, l, p, c);
    }
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(AXIS, context.getNearestLookingDirection().getAxis());
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isCreative()
                && GenericItemEmptying.canItemBeEmptied(level, held)
                && level.getBlockEntity(pos) instanceof MissileFuelTankBlockEntity fuelTank) {
            FluidStack fluid = GenericItemEmptying.emptyItem(level, held, true).getFirst();

            if (!fluid.isEmpty()
                    && fuelTank.getTank().fill(fluid, FluidAction.SIMULATE) == fluid.getAmount()) {
                if (!level.isClientSide) {
                    fuelTank.getTank().fill(fluid, FluidAction.EXECUTE);
                    player.swing(hand, true);
                    level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS,
                            1.0f, 0.9f + level.random.nextFloat() * 0.2f);
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
        }

        return super.useItemOn(held, state, level, pos, player, hand, hit);
    }

    public int getCapacity() {
        return capacity;
    }
    @Override
    public MissilePartType getPartType() {
        return MissilePartType.FUEL_TANK;
    }

    @Override
    public MissileSize getMissileSize() {
        return missileSize;
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

    @Override
    public FluidStack getFuelFluid(@Nullable CompoundTag beTag, HolderLookup.Provider registries) {
        return MissileFuelTankBlockEntity.getFluidFromTag(beTag, registries);
    }

}
