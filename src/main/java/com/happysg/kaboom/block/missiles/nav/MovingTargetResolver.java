package com.happysg.kaboom.block.missiles.nav;

import com.happysg.kaboom.block.missiles.parts.guidance.radar.RadarGuidanceBlockEntity;
import com.happysg.kaboom.block.missiles.util.MissileGuidanceData;
import com.happysg.kaboom.compat.sable.SableUtils;
import com.happysg.radar.block.controller.networkcontroller.NetworkFiltererBlockEntity;
import com.happysg.radar.block.radar.track.RadarTrack;
import com.happysg.radar.block.radar.track.RadarTrackUtil;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.UUID;

public final class MovingTargetResolver {
    private MovingTargetResolver() {
    }

    public record TargetData(
            String id,
            String category,
            Vec3 position,
            Vec3 velocity,
            long scannedTime,
            boolean live,
            String source
    ) {
        public int ageTicks(ServerLevel level) {
            return (int) Math.max(0L, level.getGameTime() - scannedTime);
        }
    }

    @Nullable
    public static TargetData resolve(ServerLevel level, MissileGuidanceData guidance, Vec3 missilePosition) {
        if (guidance == null) return null;

        RadarTrack track = switch (guidance.guidanceType()) {
            case COMMAND -> commandTrack(level, guidance.networkControllerPos());
            case RADAR -> RadarGuidanceBlockEntity.acquireGuidanceTrack(level, guidance.radarGuidancePos(), missilePosition);
            default -> null;
        };

        if (track == null) return null;
        return resolveTrack(level, track);
    }

    @Nullable
    private static RadarTrack commandTrack(ServerLevel level, @Nullable BlockPos controllerPos) {
        if (controllerPos == null) return null;
        BlockEntity be = level.getBlockEntity(controllerPos);
        if (be instanceof NetworkFiltererBlockEntity controller) {
            return controller.activeTrackCache;
        }
        return null;
    }

    @Nullable
    private static TargetData resolveTrack(ServerLevel level, RadarTrack track) {
        String id = track.getId();
        String category = track.getTrackCategory() == null ? "unknown" : track.getTrackCategory().name().toLowerCase(Locale.ROOT);
        Vec3 fallbackPosition = track.getPosition();
        Vec3 fallbackVelocity = track.getVelocity();
        long scannedTime = track.getScannedTime();

        UUID uuid = parseUuid(id);
        if (uuid != null) {
            Entity entity = level.getEntity(uuid);
            if (entity != null && entity.isAlive()) {
                return new TargetData(id, category, entity.position(), entity.getDeltaMovement(), level.getGameTime(), true, "entity");
            }

            if (isFinite(fallbackPosition)) {
                SubLevelAccess subLevel = SableUtils.getLoadedSubLevel(level, uuid, fallbackPosition);
                if (subLevel != null) {
                    Vec3 position = RadarTrackUtil.getPosition(subLevel);
                    Vec3 velocity = RadarTrackUtil.getVelocity(subLevel, level);
                    if (isFinite(position) && isFinite(velocity)) {
                        return new TargetData(id, category, position, velocity, level.getGameTime(), true, "sable");
                    }
                }
            }
        }

        if (isFinite(fallbackPosition) && isFinite(fallbackVelocity)) {
            return new TargetData(id, category, fallbackPosition, fallbackVelocity, scannedTime, false, "track_fallback");
        }

        return null;
    }

    @Nullable
    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static boolean isFinite(Vec3 v) {
        return v != null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
    }
}
