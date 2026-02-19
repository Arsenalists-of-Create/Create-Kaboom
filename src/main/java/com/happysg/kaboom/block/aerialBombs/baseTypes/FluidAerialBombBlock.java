package com.happysg.kaboom.block.aerialBombs.baseTypes;
import com.happysg.kaboom.registry.ModBlockEntityTypes;
import com.simibubi.create.content.fluids.transfer.GenericItemEmptying;
import com.simibubi.create.content.fluids.transfer.GenericItemFilling;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class FluidAerialBombBlock extends AerialBombBlock {
    public FluidAerialBombBlock(Properties properties) {
        super(properties, AerialBombProjectile.BombType.FLUID,1);
        registerDefaultState(super.defaultBlockState().setValue(COUNT,1));
    }


    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof FluidAerialBombBlockEntity fluidBE) {

            ItemStack held = player.getItemInHand(hand);

            boolean canEmpty = GenericItemEmptying.canItemBeEmptied(level, held);
            boolean canFill  = GenericItemFilling.canItemBeFilled(level, held);

            if (canEmpty && fluidBE.tryEmptyItemIntoTE(level, player, hand, held)) {
                if (!level.isClientSide) {
                    player.swing(hand, true);

                    // Pick a sound; bucket empty is a good default
                    level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0f, 0.9f + level.random.nextFloat() * 0.2f);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }

            if (canFill && fluidBE.tryFillItemFromTE(level, player, hand, held)) {
                if (!level.isClientSide) {
                    player.swing(hand, true);

                    level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0f, 0.9f + level.random.nextFloat() * 0.2f);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }

        return super.use(state, level, pos, player, hand, hit);
    }
    @Override
    public BlockEntityType<? extends FluidAerialBombBlockEntity> getBlockEntityType() {
        return ModBlockEntityTypes.FLUID_AERIAL_BOMB_BE.get();
    }
}
