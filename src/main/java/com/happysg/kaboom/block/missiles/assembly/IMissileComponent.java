package com.happysg.kaboom.block.missiles.assembly;

import com.happysg.kaboom.block.missiles.MissileContraption;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.fluids.FluidStack;
import rbasamoyai.createbigcannons.cannon_control.contraption.AbstractMountedCannonContraption;
import rbasamoyai.createbigcannons.cannons.CannonContraptionProviderBlock;
import rbasamoyai.createbigcannons.crafting.casting.CannonCastShape;

import javax.annotation.Nullable;

public interface IMissileComponent extends CannonContraptionProviderBlock {
    enum MissilePartType {
        THRUSTER,
        FUEL_TANK,
        GUIDANCE
    }

    MissilePartType getPartType();

    MissileSize getMissileSize();

    @Override
    default AbstractMountedCannonContraption getCannonContraption() {
        return new MissileContraption();
    }

    @Override
    default Direction getFacing(BlockState state) {
        if (state.hasProperty(BlockStateProperties.FACING)) {
            return state.getValue(BlockStateProperties.FACING);
        }
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        }
        if (state.hasProperty(BlockStateProperties.AXIS)) {
            return Direction.fromAxisAndDirection(state.getValue(BlockStateProperties.AXIS), Direction.AxisDirection.POSITIVE);
        }
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_AXIS)) {
            return Direction.fromAxisAndDirection(state.getValue(BlockStateProperties.HORIZONTAL_AXIS), Direction.AxisDirection.POSITIVE);
        }
        return Direction.NORTH;
    }

    @Override
    default CannonCastShape getCannonShape() {
        return getMissileSize() == MissileSize.SMALL ? CannonCastShape.VERY_SMALL : CannonCastShape.VERY_LARGE;
    }

    @Override
    default boolean isComplete(BlockState state) {
        return true;
    }

    default boolean isFuelTank() {
        return getPartType() == MissilePartType.FUEL_TANK;
    }

    default boolean isThruster() {
        return getPartType() == MissilePartType.THRUSTER;
    }
    default boolean isGuidance() {
        return getPartType() == MissilePartType.GUIDANCE;
    }

    default int getFuelCapacityMb(BlockState state) { return 0; }

    default int getFuelMb(@Nullable CompoundTag beTag) { return 0; }

    default FluidStack getFuelFluid(@Nullable CompoundTag beTag) { return FluidStack.EMPTY; }

    default int getFuelMb(@Nullable CompoundTag beTag, HolderLookup.Provider registries) {
        return getFuelMb(beTag);
    }

    default FluidStack getFuelFluid(@Nullable CompoundTag beTag, HolderLookup.Provider registries) {
        return getFuelFluid(beTag);
    }
}

