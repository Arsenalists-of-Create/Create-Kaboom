package com.happysg.kaboom.compat.radars;

import com.happysg.kaboom.block.rocketpod.RocketPodContraption;
import com.happysg.kaboom.config.KaboomConfig;
import com.happysg.kaboom.items.rocket.RocketGuidanceType;
import com.happysg.kaboom.items.rocket.RocketItem;
import com.happysg.kaboom.items.rocket.RocketPayload;
import com.happysg.kaboom.items.rocket.UnguidedRocketProjectile;
import com.happysg.radar.api.weapon.WeaponFirePreparation;
import com.happysg.radar.api.weapon.WeaponShotContext;
import com.happysg.radar.api.weapon.WeaponShotProfile;
import com.happysg.radar.block.behavior.networks.NetworkData;
import com.happysg.radar.block.controller.networkcontroller.NetworkFiltererBlockEntity;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import rbasamoyai.createbigcannons.munitions.config.DimensionMunitionProperties;
import rbasamoyai.createbigcannons.munitions.config.DimensionMunitionPropertiesHandler;
import rbasamoyai.createbigcannons.munitions.config.components.BallisticPropertiesComponent;

final class RocketPodWeaponShotAdapter {
    private RocketPodWeaponShotAdapter() {
    }

    @Nullable
    static WeaponShotProfile resolve(WeaponShotContext context) {
        if (!(context.weapon() instanceof RocketPodContraption pod)) {
            return null;
        }

        RocketPodContraption.NextRocketLaunch launch =
                pod.getNextRocketLaunch(context.level(), context.entity());
        if (launch == null) {
            return WeaponShotProfile.disabled(
                    fallbackMuzzle(context),
                    Vec3.ZERO,
                    "create_kaboom:rocket_pod_empty",
                    "rocket_pod_empty");
        }

        ItemStack rocket = launch.rocket();
        RocketGuidanceType guidance = RocketItem.getGuidanceType(rocket);
        boolean canTriggerFire =
                !launch.pending() && !launch.fireSignalPowered();
        String baseFingerprint = fingerprint(
                launch, rocket, guidance, null, 0.0);

        if (guidance == RocketGuidanceType.ARAD) {
            return WeaponShotProfile.disabled(
                    launch.spawnPosition(),
                    launch.carrierVelocity(),
                    baseFingerprint,
                    "rocket_pod_arad_unsupported");
        }

        if (guidance == RocketGuidanceType.COMMAND) {
            WeaponFirePreparation preparation =
                    RocketItem.getLinkedNetworkController(rocket) == null
                            ? fireContext -> prepareCommandRocket(
                            fireContext, pod, launch)
                            : WeaponFirePreparation.READY;
            return WeaponShotProfile.direct(
                    launch.spawnPosition(),
                    launch.carrierVelocity(),
                    baseFingerprint,
                    0.0,
                    canTriggerFire,
                    preparation,
                    "rocket_pod_command");
        }

        if (guidance == RocketGuidanceType.RADAR) {
            double tolerance = sanitizeTolerance(
                    KaboomConfig.server()
                            .radarRocketMinimumFireToleranceDegrees.getF());
            return WeaponShotProfile.direct(
                    launch.spawnPosition(),
                    launch.carrierVelocity(),
                    fingerprint(launch, rocket, guidance, null, tolerance),
                    tolerance,
                    canTriggerFire,
                    WeaponFirePreparation.READY,
                    "rocket_pod_radar");
        }

        KaboomRocketProjectileModel model = createModel(context, rocket);
        return WeaponShotProfile.ballistic(
                model,
                launch.spawnPosition(),
                launch.carrierVelocity(),
                UnguidedRocketProjectile.MAX_FLIGHT_TICKS,
                fingerprint(launch, rocket, null, model, 0.0),
                canTriggerFire,
                WeaponFirePreparation.READY,
                "rocket_pod_unguided");
    }

    private static KaboomRocketProjectileModel createModel(
            WeaponShotContext context,
            ItemStack rocket
    ) {
        RocketPayload payload = RocketItem.getPayload(rocket);
        BallisticPropertiesComponent ballistics =
                payload != null && payload.isArmorPiercing()
                        ? RocketPayload.AP_BALLISTIC_PROPERTIES
                        : RocketPayload.STANDARD_BALLISTIC_PROPERTIES;
        DimensionMunitionProperties dimension =
                DimensionMunitionPropertiesHandler.getProperties(context.level());
        return new KaboomRocketProjectileModel(
                configuredNonNegative(
                        KaboomConfig.server().rocketInitialVelocity.getF(),
                        UnguidedRocketProjectile.LAUNCH_SPEED_BLOCKS_PER_TICK),
                configuredNonNegative(
                        KaboomConfig.server().rocketAccelerationPerTick.getF(),
                        UnguidedRocketProjectile.BOOST_ACCELERATION_PER_TICK),
                configuredNonNegative(
                        KaboomConfig.server().rocketBoostDistance.getF(),
                        UnguidedRocketProjectile.BOOST_DISTANCE_BLOCKS),
                ballistics.gravity() * dimension.gravityMultiplier(),
                ballistics.drag(),
                ballistics.isQuadraticDrag(),
                dimension.dragMultiplier(),
                UnguidedRocketProjectile.MAX_FLIGHT_TICKS
        );
    }

    private static boolean prepareCommandRocket(
            WeaponShotContext context,
            RocketPodContraption pod,
            RocketPodContraption.NextRocketLaunch expected
    ) {
        BlockPos filtererPos = NetworkData.get(context.level())
                .getFiltererForWeaponMount(
                        context.level().dimension(),
                        context.mount().getBlockPos());
        if (filtererPos == null
                || !(context.level().getBlockEntity(filtererPos)
                instanceof NetworkFiltererBlockEntity)) {
            return false;
        }
        return pod.linkNextCommandRocket(
                context.entity(),
                expected.rearPos(),
                expected.slot(),
                expected.rocket(),
                filtererPos);
    }

    private static String fingerprint(
            RocketPodContraption.NextRocketLaunch launch,
            ItemStack rocket,
            @Nullable RocketGuidanceType guidance,
            @Nullable KaboomRocketProjectileModel model,
            double tolerance
    ) {
        return "create_kaboom:" + Integer.toHexString(Objects.hash(
                launch.rearPos(),
                launch.slot(),
                launch.pending() ? launch.launchId() : -1L,
                rocket.getItem(),
                rocket.getComponents(),
                guidance,
                model,
                tolerance,
                quantizeVelocity(launch.carrierVelocity().x),
                quantizeVelocity(launch.carrierVelocity().y),
                quantizeVelocity(launch.carrierVelocity().z)
        ));
    }

    private static long quantizeVelocity(double value) {
        return Double.isFinite(value) ? Math.round(value * 100.0) : 0L;
    }

    private static Vec3 fallbackMuzzle(WeaponShotContext context) {
        Vec3 position = context.entity().toGlobalVector(
                Vec3.atCenterOf(BlockPos.ZERO), 0.0F);
        return finite(position)
                ? position : context.mount().getBlockPos().getCenter();
    }

    private static double configuredNonNegative(float value, double fallback) {
        return Float.isFinite(value)
                ? Math.max(0.0, value) : Math.max(0.0, fallback);
    }

    private static double sanitizeTolerance(float value) {
        return Float.isFinite(value)
                ? Math.max(0.0, Math.min(45.0, value)) : 10.0;
    }

    private static boolean finite(Vec3 value) {
        return value != null
                && Double.isFinite(value.x)
                && Double.isFinite(value.y)
                && Double.isFinite(value.z);
    }
}
