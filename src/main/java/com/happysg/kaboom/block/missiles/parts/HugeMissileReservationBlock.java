package com.happysg.kaboom.block.missiles.parts;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Invisible, non-replaceable cells owned by a Huge missile part. The source part provides the
 * physical collision; these cells only make vanilla placement prediction reject occupied space.
 */
public class HugeMissileReservationBlock extends Block implements SimpleWaterloggedBlock {
    private static final int OWNER_OFFSET_BIAS = 1;
    public static final MapCodec<HugeMissileReservationBlock> CODEC =
            simpleCodec(HugeMissileReservationBlock::new);
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final IntegerProperty OWNER_X = IntegerProperty.create("owner_x", 0, 2);
    public static final IntegerProperty OWNER_Y = IntegerProperty.create("owner_y", 0, 2);
    public static final IntegerProperty OWNER_Z = IntegerProperty.create("owner_z", 0, 2);

    public HugeMissileReservationBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(WATERLOGGED, false)
                .setValue(OWNER_X, OWNER_OFFSET_BIAS)
                .setValue(OWNER_Y, OWNER_OFFSET_BIAS)
                .setValue(OWNER_Z, OWNER_OFFSET_BIAS));
    }

    @Override
    public MapCodec<HugeMissileReservationBlock> codec() {
        return CODEC;
    }

    public BlockState withOwner(BlockState state, BlockPos reservationPos, BlockPos ownerPos) {
        BlockPos offset = ownerPos.subtract(reservationPos);
        return state
                .setValue(OWNER_X, offset.getX() + OWNER_OFFSET_BIAS)
                .setValue(OWNER_Y, offset.getY() + OWNER_OFFSET_BIAS)
                .setValue(OWNER_Z, offset.getZ() + OWNER_OFFSET_BIAS);
    }

    public BlockPos getOwnerPos(BlockState state, BlockPos reservationPos) {
        return reservationPos.offset(
                state.getValue(OWNER_X) - OWNER_OFFSET_BIAS,
                state.getValue(OWNER_Y) - OWNER_OFFSET_BIAS,
                state.getValue(OWNER_Z) - OWNER_OFFSET_BIAS);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getFluidState().isEmpty();
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return HugeMissileReservations.isRequiredOrOwnerUnloaded(level, pos, state);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (!HugeMissileReservations.isRequiredOrOwnerUnloaded(level, pos, state)) {
            return state.getFluidState().createLegacyBlock();
        }
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED)
                ? Fluids.WATER.getSource(false)
                : super.getFluidState(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WATERLOGGED, OWNER_X, OWNER_Y, OWNER_Z);
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level,
                                       BlockPos pos, Player player) {
        return ItemStack.EMPTY;
    }
}
