package com.happysg.kaboom.block.aerialBombs.heavy;

import com.happysg.kaboom.block.aerialBombs.baseTypes.AerialBombBlock;
import com.happysg.kaboom.block.aerialBombs.baseTypes.AerialBombProjectile;

public class HeavyAerialBombBlock extends AerialBombBlock {
    public HeavyAerialBombBlock(Properties properties) {
        super(properties, AerialBombProjectile.BombType.HE,1);
    }
}
