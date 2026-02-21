package com.happysg.kaboom.block.missiles.parts;

import com.happysg.kaboom.block.missiles.assembly.IMissileComponent;
import com.happysg.kaboom.registry.ModBlockEntityTypes;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

public class MissileGuidanceBlock extends DirectionalBlock implements IMissileComponent, IBE<MissileGuidanceBlockEntity> {
    public MissileGuidanceBlock(Properties pProperties) {
        super(pProperties);
        registerDefaultState(super.defaultBlockState()
                .setValue(FACING, Direction.UP));
    }

    @Override
    public MissilePartType getPartType() {
        return MissilePartType.GUIDANCE;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }


    @Override
    public Class<MissileGuidanceBlockEntity> getBlockEntityClass() {
        return MissileGuidanceBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends MissileGuidanceBlockEntity> getBlockEntityType() {
        return ModBlockEntityTypes.GUIDANCE.get();
    }
}

