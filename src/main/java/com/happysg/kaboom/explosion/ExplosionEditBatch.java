package com.happysg.kaboom.explosion;

import java.util.ArrayList;
import java.util.List;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Compact, ring-grouped writeback data. It is consumed only by the server-thread scheduler. */
final class ExplosionEditBatch {
    final Vec3 center;
    final Entity cause;
    private final List<EditRing> rings;
    private int ringIndex;
    private int entryIndex;
    private final LongArrayList actualAirRemovals = new LongArrayList();

    private ExplosionEditBatch(Vec3 center, Entity cause, List<EditRing> rings) {
        this.center = center;
        this.cause = cause;
        this.rings = rings;
    }

    static ExplosionEditBatch create(ExplosionVolume volume, Vec3 center, Entity cause) {
        int maxRing = 0;
        boolean hasEdits = false;
        for (Int2ObjectMap.Entry<BlockState> entry : volume.originalStates().int2ObjectEntrySet()) {
            int index = entry.getIntKey();
            if (entry.getValue() == volume.state(index)) {
                continue;
            }
            hasEdits = true;
            maxRing = Math.max(maxRing, ringOf(volume, index, center));
        }

        if (!hasEdits) {
            return new ExplosionEditBatch(center, cause, List.of());
        }

        List<EditRing> rings = new ArrayList<>(maxRing + 1);
        for (int index = 0; index <= maxRing; index++) {
            rings.add(new EditRing());
        }

        for (Int2ObjectMap.Entry<BlockState> entry : volume.originalStates().int2ObjectEntrySet()) {
            int index = entry.getIntKey();
            BlockState expected = entry.getValue();
            BlockState replacement = volume.state(index);
            if (expected == replacement) {
                continue;
            }

            int x = volume.minX + volume.xFromIndex(index);
            int z = volume.minZ + volume.zFromIndex(index);
            int y = volume.minY + volume.yFromIndex(index);
            rings.get(ringOf(volume, index, center)).add(BlockPos.asLong(x, y, z), expected, replacement);
        }

        return new ExplosionEditBatch(center, cause, rings);
    }

    boolean isComplete() {
        return ringIndex >= rings.size();
    }

    int process(KaboomExplosionQueue.LevelQueue queue, int budget) {
        int processed = 0;
        while (processed < budget && ringIndex < rings.size()) {
            EditRing ring = rings.get(ringIndex);
            while (processed < budget && entryIndex < ring.positions.size()) {
                queue.applyEdit(this, ring, entryIndex++);
                processed++;
            }
            if (entryIndex >= ring.positions.size()) {
                ringIndex++;
                entryIndex = 0;
            }
        }
        return processed;
    }

    LongArrayList actualAirRemovals() {
        return actualAirRemovals;
    }

    void addActualAirRemoval(long position) {
        actualAirRemovals.add(position);
    }

    private static int ringOf(ExplosionVolume volume, int index, Vec3 center) {
        double x = volume.minX + volume.xFromIndex(index) + 0.5 - center.x;
        double z = volume.minZ + volume.zFromIndex(index) + 0.5 - center.z;
        double y = volume.minY + volume.yFromIndex(index) + 0.5 - center.y;
        return (int) Math.floor(Math.sqrt(x * x + y * y + z * z));
    }

    static final class EditRing {
        final LongArrayList positions = new LongArrayList();
        final ObjectArrayList<BlockState> expectedStates = new ObjectArrayList<>();
        final ObjectArrayList<BlockState> replacementStates = new ObjectArrayList<>();

        void add(long position, BlockState expected, BlockState replacement) {
            positions.add(position);
            expectedStates.add(expected);
            replacementStates.add(replacement);
        }
    }
}
