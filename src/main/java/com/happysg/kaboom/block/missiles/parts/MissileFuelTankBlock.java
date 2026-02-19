package com.happysg.kaboom.block.missiles.parts;

import com.happysg.kaboom.block.missiles.assembly.IMissileComponent;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

public class MissileFuelTankBlock extends RotatedPillarBlock implements IMissileComponent {

    public MissileFuelTankBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(AXIS, Direction.Axis.Y));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(AXIS, context.getClickedFace().getAxis());
    }

    @Override
    public MissilePartType getPartType() {
        return MissilePartType.FUEL_TANK;
    }
}
