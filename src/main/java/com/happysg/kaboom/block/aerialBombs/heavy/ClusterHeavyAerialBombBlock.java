package com.happysg.kaboom.block.aerialBombs.heavy;

import com.happysg.kaboom.block.aerialBombs.baseTypes.AerialBombBlock;
import com.happysg.kaboom.block.aerialBombs.baseTypes.AerialBombProjectile;

public class ClusterHeavyAerialBombBlock extends AerialBombBlock {
    public ClusterHeavyAerialBombBlock(Properties properties) {
        super(properties, AerialBombProjectile.BombType.CLUSTER,1);

    }
}
