package com.happysg.kaboom.block.missiles.parts.guidance.radar;

import com.happysg.kaboom.block.missiles.util.IMissileGuidanceProvider;
import com.happysg.kaboom.block.missiles.util.MissileFlightProfile;
import com.happysg.kaboom.block.missiles.util.MissileGuidanceData;
import com.happysg.kaboom.config.KaboomConfig;
import com.happysg.radar.block.radar.behavior.IRadar;
import com.happysg.radar.block.radar.track.RadarTrack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

public class RadarGuidanceBlockEntity extends BlockEntity implements IMissileGuidanceProvider {
    private static final int RADAR_SEARCH_RADIUS_BLOCKS = 512;

    public RadarGuidanceBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    @Override
    public MissileGuidanceData exportGuidance() {
        return MissileGuidanceData.radar(worldPosition, MissileFlightProfile.defaults());
    }

    @Nullable
    public RadarTrack acquireGuidanceTrack() {
        if (!(level instanceof ServerLevel serverLevel)) return null;
        return acquireGuidanceTrack(serverLevel, worldPosition, worldPosition.getCenter());
    }

    @Nullable
    public static RadarTrack acquireGuidanceTrack(ServerLevel level, @Nullable BlockPos radarGuidancePos, Vec3 missilePosition) {
        Vec3 origin = radarGuidancePos == null ? missilePosition : radarGuidancePos.getCenter();
        int radius = RADAR_SEARCH_RADIUS_BLOCKS;
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
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!(be instanceof IRadar radar) || !radar.isRunning()) continue;
                    for (RadarTrack track : radar.getTracks()) {
                        if (track == null || track.getPosition() == null) continue;
                        int age = (int) Math.max(0L, now - track.getScannedTime());
                        if (age > timeout) continue;

                        double radarDistance = track.getPosition().distanceTo(origin);
                        if (radarDistance > radius) continue;

                        double missileDistance = track.getPosition().distanceToSqr(missilePosition);
                        double score = age * 1000000.0 + missileDistance;
                        if (score < bestScore) {
                            bestScore = score;
                            best = track;
                        }
                    }
                }
            }
        }

        return best;
    }
}
