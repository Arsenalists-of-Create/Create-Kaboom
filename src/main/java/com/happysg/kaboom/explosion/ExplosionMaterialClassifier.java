package com.happysg.kaboom.explosion;

import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;

final class ExplosionMaterialClassifier {
    private static final float IMMUNE_RESISTANCE = 50_000.0f;
    private static final float PLANT_RESISTANCE = 0.05f;
    private static final float FLUID_RESISTANCE = 0.70f;
    private static final ConcurrentHashMap<BlockState, BlockProfile> CACHE = new ConcurrentHashMap<>();

    private ExplosionMaterialClassifier() {
    }

    static BlockProfile profile(BlockState state) {
        return CACHE.computeIfAbsent(state, ExplosionMaterialClassifier::createProfile);
    }

    private static BlockProfile createProfile(BlockState state) {
        if (state.isAir()) {
            return new BlockProfile(ExplosionMaterial.AIR, 0.0f);
        }

        Block block = state.getBlock();
        if (block instanceof LiquidBlock) {
            return new BlockProfile(ExplosionMaterial.FLUID, FLUID_RESISTANCE);
        }

        if (state.canBeReplaced()) {
            // Unlike the old engine, foliage and snow stay destructible instead of becoming untouchable "air".
            return new BlockProfile(ExplosionMaterial.PLANT, PLANT_RESISTANCE);
        }

        float resistance = Math.max(0.05f, block.getExplosionResistance());
        if (resistance >= IMMUNE_RESISTANCE) {
            return new BlockProfile(ExplosionMaterial.IMMUNE, resistance);
        }
        if (block instanceof FallingBlock) {
            return new BlockProfile(ExplosionMaterial.LOOSE, resistance);
        }
        if (isBrittle(state)) {
            return new BlockProfile(ExplosionMaterial.BRITTLE, resistance);
        }
        return new BlockProfile(ExplosionMaterial.SOLID, resistance);
    }

    private static boolean isBrittle(BlockState state) {
        if (state.is(BlockTags.LEAVES)) {
            return true;
        }

        Block block = state.getBlock();
        return block instanceof HalfTransparentBlock
            || block instanceof IronBarsBlock
            || block == Blocks.GLOWSTONE
            || block == Blocks.SEA_LANTERN;
    }

    record BlockProfile(ExplosionMaterial material, float resistance) {
    }
}
