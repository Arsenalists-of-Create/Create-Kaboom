package com.happysg.kaboom.registry;

import com.happysg.kaboom.CreateKaboom;
import com.happysg.kaboom.block.aerialBombs.baseTypes.AerialBombBlockEntity;
import com.happysg.kaboom.block.aerialBombs.baseTypes.FluidAerialBombBlockEntity;
import com.happysg.kaboom.block.missiles.parts.MissileFuelTankBlockEntity;
import com.happysg.kaboom.block.missiles.parts.MissileGuidanceBlockEntity;
import com.happysg.kaboom.block.missiles.parts.ThrusterBlockEntity;
import com.tterrag.registrate.util.entry.BlockEntityEntry;

import static com.happysg.kaboom.CreateKaboom.REGISTRATE;


public class ModBlockEntityTypes {

    public static final BlockEntityEntry<AerialBombBlockEntity> AERIAL_BOMB = REGISTRATE
            .blockEntity("aerial_bomb", AerialBombBlockEntity::new)
            .validBlocks(ModBlocks.HEAVY_AERIAL_BOMB)
            .register();
    public static final BlockEntityEntry<FluidAerialBombBlockEntity> FLUID_AERIAL_BOMB_BE =
            REGISTRATE.blockEntity("fluid_aerial_bomb", FluidAerialBombBlockEntity::new)
                    .validBlocks(ModBlocks.FLUID_AERIAL_BOMB)
                    .register();

    public static final BlockEntityEntry<ThrusterBlockEntity> MISSILE_THRUSTER_BE =
            REGISTRATE.blockEntity("missile_engine",ThrusterBlockEntity::new)
                    .validBlocks(ModBlocks.MISSILE_THRUSTER)
                    .register();
    public static final BlockEntityEntry<MissileFuelTankBlockEntity> FUEL_TANK_SMALL =
            REGISTRATE.blockEntity("small_tank", MissileFuelTankBlockEntity::new)
                    .validBlocks(
                            ModBlocks.MISSILE_FUEL,
                            ModBlocks.MISSILE_FUEL_SMALL
                    )
                    .register();
    public static final BlockEntityEntry<MissileGuidanceBlockEntity> GUIDANCE =
            REGISTRATE.blockEntity("guidance_be", MissileGuidanceBlockEntity::new)
                    .validBlocks(
                            ModBlocks.MISSILE_GUIDANCE
                    )
                    .register();

    public static void register() {
        CreateKaboom.getLogger().info("Registering block entity types!");
    }
}
