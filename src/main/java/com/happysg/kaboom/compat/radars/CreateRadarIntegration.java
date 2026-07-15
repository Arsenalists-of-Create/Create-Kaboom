package com.happysg.kaboom.compat.radars;

import com.happysg.kaboom.block.missiles.MissileEntity;
import com.happysg.kaboom.block.missiles.nav.MovingTargetResolver;
import com.happysg.kaboom.compat.sable.SableUtils;
import com.happysg.kaboom.config.KaboomConfig;
import com.happysg.kaboom.registry.ModEntities;
import com.happysg.radar.block.arad.aradnetworks.RadarContactRegistry;
import com.happysg.radar.block.arad.aradnetworks.RadarContactRegistryData;
import com.happysg.radar.block.arad.rwr.ExternalRwrEmitterRegistry;
import com.happysg.radar.block.arad.rwr.RadarType;
import com.happysg.radar.block.controller.networkcontroller.NetworkFiltererBlockEntity;
import com.happysg.radar.block.radar.behavior.IRadar;
import com.happysg.radar.block.radar.track.RadarTrack;
import com.happysg.radar.block.radar.track.TrackCategory;
import com.happysg.radar.chaff.ChaffLockAdapter;
import com.happysg.radar.chaff.ChaffLockRegistry;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.UUID;

