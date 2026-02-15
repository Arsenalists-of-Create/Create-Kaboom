package com.happysg.kaboom.registry;

import com.happysg.kaboom.CreateKaboom;
import com.happysg.kaboom.block.aerialBombs.baseTypes.AerialBombBlockEntity;
import com.happysg.kaboom.block.aerialBombs.baseTypes.FluidAerialBombBlockEntity;
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


    public static void register() {
        CreateKaboom.getLogger().info("Registering block entity types!");
    }
}
