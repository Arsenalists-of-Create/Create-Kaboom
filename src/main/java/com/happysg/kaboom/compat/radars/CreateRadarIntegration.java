package com.happysg.kaboom.compat.radars;

import com.happysg.kaboom.block.missiles.MissileEntity;
import com.happysg.kaboom.block.missiles.nav.MovingTargetResolver;
import com.happysg.kaboom.block.missiles.parts.guidance.arad.ARADGuidanceBlockEntity;
import com.happysg.kaboom.block.missiles.parts.guidance.radar.RadarTargeting;
import com.happysg.kaboom.block.missiles.util.ARADTargetReference;
import com.happysg.kaboom.compat.Mods;
import com.happysg.kaboom.compat.sable.SableUtils;
import com.happysg.kaboom.config.KaboomConfig;
import com.happysg.kaboom.items.rocket.UnguidedRocketProjectile;
import com.happysg.kaboom.registry.ModEntities;
import com.happysg.kaboom.registry.ModProjectiles;
import com.happysg.radar.block.behavior.networks.NetworkData;
import com.happysg.radar.block.arad.aradnetworks.RadarContactRegistry;
import com.happysg.radar.block.arad.aradnetworks.RadarContactRegistryData;
import com.happysg.radar.block.arad.rwr.ExternalRwrEmitterRegistry;
import com.happysg.radar.block.arad.rwr.RadarType;
import com.happysg.radar.block.controller.networkcontroller.NetworkFiltererBlockEntity;
import com.happysg.radar.block.radar.behavior.IRadar;
import com.happysg.radar.block.radar.track.RadarTrack;
import com.happysg.radar.block.radar.track.TrackCategory;
import com.happysg.radar.api.arad.ARADTargeting;
import com.happysg.radar.api.arad.ARADTargetDesignationEvent;
import com.happysg.radar.chaff.ChaffLockAdapter;
import com.happysg.radar.chaff.ChaffLockRegistry;
import com.happysg.radar.compat.vs2.PhysicsHandler;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class CreateRadarIntegration implements RadarIntegration {
    private static final int LEGACY_RADAR_SEARCH_RADIUS_BLOCKS = 512;
    private static final int EXTERNAL_EMITTER_TTL_TICKS = 5;
    private static final String EXTERNAL_EMITTER_PREFIX = "create_kaboom:radar_guidance:";

    private record RankedAradContact(
            ARADTargeting.NativeRadarContact contact,
            double alignment,
            double distanceSqr
    ) {
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void register(IEventBus modEventBus) {
        NeoForge.EVENT_BUS.register(new CommandGuidanceInteractionHandler());
        NeoForge.EVENT_BUS.register(this);
        modEventBus.addListener(this::commonSetup);
    }

    @SubscribeEvent
    public void onAradTargetDesignation(ARADTargetDesignationEvent event) {
        if (event == null || event.level() == null || event.rwrPos() == null) {
            return;
        }

        if (event.action() == ARADTargetDesignationEvent.Action.CLEAR) {
            visitAradGuidanceBlocks(event.level(), event.rwrPos(),
                    blockEntity -> blockEntity.clearRadarTarget(event.sourceId()));
            return;
        }

        ARADTargetDesignationEvent.Target target = event.target();
        if (target == null || event.sourceId() == null || event.sourceId().isBlank()) {
            return;
        }
        ARADTargetReference reference = new ARADTargetReference(
                event.sourceId(),
                target.emitterId(),
                target.radarPos(),
                target.rangeRatio(),
                target.noisyWorldPosition(),
                target.targetSublevelId(),
                target.targetLocalPosition()
        );
        if (!reference.isValid()) {
            return;
        }
        visitAradGuidanceBlocks(event.level(), event.rwrPos(),
                blockEntity -> blockEntity.setRadarTarget(reference));
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ChaffLockRegistry.register(ModEntities.MISSILE.get(), new ChaffLockAdapter<MissileEntity>() {
                @Override
                public String getTargetId(MissileEntity missile) {
                    return missile.getRadarChaffTargetId();
                }

                @Override
                public void applySuppression(MissileEntity missile, String targetId, long untilTick) {
                    missile.applyRadarChaffSuppression(targetId, untilTick);
                }
            });
            ChaffLockRegistry.register(ModProjectiles.UNGUIDED_ROCKET.get(), new ChaffLockAdapter<UnguidedRocketProjectile>() {
                @Override
                public String getTargetId(UnguidedRocketProjectile rocket) {
                    return rocket.getRadarChaffTargetId();
                }

                @Override
                public void applySuppression(UnguidedRocketProjectile rocket, String targetId, long untilTick) {
                    rocket.applyRadarChaffSuppression(targetId, untilTick);
                }
            });
        });
    }

    @Override
    @Nullable
    public BlockPos resolveWeaponMountController(ServerLevel level, @Nullable BlockPos mountPos) {
        if (level == null || mountPos == null) {
            return null;
        }
        BlockPos filtererPos = NetworkData.get(level).getFiltererForWeaponMount(level.dimension(), mountPos);
        return filtererPos == null ? null : filtererPos.immutable();
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

    @Override
    @Nullable
    public Vec3 resolveAradTarget(ServerLevel level, ARADTargetReference targetReference) {
        if (level == null || targetReference == null || !targetReference.isValid()) {
            return null;
        }

        BlockPos radarPos = targetReference.radarPos();
        IRadar radar = resolveAradRadar(level, targetReference);
        if (radar == null) {
            return null;
        }

        if (!Mods.SABLE.isLoaded()) {
            return targetReference.isMovingTarget() ? null : targetReference.noisyWorldPosition();
        }
        SubLevelAccess containingSublevel = SableCompanion.INSTANCE.getContaining(level, radarPos);
        if (!targetReference.isMovingTarget()) {
            return containingSublevel == null ? targetReference.noisyWorldPosition() : null;
        }

        UUID targetSublevelId = targetReference.targetSublevelId();
        if (containingSublevel == null || !targetSublevelId.equals(containingSublevel.getUniqueId())) {
            return null;
        }
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return null;
        }
        SubLevel targetSublevel = container.getSubLevel(targetSublevelId);
        if (targetSublevel == null || targetSublevel.isRemoved()) {
            return null;
        }

        Vec3 resolved = SableUtils.getWorldVec(targetReference.targetLocalPosition(), targetSublevel);
        return isFinite(resolved) ? resolved : null;
    }

    @Override
    @Nullable
    public Vec3 resolveAradEmitterPosition(ServerLevel level, ARADTargetReference targetReference) {
        IRadar radar = resolveAradRadar(level, targetReference);
        if (radar == null) {
            return null;
        }
        Vec3 position = PhysicsHandler.getWorldVec(level, radar.getWorldPos().getCenter());
        return isFinite(position) ? position : null;
    }

    @Nullable
    private static IRadar resolveAradRadar(ServerLevel level, ARADTargetReference targetReference) {
        if (level == null || targetReference == null || !targetReference.isValid()) {
            return null;
        }
        BlockPos radarPos = targetReference.radarPos();
        if (!(level.getBlockEntity(radarPos) instanceof IRadar radar) || !radar.isRunning()) {
            return null;
        }
        if (!RadarContactRegistry.radarSourceId(level, radar.getWorldPos()).equals(targetReference.sourceId())) {
            return null;
        }
        return targetReference.emitterId() == null || targetReference.emitterId().equals(radar.getEmitterId())
                ? radar
                : null;
    }

    @Override
    @Nullable
    public ARADTargetReference acquireAradTarget(ServerLevel level, AradAcquisitionRequest request) {
        if (level == null || request == null
                || !isFinite(request.sensorOrigin())
                || !isFinite(request.sensorForward())
                || request.sensorForward().lengthSqr() < 1.0E-8
                || !Double.isFinite(request.halfAngleDegrees())
                || !Double.isFinite(request.maxRangeBlocks())
                || request.maxRangeBlocks() < 0.0) {
            return null;
        }

        ARADTargeting.Receiver receiver;
        if (request.launcherSublevelId() == null) {
            receiver = ARADTargeting.worldReceiver(request.sensorOrigin());
        } else {
            receiver = ARADTargeting.sableReceiver(level, request.launcherSublevelId()).orElse(null);
            if (receiver == null) {
                return null;
            }
        }

        Set<UUID> launcherChain = launcherSublevelChain(level, request.launcherSublevelId());
        Vec3 forward = request.sensorForward().normalize();
        List<RankedAradContact> candidates = new ArrayList<>();
        for (ARADTargeting.NativeRadarContact contact : ARADTargeting.findNativeContacts(level, receiver)) {
            Vec3 radarPosition = contact.radarWorldPosition();
            if (!isFinite(radarPosition)
                    || contact.targetSublevelId() != null && launcherChain.contains(contact.targetSublevelId())
                    || !RadarTargeting.isWithinEnvelope(
                    request.sensorOrigin(),
                    forward,
                    radarPosition,
                    request.maxRangeBlocks(),
                    request.halfAngleDegrees())
                    || !RadarTargeting.hasLineOfSightToBlock(
                    level,
                    request.sensorOrigin(),
                    radarPosition,
                    contact.radarPos(),
                    contact.targetSublevelId())) {
                continue;
            }

            Vec3 offset = radarPosition.subtract(request.sensorOrigin());
            candidates.add(new RankedAradContact(
                    contact,
                    forward.dot(offset.normalize()),
                    offset.lengthSqr()
            ));
        }

        candidates.sort(Comparator
                .comparingDouble((RankedAradContact ranked) -> ranked.contact().signalStrength()).reversed()
                .thenComparingDouble(ranked -> ranked.contact().rangeRatio())
                .thenComparing(Comparator.comparingDouble(RankedAradContact::alignment).reversed())
                .thenComparingDouble(RankedAradContact::distanceSqr)
                .thenComparing(ranked -> ranked.contact().sourceId()));
        if (candidates.isEmpty()) {
            return null;
        }

        ARADTargetDesignationEvent.Target target = ARADTargeting.createNoisyTarget(
                level,
                candidates.getFirst().contact(),
                level.getRandom()
        );
        if (target == null) {
            return null;
        }
        ARADTargetReference reference = new ARADTargetReference(
                candidates.getFirst().contact().sourceId(),
                candidates.getFirst().contact().emitterId(),
                target.radarPos(),
                target.rangeRatio(),
                target.noisyWorldPosition(),
                target.targetSublevelId(),
                target.targetLocalPosition()
        );
        return reference.isValid() ? reference : null;
    }

    private static Set<UUID> launcherSublevelChain(ServerLevel level, @Nullable UUID launcherSublevelId) {
        Set<UUID> ids = new HashSet<>();
        if (!Mods.SABLE.isLoaded() || launcherSublevelId == null) {
            return ids;
        }
        ids.add(launcherSublevelId);
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return ids;
        }
        SubLevel launcher = container.getSubLevel(launcherSublevelId);
        if (launcher == null || launcher.isRemoved()) {
            return ids;
        }
        for (SubLevel connected : SubLevelHelper.getConnectedChain(launcher)) {
            if (connected != null && !connected.isRemoved()) {
                ids.add(connected.getUniqueId());
            }
        }
        return ids;
    }

    private static void visitAradGuidanceBlocks(ServerLevel level, BlockPos rwrPos,
                                                java.util.function.Consumer<ARADGuidanceBlockEntity> visitor) {
        if (!Mods.SABLE.isLoaded()) {
            return;
        }
        SubLevelAccess containingSublevel = SableCompanion.INSTANCE.getContaining(level, rwrPos);
        if (containingSublevel == null) {
            return;
        }
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return;
        }
        SubLevel source = container.getSubLevel(containingSublevel.getUniqueId());
        if (source == null || source.isRemoved()) {
            return;
        }

        Map<UUID, SubLevel> chain = new LinkedHashMap<>();
        chain.put(source.getUniqueId(), source);
        for (SubLevel connected : SubLevelHelper.getConnectedChain(source)) {
            if (connected != null && !connected.isRemoved()) {
                chain.putIfAbsent(connected.getUniqueId(), connected);
            }
        }

        for (SubLevel sublevel : chain.values()) {
            for (var chunkHolder : sublevel.getPlot().getLoadedChunks()) {
                LevelChunk chunk = chunkHolder.getChunk();
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (blockEntity instanceof ARADGuidanceBlockEntity aradGuidance) {
                        SubLevelAccess owner = SableCompanion.INSTANCE.getContaining(level, aradGuidance.getBlockPos());
                        if (owner != null && sublevel.getUniqueId().equals(owner.getUniqueId())) {
                            visitor.accept(aradGuidance);
                        }
                    }
                }
            }
        }
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