final class CreateRadarIntegration implements RadarIntegration {
    private static final int LEGACY_RADAR_SEARCH_RADIUS_BLOCKS = 512;
    private static final int EXTERNAL_EMITTER_TTL_TICKS = 5;
    private static final String EXTERNAL_EMITTER_PREFIX = "create_kaboom:radar_guidance:";

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void register(IEventBus modEventBus) {
        NeoForge.EVENT_BUS.register(new CommandGuidanceInteractionHandler());
        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> ChaffLockRegistry.register(ModEntities.MISSILE.get(),
                new ChaffLockAdapter<MissileEntity>() {
                    @Override
                    public String getTargetId(MissileEntity missile) {
                        return missile.getRadarChaffTargetId();
                    }

                    @Override
                    public void applySuppression(MissileEntity missile, String targetId, long untilTick) {
                        missile.applyRadarChaffSuppression(targetId, untilTick);
                    }
                }));
    }

    @Override
    public MovingTargetResolver.TargetData resolveCommandTarget(ServerLevel level, @Nullable BlockPos controllerPos,
                                                                @Nullable String preferredTargetId,
                                                                boolean includeSuppressedTrack) {
        if (controllerPos == null
                || !(level.getBlockEntity(controllerPos) instanceof NetworkFiltererBlockEntity controller)) {
            return null;
        }
        RadarTrack track = includeSuppressedTrack
                ? controller.getSelectedTrackForChaff(preferredTargetId)
                : controller.activeTrackCache;
        return resolveTrack(level, track);
    }

    @Override
    public ChaffSuppression getCommandChaffSuppression(ServerLevel level, @Nullable BlockPos controllerPos) {
        if (controllerPos == null
                || !(level.getBlockEntity(controllerPos) instanceof NetworkFiltererBlockEntity controller)) {
            return null;
        }
        NetworkFiltererBlockEntity.ChaffSuppression suppression = controller.getActiveChaffSuppression();
        return suppression == null ? null : new ChaffSuppression(suppression.targetId(), suppression.untilTick());
    }

    @Override
    public void markCommandEngaged(ServerLevel level, @Nullable BlockPos controllerPos,
                                   MovingTargetResolver.TargetData target) {
        if (!TrackCategory.SABLE.name().equalsIgnoreCase(target.category())) {
            return;
        }
        UUID shipId = parseUuid(target.id());
        if (shipId == null) {
            return;
        }

        String sourceId = null;
        if (controllerPos != null
                && level.getBlockEntity(controllerPos) instanceof NetworkFiltererBlockEntity controller) {
            sourceId = controller.getCommandGuidanceRadarSourceId(level, shipId);
        }
        if (sourceId == null) {
            RadarContactRegistry.markEngaged(level, shipId, RadarContactRegistryData.DEFAULT_ENGAGED_TTL);
        } else {
            RadarContactRegistry.markEngaged(level, shipId, sourceId,
                    RadarContactRegistryData.DEFAULT_ENGAGED_TTL);
        }
    }

    @Override
    public void updateRadarEmitter(ServerLevel level, UUID emitterId, Vec3 position, Vec3 forward,
                                   double range, double halfAngleDegrees, @Nullable UUID targetShipId,
                                   ThreatStage stage) {
        if (emitterId == null || stage == null) {
            return;
        }
        ExternalRwrEmitterRegistry.heartbeat(
                level,
                new ExternalRwrEmitterRegistry.EmitterState(
                        externalEmitterSourceId(emitterId),
                        position,
                        forward,
                        range,
                        halfAngleDegrees,
                        targetShipId,
                        ExternalRwrEmitterRegistry.ThreatStage.valueOf(stage.name()),
                        RadarType.AIRBORNE,
                        true
                ),
                EXTERNAL_EMITTER_TTL_TICKS
        );
    }

    @Override
    public void removeRadarEmitter(ServerLevel level, UUID emitterId) {
        if (emitterId != null) {
            ExternalRwrEmitterRegistry.remove(level, externalEmitterSourceId(emitterId));
        }
    }

    @Override
    public MovingTargetResolver.TargetData resolveLegacyRadarTarget(ServerLevel level,
                                                                    @Nullable BlockPos radarGuidancePos,
                                                                    Vec3 missilePosition) {
        Vec3 origin = radarGuidancePos == null ? missilePosition : radarGuidancePos.getCenter();
        int radius = LEGACY_RADAR_SEARCH_RADIUS_BLOCKS;
        int minChunkX = ((int) Math.floor(origin.x - radius)) >> 4;
        int maxChunkX = ((int) Math.floor(origin.x + radius)) >> 4;
        int minChunkZ = ((int) Math.floor(origin.z - radius)) >> 4;
        int maxChunkZ = ((int) Math.floor(origin.z + radius)) >> 4;

        RadarTrack best = null;
        double bestScore = Double.MAX_VALUE;
        long now = level.getGameTime();
        int timeout = Math.max(0, KaboomConfig.server().targetDataTimeoutTicks.get());

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!level.hasChunk(chunkX, chunkZ)) continue;
                LevelChunk chunk = level.getChunk(chunkX, chunkZ);
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (!(blockEntity instanceof IRadar radar) || !radar.isRunning()) continue;
                    for (RadarTrack track : radar.getTracks()) {
                        if (track == null || track.getPosition() == null) continue;
                        int age = (int) Math.max(0L, now - track.getScannedTime());
                        if (age > timeout || track.getPosition().distanceTo(origin) > radius) continue;

                        double score = age * 1_000_000.0 + track.getPosition().distanceToSqr(missilePosition);
                        if (score < bestScore) {
                            bestScore = score;
                            best = track;
                        }
                    }
                }
            }
        }
        return resolveTrack(level, best);
    }

    @Nullable
    private static MovingTargetResolver.TargetData resolveTrack(ServerLevel level, @Nullable RadarTrack track) {
        if (track == null) return null;
        String id = track.getId();
        String category = track.getTrackCategory() == null
                ? "unknown"
                : track.getTrackCategory().name().toLowerCase(Locale.ROOT);
        Vec3 fallbackPosition = track.getPosition();
        Vec3 fallbackVelocity = track.getVelocity();
        long scannedTime = track.getScannedTime();

        UUID uuid = parseUuid(id);
        if (uuid != null) {
            Entity entity = level.getEntity(uuid);
            if (entity != null && entity.isAlive()) {
                return new MovingTargetResolver.TargetData(id, category, entity.position(),
                        entity.getDeltaMovement(), level.getGameTime(), true, "entity");
            }

            if (isFinite(fallbackPosition)) {
                SubLevelAccess subLevel = SableUtils.getLoadedSubLevel(level, uuid, fallbackPosition);
                if (subLevel != null) {
                    Vec3 position = SableUtils.getSubLevelPosition(subLevel);
                    Vec3 velocity = SableUtils.getSubLevelVelocity(level, subLevel);
                    if (isFinite(position) && isFinite(velocity)) {
                        return new MovingTargetResolver.TargetData(id, category, position, velocity,
                                level.getGameTime(), true, "sable");
                    }
                }
            }
        }

        if (isFinite(fallbackPosition) && isFinite(fallbackVelocity)) {
            return new MovingTargetResolver.TargetData(id, category, fallbackPosition, fallbackVelocity,
                    scannedTime, false, "track_fallback");
        }
        return null;
    }

    @Nullable
    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static boolean isFinite(Vec3 value) {
        return value != null
                && Double.isFinite(value.x)
                && Double.isFinite(value.y)
                && Double.isFinite(value.z);
    }

    private static String externalEmitterSourceId(UUID emitterId) {
        return EXTERNAL_EMITTER_PREFIX + emitterId;
    }
}
