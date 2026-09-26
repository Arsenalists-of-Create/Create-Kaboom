package com.happysg.kaboom.block.missiles.assembly;

import com.happysg.kaboom.CreateKaboom;
import com.happysg.kaboom.block.missiles.MissileContraption;
import com.happysg.kaboom.block.missiles.MissileEntity;
import com.happysg.kaboom.block.missiles.chaining.ChainSystem;
import com.happysg.kaboom.block.missiles.nav.MovingTargetResolver;
import com.happysg.kaboom.block.missiles.parts.fuel.MissileFuelTankBlockEntity;
import com.happysg.kaboom.block.missiles.parts.thrust.ThrusterBlockEntity;
import com.happysg.kaboom.block.missiles.util.IMissileGuidanceProvider;
import com.happysg.kaboom.block.missiles.util.MissileGuidanceData;
import com.happysg.kaboom.block.missiles.util.MissileGuidanceType;
import com.happysg.kaboom.block.missiles.util.MissileTargetSpec;
import com.happysg.kaboom.compat.radars.RadarCompatRegistry;
import com.happysg.kaboom.compat.cbc.MountedMissileController;
import com.happysg.kaboom.compat.sable.SableUtils;
import com.happysg.kaboom.config.KaboomConfig;
import com.happysg.kaboom.networking.LaunchSoundHandoffPacket;
import com.happysg.kaboom.networking.NetworkHandler;
import com.happysg.kaboom.registry.ModEntities;
import com.simibubi.create.content.contraptions.AssemblyException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import rbasamoyai.createbigcannons.cannon_control.ControlPitchContraption;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;

import java.util.Locale;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;

public class MissileLaunchHelper {
    private static final int MINIMUM_LAUNCH_FUEL_MB = 250;
    private static final double ARAD_LAUNCH_HALF_ANGLE_DEGREES = 50.0;
    private static final double HORIZONTAL_LAUNCH_HALF_ANGLE_DEGREES = 90.0;
    private static final double MINIMUM_TARGET_VECTOR_LENGTH_SQR = 1.0E-8;

    public static boolean assembleAndSpawn(ServerLevel level, BlockPos anyThrusterPos) throws AssemblyException {
        return assembleAndSpawn(level, anyThrusterPos, true);
    }

    private static boolean assembleAndSpawn(ServerLevel level, BlockPos anyThrusterPos,
                                            boolean requireMinimumFuel) throws AssemblyException {
        FreeLaunchValidation validation = validateFreeLaunch(level, anyThrusterPos, requireMinimumFuel);
        MissileAssemblyResult result = validation.result();
        publishFreeDiagnostic(level, result, anyThrusterPos, validation.failure());
        if (validation.failure() != null) {
            logLaunchFailure(anyThrusterPos, validation.failure());
            return rejectLaunch(level, result, anyThrusterPos);
        }
        BlockPos controllerPos = result.getControllerPos();
        MissileSize missileSize = result.getMissileSize();
        BlockPos warheadWorldPos = result.getWarhead();
        BlockPos warheadLocalPos = warheadWorldPos.subtract(controllerPos);
        SableUtils.LaunchKinematics launch = validation.launch();
        MissileGuidanceData guidance = validation.guidance();

        MissileContraption mc = MissileContraptionBuilder.build(level, result, warheadWorldPos);
        mc.guidanceTag = guidance.toTag();

        BlockEntity controllerBE = level.getBlockEntity(controllerPos);
        ChainSystem chainSystem = null;
        if (controllerBE instanceof ThrusterBlockEntity thrusterBE) {
            chainSystem = thrusterBE.getChainSystem();

            chainSystem.breakDanglingChains(controllerPos, level);
            mc.chainSystemTag = chainSystem.save();
        }

        for (int i = result.getBlocks().size() - 1; i >= 0; i--) {
            level.removeBlock(result.getBlocks().get(i), false);
        }

        MissileEntity entity = ModEntities.MISSILE.get().create(level);
        if (entity == null) return rejectLaunch(level, result, anyThrusterPos);

        entity.initFromAssembly(mc, warheadLocalPos, launch);

        boolean added = level.addFreshEntity(entity);

        if (added) {
            Vec3 nozzle = launch.position().add(launch.direction().scale(-0.55));
            level.playSound(null, nozzle.x, nozzle.y, nozzle.z,
                    com.happysg.kaboom.registry.ModSounds.MISSILE_BOOF.get(),
                    net.minecraft.sounds.SoundSource.AMBIENT, 0.55F, 1.0F);
            entity.onRadarMissileLaunched();
            if (KaboomConfig.server().missileLaunchDelayTicks(missileSize) > 0) {
                NetworkHandler.sendToPlayersTrackingEntity(entity,
                        LaunchSoundHandoffPacket.freeMissile(controllerPos, entity.getId()));
            }
        }

        if (added && chainSystem != null) {
            for (UUID mobId : chainSystem.getSecuredMobIds()) {
                Entity mob = level.getEntity(mobId);
                if (mob instanceof Mob m) {
                    m.startRiding(entity, true);
                }
            }

            for (UUID playerId : chainSystem.getAttachedPlayerIds(level)) {
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
                if (player != null && !player.isCreative()) {
                    player.getInventory().dropAll();
                }
            }
        }

        return added || rejectLaunch(level, result, anyThrusterPos);
    }

