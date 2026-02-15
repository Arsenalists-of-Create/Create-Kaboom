package com.happysg.kaboom.block.aerialBombs.heavy;

import com.happysg.kaboom.block.aerialBombs.baseTypes.AerialBombBlock;

public class HeavyAerialBombBlock extends AerialBombBlock {
    public HeavyAerialBombBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.defaultBlockState().setValue(TYPE, 1));
    }
}
