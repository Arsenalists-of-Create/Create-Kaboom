package com.happysg.kaboom.block.aerialBombs.heavy;

import com.happysg.kaboom.block.aerialBombs.baseTypes.AerialBombBlock;

public class ApHeavyAerialBombBlock extends AerialBombBlock {
    public ApHeavyAerialBombBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.defaultBlockState().setValue(TYPE, 2));
    }
}