    public static PreparedFreeLaunch prepareFreeLaunch(ServerLevel level, BlockPos anyThrusterPos)
            throws AssemblyException {
        FreeLaunchValidation validation = validateFreeLaunch(level, anyThrusterPos, true);
        MissileAssemblyResult result = validation.result();
        publishFreeDiagnostic(level, result, anyThrusterPos, validation.failure());
        if (validation.failure() != null) {
            logLaunchFailure(anyThrusterPos, validation.failure());
            rejectLaunch(level, result, anyThrusterPos);
            return null;
        }

        Map<BlockPos, BlockState> snapshot = new LinkedHashMap<>();
        for (BlockPos pos : result.getBlocks()) snapshot.put(pos.immutable(), level.getBlockState(pos));
        return new PreparedFreeLaunch(result.getMissileSize(), Map.copyOf(snapshot));
    }

    public static boolean finishFreeLaunch(ServerLevel level, BlockPos controllerPos,
                                           Map<BlockPos, BlockState> expectedBlocks) throws AssemblyException {
        MissileAssemblyResult current = MissileAssembler.scan(level, controllerPos);
        if (!current.isValid() || current.getBlocks().size() != expectedBlocks.size()
                || !expectedBlocks.keySet().equals(Set.copyOf(current.getBlocks()))) {
            return false;
        }
        for (Map.Entry<BlockPos, BlockState> expected : expectedBlocks.entrySet()) {
            if (level.getBlockState(expected.getKey()).getBlock() != expected.getValue().getBlock()) return false;
        }
        return assembleAndSpawn(level, controllerPos, false);
    }

    public record PreparedFreeLaunch(MissileSize size, Map<BlockPos, BlockState> blocks) {}

    @Nullable
    public static AssemblyException diagnoseFreeLaunch(ServerLevel level, BlockPos controllerPos) {
        return validateFreeLaunch(level, controllerPos, true).failure();
    }

