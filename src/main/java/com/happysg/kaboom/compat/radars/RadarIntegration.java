package com.happysg.kaboom.compat.radars;

import com.happysg.kaboom.block.missiles.nav.MovingTargetResolver;
import com.happysg.kaboom.block.missiles.util.ARADTargetReference;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;

import javax.annotation.Nullable;
import java.util.UUID;

public interface RadarIntegration {
    record AradAcquisitionRequest(
            Vec3 sensorOrigin,
            Vec3 sensorForward,
            @Nullable UUID launcherSublevelId,
            double halfAngleDegrees,
            double maxRangeBlocks
    ) {
        public AradAcquisitionRequest(Vec3 sensorOrigin, Vec3 sensorForward,
                                      @Nullable UUID launcherSublevelId, double halfAngleDegrees) {
            this(sensorOrigin, sensorForward, launcherSublevelId, halfAngleDegrees, Double.MAX_VALUE);
        }
    }

    record ChaffSuppression(String targetId, long untilTick) {
    }

    enum ThreatStage {
        IN_RANGE,
        LOCKED,
        ENGAGED;

        public ThreatStage downgraded() {
            return switch (this) {
                case ENGAGED -> LOCKED;
                case LOCKED, IN_RANGE -> IN_RANGE;
            };
        }
    }

    boolean isAvailable();

    default void register(IEventBus modEventBus) {
    }

    /** Resolves the Create: Radar weapon-group filterer associated with a launcher mount. */
    @Nullable
    default BlockPos resolveWeaponMountController(ServerLevel level, @Nullable BlockPos mountPos) {
        return null;
    }

    @Nullable
    MovingTargetResolver.TargetData resolveCommandTarget(ServerLevel level, @Nullable BlockPos controllerPos,
                                                         @Nullable String preferredTargetId,
                                                         boolean includeSuppressedTrack);

    @Nullable
    MovingTargetResolver.TargetData resolveLegacyRadarTarget(ServerLevel level, @Nullable BlockPos radarGuidancePos,
                                                             Vec3 missilePosition);

    /** Resolves and validates the current noisy aim point for a native radar designation. */
    @Nullable
    Vec3 resolveAradTarget(ServerLevel level, ARADTargetReference targetReference);

    /** Resolves the exact current emitter position for launch-envelope validation. */
    @Nullable
    Vec3 resolveAradEmitterPosition(ServerLevel level, ARADTargetReference targetReference);

    /** Passively acquires the best currently emitting native radar for an ARAD seeker. */
    @Nullable
    ARADTargetReference acquireAradTarget(ServerLevel level, AradAcquisitionRequest request);

    @Nullable
    ChaffSuppression getCommandChaffSuppression(ServerLevel level, @Nullable BlockPos controllerPos);

    void markCommandEngaged(ServerLevel level, @Nullable BlockPos controllerPos,
                            MovingTargetResolver.TargetData target);

    default void updateRadarEmitter(ServerLevel level, UUID emitterId, Vec3 position, Vec3 forward,
                                    double range, double halfAngleDegrees, @Nullable UUID targetShipId,
                                    ThreatStage stage) {
    }

    default void removeRadarEmitter(ServerLevel level, UUID emitterId) {
    }

    /** Publishes one assembled guided missile as a selectable airborne RWR emitter. */
    default void updateGuidedMissileEmitter(ServerLevel level, UUID missileId, Vec3 position,
                                            @Nullable UUID targetShipId, ThreatStage stage) {
    }

    default void removeGuidedMissileEmitter(ServerLevel level, UUID missileId) {
    }
}
