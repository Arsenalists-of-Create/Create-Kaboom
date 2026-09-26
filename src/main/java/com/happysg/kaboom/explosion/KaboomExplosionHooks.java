package com.happysg.kaboom.explosion;

import java.util.Objects;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Optional bridge for claims/protection integrations. The engine calls this only on the server thread during writeback.
 */
public final class KaboomExplosionHooks {
    private static final ExplosionBlockFilter ALLOW_ALL = (level, position, expected, replacement, cause) -> true;
    private static volatile ExplosionBlockFilter blockFilter = ALLOW_ALL;

    private KaboomExplosionHooks() {
    }

    public static void setBlockFilter(ExplosionBlockFilter filter) {
        blockFilter = Objects.requireNonNull(filter, "filter");
    }

    static boolean canReplace(ServerLevel level, BlockPos position, BlockState expected, BlockState replacement, Entity cause) {
        return blockFilter.canReplace(level, position, expected, replacement, cause);
    }

    @FunctionalInterface
    public interface ExplosionBlockFilter {
        boolean canReplace(ServerLevel level, BlockPos position, BlockState expected, BlockState replacement, Entity cause);
    }
}
