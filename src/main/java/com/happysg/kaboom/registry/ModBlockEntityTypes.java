package com.happysg.kaboom.registry;

import com.happysg.kaboom.CreateKaboom;
import com.happysg.kaboom.block.aerialBombs.baseTypes.AerialBombBlockEntity;
import com.happysg.kaboom.block.aerialBombs.baseTypes.FluidAerialBombBlockEntity;
import com.happysg.kaboom.block.missiles.parts.fuel.MissileFuelTankBlockEntity;
import com.happysg.kaboom.block.missiles.parts.guidance.arad.ARADGuidanceBlockEntity;
import com.happysg.kaboom.block.missiles.parts.guidance.command.CommandGuidanceBlockEntity;
import com.happysg.kaboom.block.missiles.parts.guidance.radar.RadarGuidanceBlockEntity;
import com.happysg.kaboom.block.missiles.parts.thrust.ThrusterBlockEntity;
import com.happysg.kaboom.block.missiles.parts.warhead.MissileWarheadBlockEntity;
import com.happysg.kaboom.block.missiles.parts.guidance.gps.GPSGuidanceBlockEntity;
import com.happysg.kaboom.block.rocketpod.RocketPodBlockEntity;
import com.happysg.kaboom.block.targetcoordinator.TargetCoordinatorBlock;
import com.happysg.kaboom.block.targetcoordinator.TargetCoordinatorBlockEntity;
import com.simibubi.create.content.logistics.tableCloth.TableClothBlockEntity;
import com.tterrag.registrate.util.entry.BlockEntityEntry;

import static com.happysg.kaboom.CreateKaboom.REGISTRATE;

public class ModBlockEntityTypes {

    public static final BlockEntityEntry<AerialBombBlockEntity> AERIAL_BOMB = REGISTRATE
            .blockEntity("aerial_bomb", AerialBombBlockEntity::new)
            .validBlocks(
                    ModBlocks.HEAVY_AERIAL_BOMB,
                    ModBlocks.AP_HEAVY_AERIAL_BOMB,
                    ModBlocks.CLUSTER_HEAVY_AERIAL_BOMB,
                    ModBlocks.FRAG_HEAVY_AERIAL_BOMB,
                    ModBlocks.SMALL_AERIAL_BOMB,
                    ModBlocks.AP_AERIAL_BOMB,
                    ModBlocks.FRAG_AERIAL_BOMB,
                    ModBlocks.TINY_AERIAL_BOMB
            )
            .register();
    public static final BlockEntityEntry<FluidAerialBombBlockEntity> FLUID_AERIAL_BOMB_BE =
            REGISTRATE.blockEntity("fluid_aerial_bomb", FluidAerialBombBlockEntity::new)
                    .validBlocks(
                            ModBlocks.FLUID_AERIAL_BOMB,
                            ModBlocks.SMALL_FLUID_AERIAL_BOMB
                    )
                    .register();

    public static final BlockEntityEntry<MissileWarheadBlockEntity> MISSILE_WARHEAD = REGISTRATE
            .blockEntity("missile_warhead", MissileWarheadBlockEntity::new)
            .validBlocks(
                    ModBlocks.LARGE_HIGH_EXPLOSIVE_WARHEAD,
                    ModBlocks.HUGE_HIGH_EXPLOSIVE_WARHEAD,
                    ModBlocks.LARGE_ARMOR_PIERCING_WARHEAD,
                    ModBlocks.HUGE_ARMOR_PIERCING_WARHEAD,
                    ModBlocks.LARGE_FRAGMENTATION_WARHEAD,
                    ModBlocks.HUGE_FRAGMENTATION_WARHEAD,
                    ModBlocks.LARGE_CLUSTER_WARHEAD,
                    ModBlocks.HUGE_CLUSTER_WARHEAD,
                    ModBlocks.LARGE_FLUID_WARHEAD,
                    ModBlocks.HUGE_FLUID_WARHEAD
            )
            .register();

    public static final BlockEntityEntry<ThrusterBlockEntity> MISSILE_THRUSTER_BE =
            REGISTRATE.blockEntity("missile_engine",ThrusterBlockEntity::new)
                    .validBlocks(
                            ModBlocks.MISSILE_THRUSTER,
                            ModBlocks.MISSILE_THRUSTER_SMALL,
                            ModBlocks.MISSILE_THRUSTER_HUGE
                    )
                    .register();
    public static final BlockEntityEntry<MissileFuelTankBlockEntity> FUEL_TANK_SMALL =
            REGISTRATE.blockEntity("small_tank", MissileFuelTankBlockEntity::new)
                    .validBlocks(
                            ModBlocks.MISSILE_FUEL,
                            ModBlocks.MISSILE_FUEL_SMALL,
                            ModBlocks.MISSILE_FUEL_HUGE
                    )
                    .register();

    public static final BlockEntityEntry<GPSGuidanceBlockEntity>GPS_GUIDANCE =
            REGISTRATE.blockEntity("gps_guidance_be",GPSGuidanceBlockEntity::new)
                    .validBlocks(
                            ModBlocks.GPS_GUIDANCE_SMALL,
                            ModBlocks.GPS_GUIDANCE_LARGE,
                            ModBlocks.GPS_GUIDANCE_HUGE
                    )
                    .register();

    public static final BlockEntityEntry<RadarGuidanceBlockEntity>RADAR_GUIDANCE =
            REGISTRATE.blockEntity("radar_guidance_be",RadarGuidanceBlockEntity::new)
                    .validBlocks(
                            ModBlocks.RADAR_GUIDANCE_SMALL,
                            ModBlocks.RADAR_GUIDANCE_LARGE
                    )
                    .register();
    public static final BlockEntityEntry<ARADGuidanceBlockEntity> ARAD_GUIDANCE =
            REGISTRATE.blockEntity("arad_guidance_be", ARADGuidanceBlockEntity::new)
                    .validBlocks(
                            ModBlocks.ARAD_GUIDANCE_SMALL,
                            ModBlocks.ARAD_GUIDANCE_LARGE
                    )
                    .register();
    public static final BlockEntityEntry<CommandGuidanceBlockEntity>COMMAND_GUIDANCE =
            REGISTRATE.blockEntity("command_guidance_be",CommandGuidanceBlockEntity::new)
                    .validBlocks(
                            ModBlocks.COMMAND_GUIDANCE_SMALL,
                            ModBlocks.COMMAND_GUIDANCE_LARGE
                    )
                    .register();
    public static final BlockEntityEntry<TargetCoordinatorBlockEntity> TARGET_COORDINATOR_BE =
            REGISTRATE.blockEntity("target_coordinator_be", TargetCoordinatorBlockEntity::new)
                    .validBlocks(ModBlocks.TARGET_COORDINATOR)
                    .register();

    public static final BlockEntityEntry<RocketPodBlockEntity> ROCKET_POD_REAR =
            REGISTRATE.blockEntity("rocket_pod_rear", RocketPodBlockEntity::new)
                    .validBlocks(ModBlocks.ROCKET_POD_REAR)
                    .register();

    public static void register() {
        CreateKaboom.getLogger().info("Registering block entity types!");
    }
}
