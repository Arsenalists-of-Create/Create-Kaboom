package com.happysg.kaboom.block.aerialBombs.tiny;

import com.happysg.kaboom.block.aerialBombs.baseTypes.AerialBombBlock;
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
    public TinyAerialBombBlock(Properties properties) {
        super(properties);
        registerDefaultState(super.defaultBlockState().setValue(SIZE,4).setValue(COUNT,1));
    }
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        super.use(state,level,pos,player,hand,hit);

        // Only if holding another instance of this same block
        if (!(held.getItem() instanceof BlockItem bi) || bi.getBlock() != this)
            return InteractionResult.PASS;

        int size = state.getValue(COUNT);
        int max = 9;// or just hardcode

        if (size >= max)
            return InteractionResult.CONSUME; // already full

        if (!level.isClientSide) {
            level.setBlock(pos, state.setValue(COUNT, size + 1), 3);

            if (!player.getAbilities().instabuild) {
                held.shrink(1);
            }
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
