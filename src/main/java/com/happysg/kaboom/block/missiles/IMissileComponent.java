package com.happysg.kaboom.block.missiles;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.state.BlockState;
import rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile;

public interface IMissileComponent {

     enum MissileComponentType {
        FUEL,
        ENGINE,
        GUIDANCE
    }
    interface IThrusterBlock {}

    interface IFuelTankBlock {}

     interface IGuidanceUnitBlock {}

    /**
     * “Warhead” / payload that can spawn a CBC projectile (or represent one).
     * */
     interface IPayloadBlock {
        EntityType<? extends AbstractCannonProjectile> projectileType(BlockState state);
    }
}
