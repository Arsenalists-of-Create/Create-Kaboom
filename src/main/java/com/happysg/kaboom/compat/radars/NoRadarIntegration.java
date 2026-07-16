package com.happysg.kaboom.compat.radars;

import com.happysg.kaboom.block.missiles.nav.MovingTargetResolver;
import com.happysg.kaboom.block.missiles.util.ARADTargetReference;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

final class NoRadarIntegration implements RadarIntegration {
    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public MovingTargetResolver.TargetData resolveCommandTarget(ServerLevel level, @Nullable BlockPos controllerPos,
                                                                @Nullable String preferredTargetId,
                                                                boolean includeSuppressedTrack) {
        return null;
    }

    @Override
    public MovingTargetResolver.TargetData resolveLegacyRadarTarget(ServerLevel level,
                                                                    @Nullable BlockPos radarGuidancePos,
                                                                    Vec3 missilePosition) {
        return null;
    }

    @Override
    public Vec3 resolveAradTarget(ServerLevel level, ARADTargetReference targetReference) {
        return null;
    }

    @Override
    public Vec3 resolveAradEmitterPosition(ServerLevel level, ARADTargetReference targetReference) {
        return null;
    }

    @Override
    public ARADTargetReference acquireAradTarget(ServerLevel level, AradAcquisitionRequest request) {
        return null;
    }

    @Override
    public ChaffSuppression getCommandChaffSuppression(ServerLevel level, @Nullable BlockPos controllerPos) {
        return null;
    }

    @Override
    public void markCommandEngaged(ServerLevel level, @Nullable BlockPos controllerPos,
                                   MovingTargetResolver.TargetData target) {
    }
}
