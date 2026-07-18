package com.happysg.kaboom.block.missiles.assembly;

import com.happysg.kaboom.CreateKaboom;
import com.happysg.kaboom.block.missiles.MissileContraption;
import com.happysg.kaboom.block.missiles.MissileEntity;
import com.happysg.kaboom.block.missiles.chaining.ChainSystem;
import com.happysg.kaboom.block.missiles.nav.MovingTargetResolver;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
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
        MissileAssemblyResult result = MissileAssembler.scan(level, anyThrusterPos);
        if (!result.isValid()) return rejectLaunch(level, result, anyThrusterPos);

        BlockPos controllerPos = result.getControllerPos();
        MissileSize missileSize = result.getMissileSize();
        int fuelTankCount = result.getFuelTankCount();
        if (missileSize == null || missileSize.isOverweight(fuelTankCount)) {
            CreateKaboom.getLogger().info(
                    "Rejected missile launch at {}: missileSize={} fuelTankCount={} rejectionThreshold={}",
                    controllerPos,
                    missileSize,
                    fuelTankCount,
                    missileSize == null ? 0 : missileSize.fuelTankLaunchRejectionThreshold()
            );
            return rejectLaunch(level, result, anyThrusterPos);
        }

        BlockPos warheadWorldPos = result.getWarhead();
        BlockPos warheadLocalPos = warheadWorldPos.subtract(controllerPos);

        Vec3 localLaunchDirection = Vec3.atLowerCornerOf(result.getAssemblyDirection().getNormal());
        SableUtils.LaunchKinematics launch = SableUtils.getLaunchKinematics(
                level, controllerPos, controllerPos.getCenter(), localLaunchDirection
        );

        BlockPos guidanceWorldPos = result.guidance();

        MissileGuidanceData guidance = null;
        if (guidanceWorldPos != null) {
            BlockEntity be = level.getBlockEntity(guidanceWorldPos);
            if (be instanceof IMissileGuidanceProvider provider) {
                guidance = provider.exportGuidance();
            }
        }

        if (guidance == null) {
            return rejectLaunch(level, result, anyThrusterPos);
        }

        if (!isGuidanceValidForLaunch(level, controllerPos, guidance, launch.position(), launch.direction())) {
            return rejectLaunch(level, result, anyThrusterPos);
        }

        MissileContraption mc = MissileContraptionBuilder.build(level, result, warheadWorldPos);

        if (requireMinimumFuel && mc.fuelAmountMb < MINIMUM_LAUNCH_FUEL_MB) {
            CreateKaboom.getLogger().info(
                    "Rejected missile launch at {}: fuelAmountMb={} minimumFuelMb={}",
                    controllerPos,
                    mc.fuelAmountMb,
                    MINIMUM_LAUNCH_FUEL_MB
            );
            return rejectLaunch(level, result, anyThrusterPos);
        }

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
            if (KaboomConfig.server().delayedMissileLaunch.get()) {
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
        MissileAssemblyResult result = MissileAssembler.scan(level, anyThrusterPos);
        if (!result.isValid() || result.getMissileSize() == null
                || result.getMissileSize().isOverweight(result.getFuelTankCount())) {
            rejectLaunch(level, result, anyThrusterPos);
            return null;
        }

        BlockPos controllerPos = result.getControllerPos();
        Vec3 localDirection = Vec3.atLowerCornerOf(result.getAssemblyDirection().getNormal());
        SableUtils.LaunchKinematics launch = SableUtils.getLaunchKinematics(
                level, controllerPos, controllerPos.getCenter(), localDirection);
        BlockEntity guidanceBlockEntity = result.guidance() == null ? null : level.getBlockEntity(result.guidance());
        if (!(guidanceBlockEntity instanceof IMissileGuidanceProvider provider)) {
            rejectLaunch(level, result, anyThrusterPos);
            return null;
        }
        MissileGuidanceData guidance = provider.exportGuidance();
        if (!isGuidanceValidForLaunch(level, controllerPos, guidance, launch.position(), launch.direction())) {
            rejectLaunch(level, result, anyThrusterPos);
            return null;
        }
        MissileContraption captured = MissileContraptionBuilder.build(level, result, result.getWarhead());
        if (captured.fuelAmountMb < MINIMUM_LAUNCH_FUEL_MB) {
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

    public static boolean launchMounted(ServerLevel level, MissileContraption mounted,
                                        PitchOrientedContraptionEntity mountedEntity) {
        MountedLaunchContext context = validateMountedLaunch(level, mounted, mountedEntity, false);
        if (context == null) return false;
        BlockPos sourcePos = context.sourcePos;
        MountedMissileController missileController = context.controller;
        MissileGuidanceData guidance = context.guidance;
        SableUtils.LaunchKinematics launch = context.launch;

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
        if (!missileController.createKaboom$releaseMountedMissile(mountedEntity)) {
            missile.discard();
            return rejectMountedLaunch(level, mounted, mountedEntity);
        }
        level.playSound(null, nozzle.x, nozzle.y, nozzle.z,
                com.happysg.kaboom.registry.ModSounds.MISSILE_BOOF.get(),
                net.minecraft.sounds.SoundSource.AMBIENT, 0.55F, 1.0F);
        if (KaboomConfig.server().delayedMissileLaunch.get()) {
            NetworkHandler.sendToPlayersTrackingEntity(missile,
                    LaunchSoundHandoffPacket.mountedMissile(mountedEntity.getId(), missile.getId()));
        }
        mountedEntity.discard();

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
        return validateMountedLaunch(level, mounted, mountedEntity, true) != null;
    }

    private static MountedLaunchContext validateMountedLaunch(ServerLevel level, MissileContraption mounted,
                                                               PitchOrientedContraptionEntity mountedEntity,
                                                               boolean requireLaunchFuel) {
        BlockPos sourcePos = mountedEntity.blockPosition();
        ControlPitchContraption controller = mountedEntity.getController();
        if (controller instanceof BlockEntity blockEntity) {
            sourcePos = blockEntity.getBlockPos();
        }

        if (!(controller instanceof MountedMissileController missileController)
                || mounted.missileSize == null
                || mounted.missileSize == MissileSize.HUGE
                || mounted.missileSize.isOverweight(mounted.fuelTankCount)) {
            rejectMountedLaunch(level, mounted, mountedEntity);
            return null;
        }

        MissileGuidanceData guidance;
        try {
            guidance = mounted.guidanceTag == null || mounted.guidanceTag.isEmpty()
                    ? null
                    : MissileGuidanceData.fromTag(mounted.guidanceTag);
        } catch (RuntimeException exception) {
            CreateKaboom.getLogger().warn("Rejected mounted missile launch at {}: invalid guidance data", sourcePos, exception);
            rejectMountedLaunch(level, mounted, mountedEntity);
            return null;
        }
        if (guidance == null || requireLaunchFuel && mounted.fuelAmountMb < MINIMUM_LAUNCH_FUEL_MB) {
            rejectMountedLaunch(level, mounted, mountedEntity);
            return null;
        }

        BlockPos controllerLocalPos = mounted.getStartPos();
        Direction localForward = mounted.assemblyDirection == null ? mounted.initialOrientation() : mounted.assemblyDirection;
        Vec3 localControllerCenter = Vec3.atCenterOf(controllerLocalPos);
        Vec3 mountedControllerPosition = mountedEntity.toGlobalVector(localControllerCenter, 0);
        Vec3 mountedForwardPosition = mountedEntity.toGlobalVector(
                Vec3.atCenterOf(controllerLocalPos.relative(localForward)), 0);
        Vec3 mountedDirection = mountedForwardPosition.subtract(mountedControllerPosition);
        if (mountedDirection.lengthSqr() < MINIMUM_TARGET_VECTOR_LENGTH_SQR) {
            rejectMountedLaunch(level, mounted, mountedEntity);
            return null;
        }

        SableUtils.LaunchKinematics sourceKinematics = SableUtils.getLaunchKinematics(
                level, sourcePos, sourcePos.getCenter(), Vec3.atLowerCornerOf(localForward.getNormal()));
        Vec3 observedCarrierVelocity = mounted.observedNozzleVelocity();
        if (!isFinite(observedCarrierVelocity)) observedCarrierVelocity = sourceKinematics.carrierVelocity();
        SableUtils.LaunchKinematics launch = new SableUtils.LaunchKinematics(
                mountedControllerPosition, mountedDirection.normalize(), observedCarrierVelocity,
                sourceKinematics.sourceSubLevelId());
        if (!isGuidanceValidForLaunch(level, sourcePos, guidance, launch.position(), launch.direction())) {
            return null;
        }
        return new MountedLaunchContext(sourcePos, missileController, guidance, launch);
    }

    private record MountedLaunchContext(BlockPos sourcePos, MountedMissileController controller,
                                        MissileGuidanceData guidance, SableUtils.LaunchKinematics launch) {}

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

    private static boolean isGuidanceValidForLaunch(
            ServerLevel level,
            BlockPos controllerPos,
            MissileGuidanceData guidance,
            Vec3 launchPosition,
            Vec3 launchDirection
    ) {
        if (guidance.guidanceType() == MissileGuidanceType.ARAD) {
            return isAradTargetValidForLaunch(level, controllerPos, guidance, launchPosition, launchDirection);
        }
        if (guidance.guidanceType().isInterceptor()) {
            if (guidance.guidanceType() == MissileGuidanceType.COMMAND && !RadarCompatRegistry.isAvailable()) {
                CreateKaboom.getLogger().info(
                        "Rejected command-guided missile launch at {}: Create Radar compatibility is unavailable",
                        controllerPos
                );
                return false;
            }

            boolean hasResolverMetadata = switch (guidance.guidanceType()) {
                case COMMAND -> guidance.networkControllerPos() != null;
                case RADAR -> guidance.radarGuidancePos() != null;
                default -> false;
            };
            if (!hasResolverMetadata) return false;

            MovingTargetResolver.TargetData target = MovingTargetResolver.resolve(level, guidance, launchPosition);
            if (target == null) {
                CreateKaboom.getLogger().info(
                        "Rejected {} missile launch at {}: no selected target",
                        guidance.guidanceType(),
                        controllerPos
                );
                return false;
            }

            if (!target.live() && target.ageTicks(level) > configuredTargetDataTimeoutTicks()) {
                CreateKaboom.getLogger().info(
                        "Rejected {} missile launch at {}: selected target data is stale ageTicks={} timeoutTicks={}",
                        guidance.guidanceType(),
                        controllerPos,
                        target.ageTicks(level),
                        configuredTargetDataTimeoutTicks()
                );
                return false;
            }

            return isTargetValidForHorizontalLaunch(
                    controllerPos,
                    guidance.guidanceType(),
                    launchPosition,
                    launchDirection,
                    target.position()
            );
        }
        if (!isGpsTargetFarEnoughToLaunch(controllerPos, guidance, launchPosition)) {
            return false;
        }
        return isTargetValidForHorizontalLaunch(
                controllerPos,
                guidance.guidanceType(),
                launchPosition,
                launchDirection,
                guidance.target().point()
        );
    }

    private static boolean isAradTargetValidForLaunch(
            ServerLevel level,
            BlockPos controllerPos,
            MissileGuidanceData guidance,
            Vec3 launchPosition,
            Vec3 launchDirection
    ) {
        if (!RadarCompatRegistry.isAvailable()) {
            CreateKaboom.getLogger().info(
                    "Rejected ARAD missile launch at {}: Create Radar compatibility is unavailable",
                    controllerPos
            );
            return false;
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
            CreateKaboom.getLogger().info(
                    "Rejected ARAD missile launch at {}: no valid running radar target or target coordinates",
                    controllerPos
            );
            return false;
        }
        if (!isFinite(launchPosition) || !isFinite(launchDirection) || launchDirection.lengthSqr() < MINIMUM_TARGET_VECTOR_LENGTH_SQR) {
            CreateKaboom.getLogger().info(
                    "Rejected ARAD missile launch at {}: invalid world-space launch transform",
                    controllerPos
            );
            return false;
        }

        Vec3 toTarget = launchEnvelopeTarget.subtract(launchPosition);
        if (toTarget.lengthSqr() < MINIMUM_TARGET_VECTOR_LENGTH_SQR) {
            CreateKaboom.getLogger().info(
                    "Rejected ARAD missile launch at {}: target coincides with launch position",
                    controllerPos
            );
            return false;
        }

        if (!isHorizontalLaunchDirection(launchDirection)) {
            return true;
        }

        double targetDot = launchDirection.normalize().dot(toTarget.normalize());
        double minimumDot = Math.cos(Math.toRadians(ARAD_LAUNCH_HALF_ANGLE_DEGREES));
        if (targetDot + 1.0E-12 < minimumDot) {
            double targetAngle = Math.toDegrees(Math.acos(Mth.clamp(targetDot, -1.0, 1.0)));
            CreateKaboom.getLogger().info(
                    "Rejected ARAD missile launch at {}: target={} angleDegrees={} maximumAngleDegrees={}",
                    controllerPos,
                    fmt(launchEnvelopeTarget),
                    String.format(Locale.ROOT, "%.3f", targetAngle),
                    String.format(Locale.ROOT, "%.3f", ARAD_LAUNCH_HALF_ANGLE_DEGREES)
            );
            return false;
        }
        return true;
    }

    private static boolean isGpsTargetFarEnoughToLaunch(
            BlockPos controllerPos,
            MissileGuidanceData guidance,
            Vec3 launchPosition
    ) {
        MissileTargetSpec target = guidance.target();
        if (target == null
                || target.type() != MissileTargetSpec.TargetType.POINT
                || !isFinite(target.point())
                || !isFinite(launchPosition)) {
            return false;
        }

        Vec3 targetPoint = target.point();
        double dx = targetPoint.x - launchPosition.x;
        double dz = targetPoint.z - launchPosition.z;
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        double minimumDistance = configuredMinimumGpsLaunchHorizontalDistance();

        if (horizontalDistance < minimumDistance) {
            CreateKaboom.getLogger().info(
                    "Rejected GPS missile launch at {}: target={} horizontalDistance={} minimumDistance={}",
                    controllerPos,
                    fmt(targetPoint),
                    String.format(Locale.ROOT, "%.3f", horizontalDistance),
                    String.format(Locale.ROOT, "%.3f", minimumDistance)
            );
            return false;
        }

        return true;
    }

    private static boolean isTargetValidForHorizontalLaunch(
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
            CreateKaboom.getLogger().info(
                    "Rejected {} missile launch at {}: invalid world-space launch direction or target position",
                    guidanceType,
                    controllerPos
            );
            return false;
        }

        Vec3 heading = launchDirection.normalize();
        if (!isHorizontalLaunchDirection(heading)) {
            return true;
        }

        Vec3 toTarget = targetPosition.subtract(launchPosition);
        if (toTarget.lengthSqr() < MINIMUM_TARGET_VECTOR_LENGTH_SQR) {
            CreateKaboom.getLogger().info(
                    "Rejected {} missile launch at {}: target coincides with launch position",
                    guidanceType,
                    controllerPos
            );
            return false;
        }

        double targetDot = heading.dot(toTarget.normalize());
        double minimumDot = Math.cos(Math.toRadians(HORIZONTAL_LAUNCH_HALF_ANGLE_DEGREES));
        if (targetDot + 1.0E-12 >= minimumDot) {
            return true;
        }

        double targetAngle = Math.toDegrees(Math.acos(Mth.clamp(targetDot, -1.0, 1.0)));
        CreateKaboom.getLogger().info(
                "Rejected horizontal {} missile launch at {}: target={} angleDegrees={} maximumAngleDegrees={}",
                guidanceType,
                controllerPos,
                fmt(targetPosition),
                String.format(Locale.ROOT, "%.3f", targetAngle),
                String.format(Locale.ROOT, "%.3f", HORIZONTAL_LAUNCH_HALF_ANGLE_DEGREES)
        );
        return false;
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

    private static String fmt(Vec3 v) {
        return String.format(Locale.ROOT, "(%.3f, %.3f, %.3f)", v.x, v.y, v.z);
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
