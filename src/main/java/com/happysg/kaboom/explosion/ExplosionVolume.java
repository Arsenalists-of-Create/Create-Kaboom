package com.happysg.kaboom.explosion;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Pure off-thread simulation state. It deliberately contains no Level, chunk, entity, or block entity references.
 */
final class ExplosionVolume {
    private static final ExplosionMaterial[] MATERIAL_VALUES = ExplosionMaterial.values();
    private static final BlockState AIR_STATE = Blocks.AIR.defaultBlockState();
    private static final BlockState GRAVEL_STATE = Blocks.GRAVEL.defaultBlockState();
    private static final BlockState BOUNDARY_STATE = Blocks.BEDROCK.defaultBlockState();
    private static final float GRAVEL_RESISTANCE = ExplosionMaterialClassifier.profile(GRAVEL_STATE).resistance();

    final int width;
    final int depth;
    final int height;
    final int minX;
    final int minZ;
    final int minY;

    private final byte[] materials;
    private final float[] resistances;
    private final BlockState[] states;
    private final Int2ObjectOpenHashMap<BlockState> originalStates = new Int2ObjectOpenHashMap<>();
    private final int maxChanges;
    private boolean truncated;

    ExplosionVolume(int width, int depth, int height, int minX, int minZ, int minY, int maxChanges) {
        this.width = width;
        this.depth = depth;
        this.height = height;
        this.minX = minX;
        this.minZ = minZ;
        this.minY = minY;
        this.maxChanges = maxChanges;

        int size = Math.multiplyExact(Math.multiplyExact(width, depth), height);
        materials = new byte[size];
        resistances = new float[size];
        states = new BlockState[size];
    }

    int size() {
        return states.length;
    }

    int index(int x, int z, int y) {
        return (x * depth + z) * height + y;
    }

    boolean inBounds(int x, int z, int y) {
        return x >= 0 && x < width && z >= 0 && z < depth && y >= 0 && y < height;
    }

    int xFromIndex(int index) {
        return index / (depth * height);
    }

    int zFromIndex(int index) {
        return (index / height) % depth;
    }

    int yFromIndex(int index) {
        return index % height;
    }

    void setInitial(int index, BlockState state) {
        ExplosionMaterialClassifier.BlockProfile profile = ExplosionMaterialClassifier.profile(state);
        states[index] = state;
        materials[index] = (byte) profile.material().ordinal();
        resistances[index] = profile.resistance();
    }

    void setBoundary(int index) {
        states[index] = BOUNDARY_STATE;
        materials[index] = (byte) ExplosionMaterial.BOUNDARY.ordinal();
        resistances[index] = Float.MAX_VALUE;
    }

    ExplosionMaterial material(int index) {
        return MATERIAL_VALUES[materials[index]];
    }

    BlockState state(int index) {
        return states[index];
    }

    float resistance(int index) {
        return resistances[index];
    }

    boolean setState(int index, BlockState nextState) {
        ExplosionMaterialClassifier.BlockProfile profile = ExplosionMaterialClassifier.profile(nextState);
        return set(index, nextState, profile.material(), profile.resistance());
    }

    boolean clear(int index) {
        return set(index, AIR_STATE, ExplosionMaterial.AIR, 0.0f);
    }

    boolean setLooseDebris(int index) {
        return set(index, GRAVEL_STATE, ExplosionMaterial.LOOSE, GRAVEL_RESISTANCE);
    }

    boolean set(int index, BlockState nextState, ExplosionMaterial nextMaterial, float nextResistance) {
        if (states[index] == nextState && material(index) == nextMaterial) {
            return true;
        }

        if (!originalStates.containsKey(index)) {
            if (originalStates.size() >= maxChanges) {
                truncated = true;
                return false;
            }
            originalStates.put(index, states[index]);
        }

        states[index] = nextState;
        materials[index] = (byte) nextMaterial.ordinal();
        resistances[index] = nextResistance;
        return true;
    }

    Int2ObjectOpenHashMap<BlockState> originalStates() {
        return originalStates;
    }

    boolean truncated() {
        return truncated;
    }
}