    private static FreeLaunchValidation validateFreeLaunch(ServerLevel level, BlockPos anyThrusterPos,
                                                            boolean requireMinimumFuel) {
        MissileAssemblyResult result = MissileAssembler.scan(level, anyThrusterPos);
        if (!result.isValid()) {
            AssemblyException failure = result.getFailure() == null
                    ? failure("invalidAssembly") : result.getFailure();
            return new FreeLaunchValidation(result, null, null, failure);
        }

        MissileSize size = result.getMissileSize();
        if (size == null) {
            return new FreeLaunchValidation(result, null, null, failure("invalidAssembly"));
        }
        if (size.isOverweight(result.getFuelTankCount())) {
            return new FreeLaunchValidation(result, null, null, failure(
                    "overweight", result.getFuelTankCount(), size.fuelTankLaunchRejectionThreshold() - 1));
        }

        BlockPos controllerPos = result.getControllerPos();
        Vec3 localDirection = Vec3.atLowerCornerOf(result.getAssemblyDirection().getNormal());
        SableUtils.LaunchKinematics launch = SableUtils.getLaunchKinematics(
                level, controllerPos, controllerPos.getCenter(), localDirection);
        if (requireMinimumFuel) {
            int fuelAmount = currentFuelAmount(level, result);
            if (fuelAmount < MINIMUM_LAUNCH_FUEL_MB) {
                return new FreeLaunchValidation(result, null, launch,
                        failure("insufficientFuel", fuelAmount, MINIMUM_LAUNCH_FUEL_MB));
            }
        }
        BlockEntity guidanceBlockEntity = result.guidance() == null
                ? null : level.getBlockEntity(result.guidance());
        if (!(guidanceBlockEntity instanceof IMissileGuidanceProvider provider)) {
            return new FreeLaunchValidation(result, null, launch, failure("missingGuidanceData"));
        }
        MissileGuidanceData guidance = provider.exportGuidance();
        if (guidance == null) {
            return new FreeLaunchValidation(result, null, launch, failure("missingGuidanceData"));
        }
        AssemblyException guidanceFailure = guidanceFailure(
                level, controllerPos, guidance, launch.position(), launch.direction());
        if (guidanceFailure != null) {
            return new FreeLaunchValidation(result, guidance, launch, guidanceFailure);
        }

        return new FreeLaunchValidation(result, guidance, launch, null);
    }

    private static int currentFuelAmount(ServerLevel level, MissileAssemblyResult result) {
        int fuelAmount = 0;
        for (BlockPos pos : result.getBlocks()) {
            if (level.getBlockEntity(pos) instanceof MissileFuelTankBlockEntity tank) {
                fuelAmount += tank.getTank().getFluidAmount();
            }
        }
        return fuelAmount;
    }

    private record FreeLaunchValidation(MissileAssemblyResult result,
                                        @Nullable MissileGuidanceData guidance,
                                        @Nullable SableUtils.LaunchKinematics launch,
                                        @Nullable AssemblyException failure) {}

    private static void publishFreeDiagnostic(ServerLevel level, MissileAssemblyResult result,
                                              BlockPos fallbackPos,
                                              @Nullable AssemblyException diagnostic) {
        BlockPos controllerPos = result.isValid() ? result.getControllerPos() : fallbackPos;
        if (level.getBlockEntity(controllerPos) instanceof ThrusterBlockEntity thruster) {
            thruster.setLaunchDiagnostic(diagnostic);
        }
    }

