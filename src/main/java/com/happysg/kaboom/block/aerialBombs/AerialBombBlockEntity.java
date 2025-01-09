package com.happysg.kaboom.block.aerialBombs;

import com.happysg.kaboom.compat.vs2.VS2Utils;
import com.happysg.kaboom.registry.ModProjectiles;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import rbasamoyai.createbigcannons.munitions.big_cannon.FuzedBlockEntity;


public class AerialBombBlockEntity extends FuzedBlockEntity {

    public AerialBombBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public void activate() {
        if(level==null || level.isClientSide) return;
        AerialBombProjectile projectile= ModProjectiles.AERIAL_BOMB_PROJECTILE.create(level);
        projectile.setPos(VS2Utils.getWorldPos(this).below().getCenter());
        projectile.setState(getBlockState());
        projectile.setFuze(getFuze());
        level.addFreshEntity(projectile);
    }
}
