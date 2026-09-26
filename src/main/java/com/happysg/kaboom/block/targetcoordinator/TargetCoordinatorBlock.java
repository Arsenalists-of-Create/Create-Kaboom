package com.happysg.kaboom.block.targetcoordinator;


import com.happysg.kaboom.registry.ModBlockEntityTypes;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.block.WrenchableDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class TargetCoordinatorBlock extends WrenchableDirectionalBlock implements IBE<TargetCoordinatorBlockEntity> {

    public TargetCoordinatorBlock(Properties properties) {
        super(properties);
    }

    @Override
    public Class<TargetCoordinatorBlockEntity> getBlockEntityClass() {
        return TargetCoordinatorBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends TargetCoordinatorBlockEntity> getBlockEntityType() {
        return ModBlockEntityTypes.TARGET_COORDINATOR_BE.get();
    }
}
