package com.happysg.kaboom.block.missiles.assembly;

import com.happysg.kaboom.block.missiles.parts.ThrusterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;

public interface IMissileComponent {
    enum MissilePartType {
        THRUSTER,
        FUEL_TANK,
        GUIDANCE
    }

    MissilePartType getPartType();

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
}


