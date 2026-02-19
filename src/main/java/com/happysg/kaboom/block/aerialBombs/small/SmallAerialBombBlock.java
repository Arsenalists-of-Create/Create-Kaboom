package com.happysg.kaboom.block.aerialBombs.small;

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

public class SmallAerialBombBlock extends AerialBombBlock {
    private static final int MAX_COUNT = 4;


    public SmallAerialBombBlock(Properties properties) {
        this(properties, AerialBombProjectile.BombType.HE);
    }

    protected SmallAerialBombBlock(Properties properties, AerialBombProjectile.BombType bombType) {
        super(properties, bombType, 2);

        // Default count for small bombs is 4
        this.registerDefaultState(this.defaultBlockState().setValue(COUNT, MAX_COUNT));
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit) {

        // If your base class handles fuze insertion / other interactions, respect it.
        InteractionResult base = super.use(state, level, pos, player, hand, hit);
        if (base.consumesAction())
            return base;

        ItemStack held = player.getItemInHand(hand);

        // Only if holding another instance of this same block
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