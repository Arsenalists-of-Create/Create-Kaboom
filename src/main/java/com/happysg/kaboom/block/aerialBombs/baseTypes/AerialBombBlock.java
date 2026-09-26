package com.happysg.kaboom.block.aerialBombs.baseTypes;

import com.happysg.kaboom.registry.ModBlockEntityTypes;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Explosion;
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
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import rbasamoyai.createbigcannons.munitions.fuzes.FuzeItem;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

public class AerialBombBlock extends HorizontalDirectionalBlock implements IBE<AerialBombBlockEntity> {
    public static final BooleanProperty FUZED = BooleanProperty.create("fuzed");
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final IntegerProperty COUNT = IntegerProperty.create("count", 0, 9);

    private final AerialBombProjectile.BombType bombType;
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
        if (level.isClientSide) {
            return;
        }

        boolean wasPowered = state.getValue(POWERED);
        boolean isPowered = level.hasNeighborSignal(pos);
        scheduleFuzeTick(level, pos);
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

        int activeSlots = AerialBombFuzeLayout.activeSlotCount(state);
        for (int slot = 0; slot < activeSlots; slot++) {
            ItemStack fuzeStack = blockEntity.getFuze(slot);
            if (!(fuzeStack.getItem() instanceof FuzeItem fuzeItem)) {
                continue;
            }

            for (Direction direction : Direction.values()) {
                int signal = level.getSignal(pos.relative(direction), direction);
                if (fuzeItem.onRedstoneSignal(fuzeStack, level, pos, state, signal, direction)) {
                    blockEntity.detonateOnSpot(direction);
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
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
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos,
                                       Player player) {
        ItemStack result = new ItemStack(this);
        if (target instanceof BlockHitResult blockHit
                && level.getBlockEntity(pos) instanceof AerialBombBlockEntity aerialBomb) {
            int slot = AerialBombFuzeLayout.selectedSlot(state, pos, blockHit);
            if (slot < 0) {
                slot = AerialBombFuzeLayout.launchSlot(state);
            }
            AerialBombBlockItem.setFuze(result, aerialBomb.getFuze(slot), aerialBomb.getType(), level.registryAccess());
        }
        return result;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide) {
            return;
        }

        ItemStack placedFuze = AerialBombBlockItem.getFuze(stack, level.registryAccess());
        BlockState cleanState = state
                .setValue(FUZED, !placedFuze.isEmpty())
                .setValue(POWERED, false)
                .setValue(COUNT, 1);
        if (!cleanState.equals(state)) {
            level.setBlock(pos, cleanState, 3);
        }

        if (level.getBlockEntity(pos) instanceof AerialBombBlockEntity aerialBomb) {
            aerialBomb.setFuze(placedFuze);
            aerialBomb.notifyUpdate();
        }
        scheduleFuzeTick(level, pos);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult result) {
        InteractionResult fuzeResult = useFuze(state, level, pos, player, hand, result);
        if (fuzeResult.consumesAction()) {
            return toItemInteractionResult(fuzeResult);
        }
        return tryStackBomb(stack, state, level, pos, player);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult result) {
        return useFuze(state, level, pos, player, InteractionHand.MAIN_HAND, result);
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

    private InteractionResult useFuze(BlockState state, Level level, BlockPos pos, Player player,
                                      InteractionHand hand, BlockHitResult hit) {
        if (hand == InteractionHand.OFF_HAND) {
            return InteractionResult.PASS;
        }

        AerialBombBlockEntity blockEntity = getBlockEntity(level, pos);
        if (blockEntity == null) {
            return InteractionResult.PASS;
        }

        int slot = AerialBombFuzeLayout.selectedSlot(state, pos, hit);
        if (slot < 0) {
            return InteractionResult.PASS;
        }

        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty()) {
            if (blockEntity.getFuze(slot).isEmpty()) {
                return InteractionResult.PASS;
            }

            if (!level.isClientSide) {
                ItemStack removed = blockEntity.removeFuze(slot);
                if (!player.addItem(removed) && !player.isCreative()) {
                    ItemEntity item = player.drop(removed, false);
                    if (item != null) {
                        item.setNoPickUpDelay();
                        item.setTarget(player.getUUID());
                    }
                }
                blockEntity.notifyUpdate();
                synchronizeAfterFuzeChange(level, pos, state, blockEntity);
            }

            level.playSound(player, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.NEUTRAL, 1.0F, 1.0F);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (!(held.getItem() instanceof FuzeItem) || !blockEntity.getFuze(slot).isEmpty()) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide) {
            ItemStack inserted = player.getAbilities().instabuild ? held.copy() : held.split(1);
            inserted.setCount(1);
            blockEntity.setFuze(slot, inserted);
            blockEntity.notifyUpdate();
            synchronizeAfterFuzeChange(level, pos, state, blockEntity);
        }

        level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.NEUTRAL, 1.0F, 1.0F);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private ItemInteractionResult tryStackBomb(ItemStack held, BlockState state, Level level, BlockPos pos,
                                               Player player) {
        if (!(held.getItem() instanceof BlockItem blockItem) || blockItem.getBlock() != this) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        int capacity = AerialBombFuzeLayout.capacity(state);
        if (capacity <= 1) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        int count = AerialBombFuzeLayout.activeSlotCount(state);
        if (count >= capacity) {
            return ItemInteractionResult.CONSUME;
        }

        if (!level.isClientSide) {
            int newSlot = count;
            BlockState stackedState = state.setValue(COUNT, count + 1);
            level.setBlock(pos, stackedState, 3);
            if (level.getBlockEntity(pos) instanceof AerialBombBlockEntity aerialBomb) {
                ItemStack itemFuze = AerialBombBlockItem.getFuze(held, level.registryAccess());
                aerialBomb.setFuze(newSlot, itemFuze);
                stackedState = stackedState.setValue(FUZED, aerialBomb.hasActiveFuze(stackedState));
                level.setBlock(pos, stackedState, 3);
                aerialBomb.notifyUpdate();
                scheduleFuzeTick(level, pos);
            }

            if (!player.getAbilities().instabuild) {
                held.shrink(1);
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    private void synchronizeAfterFuzeChange(Level level, BlockPos pos, BlockState state,
                                            AerialBombBlockEntity blockEntity) {
        boolean fuzed = blockEntity.hasActiveFuze(state);
        if (state.getValue(FUZED) != fuzed) {
            level.setBlockAndUpdate(pos, state.setValue(FUZED, fuzed));
        }
        scheduleFuzeTick(level, pos);
    }

    private void scheduleFuzeTick(Level level, BlockPos pos) {
        if (!level.getBlockTicks().willTickThisTick(pos, this)) {
            level.scheduleTick(pos, this, 0);
        }
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> vanillaDrops = super.getDrops(state, params);
        BlockEntity blockEntity = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (!(blockEntity instanceof AerialBombBlockEntity aerialBomb)) {
            return vanillaDrops;
        }

        int activeSlots = AerialBombFuzeLayout.activeSlotCount(state);
        List<ItemStack> perBombDrops = new ArrayList<>();
        for (ItemStack drop : vanillaDrops) {
            if (!(drop.getItem() instanceof BlockItem blockItem) || blockItem.getBlock() != this) {
                perBombDrops.add(drop);
                continue;
            }

            for (int lootCopy = 0; lootCopy < drop.getCount(); lootCopy++) {
                for (int slot = 0; slot < activeSlots; slot++) {
                    ItemStack bomb = drop.copyWithCount(1);
                    AerialBombBlockItem.setFuze(bomb, aerialBomb.getFuze(slot), aerialBomb.getType(),
                            params.getLevel().registryAccess());
                    perBombDrops.add(bomb);
                }
            }
        }
        return perBombDrops;
    }

    @Override
    protected void onExplosionHit(BlockState state, Level level, BlockPos pos, Explosion explosion,
                                  BiConsumer<ItemStack, BlockPos> dropConsumer) {
        AerialBombBlockEntity blockEntity = getBlockEntity(level, pos);
        if (blockEntity == null) {
            super.onExplosionHit(state, level, pos, explosion, dropConsumer);
            return;
        }

        if (!level.isClientSide) {
            Vec3 awayFromExplosion = Vec3.atCenterOf(pos).subtract(explosion.center());
            Direction direction = awayFromExplosion.lengthSqr() < 1.0E-6
                    ? Direction.UP
                    : Direction.getNearest(awayFromExplosion);
            blockEntity.detonateOnSpot(direction);
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
