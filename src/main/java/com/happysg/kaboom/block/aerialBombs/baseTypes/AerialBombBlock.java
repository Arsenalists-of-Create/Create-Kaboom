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
import net.minecraft.world.level.Explosion;
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
import net.minecraft.world.phys.Vec3;
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
        ItemStack stack = new ItemStack(this);
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof AerialBombBlockEntity aerialBomb) {
            boolean hasAnyFuze = false;
            for (int i = 0; i < 9; i++) {
                if (!aerialBomb.getFuze(i).isEmpty()) {
                    hasAnyFuze = true;
                    break;
                }
            }
            if (hasAnyFuze) {
                net.minecraft.nbt.CompoundTag blockEntityData = new net.minecraft.nbt.CompoundTag();
                blockEntityData.putString("id", net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()).toString());
                net.minecraft.nbt.ListTag fuzesList = new net.minecraft.nbt.ListTag();
                for (int i = 0; i < 9; i++) {
                    ItemStack fuze = aerialBomb.getFuze(i);
                    if (!fuze.isEmpty()) {
                        net.minecraft.nbt.CompoundTag fuzeTag = new net.minecraft.nbt.CompoundTag();
                        fuzeTag.putInt("Index", i);
                        fuzeTag.put("Fuze", fuze.save(level.registryAccess()));
                        fuzesList.add(fuzeTag);
                    }
                }
                blockEntityData.put("KaboomFuzes", fuzesList);
                stack.set(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA,
                        net.minecraft.world.item.component.CustomData.of(blockEntityData));
            }
        }
        return stack;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);

        if (level.isClientSide) {
            return;
        }

        BlockEntity be = level.getBlockEntity(pos);
        boolean fuzed = false;
        if (be instanceof AerialBombBlockEntity aerialBomb) {
            for (int i = 0; i < 9; i++) {
                if (!aerialBomb.getFuze(i).isEmpty()) {
                    fuzed = true;
                    break;
                }
            }
        }

        BlockState cleanState = state
                .setValue(FUZED, fuzed)
                .setValue(POWERED, false)
                .setValue(COUNT, state.hasProperty(COUNT) ? state.getValue(COUNT) : 1);

        if (!cleanState.equals(state)) {
            level.setBlock(pos, cleanState, 3);
        }
    }

    private java.util.List<ItemStack> getCustomDrops(BlockState state, Level level, BlockPos pos, BlockEntity be) {
        java.util.List<ItemStack> drops = new java.util.ArrayList<>();
        if (be instanceof AerialBombBlockEntity aerialBomb) {
            int count = state.hasProperty(COUNT) ? state.getValue(COUNT) : 1;
            boolean isTiny = this instanceof com.happysg.kaboom.block.aerialBombs.tiny.TinyAerialBombBlock;

            java.util.Set<Integer> mappedIndices = new java.util.HashSet<>();
            for (int i = 0; i < count; i++) {
                int globalIndex = getGlobalIndex(isTiny, count, i);
                mappedIndices.add(globalIndex);
                ItemStack fuze = aerialBomb.getFuze(globalIndex);
                ItemStack bombStack = new ItemStack(this.asItem());
                if (!fuze.isEmpty()) {
                    net.minecraft.nbt.CompoundTag blockEntityData = new net.minecraft.nbt.CompoundTag();
                    blockEntityData.putString("id", net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()).toString());
                    net.minecraft.nbt.ListTag fuzesList = new net.minecraft.nbt.ListTag();
                    int singleBombIndex = isTiny ? 7 : 0;
                    net.minecraft.nbt.CompoundTag fuzeTag = new net.minecraft.nbt.CompoundTag();
                    fuzeTag.putInt("Index", singleBombIndex);
                    fuzeTag.put("Fuze", fuze.save(level.registryAccess()));
                    fuzesList.add(fuzeTag);
                    blockEntityData.put("KaboomFuzes", fuzesList);
                    bombStack.set(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA,
                            net.minecraft.world.item.component.CustomData.of(blockEntityData));
                }
                drops.add(bombStack);
            }

            for (int j = 0; j < 9; j++) {
                if (!mappedIndices.contains(j)) {
                    ItemStack fuze = aerialBomb.getFuze(j);
                    if (!fuze.isEmpty()) {
                        drops.add(fuze.copy());
                    }
                }
            }
        } else {
            int count = state.hasProperty(COUNT) ? state.getValue(COUNT) : 1;
            drops.add(new ItemStack(this.asItem(), count));
        }
        return drops;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof AerialBombBlockEntity aerialBomb) {
                if (player.isCreative()) {
                    aerialBomb.creativeBroken = true;
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state, BlockEntity blockEntity, ItemStack tool) {
        player.awardStat(net.minecraft.stats.Stats.BLOCK_MINED.get(this));
        player.causeFoodExhaustion(0.005F);
    }

    public static void tryPlaceFuzeFromItem(Level level, BlockPos pos, BlockState state, ItemStack bombStack, int newCount) {
        if (level.isClientSide) return;
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof AerialBombBlockEntity aerialBomb) {
            net.minecraft.world.item.component.CustomData customData = bombStack.get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA);
            if (customData != null) {
                net.minecraft.nbt.CompoundTag tag = customData.copyTag();
                if (tag.contains("KaboomFuzes")) {
                    net.minecraft.nbt.ListTag list = tag.getList("KaboomFuzes", 10);
                    if (!list.isEmpty()) {
                        net.minecraft.nbt.CompoundTag fuzeTag = list.getCompound(0);
                        ItemStack fuze = ItemStack.parseOptional(level.registryAccess(), fuzeTag.getCompound("Fuze"));
                        if (!fuze.isEmpty()) {
                            boolean isTiny = state.getBlock() instanceof com.happysg.kaboom.block.aerialBombs.tiny.TinyAerialBombBlock;
                            int globalIndex = getGlobalIndex(isTiny, newCount, newCount - 1);
                            aerialBomb.setFuze(globalIndex, fuze);
                            level.setBlockAndUpdate(pos, state.setValue(FUZED, true));
                            aerialBomb.notifyUpdate();
                            if (!level.getBlockTicks().willTickThisTick(pos, state.getBlock())) {
                                level.scheduleTick(pos, state.getBlock(), 0);
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public void wasExploded(Level level, BlockPos pos, Explosion explosion) {
        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof AerialBombBlockEntity aerialBomb) {
                aerialBomb.detonateOnSpot(Direction.UP);
            }
        }
        super.wasExploded(level, pos, explosion);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof AerialBombBlockEntity aerialBomb) {
                if (!aerialBomb.creativeBroken && !aerialBomb.activated && !isMoving && !level.isClientSide) {
                    for (ItemStack drop : getCustomDrops(state, level, pos, be)) {
                        popResource(level, pos, drop);
                    }
                }
            }
            super.onRemove(state, level, pos, newState, isMoving);
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

    public static double[][] getActiveCenters(boolean isTiny, int count) {
        if (isTiny) {
            double[][] allTiny = {
                {13.5, 13.5}, {8.0, 13.5}, {2.5, 13.5},
                {13.5, 8.0},  {8.0, 8.0},  {2.5, 8.0},
                {13.5, 2.5},  {8.0, 2.5},  {2.5, 2.5}
            };
            if (count == 1) {
                return new double[][]{allTiny[7]};
            } else {
                double[][] active = new double[count][2];
                for (int i = 0; i < count; i++) {
                    active[i] = allTiny[i];
                }
                return active;
            }
        } else {
            if (count == 1) {
                return new double[][]{{8.0, 12.0}};
            } else if (count == 2) {
                return new double[][]{{12.0, 12.0}, {4.0, 12.0}};
            } else if (count == 3) {
                return new double[][]{{12.0, 12.0}, {4.0, 12.0}, {12.0, 4.0}};
            } else {
                return new double[][]{{12.0, 12.0}, {4.0, 12.0}, {12.0, 4.0}, {4.0, 4.0}};
            }
        }
    }

    public static int getGlobalIndex(boolean isTiny, int count, int activeIndex) {
        if (isTiny) {
            if (count == 1) {
                return 7;
            } else {
                return activeIndex;
            }
        } else {
            return activeIndex;
        }
    }

    protected InteractionResult useLegacy(BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult result) {
        if (hand == InteractionHand.OFF_HAND) {
            return InteractionResult.PASS;
        } else {
            FuzedBlockEntity fuzedBlock = this.getBlockEntity(level, pos);
            if (!(fuzedBlock instanceof AerialBombBlockEntity aerialBomb)) {
                return InteractionResult.PASS;
            } else {
                ItemStack stack = player.getItemInHand(hand);
                Direction fuzeFace = state.getValue(FACING);
                if (result.getDirection() != fuzeFace) {
                    return InteractionResult.PASS;
                }

                int count = state.hasProperty(COUNT) ? state.getValue(COUNT) : 1;
                boolean isTiny = this instanceof com.happysg.kaboom.block.aerialBombs.tiny.TinyAerialBombBlock;
                boolean isSmall = this instanceof com.happysg.kaboom.block.aerialBombs.small.SmallAerialBombBlock || this instanceof com.happysg.kaboom.block.aerialBombs.small.FluidSmallAerialBombBlock;
                int globalIndex = 0;

                if (isTiny || isSmall) {
                    Vec3 hitVec = result.getLocation();
                    double localX = hitVec.x - pos.getX();
                    double localY = hitVec.y - pos.getY();
                    double localZ = hitVec.z - pos.getZ();

                    double u = localX;
                    double v = localY;

                    switch (fuzeFace) {
                        case NORTH:
                            u = localX;
                            break;
                        case SOUTH:
                            u = 1.0 - localX;
                            break;
                        case EAST:
                            u = localZ;
                            break;
                        case WEST:
                            u = 1.0 - localZ;
                            break;
                    }

                    double[][] activeCenters = getActiveCenters(isTiny, count);
                    double minDistanceSq = Double.MAX_VALUE;
                    int activeIndex = 0;
                    for (int i = 0; i < activeCenters.length; i++) {
                        double cx = activeCenters[i][0];
                        double cy = activeCenters[i][1];
                        double dx = u * 16.0 - cx;
                        double dy = v * 16.0 - cy;
                        double distSq = dx * dx + dy * dy;
                        if (distSq < minDistanceSq) {
                            minDistanceSq = distSq;
                            activeIndex = i;
                        }
                    }
                    globalIndex = getGlobalIndex(isTiny, count, activeIndex);
                }

                ItemStack fuzeStack = aerialBomb.getFuze(globalIndex);
                ItemStack copy;
                if (stack.isEmpty()) {
                    if (fuzeStack.isEmpty()) {
                        return InteractionResult.PASS;
                    }

                    if (!level.isClientSide) {
                        copy = aerialBomb.removeFuze(globalIndex);
                        if (!player.addItem(copy) && !player.isCreative()) {
                            ItemEntity item = player.drop(copy, false);
                            if (item != null) {
                                item.setNoPickUpDelay();
                                item.setTarget(player.getUUID());
                            }
                        }

                        aerialBomb.notifyUpdate();
                        if (!level.getBlockTicks().willTickThisTick(pos, this)) {
                            level.scheduleTick(pos, this, 0);
                        }
                    }

                    level.playSound(player, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.NEUTRAL, 1.0F, 1.0F);

                    if (!level.isClientSide) {
                        boolean anyFuzed = false;
                        for (int i = 0; i < 9; i++) {
                            if (!aerialBomb.getFuze(i).isEmpty()) {
                                anyFuzed = true;
                                break;
                            }
                        }
                        level.setBlockAndUpdate(pos, state.setValue(FUZED, anyFuzed));
                    }
                    return InteractionResult.sidedSuccess(level.isClientSide);
                } else {
                    if (!(stack.getItem() instanceof FuzeItem)) {
                        return InteractionResult.PASS;
                    }

                    if (!fuzeStack.isEmpty()) {
                        return InteractionResult.PASS;
                    } else {
                        if (!level.isClientSide) {
                            copy = player.getAbilities().instabuild ? stack.copy() : stack.split(1);
                            copy.setCount(1);
                            aerialBomb.setFuze(globalIndex, copy);
                            level.setBlockAndUpdate(pos, state.setValue(FUZED, true));
                            aerialBomb.notifyUpdate();
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
