package com.happysg.kaboom.block.missiles.parts.thrust;

import com.happysg.kaboom.block.missiles.assembly.IMissileComponent;
import com.happysg.kaboom.registry.ModBlockEntityTypes;
import com.happysg.kaboom.registry.ModBlocks;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import org.slf4j.Logger;

public class ThrusterBlock extends DirectionalBlock implements IMissileComponent, EntityBlock {
    private static final Logger LOGGER = LogUtils.getLogger();
    public ThrusterBlock(Properties pProperties) {
        super(pProperties);
        registerDefaultState(super.defaultBlockState()
                .setValue(FACING, Direction.UP));

    }
    private static final VoxelShape SMALL = Block.box(3, 0, 3, 13, 16, 13);
    private static final VoxelShape FULL  = Block.box(0, 0, 0, 16, 16, 16);

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return this == ModBlocks.MISSILE_THRUSTER_SMALL.get() ? SMALL : FULL;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return this == ModBlocks.MISSILE_THRUSTER_SMALL.get() ? SMALL : FULL;
    }

    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, neighborBlock, fromPos, isMoving);

        if (level.isClientSide) return;
        LOGGER.warn("[THRUSTER] neighborChanged @ {} poweredNow={}", pos, level.hasNeighborSignal(pos));

        BlockEntity be = level.getBlockEntity(pos);
        LOGGER.warn("[THRUSTER] BE @ {} is {}", pos, (be == null ? "null" : be.getClass().getName()));

        if (be instanceof ThrusterBlockEntity thrusterBE) {
            thrusterBE.onRedstoneUpdated();
        }
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

    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ThrusterBlockEntity(ModBlockEntityTypes.MISSILE_THRUSTER_BE.get(),pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : (lvl, p, st, be) -> {
            if (be instanceof ThrusterBlockEntity thruster) thruster.tick();
        };
    }
}
