package com.happysg.kaboom.block.missiles.parts;

import com.happysg.kaboom.block.missiles.IMissileComponent;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class MissileEngineBlock extends Block implements IMissileComponent {

    public MissileEngineBlock(Properties pProperties) {
        super(pProperties);

    }

    public MissileComponentType missileComponentType(BlockState state) {
        return MissileComponentType.ENGINE;
    }
}
