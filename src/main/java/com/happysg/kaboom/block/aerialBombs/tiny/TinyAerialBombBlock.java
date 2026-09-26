package com.happysg.kaboom.block.aerialBombs.tiny;

import com.happysg.kaboom.block.aerialBombs.baseTypes.AerialBombBlock;
import com.happysg.kaboom.block.aerialBombs.baseTypes.AerialBombProjectile;

public class TinyAerialBombBlock extends AerialBombBlock {
    public TinyAerialBombBlock(Properties properties) {
        super(properties, AerialBombProjectile.BombType.HE, 4);
    }
}
