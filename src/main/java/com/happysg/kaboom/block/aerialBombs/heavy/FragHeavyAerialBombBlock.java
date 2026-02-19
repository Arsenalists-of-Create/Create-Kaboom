package com.happysg.kaboom.block.aerialBombs.heavy;

import com.happysg.kaboom.block.aerialBombs.baseTypes.AerialBombBlock;
import com.happysg.kaboom.block.aerialBombs.baseTypes.AerialBombProjectile;

public class FragHeavyAerialBombBlock extends AerialBombBlock {
    public FragHeavyAerialBombBlock(Properties properties) {
        super(properties, AerialBombProjectile.BombType.FRAG,1);
    }
}
