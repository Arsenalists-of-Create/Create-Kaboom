package com.happysg.kaboom.block.missiles.parts.thrust;

import com.happysg.kaboom.block.missiles.assembly.IMissileComponent;
import com.happysg.kaboom.block.missiles.assembly.MissileSize;
import com.happysg.kaboom.block.missiles.parts.MissilePartShapes;
import com.happysg.kaboom.block.missiles.parts.HugeMissileReservations;
import com.happysg.kaboom.registry.ModBlockEntityTypes;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ThrusterBlock extends DirectionalBlock implements IMissileComponent, EntityBlock {
    private final MissileSize missileSize;

    public ThrusterBlock(Properties pProperties, MissileSize missileSize) {
        super(pProperties);
        this.missileSize = missileSize;
        registerDefaultState(super.defaultBlockState()
                .setValue(FACING, Direction.UP));

    }
    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return simpleCodec(properties -> new ThrusterBlock(properties, missileSize));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        Direction.Axis axis = state.getValue(FACING).getAxis();
        return switch (missileSize) {
            case SMALL -> MissilePartShapes.small(axis);
            case LARGE -> MissilePartShapes.FULL;
            case HUGE -> MissilePartShapes.hugeBody(axis);
        };
    }

    @Override
    public VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return getShape(s, l, p, c);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState,
                           boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (missileSize == MissileSize.HUGE) {
            HugeMissileReservations.reconcileOwner(level, pos, state);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState,
                            boolean movedByPiston) {
        if (missileSize == MissileSize.HUGE && !state.is(newState.getBlock())) {
            HugeMissileReservations.removeForOwner(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }

    @Override
    public MissilePartType getPartType() {
        return MissilePartType.THRUSTER;
    }

    @Override
    public MissileSize getMissileSize() {
        return missileSize;
    }

    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ThrusterBlockEntity(ModBlockEntityTypes.MISSILE_THRUSTER_BE.get(),pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return (lvl, p, st, be) -> {
            if (be instanceof ThrusterBlockEntity thruster) thruster.tick();
        };
    }
}
