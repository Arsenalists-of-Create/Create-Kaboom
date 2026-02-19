package com.happysg.kaboom.block.aerialBombs.tiny;

import com.happysg.kaboom.block.aerialBombs.baseTypes.AerialBombBlock;
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

public class TinyAerialBombBlock extends AerialBombBlock {

    private static final int MAX_COUNT = 9;

    public TinyAerialBombBlock(Properties properties) {
        super(properties, AerialBombProjectile.BombType.HE, 4); // size = 4 constant
        this.registerDefaultState(this.defaultBlockState().setValue(COUNT, 1));
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit) {
        InteractionResult base = super.use(state, level, pos, player, hand, hit);
        if (base.consumesAction())
            return base;
        ItemStack held = player.getItemInHand(hand);
        if (!(held.getItem() instanceof BlockItem bi) || bi.getBlock() != this)
            return InteractionResult.PASS;
        int count = state.getValue(COUNT);
        if (count >= MAX_COUNT)
            return InteractionResult.CONSUME; // already full
        if (!level.isClientSide) {
            level.setBlock(pos, state.setValue(COUNT, count + 1), 3);

            if (!player.getAbilities().instabuild) {
                held.shrink(1);
            }
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}