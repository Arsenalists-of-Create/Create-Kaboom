package com.happysg.kaboom.block.aerialBombs.small;

import com.happysg.kaboom.block.aerialBombs.baseTypes.AerialBombBlock;
import com.happysg.kaboom.block.aerialBombs.baseTypes.AerialBombProjectile;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class SmallAerialBombBlock extends AerialBombBlock {
    private static final int MAX_COUNT = 4;

    public SmallAerialBombBlock(Properties properties) {
        this(properties, AerialBombProjectile.BombType.HE);
    }

    protected SmallAerialBombBlock(Properties properties, AerialBombProjectile.BombType bombType) {
        super(properties, bombType, 2);

        this.registerDefaultState(this.defaultBlockState().setValue(COUNT, 1));
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {

        ItemInteractionResult base = super.useItemOn(held, state, level, pos, player, hand, hit);
        if (base.consumesAction())
            return base;

        if (!(held.getItem() instanceof BlockItem bi) || bi.getBlock() != this)
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

        int count = state.getValue(COUNT);
        if (count >= MAX_COUNT)
            return ItemInteractionResult.CONSUME;

        if (!level.isClientSide) {
            level.setBlock(pos, state.setValue(COUNT, count + 1), 3);

            if (!player.getAbilities().instabuild) {
                held.shrink(1);
            }
        }

        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
}
