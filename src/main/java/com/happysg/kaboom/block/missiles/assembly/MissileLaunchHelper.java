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
import com.happysg.kaboom.compat.sable.SableUtils;
import com.happysg.kaboom.config.KaboomConfig;
import com.happysg.kaboom.registry.ModEntities;
import com.simibubi.create.content.contraptions.AssemblyException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

public class MissileLaunchHelper {
    private static final int MINIMUM_LAUNCH_FUEL_MB = 250;

    public static boolean assembleAndSpawn(ServerLevel level, BlockPos anyThrusterPos) throws AssemblyException {
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

        if (!isGuidanceValidForLaunch(level, controllerPos, guidance)) {
            return rejectLaunch(level, result, anyThrusterPos);
        }

        MissileContraption mc = MissileContraptionBuilder.build(level, result, warheadWorldPos);

        if (mc.fuelAmountMb < MINIMUM_LAUNCH_FUEL_MB) {
            CreateKaboom.getLogger().info(
                    "Rejected missile launch at {}: fuelAmountMb={} minimumFuelMb={}",
                    controllerPos,
                    mc.fuelAmountMb,
                    MINIMUM_LAUNCH_FUEL_MB
            );
            return rejectLaunch(level, result, anyThrusterPos);
        }

        mc.guidanceTag = guidance.toTag();

        Vec3 localLaunchDirection = Vec3.atLowerCornerOf(result.getAssemblyDirection().getNormal());
        SableUtils.LaunchKinematics launch = SableUtils.getLaunchKinematics(
                level, controllerPos, controllerPos.getCenter(), localLaunchDirection
        );

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
            entity.onRadarMissileLaunched();
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

    private static boolean isGuidanceValidForLaunch(ServerLevel level, BlockPos controllerPos, MissileGuidanceData guidance) {
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

            MovingTargetResolver.TargetData target = MovingTargetResolver.resolve(level, guidance, controllerPos.getCenter());
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

            return true;
        }
        return isGpsTargetFarEnoughToLaunch(controllerPos, guidance);
    }

    private static boolean isGpsTargetFarEnoughToLaunch(BlockPos controllerPos, MissileGuidanceData guidance) {
        MissileTargetSpec target = guidance.target();
        if (target == null || target.type() != MissileTargetSpec.TargetType.POINT || !isFinite(target.point())) {
            return false;
        }

        Vec3 targetPoint = target.point();
        Vec3 launcher = controllerPos.getCenter();
        double dx = targetPoint.x - launcher.x;
        double dz = targetPoint.z - launcher.z;
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
