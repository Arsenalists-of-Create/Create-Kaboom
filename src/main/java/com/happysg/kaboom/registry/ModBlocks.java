package com.happysg.kaboom.registry;

import com.happysg.kaboom.CreateKaboom;
import com.happysg.kaboom.block.aerialBombs.baseTypes.AerialBombBlock;
import com.happysg.kaboom.block.aerialBombs.baseTypes.AerialBombBlockItem;
import com.happysg.kaboom.block.aerialBombs.baseTypes.AerialBombProjectile;
import com.happysg.kaboom.block.aerialBombs.baseTypes.FluidAerialBombBlock;

import com.happysg.kaboom.block.aerialBombs.heavy.ApHeavyAerialBombBlock;
import com.happysg.kaboom.block.aerialBombs.heavy.ClusterHeavyAerialBombBlock;
import com.happysg.kaboom.block.aerialBombs.heavy.FragHeavyAerialBombBlock;
import com.happysg.kaboom.block.aerialBombs.heavy.HeavyAerialBombBlock;
import com.happysg.kaboom.block.aerialBombs.small.ApSmallAerialBombBlock;
import com.happysg.kaboom.block.aerialBombs.small.FragSmallAerialBombBlock;
import com.happysg.kaboom.block.aerialBombs.small.FluidSmallAerialBombBlock;
import com.happysg.kaboom.block.aerialBombs.small.SmallAerialBombBlock;
import com.happysg.kaboom.block.aerialBombs.tiny.TinyAerialBombBlock;

import com.happysg.kaboom.block.missiles.assembly.MissileSize;
import com.happysg.kaboom.block.missiles.parts.HugeMissileReservationBlock;
import com.happysg.kaboom.block.missiles.parts.guidance.arad.ARADGuidanceBlock;
import com.happysg.kaboom.block.missiles.parts.guidance.command.CommandGuidanceBlock;
import com.happysg.kaboom.block.missiles.parts.guidance.radar.RadarGuidanceBlock;
import com.happysg.kaboom.block.missiles.parts.thrust.ThrusterBlock;
import com.happysg.kaboom.block.missiles.parts.fuel.MissileFuelTankBlock;
import com.happysg.kaboom.block.missiles.parts.guidance.gps.GPSGuidanceBlock;
import com.happysg.kaboom.block.missiles.parts.warhead.AbstractMissileWarhead;
import com.happysg.kaboom.block.rocketpod.RocketPod;
import com.happysg.kaboom.block.targetcoordinator.TargetCoordinatorBlock;
import com.happysg.radar.compat.Mods;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.simibubi.create.foundation.data.SharedProperties;
import com.tterrag.registrate.Registrate;
import com.tterrag.registrate.builders.BlockBuilder;
import com.tterrag.registrate.util.entry.BlockEntry;
import com.tterrag.registrate.util.nullness.NonNullFunction;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;
import net.neoforged.neoforge.client.model.generators.ModelFile;

import static com.happysg.kaboom.CreateKaboom.REGISTRATE;

public class ModBlocks {

