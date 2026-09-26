package com.happysg.kaboom.explosion;

import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/** Server-thread-only writeback and small support-cleanup scheduler. */
final class KaboomExplosionQueue {
    private static final int UPDATE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
    private static final int MAX_BLOCKS_PER_SERVER_TICK = 12_000;
    private static final int MAX_CLEANUP_OPS_PER_SERVER_TICK = 4_000;
    private static final int MAX_BLOCKS_PER_BATCH_SLICE = 2_000;
    private static final int MAX_CLEANUP_PER_TASK = 80_000;
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final Map<ServerLevel, LevelQueue> QUEUES = new IdentityHashMap<>();

    private KaboomExplosionQueue() {
    }

    static void enqueue(ServerLevel level, ExplosionEditBatch batch) {
        if (batch.isComplete()) {
            return;
        }
        QUEUES.computeIfAbsent(level, LevelQueue::new).batches.addLast(batch);
    }

    static void tick(MinecraftServer server) {
        int writeBudget = MAX_BLOCKS_PER_SERVER_TICK;
        int cleanupBudget = MAX_CLEANUP_OPS_PER_SERVER_TICK;

        for (LevelQueue queue : QUEUES.values()) {
            if (queue.level.getServer() != server || writeBudget <= 0) {
                continue;
            }
            writeBudget -= queue.apply(Math.min(writeBudget, MAX_BLOCKS_PER_BATCH_SLICE));
        }

        for (LevelQueue queue : QUEUES.values()) {
            if (queue.level.getServer() != server || cleanupBudget <= 0) {
                continue;
            }
            cleanupBudget -= queue.cleanup(Math.min(cleanupBudget, MAX_CLEANUP_PER_TASK));
        }

        Iterator<Map.Entry<ServerLevel, LevelQueue>> iterator = QUEUES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<ServerLevel, LevelQueue> entry = iterator.next();
            if (entry.getValue().isEmpty()) {
                iterator.remove();
            }
        }
    }

    static void clearAll() {
        QUEUES.clear();
    }

    static final class LevelQueue {
        private final ServerLevel level;
        private final ArrayDeque<ExplosionEditBatch> batches = new ArrayDeque<>();
        private final ArrayDeque<SupportCleanup> cleanupTasks = new ArrayDeque<>();
        private final BlockPos.MutableBlockPos mutablePosition = new BlockPos.MutableBlockPos();

        private LevelQueue(ServerLevel level) {
            this.level = level;
        }

        private int apply(int budget) {
            int processed = 0;
            while (processed < budget && !batches.isEmpty()) {
                ExplosionEditBatch batch = batches.peekFirst();
                int slice = Math.min(budget - processed, MAX_BLOCKS_PER_BATCH_SLICE);
                processed += batch.process(this, slice);

                if (!batch.isComplete()) {
                    break;
                }

                batches.removeFirst();
                if (!batch.actualAirRemovals().isEmpty()) {
                    cleanupTasks.addLast(new SupportCleanup(batch.actualAirRemovals()));
                }
            }
            return processed;
        }

        void applyEdit(ExplosionEditBatch batch, ExplosionEditBatch.EditRing ring, int index) {
            long packed = ring.positions.getLong(index);
            int x = BlockPos.getX(packed);
            int y = BlockPos.getY(packed);
            int z = BlockPos.getZ(packed);
            if (!level.hasChunk(x >> 4, z >> 4)) {
                return;
            }

            mutablePosition.set(x, y, z);
            BlockState expected = ring.expectedStates.get(index);
            BlockState replacement = ring.replacementStates.get(index);
            if (level.getBlockState(mutablePosition) != expected) {
                return;
            }

            BlockPos immutablePosition = mutablePosition.immutable();
            if (!KaboomExplosionHooks.canReplace(level, immutablePosition, expected, replacement, batch.cause)) {
                return;
            }

            level.setBlock(mutablePosition, replacement, UPDATE_FLAGS);
            if (replacement.isAir()) {
                batch.addActualAirRemoval(packed);
            }
        }

        private int cleanup(int budget) {
            int processed = 0;
            while (processed < budget && !cleanupTasks.isEmpty()) {
                SupportCleanup cleanup = cleanupTasks.peekFirst();
                processed += cleanup.process(level, mutablePosition, budget - processed);
                if (cleanup.isDone()) {
                    cleanupTasks.removeFirst();
                } else {
                    break;
                }
            }
            return processed;
        }

        private boolean isEmpty() {
            return batches.isEmpty() && cleanupTasks.isEmpty();
        }
    }

    private static final class SupportCleanup {
        private final LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        private final LongOpenHashSet checked = new LongOpenHashSet();
        private final LongOpenHashSet scheduledFluids = new LongOpenHashSet();
        private int remaining = MAX_CLEANUP_PER_TASK;

        private SupportCleanup(it.unimi.dsi.fastutil.longs.LongArrayList removals) {
            for (int index = 0; index < removals.size(); index++) {
                queue.enqueue(removals.getLong(index));
            }
        }

        private int process(ServerLevel level, BlockPos.MutableBlockPos mutablePosition, int budget) {
            int processed = 0;
            while (processed < budget && remaining > 0 && !queue.isEmpty()) {
                long removed = queue.dequeueLong();
                mutablePosition.set(removed);

                for (Direction direction : DIRECTIONS) {
                    mutablePosition.move(direction);
                    int x = mutablePosition.getX();
                    int z = mutablePosition.getZ();
                    long neighbor = mutablePosition.asLong();
                    if (!level.hasChunk(x >> 4, z >> 4)) {
                        mutablePosition.move(direction.getOpposite());
                        continue;
                    }

                    BlockState state = level.getBlockState(mutablePosition);
                    if (!state.isAir()) {
                        FluidState fluid = state.getFluidState();
                        if (!fluid.isEmpty() && scheduledFluids.add(neighbor)) {
                            level.scheduleTick(mutablePosition.immutable(), fluid.getType(), fluid.getType().getTickDelay(level));
                        }

                        if (!state.canSurvive(level, mutablePosition) && checked.add(neighbor)) {
                            level.setBlock(mutablePosition, Blocks.AIR.defaultBlockState(), UPDATE_FLAGS);
                            queue.enqueue(neighbor);
                        }
                    }
                    mutablePosition.move(direction.getOpposite());
                }

                processed++;
                remaining--;
            }
            return processed;
        }

        private boolean isDone() {
            return remaining <= 0 || queue.isEmpty();
        }
    }
}