    public static boolean launchMounted(ServerLevel level, MissileContraption mounted,
                                        PitchOrientedContraptionEntity mountedEntity) {
        MountedLaunchValidation validation = validateMountedLaunch(level, mounted, mountedEntity, false);
        if (validation.failure() != null || validation.context() == null) {
            publishMountedDiagnostic(mountedEntity, validation.failure());
            return rejectMountedLaunch(level, mounted, mountedEntity);
        }
        publishMountedDiagnostic(mountedEntity, null);
        MountedLaunchContext context = validation.context();
        BlockPos sourcePos = context.sourcePos;
        MountedMissileController missileController = context.controller;
        MissileGuidanceData guidance = context.guidance;
        SableUtils.LaunchKinematics launch = context.launch;
        boolean keepMount = mounted.hasAdditionalMountedMissiles();
        MissileSize launchedSize = mounted.missileSize;

        MissileContraption flight = mounted.createFlightCopy(level);
        flight.guidanceTag = guidance.toTag();
        MissileEntity missile = ModEntities.MISSILE.get().create(level);
        if (missile == null || flight.warheadLocalPos == null) {
            return rejectMountedLaunch(level, mounted, mountedEntity);
        }
        missile.initFromAssembly(flight, flight.warheadLocalPos, launch);
        if (!level.addFreshEntity(missile)) {
            return rejectMountedLaunch(level, mounted, mountedEntity);
        }

        Vec3 nozzle = mounted.nozzleWorldPosition(mountedEntity);
        if (keepMount) {
            mounted.finishGroupedLaunch(mountedEntity);
        } else {
            if (!missileController.createKaboom$releaseMountedMissile(mountedEntity)) {
                missile.discard();
                return rejectMountedLaunch(level, mounted, mountedEntity);
            }
        }
        level.playSound(null, nozzle.x, nozzle.y, nozzle.z,
                com.happysg.kaboom.registry.ModSounds.MISSILE_BOOF.get(),
                net.minecraft.sounds.SoundSource.AMBIENT, 0.55F, 1.0F);
        if (KaboomConfig.server().missileLaunchDelayTicks(launchedSize) > 0) {
            NetworkHandler.sendToPlayersTrackingEntity(missile,
                    LaunchSoundHandoffPacket.mountedMissile(mountedEntity.getId(), missile.getId()));
        }
        if (!keepMount) {
            mountedEntity.discard();
        }

        ChainSystem chainSystem = missile.getChainSystem();
        chainSystem.breakDanglingChains(sourcePos, level);
        flight.chainSystemTag = chainSystem.save();
        missile.onRadarMissileLaunched();

        for (UUID mobId : chainSystem.getSecuredMobIds()) {
            Entity mob = level.getEntity(mobId);
            if (mob instanceof Mob securedMob) securedMob.startRiding(missile, true);
        }
        for (UUID playerId : chainSystem.getAttachedPlayerIds(level)) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
            if (player != null && !player.isCreative()) player.getInventory().dropAll();
        }
        return true;
    }

    public static boolean canBeginMountedLaunch(ServerLevel level, MissileContraption mounted,
                                                PitchOrientedContraptionEntity mountedEntity) {
        MountedLaunchValidation validation = validateMountedLaunch(level, mounted, mountedEntity, true);
        publishMountedDiagnostic(mountedEntity, validation.failure());
        if (validation.failure() != null) {
            rejectMountedLaunch(level, mounted, mountedEntity);
            return false;
        }
        return validation.context() != null;
    }

    private static MountedLaunchValidation validateMountedLaunch(ServerLevel level, MissileContraption mounted,
                                                                  PitchOrientedContraptionEntity mountedEntity,
                                                                  boolean requireLaunchFuel) {
        BlockPos sourcePos = mountedEntity.blockPosition();
        ControlPitchContraption controller = mountedEntity.getController();
        if (controller instanceof BlockEntity blockEntity) {
            sourcePos = blockEntity.getBlockPos();
        }

        if (!(controller instanceof MountedMissileController missileController)) {
            return new MountedLaunchValidation(null, failure("unsupportedMountedController"));
        }
        if (mounted.missileSize == null) {
            return new MountedLaunchValidation(null, failure("invalidAssembly"));
        }
        if (mounted.missileSize == MissileSize.HUGE) {
            return new MountedLaunchValidation(null, failure("unsupportedMountedSize"));
        }
        if (mounted.missileSize.isOverweight(mounted.fuelTankCount)) {
            return new MountedLaunchValidation(null, failure(
                    "overweight", mounted.fuelTankCount,
                    mounted.missileSize.fuelTankLaunchRejectionThreshold() - 1));
        }
        if (requireLaunchFuel && mounted.fuelAmountMb < MINIMUM_LAUNCH_FUEL_MB) {
            return new MountedLaunchValidation(null, failure(
                    "insufficientFuel", mounted.fuelAmountMb, MINIMUM_LAUNCH_FUEL_MB));
        }

        MissileGuidanceData guidance;
        try {
            guidance = mounted.guidanceTag == null || mounted.guidanceTag.isEmpty()
                    ? null
                    : MissileGuidanceData.fromTag(mounted.guidanceTag);
        } catch (RuntimeException exception) {
            CreateKaboom.getLogger().warn("Rejected mounted missile launch at {}: invalid guidance data", sourcePos, exception);
            return new MountedLaunchValidation(null, failure("invalidGuidanceData"));
        }
        if (guidance == null) {
            return new MountedLaunchValidation(null, failure("missingGuidanceData"));
        }

        BlockPos controllerLocalPos = mounted.getStartPos();
        Direction localForward = mounted.assemblyDirection == null ? mounted.initialOrientation() : mounted.assemblyDirection;
        Vec3 localControllerCenter = Vec3.atCenterOf(controllerLocalPos);
        Vec3 mountedControllerPosition = mountedEntity.toGlobalVector(localControllerCenter, 0);
        Vec3 mountedForwardPosition = mountedEntity.toGlobalVector(
                Vec3.atCenterOf(controllerLocalPos.relative(localForward)), 0);
        Vec3 mountedDirection = mountedForwardPosition.subtract(mountedControllerPosition);
        if (mountedDirection.lengthSqr() < MINIMUM_TARGET_VECTOR_LENGTH_SQR) {
            return new MountedLaunchValidation(null, failure("invalidLaunchTransform"));
        }

        SableUtils.LaunchKinematics sourceKinematics = SableUtils.getLaunchKinematics(
                level, sourcePos, sourcePos.getCenter(), Vec3.atLowerCornerOf(localForward.getNormal()));
        Vec3 observedCarrierVelocity = mounted.observedNozzleVelocity();
        if (!isFinite(observedCarrierVelocity)) observedCarrierVelocity = sourceKinematics.carrierVelocity();
        SableUtils.LaunchKinematics launch = new SableUtils.LaunchKinematics(
                mountedControllerPosition, mountedDirection.normalize(), observedCarrierVelocity,
                sourceKinematics.sourceSubLevelId());
        AssemblyException guidanceFailure = guidanceFailure(
                level, sourcePos, guidance, launch.position(), launch.direction());
        if (guidanceFailure != null) {
            return new MountedLaunchValidation(null, guidanceFailure);
        }
        return new MountedLaunchValidation(
                new MountedLaunchContext(sourcePos, missileController, guidance, launch), null);
    }

    private record MountedLaunchContext(BlockPos sourcePos, MountedMissileController controller,
                                        MissileGuidanceData guidance, SableUtils.LaunchKinematics launch) {}

    private record MountedLaunchValidation(@Nullable MountedLaunchContext context,
                                           @Nullable AssemblyException failure) {}

    private static void publishMountedDiagnostic(PitchOrientedContraptionEntity entity,
                                                 @Nullable AssemblyException diagnostic) {
        if (entity.getController() instanceof MountedMissileController controller) {
            controller.createKaboom$setMissileDiagnostic(diagnostic);
        }
    }

    private static boolean rejectMountedLaunch(ServerLevel level, MissileContraption contraption,
                                               PitchOrientedContraptionEntity entity) {
        if (contraption.getBlocks().isEmpty()) {
            Vec3 pos = entity.position();
            level.sendParticles(ParticleTypes.SMOKE, pos.x, pos.y, pos.z,
                    3, 0.08, 0.08, 0.08, 0.015);
            return false;
        }
        for (BlockPos localPos : contraption.getBlocks().keySet()) {
            Vec3 pos = entity.toGlobalVector(Vec3.atCenterOf(localPos), 0);
            level.sendParticles(ParticleTypes.SMOKE, pos.x, pos.y, pos.z,
                    3, 0.08, 0.08, 0.08, 0.015);
        }
        return false;
    }

    private static boolean rejectLaunch(ServerLevel level, MissileAssemblyResult result, BlockPos fallbackPos) {
        if (result.isValid() && !result.getBlocks().isEmpty()) {
            Set<BlockPos> missileParts = Set.copyOf(result.getBlocks());
            for (BlockPos partPos : result.getBlocks()) {
                spawnRejectedLaunchSmoke(level, partPos, missileParts);
            }
        } else {
            spawnRejectedLaunchSmoke(level, fallbackPos, Set.of(fallbackPos));
        }
        return false;
    }

    private static void spawnRejectedLaunchSmoke(ServerLevel level, BlockPos pos, Set<BlockPos> missileParts) {
        Direction[] exposedFaces = Arrays.stream(Direction.values())
                .filter(direction -> {
                    BlockPos adjacent = pos.relative(direction);
                    return !missileParts.contains(adjacent) && level.getBlockState(adjacent).isAir();
                })
                .toArray(Direction[]::new);
        Direction face = exposedFaces.length == 0
                ? Direction.UP
                : exposedFaces[Math.floorMod(pos.hashCode() ^ (int) level.getGameTime(), exposedFaces.length)];
        Vec3 center = pos.getCenter().add(
                face.getStepX() * 0.55,
                face.getStepY() * 0.55,
                face.getStepZ() * 0.55
        );
        level.sendParticles(ParticleTypes.SMOKE, center.x, center.y, center.z,
                3, 0.08, 0.08, 0.08, 0.015);
    }

    @Nullable
    private static AssemblyException guidanceFailure(
            ServerLevel level,
            BlockPos controllerPos,
            MissileGuidanceData guidance,
            Vec3 launchPosition,
            Vec3 launchDirection
    ) {
        if (guidance.guidanceType() == MissileGuidanceType.ARAD) {
            return aradTargetFailure(level, controllerPos, guidance, launchPosition, launchDirection);
        }
        if (guidance.guidanceType().isInterceptor()) {
            if (guidance.guidanceType() == MissileGuidanceType.COMMAND && !RadarCompatRegistry.isAvailable()) {
                return failure("radarCompatUnavailable");
            }

            boolean hasResolverMetadata = switch (guidance.guidanceType()) {
                case COMMAND -> guidance.networkControllerPos() != null;
                case RADAR -> guidance.radarGuidancePos() != null;
                default -> false;
            };
            if (!hasResolverMetadata) return failure("missingGuidanceData");

            MovingTargetResolver.TargetData target = MovingTargetResolver.resolve(level, guidance, launchPosition);
            if (target == null) {
                return failure("missingTarget");
            }

            if (!target.live() && target.ageTicks(level) > configuredTargetDataTimeoutTicks()) {
                return failure("staleTarget", configuredTargetDataTimeoutTicks());
            }

            return horizontalTargetFailure(
                    controllerPos,
                    guidance.guidanceType(),
                    launchPosition,
                    launchDirection,
                    target.position()
            );
        }
        AssemblyException gpsFailure = gpsTargetFailure(controllerPos, guidance, launchPosition);
        if (gpsFailure != null) {
            return gpsFailure;
        }
        return horizontalTargetFailure(
                controllerPos,
                guidance.guidanceType(),
                launchPosition,
                launchDirection,
                guidance.target().point()
        );
    }

    @Nullable
    private static AssemblyException aradTargetFailure(
            ServerLevel level,
            BlockPos controllerPos,
            MissileGuidanceData guidance,
            Vec3 launchPosition,
            Vec3 launchDirection
    ) {
        if (!RadarCompatRegistry.isAvailable()) {
            return failure("radarCompatUnavailable");
        }

        Vec3 targetPoint;
        Vec3 launchEnvelopeTarget;
        if (guidance.aradTargetReference() != null) {
            targetPoint = RadarCompatRegistry.get().resolveAradTarget(level, guidance.aradTargetReference());
            launchEnvelopeTarget = RadarCompatRegistry.get()
                    .resolveAradEmitterPosition(level, guidance.aradTargetReference());
        } else {
            MissileTargetSpec target = guidance.target();
            targetPoint = target != null && target.type() == MissileTargetSpec.TargetType.POINT
                    ? target.point()
                    : null;
            launchEnvelopeTarget = targetPoint;
        }
        if (!isFinite(targetPoint) || !isFinite(launchEnvelopeTarget)) {
            return failure("missingTarget");
        }
        if (!isFinite(launchPosition) || !isFinite(launchDirection) || launchDirection.lengthSqr() < MINIMUM_TARGET_VECTOR_LENGTH_SQR) {
            return failure("invalidLaunchTransform");
        }

        Vec3 toTarget = launchEnvelopeTarget.subtract(launchPosition);
        if (toTarget.lengthSqr() < MINIMUM_TARGET_VECTOR_LENGTH_SQR) {
            return failure("targetCoincides");
        }

        if (!isHorizontalLaunchDirection(launchDirection)) {
            return null;
        }

        double targetDot = launchDirection.normalize().dot(toTarget.normalize());
        double minimumDot = Math.cos(Math.toRadians(ARAD_LAUNCH_HALF_ANGLE_DEGREES));
        if (targetDot + 1.0E-12 < minimumDot) {
            return failure("targetOutsideEnvelope", (int) ARAD_LAUNCH_HALF_ANGLE_DEGREES);
        }
        return null;
    }

    @Nullable
    private static AssemblyException gpsTargetFailure(
            BlockPos controllerPos,
            MissileGuidanceData guidance,
            Vec3 launchPosition
    ) {
        MissileTargetSpec target = guidance.target();
        if (target == null
                || target.type() != MissileTargetSpec.TargetType.POINT
                || !isFinite(target.point())
                || !isFinite(launchPosition)) {
            return failure("missingTarget");
        }

        Vec3 targetPoint = target.point();
        double dx = targetPoint.x - launchPosition.x;
        double dz = targetPoint.z - launchPosition.z;
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        double minimumDistance = configuredMinimumGpsLaunchHorizontalDistance();

        if (horizontalDistance < minimumDistance) {
            return failure("gpsTargetTooClose", String.format(Locale.ROOT, "%.1f", minimumDistance));
        }

        return null;
    }

    @Nullable
    private static AssemblyException horizontalTargetFailure(
            BlockPos controllerPos,
            MissileGuidanceType guidanceType,
            Vec3 launchPosition,
            Vec3 launchDirection,
            Vec3 targetPosition
    ) {
        if (!isFinite(launchPosition)
                || !isFinite(launchDirection)
                || launchDirection.lengthSqr() < MINIMUM_TARGET_VECTOR_LENGTH_SQR
                || !isFinite(targetPosition)) {
            return failure("invalidLaunchTransform");
        }

        Vec3 heading = launchDirection.normalize();
        if (!isHorizontalLaunchDirection(heading)) {
            return null;
        }

        Vec3 toTarget = targetPosition.subtract(launchPosition);
        if (toTarget.lengthSqr() < MINIMUM_TARGET_VECTOR_LENGTH_SQR) {
            return failure("targetCoincides");
        }

        double targetDot = heading.dot(toTarget.normalize());
        double minimumDot = Math.cos(Math.toRadians(HORIZONTAL_LAUNCH_HALF_ANGLE_DEGREES));
        if (targetDot + 1.0E-12 >= minimumDot) {
            return null;
        }
        return failure("targetOutsideEnvelope", (int) HORIZONTAL_LAUNCH_HALF_ANGLE_DEGREES);
    }

    private static boolean isHorizontalLaunchDirection(Vec3 launchDirection) {
        if (!isFinite(launchDirection) || launchDirection.lengthSqr() < MINIMUM_TARGET_VECTOR_LENGTH_SQR) {
            return false;
        }
        Vec3 heading = launchDirection.normalize();
        double horizontalComponentSqr = heading.x * heading.x + heading.z * heading.z;
        double verticalComponentSqr = heading.y * heading.y;
        return horizontalComponentSqr > verticalComponentSqr;
    }

    private static double configuredMinimumGpsLaunchHorizontalDistance() {
        return Math.max(0.0, KaboomConfig.server().minimumGpsLaunchHorizontalDistance.getF());
    }

    private static int configuredTargetDataTimeoutTicks() {
        return Math.max(0, KaboomConfig.server().targetDataTimeoutTicks.get());
    }

    private static boolean isFinite(Vec3 v) {
        return v != null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
    }

    private static AssemblyException failure(String reason, Object... args) {
        return new AssemblyException(Component.translatable(
                "exception." + CreateKaboom.MODID + ".missile." + reason, args));
    }

    private static void logLaunchFailure(BlockPos pos, AssemblyException failure) {
        CreateKaboom.getLogger().info("Rejected missile launch at {}: {}", pos, failure.component.getString());
    }

    public static void requestLaunch(ServerLevel level, BlockPos triggeringThrusterPos) {

        BlockPos controller = MissileAssembler.findControllerThruster(level, triggeringThrusterPos);

        if (controller == null) {
            return;
        }

        try {
            assembleAndSpawn(level, controller);
        } catch (AssemblyException e) {
            throw new RuntimeException(e);
        }
    }
}
