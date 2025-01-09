package com.happysg.kaboom.registry;

import com.happysg.kaboom.CreateKaboom;
import com.happysg.kaboom.block.aerialBombs.AerialBombBlock;
import com.simibubi.create.foundation.data.AssetLookup;
import com.simibubi.create.foundation.data.SharedProperties;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.client.model.generators.ConfiguredModel;

import static com.happysg.kaboom.CreateKaboom.REGISTRATE;

public class ModBlocks {
    public static final BlockEntry<AerialBombBlock> HEAVY_AERIAL_BOMB = bomb("heavy_aerial_bomb");


    public static void register() {
        CreateKaboom.getLogger().info("Registering blocks!");
    }

    public static BlockEntry<AerialBombBlock> bomb(String name) {
        return REGISTRATE.block(name, AerialBombBlock::new)
                .initialProperties(SharedProperties::softMetal)
                .properties(BlockBehaviour.Properties::noOcclusion)
                .properties(properties -> properties.isRedstoneConductor((pState, pLevel, pPos) -> false))
                .addLayer(() -> RenderType::cutoutMipped)
                .blockstate((c, p) ->
                        p.getVariantBuilder(c.get())
                                .forAllStates(state -> {
                                    String fuze = state.getValue(AerialBombBlock.FUZED) ? "fuzed_" : "";
                                    Direction facing = state.getValue(AerialBombBlock.FACING);
                                    return ConfiguredModel.builder()
                                            .modelFile(p.models()
                                                    .getExistingFile(CreateKaboom.asResource("block/" + fuze + c.getName())
                                                    )).rotationY(((int) facing.toYRot() + 180) % 360)
                                            .build();

                                }))
                .simpleItem()
                .register();
    }
}
