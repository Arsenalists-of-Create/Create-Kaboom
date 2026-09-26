package com.happysg.kaboom.block.aerialBombs.small;

import com.happysg.kaboom.block.aerialBombs.baseTypes.AerialBombBlock;
import com.happysg.kaboom.block.aerialBombs.baseTypes.AerialBombProjectile;

public class SmallAerialBombBlock extends AerialBombBlock {
    public SmallAerialBombBlock(Properties properties) {
        this(properties, AerialBombProjectile.BombType.HE);
    }

    protected SmallAerialBombBlock(Properties properties, AerialBombProjectile.BombType bombType) {
        super(properties, bombType, 2);
    }
}
