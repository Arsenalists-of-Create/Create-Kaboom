package com.happysg.kaboom.explosion;

import java.util.BitSet;

/** Lightweight cleanup passes. This intentionally removes detached fragments instead of simulating heavy rigid bodies. */
final class ExplosionCollapse {
    private static final int[] DX = {1, -1, 0, 0, 0, 0};
    private static final int[] DZ = {0, 0, 1, -1, 0, 0};
    private static final int[] DY = {0, 0, 0, 0, 1, -1};

    private ExplosionCollapse() {
    }

    static void pruneDetachedFragments(ExplosionVolume volume, int maxDetachedFragmentBlocks) {
        int size = volume.size();
        BitSet visited = new BitSet(size);
        int[] stack = new int[size];
        int[] component = new int[size];

        for (int start = 0; start < size; start++) {
            if (visited.get(start) || !volume.material(start).isStructural()) {
                continue;
            }

            int top = 0;
            int count = 0;
            boolean supported = false;
            stack[top++] = start;
            visited.set(start);

            while (top > 0) {
                int current = stack[--top];
                component[count++] = current;
                ExplosionMaterial material = volume.material(current);
                if (material.isPermanentSupport() || touchesBoundary(volume, current)) {
                    supported = true;
                }

                int x = volume.xFromIndex(current);
                int z = volume.zFromIndex(current);
                int y = volume.yFromIndex(current);
                for (int direction = 0; direction < 6; direction++) {
                    int nextX = x + DX[direction];
                    int nextZ = z + DZ[direction];
                    int nextY = y + DY[direction];
                    if (!volume.inBounds(nextX, nextZ, nextY)) {
                        supported = true;
                        continue;
                    }

                    int next = volume.index(nextX, nextZ, nextY);
                    if (!visited.get(next) && volume.material(next).isStructural()) {
                        visited.set(next);
                        stack[top++] = next;
                    }
                }
            }

            if (!supported && count <= maxDetachedFragmentBlocks) {
                for (int index = 0; index < count; index++) {
                    int voxel = component[index];
                    if (volume.material(voxel).isDestructible()) {
                        volume.clear(voxel);
                    }
                }
            }
        }
    }

    static void removeSpecks(ExplosionVolume volume, int iterations) {
        for (int iteration = 0; iteration < iterations; iteration++) {
            BitSet remove = new BitSet(volume.size());
            boolean found = false;

            for (int voxel = 0; voxel < volume.size(); voxel++) {
                ExplosionMaterial material = volume.material(voxel);
                if (!material.isDestructible() || !material.isSolid()) {
                    continue;
                }

                int neighbors = solidNeighborCount(volume, voxel);
                if (neighbors <= 1) {
                    remove.set(voxel);
                    found = true;
                }
            }

            if (!found) {
                return;
            }

            for (int voxel = remove.nextSetBit(0); voxel >= 0; voxel = remove.nextSetBit(voxel + 1)) {
                volume.clear(voxel);
            }
        }
    }

    private static boolean touchesBoundary(ExplosionVolume volume, int index) {
        int x = volume.xFromIndex(index);
        int z = volume.zFromIndex(index);
        int y = volume.yFromIndex(index);
        return x == 0 || x == volume.width - 1 || z == 0 || z == volume.depth - 1 || y == 0 || y == volume.height - 1;
    }

    private static int solidNeighborCount(ExplosionVolume volume, int index) {
        int x = volume.xFromIndex(index);
        int z = volume.zFromIndex(index);
        int y = volume.yFromIndex(index);
        int count = 0;

        for (int direction = 0; direction < 6; direction++) {
            int nextX = x + DX[direction];
            int nextZ = z + DZ[direction];
            int nextY = y + DY[direction];
            if (!volume.inBounds(nextX, nextZ, nextY)) {
                count++;
                continue;
            }

            if (volume.material(volume.index(nextX, nextZ, nextY)).isSolid()) {
                count++;
            }
        }
        return count;
    }
}
