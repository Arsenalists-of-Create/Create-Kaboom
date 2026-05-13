package com.happysg.kaboom.block.aerialBombs.baseTypes;

import com.happysg.kaboom.registry.ModBlockEntityTypes;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.common.util.FakePlayer;
import rbasamoyai.createbigcannons.munitions.big_cannon.FuzedBlockEntity;
import rbasamoyai.createbigcannons.munitions.fuzes.FuzeItem;

public class AerialBombBlock extends HorizontalDirectionalBlock implements IBE<AerialBombBlockEntity> {
    private final AerialBombProjectile.BombType bombType;
    public static final BooleanProperty FUZED = BooleanProperty.create("fuzed");
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final IntegerProperty COUNT = IntegerProperty.create("count", 0, 9);
    private final int bombSize;

    public AerialBombBlock(Properties props, AerialBombProjectile.BombType bombType, int bombSize) {
        super(props);
        this.bombType = bombType;
        this.bombSize = Mth.clamp(bombSize, 1, 4);

        registerDefaultState(defaultBlockState()
                .setValue(FACING, Direction.NORTH)
                .setValue(FUZED, false)
                .setValue(POWERED, false)
                .setValue(COUNT, 1));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(properties -> new AerialBombBlock(properties, bombType, bombSize));
    }

    public AerialBombProjectile.BombType getBombType() {
        return bombType;
    }

    public int getBombSize() {
        return bombSize;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FUZED, POWERED, COUNT);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos,
                                Block block, BlockPos fromPos, boolean isMoving) {

        if (level.isClientSide) return;

        boolean wasPowered = state.getValue(POWERED);
        boolean isPowered = level.hasNeighborSignal(pos);

        if (!level.getBlockTicks().willTickThisTick(pos, this)) {
            level.scheduleTick(pos, this, 0);
        }

        if (wasPowered == isPowered) {
            return;
        }

        BlockState updatedState = state.setValue(POWERED, isPowered);
        level.setBlock(pos, updatedState, 3);

        if (isPowered) {
            if (tryDetonateFromRedstoneFuze(updatedState, level, pos)) {
                return;
            }
            withBlockEntityDo(level, pos, AerialBombBlockEntity::activate);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        tryDetonateFromRedstoneFuze(state, level, pos);
    }

    private boolean tryDetonateFromRedstoneFuze(BlockState state, Level level, BlockPos pos) {
        AerialBombBlockEntity blockEntity = getBlockEntity(level, pos);
        if (blockEntity == null) {
            return false;
        }

        ItemStack fuzeStack = blockEntity.getFuze();
        if (!(fuzeStack.getItem() instanceof FuzeItem fuzeItem)) {
            return false;
        }

        for (Direction direction : Direction.values()) {
            int signal = level.getSignal(pos.relative(direction), direction);
            if (fuzeItem.onRedstoneSignal(fuzeStack, level, pos, state, signal, direction)) {
                blockEntity.detonateOnSpot(direction);
                return true;
            }
        }

        return false;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, getPlacementFacing(context))
                .setValue(FUZED, false)
                .setValue(POWERED, false);
    }

    private Direction getPlacementFacing(BlockPlaceContext context) {
        Player player = context.getPlayer();

        if (player instanceof FakePlayer) {
            Direction clickedFace = context.getClickedFace();
            return clickedFace.getAxis() == Direction.Axis.Y
                    ? defaultBlockState().getValue(FACING)
                    : clickedFace.getOpposite();
        }

        Direction facing = context.getHorizontalDirection();
        return player != null && player.isCrouching() ? facing.getOpposite() : facing;
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        return new ItemStack(this);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);

        if (level.isClientSide) {
            return;
        }

        BlockState cleanState = state
                .setValue(FUZED, false)
                .setValue(POWERED, false)
                .setValue(COUNT, defaultBlockState().getValue(COUNT));

        if (!cleanState.equals(state)) {
            level.setBlock(pos, cleanState, 3);
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof AerialBombBlockEntity aerialBomb) {
            aerialBomb.setFuze(ItemStack.EMPTY);
            aerialBomb.notifyUpdate();
        }
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult result) {
        return toItemInteractionResult(useLegacy(state, level, pos, player, hand, result));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult result) {
        return useLegacy(state, level, pos, player, InteractionHand.MAIN_HAND, result);
    }

    protected static ItemInteractionResult toItemInteractionResult(InteractionResult result) {
        return switch (result) {
            case SUCCESS -> ItemInteractionResult.SUCCESS;
            case CONSUME -> ItemInteractionResult.CONSUME;
            case CONSUME_PARTIAL -> ItemInteractionResult.CONSUME_PARTIAL;
            case FAIL -> ItemInteractionResult.FAIL;
            case PASS, SUCCESS_NO_ITEM_USED -> ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        };
    }

    protected InteractionResult useLegacy(BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult result) {
        if (hand == InteractionHand.OFF_HAND) {
            return InteractionResult.PASS;
        } else {
            FuzedBlockEntity fuzedBlock = this.getBlockEntity(level, pos);
            if (fuzedBlock == null) {
                return InteractionResult.PASS;
            } else {
                ItemStack stack = player.getItemInHand(hand);
                Direction fuzeFace = state.getValue(FACING);
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

                        level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.NEUTRAL, 1.0F, 1.0F);
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
