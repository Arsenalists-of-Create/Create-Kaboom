package com.happysg.kaboom.block.aerialBombs.heavy;

import com.happysg.kaboom.block.aerialBombs.baseTypes.AerialBombBlock;
import com.happysg.kaboom.block.aerialBombs.baseTypes.AerialBombProjectile;

public class ApHeavyAerialBombBlock extends AerialBombBlock {
    public ApHeavyAerialBombBlock(Properties properties) {
        super(properties, AerialBombProjectile.BombType.AP,1);
    }
}
