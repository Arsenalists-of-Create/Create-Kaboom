package com.happysg.kaboom.registry;

import com.happysg.kaboom.CreateKaboom;
import com.happysg.kaboom.block.aerialBombs.baseTypes.AerialBombBlock;
import com.happysg.kaboom.block.aerialBombs.baseTypes.FluidAerialBombBlock;

import com.happysg.kaboom.block.aerialBombs.heavy.ApHeavyAerialBombBlock;
import com.happysg.kaboom.block.aerialBombs.heavy.ClusterHeavyAerialBombBlock;
import com.happysg.kaboom.block.aerialBombs.heavy.FragHeavyAerialBombBlock;
import com.happysg.kaboom.block.aerialBombs.heavy.HeavyAerialBombBlock;
import com.happysg.kaboom.block.aerialBombs.small.ApSmallAerialBombBlock;
import com.happysg.kaboom.block.aerialBombs.small.FragSmallAerialBombBlock;
import com.happysg.kaboom.block.aerialBombs.small.SmallAerialBombBlock;
import com.happysg.kaboom.block.aerialBombs.tiny.TinyAerialBombBlock;

import com.happysg.kaboom.block.missiles.parts.ThrusterBlock;
import com.happysg.kaboom.block.missiles.parts.MissileFuelTankBlock;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.simibubi.create.foundation.data.SharedProperties;
import com.tterrag.registrate.builders.BlockBuilder;
import com.tterrag.registrate.util.entry.BlockEntry;
import com.tterrag.registrate.util.nullness.NonNullFunction;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.client.model.generators.ConfiguredModel;

import static com.happysg.kaboom.CreateKaboom.REGISTRATE;

public class ModBlocks {

    public static final BlockEntry<HeavyAerialBombBlock> HEAVY_AERIAL_BOMB =
            bomb("heavy_aerial_bomb", HeavyAerialBombBlock::new).register();

    public static final BlockEntry<ApHeavyAerialBombBlock> AP_HEAVY_AERIAL_BOMB =
            bomb("ap_heavy_aerial_bomb", ApHeavyAerialBombBlock::new).register();

    public static final BlockEntry<ClusterHeavyAerialBombBlock> CLUSTER_HEAVY_AERIAL_BOMB =
            bomb("cluster_heavy_aerial_bomb", ClusterHeavyAerialBombBlock::new).register();



    public static final BlockEntry<FragHeavyAerialBombBlock> FRAG_HEAVY_AERIAL_BOMB =
            bomb("frag_heavy_aerial_bomb", FragHeavyAerialBombBlock::new).register();

    public static final BlockEntry<FluidAerialBombBlock> FLUID_AERIAL_BOMB =
            bomb("fluid_heavy_aerial_bomb", FluidAerialBombBlock::new).register();
    public static final BlockEntry<FluidAerialBombBlock> SMALL_FLUID_AERIAL_BOMB =
            bomb("fluid_aerial_bomb", FluidAerialBombBlock::new).register();

    public static final BlockEntry<SmallAerialBombBlock> SMALL_AERIAL_BOMB =
            bomb("aerial_bomb", SmallAerialBombBlock::new).register();

    public static final BlockEntry<ApSmallAerialBombBlock> AP_AERIAL_BOMB =
            bomb("ap_aerial_bomb", ApSmallAerialBombBlock::new).register();

    public static final BlockEntry<FragSmallAerialBombBlock> FRAG_AERIAL_BOMB =
            bomb("frag_aerial_bomb", FragSmallAerialBombBlock::new).register();

    public static final BlockEntry<TinyAerialBombBlock> TINY_AERIAL_BOMB =
            bomb("tiny_aerial_bomb", TinyAerialBombBlock::new).register();

    public static void register() {
        CreateKaboom.getLogger().info("Registering blocks!");
    }

    public static <T extends AerialBombBlock> BlockBuilder<T, CreateRegistrate> bomb(String name, NonNullFunction<BlockBehaviour.Properties, T> factory) {
        return REGISTRATE.block(name, factory)
                .initialProperties(SharedProperties::softMetal)
                .properties(BlockBehaviour.Properties::noOcclusion)
                .properties(p -> p.isRedstoneConductor((s, l, pos) -> false))
                .addLayer(() -> RenderType::cutoutMipped)
                .blockstate((c, p) ->
                        p.getVariantBuilder(c.get())
                                .forAllStates(state -> {
                                    String fuze = state.getValue(AerialBombBlock.FUZED) ? "fuzed_" : "";
                                    Direction facing = state.getValue(AerialBombBlock.FACING);

                                    return ConfiguredModel.builder()
                                            .modelFile(p.models().getExistingFile(
                                                    CreateKaboom.asResource("block/" + fuze + c.getName())
                                            ))
                                            .rotationY(((int) facing.toYRot() + 180) % 360)
                                            .build();
                                })
                )
                .simpleItem();
    }
    public static final BlockEntry<ThrusterBlock> MISSILE_THRUSTER = REGISTRATE.block("missile_solid_thruster", ThrusterBlock::new)
            .initialProperties(SharedProperties::softMetal)
            .blockstate((ctx, prov) -> prov.directionalBlock(ctx.getEntry(), prov.models()
                    .getExistingFile(ctx.getId()), 0))
            .simpleItem()
            .register();
    public static final BlockEntry<MissileFuelTankBlock> MISSILE_FUEL = REGISTRATE.block("missile_solid_fuel_tank", MissileFuelTankBlock::new)
            .initialProperties(SharedProperties::softMetal)
            .blockstate((ctx, prov) -> prov.axisBlock(ctx.getEntry()))
            .simpleItem()
            .register();
//    public static final BlockEntry<MissileDirectionalBlock> MISSILE_HEAD_FUZE = REGISTRATE.block("missile_head_fuze", MissileDirectionalBlock::new)
//            .initialProperties(SharedProperties::softMetal)
//            .blockstate((ctx, prov) -> prov.directionalBlock(ctx.getEntry(), prov.models()
//                    .getExistingFile(ctx.getId()), 0))
//            .simpleItem()
//            .register();
//    public static final BlockEntry<MissileDirectionalBlock> MISSILE_HIGH_EXPLOSIVE = REGISTRATE.block("missile_high_explosive_head", MissileDirectionalBlock::new)
//            .initialProperties(SharedProperties::softMetal)
//            .blockstate((ctx, prov) -> prov.directionalBlock(ctx.getEntry(), prov.models()
//                    .getExistingFile(ctx.getId()), 0))
//            .simpleItem()
//            .register();


}
