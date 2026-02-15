package com.happysg.kaboom.block.aerialBombs.baseTypes;

import com.happysg.kaboom.registry.ModBlockEntityTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import rbasamoyai.createbigcannons.munitions.big_cannon.FuzedBlockEntity;
import rbasamoyai.createbigcannons.munitions.fuzes.FuzeItem;

public class AerialBombBlock extends HorizontalDirectionalBlock implements IBE<AerialBombBlockEntity> {

    public static final BooleanProperty FUZED = BooleanProperty.create("fuzed");
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final IntegerProperty TYPE = IntegerProperty.create("type", 1,7 );
    public static final IntegerProperty SIZE = IntegerProperty.create("size",1,4);
    public static final IntegerProperty COUNT= IntegerProperty.create("count", 0,9);


    public AerialBombBlock(Properties properties) {
        super(properties);
        registerDefaultState(super.defaultBlockState()
                .setValue(FACING, Direction.NORTH)
                .setValue(FUZED, false)
                .setValue(POWERED, false)
                .setValue(TYPE,1)
                .setValue(SIZE,1)
                .setValue(COUNT,1));
    }


    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
        builder.add(FUZED);
        builder.add(POWERED);
        builder.add(TYPE);
        builder.add(SIZE);
        builder.add(COUNT);
        super.createBlockStateDefinition(builder);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos,
                                Block block, BlockPos fromPos, boolean isMoving) {

        if (level.isClientSide) return;

        boolean wasPowered = state.getValue(POWERED);
        boolean isPowered = level.hasNeighborSignal(pos);

        if (!wasPowered && isPowered) {
            withBlockEntityDo(level, pos, AerialBombBlockEntity::activate);
            return;
        }

        if (wasPowered != isPowered) {
            level.setBlock(pos, state.setValue(POWERED, isPowered), 3);
        }
    }


    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        boolean crouch = context.getPlayer() != null && context.getPlayer().isCrouching();

        return this.defaultBlockState()
                .setValue(FACING, crouch ?
                        context.getHorizontalDirection().getOpposite() : context.getHorizontalDirection())
                .setValue(POWERED, false);
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
                Direction fuzeFace = (Direction) state.getValue(FACING);
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
                    level.setBlockAndUpdate(pos, state.setValue(FUZED, false));
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

                        level.playSound((Player) null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.NEUTRAL, 1.0F, 1.0F);
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