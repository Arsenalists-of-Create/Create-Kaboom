package com.happysg.kaboom.block.seamine;

import com.happysg.kaboom.compat.CBCExplosionHelper;
import com.happysg.kaboom.items.MineFuze;
import com.happysg.kaboom.registry.ModBlockEntityTypes;
import com.happysg.kaboom.registry.ModBlocks;
import com.simibubi.create.AllItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import rbasamoyai.createbigcannons.config.CBCConfigs;
import rbasamoyai.createbigcannons.munitions.fuzes.FuzeItem;

public class SeaMineBlock extends Block implements EntityBlock, SimpleWaterloggedBlock {

    public static final BooleanProperty FUZED = BooleanProperty.create("fuzed");
    public static final BooleanProperty ANCHORED = BooleanProperty.create("anchored");
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    protected static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);

    public SeaMineBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FUZED, false)
                .setValue(ANCHORED, false)
                .setValue(WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FUZED, ANCHORED, WATERLOGGED);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos currentPos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(currentPos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, direction, neighborState, level, currentPos, neighborPos);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return ModBlockEntityTypes.SEA_MINE_BE.get().create(pos, state);
    }

    public static boolean isOnBottom(Level level, BlockPos pos) {
        FluidState fluidState = level.getFluidState(pos);
        if (!fluidState.is(FluidTags.WATER) && !level.getBlockState(pos).getValue(WATERLOGGED)) {
            return false;
        }
        BlockPos below = pos.below();
        BlockState belowState = level.getBlockState(below);
        return !belowState.isAir() && !belowState.getFluidState().is(FluidTags.WATER);
    }

    public void explodeMine(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof SeaMineBlockEntity seaMineBE) {
            seaMineBE.clearContents();
        }
        level.removeBlock(pos, false);
        CBCExplosionHelper.createShellExplosion(
                level, null, null,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                6.0f, false,
                CBCConfigs.server().munitions.damageRestriction.get().explosiveInteraction()
        );
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof SeaMineBlockEntity seaMineBE) {
                ItemStack fuze = seaMineBE.getFuze();
                if (!fuze.isEmpty()) {
                    Block.popResource(level, pos, fuze);
                }
                ItemStack chain = seaMineBE.getChainStack();
                if (!chain.isEmpty()) {
                    Block.popResource(level, pos, chain);
                }
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        FluidState fluidstate = context.getLevel().getFluidState(context.getClickedPos());
        return this.defaultBlockState().setValue(WATERLOGGED, fluidstate.getType() == Fluids.WATER);
    }

    @Override
    public void onBlockExploded(BlockState state, Level level, BlockPos pos, Explosion explosion) {
        if (!level.isClientSide) {
            explodeMine(level, pos);
        }
        super.onBlockExploded(state, level, pos, explosion);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof SeaMineBlockEntity seaMineBE) {
            if (player.isShiftKeyDown()) {
                if (isOnBottom(level, pos)) {
                    if (!level.isClientSide) {
                        seaMineBE.tryAssemble(player);
                    }
                    return ItemInteractionResult.sidedSuccess(level.isClientSide);
                }

                if (!seaMineBE.getFuze().isEmpty() || !seaMineBE.getChainStack().isEmpty()) {
                    if (!level.isClientSide) {
                        ItemStack fuze = seaMineBE.getFuze();
                        if (!fuze.isEmpty()) {
                            if (!player.addItem(fuze)) {
                                Block.popResource(level, pos, fuze);
                            }
                            seaMineBE.setFuze(ItemStack.EMPTY);
                        }
                        ItemStack chain = seaMineBE.getChainStack();
                        if (!chain.isEmpty()) {
                            if (!player.addItem(chain)) {
                                Block.popResource(level, pos, chain);
                            }
                            seaMineBE.setChainStack(ItemStack.EMPTY);
                        }
                        seaMineBE.clearContents();
                        level.setBlock(pos, state.setValue(FUZED, false).setValue(ANCHORED, false), 3);
                        level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 1.0F, 1.0F);
                    }
                    return ItemInteractionResult.sidedSuccess(level.isClientSide);
                }
            }

            if (!player.isShiftKeyDown()) {
                if (stack.getItem() instanceof MineFuze || stack.getItem() instanceof FuzeItem) {
                    if (seaMineBE.getFuze().isEmpty()) {
                        if (!level.isClientSide) {
                            seaMineBE.setFuze(stack.copyWithCount(1));
                            level.setBlock(pos, state.setValue(FUZED, true), 3);
                            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 1.0F, 1.0F);
                            if (!player.isCreative()) {
                                stack.shrink(1);
                            }
                        }
                        return ItemInteractionResult.sidedSuccess(level.isClientSide);
                    }
                }

                if (stack.is(Items.CHAIN)) {
                    if (seaMineBE.getChainStack().isEmpty()) {
                        if (!level.isClientSide) {
                            int count = Math.min(stack.getCount(), 64);
                            seaMineBE.setChainStack(stack.copyWithCount(count));
                            level.setBlock(pos, state.setValue(ANCHORED, true), 3);
                            level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
                            if (!player.isCreative()) {
                                stack.shrink(count);
                            }
                        }
                        return ItemInteractionResult.sidedSuccess(level.isClientSide);
                    }
                }

                if (level.isClientSide) {
                    openScreen(pos);
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof SeaMineBlockEntity seaMineBE) {
            if (player.isShiftKeyDown()) {
                if (isOnBottom(level, pos)) {
                    if (!level.isClientSide) {
                        seaMineBE.tryAssemble(player);
                    }
                    return InteractionResult.sidedSuccess(level.isClientSide);
                }

                if (!seaMineBE.getFuze().isEmpty() || !seaMineBE.getChainStack().isEmpty()) {
                    if (!level.isClientSide) {
                        ItemStack fuze = seaMineBE.getFuze();
                        if (!fuze.isEmpty()) {
                            if (!player.addItem(fuze)) {
                                Block.popResource(level, pos, fuze);
                            }
                            seaMineBE.setFuze(ItemStack.EMPTY);
                        }
                        ItemStack chain = seaMineBE.getChainStack();
                        if (!chain.isEmpty()) {
                            if (!player.addItem(chain)) {
                                Block.popResource(level, pos, chain);
                            }
                            seaMineBE.setChainStack(ItemStack.EMPTY);
                        }
                        seaMineBE.clearContents();
                        level.setBlock(pos, state.setValue(FUZED, false).setValue(ANCHORED, false), 3);
                        level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 1.0F, 1.0F);
                    }
                    return InteractionResult.sidedSuccess(level.isClientSide);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            } else {
                if (level.isClientSide) {
                    openScreen(pos);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }
        return InteractionResult.PASS;
    }

    @OnlyIn(Dist.CLIENT)
    private void openScreen(BlockPos pos) {
        net.minecraft.client.Minecraft.getInstance().setScreen(new SeaMineScreen(pos));
    }

    @Override
    public boolean canDropFromExplosion(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion) {
        return false;
    }
}
