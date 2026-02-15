package com.happysg.kaboom.block.aerialBombs.heavy;

import com.happysg.kaboom.block.aerialBombs.baseTypes.AerialBombBlock;

public class FragHeavyAerialBombBlock extends AerialBombBlock {
    public FragHeavyAerialBombBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.defaultBlockState().setValue(TYPE, 3));
    }
}
