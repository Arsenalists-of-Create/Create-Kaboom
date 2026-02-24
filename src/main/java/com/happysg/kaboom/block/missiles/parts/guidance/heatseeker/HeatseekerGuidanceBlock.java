package com.happysg.kaboom.block.missiles.parts.guidance.heatseeker;

import com.happysg.kaboom.block.missiles.assembly.IMissileComponent;
import com.happysg.kaboom.block.missiles.parts.guidance.IGuidanceBlock;
import com.happysg.kaboom.block.missiles.util.MissileTargetSpec;
import com.happysg.kaboom.registry.ModBlockEntityTypes;
import com.happysg.kaboom.registry.ModBlocks;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class HeatseekerGuidanceBlock extends DirectionalBlock implements IBE<HeatseekerGuidanceBlockEntity>, IMissileComponent, IGuidanceBlock {
    public HeatseekerGuidanceBlock(Properties pProperties) {
        super(pProperties);
        registerDefaultState(super.defaultBlockState()
                .setValue(FACING, Direction.UP));
    }
    private static final VoxelShape SMALL = Block.box(3, 0, 3, 13, 16, 13);

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SMALL;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return SMALL;
    }
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }

    @Override
    public IMissileComponent.MissilePartType getPartType() {
        return IMissileComponent.MissilePartType.THRUSTER;
    }

    @Override
    public Class<HeatseekerGuidanceBlockEntity> getBlockEntityClass() {
        return HeatseekerGuidanceBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends HeatseekerGuidanceBlockEntity> getBlockEntityType() {
        return ModBlockEntityTypes.HEATSEEKER_GUIDANCE.get();
    }

}


