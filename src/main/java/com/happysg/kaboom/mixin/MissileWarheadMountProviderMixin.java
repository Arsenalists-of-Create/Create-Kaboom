package com.happysg.kaboom.mixin;

import com.happysg.kaboom.block.missiles.MissileContraption;
import com.happysg.kaboom.block.missiles.assembly.MissileSize;
import com.happysg.kaboom.block.missiles.parts.warhead.AbstractMissileWarhead;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.spongepowered.asm.mixin.Mixin;
import rbasamoyai.createbigcannons.cannon_control.contraption.AbstractMountedCannonContraption;
import rbasamoyai.createbigcannons.cannons.CannonContraptionProviderBlock;
import rbasamoyai.createbigcannons.crafting.casting.CannonCastShape;
import rbasamoyai.createbigcannons.munitions.big_cannon.FuzedProjectileBlock;

@Mixin(FuzedProjectileBlock.class)
public abstract class MissileWarheadMountProviderMixin implements CannonContraptionProviderBlock {
    @Override
    public AbstractMountedCannonContraption getCannonContraption() {
        return new MissileContraption();
    }

    @Override
    public Direction getFacing(BlockState state) {
        return state.hasProperty(BlockStateProperties.FACING)
                ? state.getValue(BlockStateProperties.FACING)
                : Direction.NORTH;
    }

    @Override
    public CannonCastShape getCannonShape() {
        Object block = this;
        return block instanceof AbstractMissileWarhead warhead
                && warhead.getMissileSize() != MissileSize.SMALL
                ? CannonCastShape.VERY_LARGE
                : CannonCastShape.VERY_SMALL;
    }

    @Override
    public boolean isComplete(BlockState state) {
        return true;
    }
}
