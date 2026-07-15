package com.happysg.kaboom.block.missiles.parts.guidance.radar;

import com.happysg.kaboom.block.missiles.assembly.IMissileComponent;
import com.happysg.kaboom.block.missiles.parts.MissilePartShapes;
import com.happysg.kaboom.block.missiles.parts.guidance.IGuidanceBlock;
import com.happysg.kaboom.registry.ModBlockEntityTypes;
import com.happysg.kaboom.registry.ModBlocks;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class RadarGuidanceBlock extends RotatedPillarBlock implements IBE<RadarGuidanceBlockEntity>, IGuidanceBlock, IMissileComponent {
    public RadarGuidanceBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(AXIS, Direction.Axis.Y));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return this == ModBlocks.RADAR_GUIDANCE_SMALL.get()
                ? MissilePartShapes.small(state.getValue(AXIS))
                : MissilePartShapes.FULL;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return getShape(state, level, pos, ctx);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(AXIS, context.getNearestLookingDirection().getAxis());
    }

    @Override
    public Class<RadarGuidanceBlockEntity> getBlockEntityClass() {
        return RadarGuidanceBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends RadarGuidanceBlockEntity> getBlockEntityType() {
        return ModBlockEntityTypes.RADAR_GUIDANCE.get();
    }

    @Override
    public MissilePartType getPartType() {
        return MissilePartType.GUIDANCE;
    }
}
