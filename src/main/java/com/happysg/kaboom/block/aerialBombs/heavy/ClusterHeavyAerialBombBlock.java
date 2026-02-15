package com.happysg.kaboom.block.aerialBombs.heavy;

import com.happysg.kaboom.block.aerialBombs.baseTypes.AerialBombBlock;

public class ClusterHeavyAerialBombBlock extends AerialBombBlock {
    public ClusterHeavyAerialBombBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.defaultBlockState().setValue(TYPE, 7));
    }
}
