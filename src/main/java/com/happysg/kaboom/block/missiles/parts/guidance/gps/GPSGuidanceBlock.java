package com.happysg.kaboom.block.missiles.parts.guidance.gps;

import com.happysg.kaboom.block.missiles.assembly.IMissileComponent;
import com.happysg.kaboom.block.missiles.parts.guidance.IGuidanceBlock;
import com.happysg.kaboom.block.missiles.parts.guidance.MissileTargetSpec;
import com.happysg.kaboom.registry.ModBlockEntityTypes;
import com.happysg.kaboom.registry.ModBlocks;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class GPSGuidanceBlock extends RotatedPillarBlock implements IBE<GPSGuidanceBlockEntity>, IGuidanceBlock, IMissileComponent {
    public GPSGuidanceBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(AXIS, Direction.Axis.Y));
    }

    private static final VoxelShape SMALL = Block.box(3, 0, 3, 13, 16, 13);
    private static final VoxelShape FULL  = Block.box(0, 0, 0, 16, 16, 16);

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return this == ModBlocks.GPS_GUIDANCE_SMALL.get() ? SMALL : FULL;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return this == ModBlocks.GPS_GUIDANCE_SMALL.get() ? SMALL : FULL;
    }
    @Override
    public void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);

    }
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(AXIS, context.getClickedFace().getAxis());
    }

    @Override
    public MissilePartType getPartType() {
        return MissilePartType.GUIDANCE;
    }

    @Override
    public Class<GPSGuidanceBlockEntity> getBlockEntityClass() {
        return GPSGuidanceBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends GPSGuidanceBlockEntity> getBlockEntityType() {
        return ModBlockEntityTypes.GPS_GUIDANCE.get();
    }

    @Override
    public MissileTargetSpec exportTargetSpec() {
        return null;
    }
}
