package com.happysg.kaboom.block.aerialBombs.small;

import com.happysg.kaboom.block.aerialBombs.baseTypes.AerialBombProjectile;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class FragSmallAerialBombBlock extends SmallAerialBombBlock {
    public FragSmallAerialBombBlock(Properties properties) {
        super(properties, AerialBombProjectile.BombType.FRAG);

    }

}
