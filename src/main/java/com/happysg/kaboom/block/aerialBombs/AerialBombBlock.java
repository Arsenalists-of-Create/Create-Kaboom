package com.happysg.kaboom.block.aerialBombs;

import com.happysg.kaboom.registry.ModBlockEntityTypes;
import com.happysg.kaboom.registry.ModProjectiles;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.MobSpawnType;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import rbasamoyai.createbigcannons.index.CBCItems;
import rbasamoyai.createbigcannons.munitions.big_cannon.FuzedBlockEntity;
import rbasamoyai.createbigcannons.munitions.fuzes.FuzeItem;

public class AerialBombBlock extends HorizontalDirectionalBlock implements IBE<AerialBombBlockEntity> {

    public static final BooleanProperty FUZED = BooleanProperty.create("fuzed");


    public AerialBombBlock(Properties properties) {
        super(properties);
        registerDefaultState(super.defaultBlockState()
                .setValue(FACING, Direction.NORTH)
                .setValue(FUZED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
        builder.add(FUZED);
        super.createBlockStateDefinition(builder);
    }

    public void neighborChanged(BlockState pState, Level pLevel, BlockPos pPos, Block pBlock, BlockPos pFromPos, boolean pIsMoving) {
        if (pLevel.hasNeighborSignal(pPos))
            withBlockEntityDo(pLevel, pPos, AerialBombBlockEntity::activate);
    }



    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        boolean crouch= context.getPlayer() != null && context.getPlayer().isCrouching();

        return this.defaultBlockState()
                .setValue(FACING, crouch?
                        context.getHorizontalDirection().getOpposite() : context.getHorizontalDirection());
    }


    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult result) {
        if (hand == InteractionHand.OFF_HAND) {
            return InteractionResult.PASS;
        } else {
            FuzedBlockEntity fuzedBlock = this.getBlockEntity(level, pos);
            if (fuzedBlock == null) {
                return InteractionResult.PASS;
            } else {
                ItemStack stack = player.getItemInHand(hand);
                Direction fuzeFace = (Direction)state.getValue(FACING);
                byte slot;
                ItemStack copy;
                if (stack.isEmpty()) {

                        if (result.getDirection() != fuzeFace || fuzedBlock.getItem(1).isEmpty()) {
                            return InteractionResult.PASS;
                        }

                        slot = 1;


                    if (!level.isClientSide) {
                        copy = fuzedBlock.removeItem(slot, 1);
                        if (!player.addItem(copy) && !player.isCreative()) {
                            ItemEntity item = player.drop(copy, false);
                            if (item != null) {
                                item.setNoPickUpDelay();
                                item.setTarget(player.getUUID());
                            }
                        }

                        fuzedBlock.notifyUpdate();
                        if (!level.getBlockTicks().willTickThisTick(pos, this)) {
                            level.scheduleTick(pos, this, 0);
                        }
                    }

                    level.playSound(player, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.NEUTRAL, 1.0F, 1.0F);
                    return InteractionResult.sidedSuccess(level.isClientSide);
                } else {
                        if (!(stack.getItem() instanceof FuzeItem) || result.getDirection() != fuzeFace) {
                            return InteractionResult.PASS;
                        }

                        slot = 1;
                    if (!fuzedBlock.getItem(slot).isEmpty()) {
                        return InteractionResult.PASS;
                    } else {
                        if (!level.isClientSide) {
                            copy = player.getAbilities().instabuild ? stack.copy() : stack.split(1);
                            copy.setCount(1);
                            fuzedBlock.setItem(slot, copy);
                            level.setBlockAndUpdate(pos, state.setValue(FUZED, true));
                            fuzedBlock.notifyUpdate();
                            if (!level.getBlockTicks().willTickThisTick(pos, this)) {
                                level.scheduleTick(pos, this, 0);
                            }
                        }

                        level.playSound((Player)null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.NEUTRAL, 1.0F, 1.0F);
                        return InteractionResult.sidedSuccess(level.isClientSide);
                    }
                }
            }
        }
    }


    @Override
    public Class<AerialBombBlockEntity> getBlockEntityClass() {
        return AerialBombBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends AerialBombBlockEntity> getBlockEntityType() {
        return ModBlockEntityTypes.AERIAL_BOMB.get();
    }
}
