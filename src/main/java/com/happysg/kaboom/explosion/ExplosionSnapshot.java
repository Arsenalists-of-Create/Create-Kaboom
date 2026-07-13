package com.happysg.kaboom.explosion;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Section-copy snapshot built on the server thread. Decode and all simulation happen on a worker thread.
 * Unloaded chunks become immutable boundaries; an explosion never generates, force-loads, or edits them.
 */
final class ExplosionSnapshot {
    private final int width;
    private final int depth;
    private final int height;
    private final int minX;
    private final int minZ;
    private final int minY;
    private final int firstSectionY;
    private final Long2ObjectOpenHashMap<PalettedContainer<BlockState>[]> sections;

    private ExplosionSnapshot(int width, int depth, int height, int minX, int minZ, int minY, int firstSectionY,
                              Long2ObjectOpenHashMap<PalettedContainer<BlockState>[]> sections) {
        this.width = width;
        this.depth = depth;
        this.height = height;
        this.minX = minX;
        this.minZ = minZ;
        this.minY = minY;
        this.firstSectionY = firstSectionY;
        this.sections = sections;
    }

    static ExplosionSnapshot capture(ServerLevel level, net.minecraft.world.phys.Vec3 center, int halfExtent) {
        int centerX = Mth.floor(center.x);
        int centerZ = Mth.floor(center.z);
        int centerY = Mth.floor(center.y);

        int minY = Math.max(level.getMinBuildHeight(), centerY - halfExtent);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, centerY + halfExtent);
        int minX = centerX - halfExtent;
        int minZ = centerZ - halfExtent;
        int width = halfExtent * 2 + 1;
        int depth = halfExtent * 2 + 1;
        int height = maxY - minY + 1;
        int firstSectionY = minY >> 4;
        int lastSectionY = maxY >> 4;
        int sectionCount = lastSectionY - firstSectionY + 1;

        Long2ObjectOpenHashMap<PalettedContainer<BlockState>[]> sectionCopies = new Long2ObjectOpenHashMap<>();
        int maxX = minX + width - 1;
        int maxZ = minZ + depth - 1;

        for (int chunkX = minX >> 4; chunkX <= maxX >> 4; chunkX++) {
            for (int chunkZ = minZ >> 4; chunkZ <= maxZ >> 4; chunkZ++) {
                if (!level.hasChunk(chunkX, chunkZ)) {
                    continue;
                }

                LevelChunk chunk = level.getChunk(chunkX, chunkZ);
                LevelChunkSection[] chunkSections = chunk.getSections();
                @SuppressWarnings("unchecked")
                PalettedContainer<BlockState>[] copies = new PalettedContainer[sectionCount];

                for (int sectionY = firstSectionY; sectionY <= lastSectionY; sectionY++) {
                    int arrayIndex = level.getSectionIndex(sectionY << 4);
                    if (arrayIndex < 0 || arrayIndex >= chunkSections.length) {
                        continue;
                    }

                    LevelChunkSection section = chunkSections[arrayIndex];
                    if (section != null && !section.hasOnlyAir()) {
                        copies[sectionY - firstSectionY] = section.getStates().copy();
                    }
                }

                // Keep an entry even for a fully empty loaded chunk. Missing map entry means "unloaded boundary".
                sectionCopies.put(ChunkPos.asLong(chunkX, chunkZ), copies);
            }
        }

        return new ExplosionSnapshot(width, depth, height, minX, minZ, minY, firstSectionY, sectionCopies);
    }

    boolean hasLoadedChunks() {
        return !sections.isEmpty();
    }

    ExplosionVolume decode(KaboomExplosionProfile profile) {
        ExplosionVolume volume = new ExplosionVolume(width, depth, height, minX, minZ, minY, profile.maxBlockChanges());

        for (int localX = 0; localX < width; localX++) {
            int worldX = minX + localX;
            int chunkX = worldX >> 4;
            int sectionX = worldX & 15;

            for (int localZ = 0; localZ < depth; localZ++) {
                int worldZ = minZ + localZ;
                int chunkZ = worldZ >> 4;
                int sectionZ = worldZ & 15;
                PalettedContainer<BlockState>[] chunkSections = sections.get(ChunkPos.asLong(chunkX, chunkZ));

                for (int localY = 0; localY < height; localY++) {
                    int index = volume.index(localX, localZ, localY);
                    if (chunkSections == null) {
                        volume.setBoundary(index);
                        continue;
                    }

                    int worldY = minY + localY;
                    PalettedContainer<BlockState> section = chunkSections[(worldY >> 4) - firstSectionY];
                    if (section == null) {
                        volume.setInitial(index, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                    } else {
                        volume.setInitial(index, section.get(sectionX, worldY & 15, sectionZ));
                    }
                }
            }
        }

        return volume;
    }
}
