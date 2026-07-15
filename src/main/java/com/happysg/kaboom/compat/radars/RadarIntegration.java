package com.happysg.kaboom.compat.radars;

import com.happysg.kaboom.block.missiles.nav.MovingTargetResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;

import javax.annotation.Nullable;
import java.util.UUID;

public interface RadarIntegration {
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

    @Nullable
    MovingTargetResolver.TargetData resolveCommandTarget(ServerLevel level, @Nullable BlockPos controllerPos,
                                                         @Nullable String preferredTargetId,
                                                         boolean includeSuppressedTrack);

    @Nullable
    MovingTargetResolver.TargetData resolveLegacyRadarTarget(ServerLevel level, @Nullable BlockPos radarGuidancePos,
                                                             Vec3 missilePosition);

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
}
