package com.happysg.kaboom.items.rocket;

import com.happysg.kaboom.block.missiles.nav.MovingTargetInterceptorNavigation;
import com.happysg.kaboom.block.missiles.nav.MovingTargetResolver;
import com.happysg.kaboom.block.missiles.parts.guidance.radar.RadarTargeting;
import com.happysg.kaboom.block.missiles.util.ARADTargetReference;
import com.happysg.kaboom.block.missiles.util.MissileFlightProfile;
import com.happysg.kaboom.block.missiles.util.MissileGuidanceData;
import com.happysg.kaboom.compat.radars.RadarCompatRegistry;
import com.happysg.kaboom.compat.radars.RadarIntegration;
import com.happysg.kaboom.compat.sable.SableUtils;
import com.happysg.kaboom.config.KaboomConfig;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import rbasamoyai.createbigcannons.cannon_control.ControlPitchContraption;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;


public final class RocketGuidanceLaunchResolver {
    public static final double ACQUISITION_RANGE_BLOCKS = 500.0;
    public static final double ARAD_HALF_ANGLE_DEGREES = 100.0;
    private static final double MIN_VECTOR_LENGTH_SQR = 1.0e-8;

    private RocketGuidanceLaunchResolver() {
    }

    public record Resolution(boolean accepted, @Nullable MissileGuidanceData guidanceData) {
        private static Resolution unguided() {
            return new Resolution(true, null);
        }

        private static Resolution guided(MissileGuidanceData guidanceData) {
            return new Resolution(true, guidanceData);
        }

        private static Resolution rejected() {
            return new Resolution(false, null);
        }
    }

    public static Resolution resolve(ServerLevel level, PitchOrientedContraptionEntity launcher,
                                     ItemStack rocket, Vec3 launchPosition, Vec3 launchDirection) {
        RocketGuidanceType type = RocketItem.getGuidanceType(rocket);
        if (type == null) {
            return Resolution.unguided();
        }
        if (!isFinite(launchPosition) || !isFinite(launchDirection)
                || launchDirection.lengthSqr() < MIN_VECTOR_LENGTH_SQR) {
            return Resolution.rejected();
        }

        Vec3 forward = launchDirection.normalize();
        BlockPos sourcePos = sourcePosition(launcher);
        SubLevelAccess sourceSublevel = SableUtils.getShipManagingPos(level, sourcePos);
        UUID sourceSublevelId = sourceSublevel == null ? null : sourceSublevel.getUniqueId();
        RadarTargeting.SensorFrame frame = new RadarTargeting.SensorFrame(
                launchPosition, forward, sourceSublevelId);

        return switch (type) {
            case COMMAND -> resolveCommand(level, launcher, rocket, launchPosition);
            case RADAR -> resolveRadar(level, frame, sourcePos, launcher.getUUID());
            case ARAD -> resolveArad(level, frame);
        };
    }

    private static Resolution resolveCommand(ServerLevel level, PitchOrientedContraptionEntity launcher,
                                             ItemStack rocket, Vec3 launchPosition) {
        if (!RadarCompatRegistry.isAvailable()) {
            return Resolution.rejected();
        }

        BlockPos filtererPos = RocketItem.getLinkedNetworkController(rocket);
        if (filtererPos == null) {
            ControlPitchContraption controller = launcher.getController();
            if (!(controller instanceof CannonMountBlockEntity mount)) {
                return Resolution.rejected();
            }
            filtererPos = RadarCompatRegistry.get()
                    .resolveWeaponMountController(level, mount.getBlockPos());
        }
        if (filtererPos == null) {
            return Resolution.rejected();
        }

        MissileGuidanceData guidance = MissileGuidanceData.command(
                filtererPos, MissileFlightProfile.defaults());
        MovingTargetResolver.TargetData target = MovingTargetResolver.resolve(level, guidance, launchPosition);
        if (target == null || !isFinite(target.position())) {
            return Resolution.rejected();
        }
        int timeout = Math.max(0, KaboomConfig.server().targetDataTimeoutTicks.get());
        if (!target.live() && target.ageTicks(level) > timeout) {
            return Resolution.rejected();
        }
        return Resolution.guided(guidance);
    }

    private static Resolution resolveRadar(ServerLevel level, RadarTargeting.SensorFrame frame,
                                           BlockPos sourcePos, UUID launcherId) {
        MovingTargetInterceptorNavigation.RadarSeekerProfile seeker =
                MovingTargetInterceptorNavigation.RadarSeekerProfile
                        .configuredRocket(ACQUISITION_RANGE_BLOCKS);
        RadarTargeting.Candidate candidate = RadarTargeting.acquire(
                level,
                frame,
                ACQUISITION_RANGE_BLOCKS,
                seeker.seekerHalfAngleDegrees(),
                null,
                launcherId
        );
        if (candidate == null) {
            return Resolution.rejected();
        }

        return Resolution.guided(MissileGuidanceData.radar(
                sourcePos,
                candidate.id(),
                MissileFlightProfile.defaults()
        ));
    }

    private static Resolution resolveArad(ServerLevel level, RadarTargeting.SensorFrame frame) {
        if (!RadarCompatRegistry.isAvailable()) {
            return Resolution.rejected();
        }
        RadarIntegration integration = RadarCompatRegistry.get();
        ARADTargetReference reference = integration.acquireAradTarget(
                level,
                new RadarIntegration.AradAcquisitionRequest(
                        frame.origin(),
                        frame.forward(),
                        frame.launcherSublevelId(),
                        ARAD_HALF_ANGLE_DEGREES,
                        ACQUISITION_RANGE_BLOCKS
                )
        );
        if (reference == null || !reference.isValid()) {
            return Resolution.rejected();
        }
        Vec3 target = integration.resolveAradTarget(level, reference);
        Vec3 emitter = integration.resolveAradEmitterPosition(level, reference);
        if (!isFinite(target) || !isFinite(emitter)) {
            return Resolution.rejected();
        }
        return Resolution.guided(MissileGuidanceData.arad(
                target, reference, MissileFlightProfile.defaults()));
    }

    private static BlockPos sourcePosition(PitchOrientedContraptionEntity launcher) {
        ControlPitchContraption controller = launcher.getController();
        return controller instanceof BlockEntity blockEntity
                ? blockEntity.getBlockPos()
                : launcher.blockPosition();
    }

    private static boolean isFinite(@Nullable Vec3 value) {
        return value != null
                && Double.isFinite(value.x)
                && Double.isFinite(value.y)
                && Double.isFinite(value.z);
    }
}