    public static final BlockEntry<HugeMissileReservationBlock> HUGE_MISSILE_RESERVATION =
            REGISTRATE.block("huge_missile_reservation", HugeMissileReservationBlock::new)
                    .initialProperties(() -> Blocks.BARRIER)
                    .properties(properties -> properties
                            .noCollission()
                            .noOcclusion()
                            .strength(-1.0F, 3_600_000.0F)
                            .pushReaction(PushReaction.BLOCK)
                            .noLootTable()
                            .isRedstoneConductor((state, level, pos) -> false)
                            .isSuffocating((state, level, pos) -> false)
                            .isViewBlocking((state, level, pos) -> false))
                    .blockstate((context, provider) -> {
                    })
                    .register();

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
    public static final BlockEntry<FluidSmallAerialBombBlock> SMALL_FLUID_AERIAL_BOMB =
            bomb("fluid_aerial_bomb", FluidSmallAerialBombBlock::new).register();

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
                .blockstate((c, p) ->
                        p.getVariantBuilder(c.get())
                                .forAllStates(state -> {
                                    Direction facing = state.getValue(AerialBombBlock.FACING);
                                    int count = state.getValue(AerialBombBlock.COUNT);

                                    return ConfiguredModel.builder()
                                            .modelFile(p.models().getExistingFile(
                                                    CreateKaboom.asResource("block/" + bombModelPath(c.getName(), "", count))
                                            ))
                                            .rotationY(((int) facing.toYRot() + 180) % 360)
                                            .build();
                                })
                )
                .item(AerialBombBlockItem::new)
                .model((ctx, p) -> p.withExistingParent(ctx.getName(),
                        CreateKaboom.asResource("block/" + bombModelPath(name, "", 1))))
                .build();
    }

    private static String bombModelPath(String name, String fuze, int count) {
        if (name.equals("tiny_aerial_bomb")) {
            int suffixCount = Mth.clamp(count, 1, 9);
            return "tiny_bomb/" + fuze + name + (suffixCount <= 1 ? "" : "_" + suffixCount);
        }

        if (name.contains("heavy_aerial_bomb"))
            return "heavy_bomb/" + fuze + name;

        int suffixCount = Mth.clamp(count, 1, 4);
        return "aerial_bomb/" + fuze + name + (suffixCount <= 1 ? "" : "_" + suffixCount);
    }

    private static ConfiguredModel[] rocketPodModel(ModelFile model, Direction facing) {
        int rotationX = facing == Direction.DOWN ? 180 : facing.getAxis().isHorizontal() ? 90 : 0;
        int rotationY = facing.getAxis().isVertical() ? 0 : (int) facing.toYRot();
        return ConfiguredModel.builder()
                .modelFile(model)
                .rotationX(rotationX)
                .rotationY(rotationY)
                .build();
    }

    public static final BlockEntry<ThrusterBlock> MISSILE_THRUSTER = REGISTRATE.block("missile_liquid_thruster_large",
                    properties -> new ThrusterBlock(properties, MissileSize.LARGE))
            .initialProperties(SharedProperties::softMetal)
            .blockstate((ctx, prov) -> prov.directionalBlock(ctx.getEntry(), prov.models()
                    .getExistingFile(CreateKaboom.asResource("block/missile/medium_solid_fuel_thruster")), 180))
            .item()
            .model((ctx, p) -> p.withExistingParent(ctx.getName(),
                    CreateKaboom.asResource("block/missile/medium_solid_fuel_thruster")))
            .build()
            .register();
    public static final BlockEntry<ThrusterBlock> MISSILE_THRUSTER_SMALL = REGISTRATE.block("missile_liquid_thruster_small",
                    properties -> new ThrusterBlock(properties, MissileSize.SMALL))
            .initialProperties(SharedProperties::softMetal)
            .properties(BlockBehaviour.Properties::noOcclusion)
            .blockstate((ctx, prov) -> prov.directionalBlock(ctx.getEntry(), prov.models()
                    .getExistingFile(CreateKaboom.asResource("block/missile/small_liquid_fuel_thruster")), 180))
            .item()
            .model((ctx, p) -> p.withExistingParent(ctx.getName(),
                    CreateKaboom.asResource("block/missile/small_liquid_fuel_thruster")))
            .build()
            .register();
    public static final BlockEntry<ThrusterBlock> MISSILE_THRUSTER_HUGE = REGISTRATE.block("missile_liquid_thruster_huge",
                    properties -> new ThrusterBlock(properties, MissileSize.HUGE))
            .initialProperties(SharedProperties::softMetal)
            .properties(BlockBehaviour.Properties::noOcclusion)
            .blockstate((ctx, prov) -> prov.directionalBlock(ctx.getEntry(), prov.models()
                    .getExistingFile(CreateKaboom.asResource("block/missile/huge_solid_fuel_thruster")), 180))
            .item()
            .model((ctx, p) -> p.withExistingParent(ctx.getName(),
                    CreateKaboom.asResource("block/missile/huge_solid_fuel_thruster")))
            .build()
            .register();

    public static final BlockEntry<MissileFuelTankBlock> MISSILE_FUEL_SMALL =
            REGISTRATE.block("missile_liquid_fuel_small",
                            p -> new MissileFuelTankBlock(p, 4000, MissileSize.SMALL))
                    .properties(BlockBehaviour.Properties::noOcclusion)
                    .initialProperties(SharedProperties::softMetal)
                    .blockstate((ctx, prov) -> {
                        var model = prov.models().getExistingFile(CreateKaboom.asResource("block/missile/small_liquid_fuel_tank"));
                        prov.axisBlock(ctx.getEntry(), model, model);
                    })
                    .item()
                    .model((ctx, p) -> p.withExistingParent(ctx.getName(),
                            CreateKaboom.asResource("block/missile/small_liquid_fuel_tank")))
                    .build()
                    .register();

    public static final BlockEntry<MissileFuelTankBlock> MISSILE_FUEL =
            REGISTRATE.block("missile_liquid_fuel_large",
                            p -> new MissileFuelTankBlock(p, 16000, MissileSize.LARGE))
                    .initialProperties(SharedProperties::softMetal)
                    .blockstate((ctx, prov) -> {
                        var model = prov.models().getExistingFile(CreateKaboom.asResource("block/missile/medium_solid_fuel_tank"));
                        prov.axisBlock(ctx.getEntry(), model, model);
                    })
                    .item()
                    .model((ctx, p) -> p.withExistingParent(ctx.getName(),
                            CreateKaboom.asResource("block/missile/medium_solid_fuel_tank")))
                    .build()
                    .register();
    public static final BlockEntry<MissileFuelTankBlock>MISSILE_FUEL_HUGE =
            REGISTRATE.block("missile_liquid_fuel_huge",
                            p -> new MissileFuelTankBlock(p, 32000, MissileSize.HUGE))
                    .initialProperties(SharedProperties::softMetal)
                    .blockstate((ctx, prov) -> {
                        var model = prov.models().getExistingFile(CreateKaboom.asResource("block/missile/huge_solid_fuel_tank"));
                        prov.axisBlock(ctx.getEntry(), model, model);
                    })
                    .item()
                    .model((ctx, p) -> p.withExistingParent(ctx.getName(),
                            CreateKaboom.asResource("block/missile/huge_solid_fuel_tank")))
                    .build()
                    .register();
    public static final BlockEntry<GPSGuidanceBlock> GPS_GUIDANCE_SMALL =
            REGISTRATE.block("gps_guidance_small", properties -> new GPSGuidanceBlock(properties, MissileSize.SMALL))
                    .properties(BlockBehaviour.Properties::noOcclusion)
                    .initialProperties(SharedProperties::softMetal)
                    .blockstate((ctx, prov) -> {
                        var model = prov.models().getExistingFile(CreateKaboom.asResource("block/missile/small_inertial_guidance"));
                        prov.axisBlock(ctx.getEntry(), model, model);
                    })
                    .item()
                    .model((ctx, p) -> p.withExistingParent(ctx.getName(),
                            CreateKaboom.asResource("block/missile/small_inertial_guidance")))
                    .build()
                    .register();
    public static final BlockEntry<GPSGuidanceBlock> GPS_GUIDANCE_LARGE =
            REGISTRATE.block("gps_guidance_large", properties -> new GPSGuidanceBlock(properties, MissileSize.LARGE))
                    .properties(BlockBehaviour.Properties::noOcclusion)
                    .initialProperties(SharedProperties::softMetal)
                    .blockstate((ctx, prov) -> {
                        var model = prov.models().getExistingFile(CreateKaboom.asResource("block/missile/medium_inertial_guidance"));
                        prov.axisBlock(ctx.getEntry(), model, model);
                    })
                    .item()
                    .model((ctx, p) -> p.withExistingParent(ctx.getName(),
                            CreateKaboom.asResource("block/missile/medium_inertial_guidance")))
                    .build()
                    .register();
    public static final BlockEntry<GPSGuidanceBlock> GPS_GUIDANCE_HUGE =
            REGISTRATE.block("gps_guidance_huge", properties -> new GPSGuidanceBlock(properties, MissileSize.HUGE))
                    .properties(BlockBehaviour.Properties::noOcclusion)
                    .initialProperties(SharedProperties::softMetal)
                    .blockstate((ctx, prov) -> {
                        var model = prov.models().getExistingFile(CreateKaboom.asResource("block/missile/huge_inertial_guidance"));
                        prov.axisBlock(ctx.getEntry(), model, model);
                    })
                    .item()
                    .model((ctx, p) -> p.withExistingParent(ctx.getName(),
                            CreateKaboom.asResource("block/missile/huge_inertial_guidance")))
                    .build()
                    .register();

    public static final BlockEntry<CommandGuidanceBlock> COMMAND_GUIDANCE_SMALL =
            REGISTRATE.block("command_guidance_small", properties -> new CommandGuidanceBlock(properties, MissileSize.SMALL))
                    .properties(BlockBehaviour.Properties::noOcclusion)
                    .initialProperties(SharedProperties::softMetal)
                    .blockstate((ctx, prov) -> {
                        var model = prov.models().getExistingFile(CreateKaboom.asResource("block/missile/small_command_guidance"));
                        prov.axisBlock(ctx.getEntry(), model, model);
                    })
                    .item()
                    .model((ctx, p) -> p.withExistingParent(ctx.getName(),
                            CreateKaboom.asResource("block/missile/small_command_guidance")))
                    .build()
                    .register();
    public static final BlockEntry<CommandGuidanceBlock> COMMAND_GUIDANCE_LARGE =
            REGISTRATE.block("command_guidance_large", properties -> new CommandGuidanceBlock(properties, MissileSize.LARGE))
                    .properties(BlockBehaviour.Properties::noOcclusion)
                    .initialProperties(SharedProperties::softMetal)
                    .blockstate((ctx, prov) -> {
                        var model = prov.models().getExistingFile(CreateKaboom.asResource("block/missile/medium_command_guidance"));
                        prov.axisBlock(ctx.getEntry(), model, model);
                    })
                    .item()
                    .model((ctx, p) -> p.withExistingParent(ctx.getName(),
                            CreateKaboom.asResource("block/missile/medium_command_guidance")))
                    .build()
                    .register();
    public static final BlockEntry<CommandGuidanceBlock> COMMAND_GUIDANCE_HUGE =
            REGISTRATE.block("command_guidance_huge", properties -> new CommandGuidanceBlock(properties, MissileSize.HUGE))
                    .properties(BlockBehaviour.Properties::noOcclusion)
                    .initialProperties(SharedProperties::softMetal)
                    .lang("Huge Command Guidance Unit")
                    .blockstate((ctx, prov) -> {
                        var model = prov.models().getExistingFile(CreateKaboom.asResource("block/missile/huge_command_guidance"));
                        prov.axisBlock(ctx.getEntry(), model, model);
                    })
                    .item()
                    .model((ctx, p) -> p.withExistingParent(ctx.getName(),
                            CreateKaboom.asResource("block/missile/huge_command_guidance")))
                    .build()
                    .register();
    public static final BlockEntry<ARADGuidanceBlock> ARAD_GUIDANCE_SMALL =
            REGISTRATE.block("arad_guidance_small", properties -> new ARADGuidanceBlock(properties, MissileSize.SMALL))
                    .properties(BlockBehaviour.Properties::noOcclusion)
                    .initialProperties(SharedProperties::softMetal)
                    .lang("Small ARAD Guidance Unit")
                    .blockstate((ctx, prov) -> {
                        var model = prov.models().getExistingFile(CreateKaboom.asResource("block/missile/small_arad_guidance"));
                        prov.axisBlock(ctx.getEntry(), model, model);
                    })
                    .item()
                    .model((ctx, p) -> p.withExistingParent(ctx.getName(),
                            CreateKaboom.asResource("block/missile/small_arad_guidance")))
                    .build()
                    .register();
    public static final BlockEntry<ARADGuidanceBlock> ARAD_GUIDANCE_LARGE =
            REGISTRATE.block("arad_guidance_large", properties -> new ARADGuidanceBlock(properties, MissileSize.LARGE))
                    .properties(BlockBehaviour.Properties::noOcclusion)
                    .initialProperties(SharedProperties::softMetal)
                    .lang("Large ARAD Guidance Unit")
                    .blockstate((ctx, prov) -> {
                        var model = prov.models().getExistingFile(CreateKaboom.asResource("block/missile/medium_arad_guidance"));
                        prov.axisBlock(ctx.getEntry(), model, model);
                    })
                    .item()
                    .model((ctx, p) -> p.withExistingParent(ctx.getName(),
                            CreateKaboom.asResource("block/missile/medium_arad_guidance")))
                    .build()
                    .register();
    public static final BlockEntry<RadarGuidanceBlock> RADAR_GUIDANCE_SMALL =
            REGISTRATE.block("radar_guidance_small", properties -> new RadarGuidanceBlock(properties, MissileSize.SMALL))
                    .properties(BlockBehaviour.Properties::noOcclusion)
                    .initialProperties(SharedProperties::softMetal)
                    .simpleItem()
                    .register();
    public static final BlockEntry<RadarGuidanceBlock> RADAR_GUIDANCE_LARGE =
            REGISTRATE.block("radar_guidance_large", properties -> new RadarGuidanceBlock(properties, MissileSize.LARGE))
                    .properties(BlockBehaviour.Properties::noOcclusion)
                    .initialProperties(SharedProperties::softMetal)
                    .simpleItem()
                    .register();
    public static final BlockEntry<TargetCoordinatorBlock> TARGET_COORDINATOR =
            REGISTRATE.block("target_coordinator",TargetCoordinatorBlock::new)
                    .properties(BlockBehaviour.Properties::noOcclusion)
                    .initialProperties(SharedProperties::softMetal)
                    .simpleItem()
                    .register();


    public static final BlockEntry<RocketPod> ROCKET_POD_FRONT =
            REGISTRATE.block("rocket_pod_front",RocketPod::new)
                    .properties(BlockBehaviour.Properties::noOcclusion)
                    .initialProperties(SharedProperties::softMetal)
                    .blockstate((ctx, prov) -> {
                        var model = prov.models().getExistingFile(CreateKaboom.asResource("block/rocket_pod_front"));
                        prov.getVariantBuilder(ctx.getEntry()).forAllStates(state ->
                                rocketPodModel(model, state.getValue(BlockStateProperties.FACING)));
                    })
                    .simpleItem()
                    .register();
    public static final BlockEntry<RocketPod> ROCKET_POD_REAR =
            REGISTRATE.block("rocket_pod_rear",RocketPod::new)
                    .properties(BlockBehaviour.Properties::noOcclusion)
                    .initialProperties(SharedProperties::softMetal)
                    .blockstate((ctx, prov) -> {
                        var model = prov.models().getExistingFile(CreateKaboom.asResource("block/rocket_pod_rear"));
                        prov.getVariantBuilder(ctx.getEntry()).forAllStates(state ->
                                rocketPodModel(model, state.getValue(BlockStateProperties.FACING)));
                    })
                    .simpleItem()
                    .register();

    public static final BlockEntry<RocketPod> ROCKET_POD_CENTER =
            REGISTRATE.block("rocket_pod_center",RocketPod::new)
                    .properties(BlockBehaviour.Properties::noOcclusion)
                    .initialProperties(SharedProperties::softMetal)
                    .blockstate((ctx, prov) -> {
                        var model = prov.models().getExistingFile(CreateKaboom.asResource("block/rocket_pod_center"));
                        prov.getVariantBuilder(ctx.getEntry()).forAllStates(state ->
                                rocketPodModel(model, state.getValue(BlockStateProperties.FACING)));
                    })
                    .simpleItem()
                    .register();









    public static final BlockEntry<AbstractMissileWarhead> HUGE_CLUSTER_WARHEAD =
            warhead("huge_cluster_warhead", "huge_cluster_warhead", AerialBombProjectile.BombType.CLUSTER, 1, MissileSize.HUGE).register();
    public static final BlockEntry<AbstractMissileWarhead> LARGE_CLUSTER_WARHEAD =
            warhead("large_cluster_warhead", "medium_cluster_warhead", AerialBombProjectile.BombType.CLUSTER, 2, MissileSize.LARGE).register();
    public static final BlockEntry<AbstractMissileWarhead> HUGE_FLUID_WARHEAD =
            warhead("huge_fluid_warhead", "huge_fluid_warhead", AerialBombProjectile.BombType.FLUID, 1, MissileSize.HUGE).register();
    public static final BlockEntry<AbstractMissileWarhead> LARGE_FLUID_WARHEAD =
            warhead("large_fluid_warhead", "medium_fluid_warhead", AerialBombProjectile.BombType.FLUID, 2, MissileSize.LARGE).register();
    public static final BlockEntry<AbstractMissileWarhead> HUGE_HIGH_EXPLOSIVE_WARHEAD =
            warhead("huge_high_explosive_warhead", "huge_high_explosive_warhead", AerialBombProjectile.BombType.HE, 1, MissileSize.HUGE).register();
    public static final BlockEntry<AbstractMissileWarhead> LARGE_HIGH_EXPLOSIVE_WARHEAD =
            warhead("large_high_explosive_warhead", "medium_high_explosive_warhead", AerialBombProjectile.BombType.HE, 2, MissileSize.LARGE).register();
    public static final BlockEntry<AbstractMissileWarhead> HUGE_FRAGMENTATION_WARHEAD =
            warhead("huge_fragmentation_warhead", "huge_fragmentation_warhead", AerialBombProjectile.BombType.FRAG, 1, MissileSize.HUGE).register();
    public static final BlockEntry<AbstractMissileWarhead> LARGE_FRAGMENTATION_WARHEAD =
            warhead("large_fragmentation_warhead", "medium_fragmentation_warhead", AerialBombProjectile.BombType.FRAG, 2, MissileSize.LARGE).register();
    public static final BlockEntry<AbstractMissileWarhead> HUGE_ARMOR_PIERCING_WARHEAD =
            warhead("huge_armor_piercing_warhead", "huge_armor_piercing_warhead", AerialBombProjectile.BombType.AP, 1, MissileSize.HUGE).register();
    public static final BlockEntry<AbstractMissileWarhead> LARGE_ARMOR_PIERCING_WARHEAD =
            warhead("large_armor_piercing_warhead", "medium_armor_piercing_warhead", AerialBombProjectile.BombType.AP, 2, MissileSize.LARGE).register();

    private static BlockBuilder<AbstractMissileWarhead, CreateRegistrate> warhead(
            String name, String modelName, AerialBombProjectile.BombType bombType, int bombSize, MissileSize missileSize) {
        return REGISTRATE.block(name, properties -> new AbstractMissileWarhead(properties, bombType, bombSize, missileSize))
                .properties(BlockBehaviour.Properties::noOcclusion)
                .properties(properties -> properties.isRedstoneConductor((state, level, pos) -> false))
                .initialProperties(SharedProperties::softMetal)
                .lang(CreateKaboom.toHumanReadable(name))
                .blockstate((context, provider) -> {
                    var model = provider.models().getExistingFile(
                            CreateKaboom.asResource("block/missile/" + modelName));
                    provider.getVariantBuilder(context.getEntry()).forAllStates(state -> {
                        Direction facing = state.getValue(BlockStateProperties.FACING);
                        int rotationX = facing == Direction.DOWN ? 180 : facing.getAxis().isHorizontal() ? 90 : 0;
                        int rotationY = facing.getAxis().isHorizontal() ? (int) facing.toYRot() : 0;
                        return ConfiguredModel.builder()
                                .modelFile(model)
                                .rotationX(rotationX)
                                .rotationY(rotationY)
                                .build();
                    });
                })
                .item()
                .model((context, provider) -> provider.withExistingParent(
                        context.getName(), CreateKaboom.asResource("block/missile/" + modelName)))
                .build();
    }




}
