package com.happysg.kaboom.block.missiles.parts;

import com.happysg.kaboom.registry.ModBlocks;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.material.FluidState;

/** Maintains the invisible cells that reserve the 3x3 cross-section of Huge missile parts. */
public final class HugeMissileReservations {
    private static final int SEARCH_RADIUS = 1;

    private HugeMissileReservations() {
    }

    public static boolean isReservation(BlockState state) {
        return state.is(ModBlocks.HUGE_MISSILE_RESERVATION.get());
    }

    public static List<BlockPos> positionsFor(BlockPos ownerPos, BlockState ownerState) {
        Direction.Axis axis = getAxis(ownerState);
        if (!HugeMissileOverlap.isHugeMissilePart(ownerState) || axis == null) {
            return List.of();
        }

        List<BlockPos> positions = new ArrayList<>(8);
        for (int first = -1; first <= 1; first++) {
            for (int second = -1; second <= 1; second++) {
                if (first == 0 && second == 0) {
                    continue;
                }
                positions.add(switch (axis) {
                    case X -> ownerPos.offset(0, first, second);
                    case Y -> ownerPos.offset(first, 0, second);
                    case Z -> ownerPos.offset(first, second, 0);
                });
            }
        }
        return positions;
    }

    public static boolean canReservePlacements(LevelReader level,
                                               Map<BlockPos, BlockState> placements) {
        Set<BlockPos> placementPositions = placements.keySet();
        for (Map.Entry<BlockPos, BlockState> placement : placements.entrySet()) {
            if (!HugeMissileOverlap.isHugeMissilePart(placement.getValue())) {
                continue;
            }
            for (BlockPos reservationPos : positionsFor(placement.getKey(), placement.getValue())) {
                if (placementPositions.contains(reservationPos) || !hasChunk(level, reservationPos)) {
                    return false;
                }

                BlockState existing = level.getBlockState(reservationPos);
                if (isReservation(existing)) {
                    BlockPos existingOwner = reservationBlock().getOwnerPos(existing, reservationPos);
                    if (!existingOwner.equals(placement.getKey())) {
                        return false;
                    }
                    continue;
                }

                FluidState fluid = existing.getFluidState();
                if (!fluid.isEmpty() && !fluid.is(FluidTags.WATER)) {
                    return false;
                }
                if (!existing.canBeReplaced()) {
                    return false;
                }
            }
        }
        return true;
    }

    public static void reconcileOwner(Level level, BlockPos ownerPos, BlockState ownerState) {
        if (!HugeMissileOverlap.isHugeMissilePart(ownerState)) {
            return;
        }

        Set<BlockPos> expected = new HashSet<>(positionsFor(ownerPos, ownerState));
        for (BlockPos reservationPos : expected) {
            ensureReservation(level, ownerPos, reservationPos);
        }

        for (BlockPos nearby : BlockPos.betweenClosed(
                ownerPos.offset(-SEARCH_RADIUS, -SEARCH_RADIUS, -SEARCH_RADIUS),
                ownerPos.offset(SEARCH_RADIUS, SEARCH_RADIUS, SEARCH_RADIUS))) {
            if (!hasChunk(level, nearby)) {
                continue;
            }
            BlockState nearbyState = level.getBlockState(nearby);
            if (isReservation(nearbyState)
                    && reservationBlock().getOwnerPos(nearbyState, nearby).equals(ownerPos)
                    && !expected.contains(nearby)) {
                restoreContainedFluid(level, nearby, nearbyState);
            }
        }
    }

    public static void removeForOwner(Level level, BlockPos ownerPos) {
        for (BlockPos nearby : BlockPos.betweenClosed(
                ownerPos.offset(-SEARCH_RADIUS, -SEARCH_RADIUS, -SEARCH_RADIUS),
                ownerPos.offset(SEARCH_RADIUS, SEARCH_RADIUS, SEARCH_RADIUS))) {
            if (!hasChunk(level, nearby)) {
                continue;
            }
            BlockState nearbyState = level.getBlockState(nearby);
            if (isReservation(nearbyState)
                    && reservationBlock().getOwnerPos(nearbyState, nearby).equals(ownerPos)) {
                restoreContainedFluid(level, nearby, nearbyState);
            }
        }
    }

    public static boolean isRequiredOrOwnerUnloaded(LevelReader level, BlockPos reservationPos,
                                                     BlockState reservationState) {
        if (!isReservation(reservationState)) {
            return false;
        }
        BlockPos ownerPos = reservationBlock().getOwnerPos(reservationState, reservationPos);
        if (!hasChunk(level, ownerPos)) {
            return true;
        }
        BlockState ownerState = level.getBlockState(ownerPos);
        return positionsFor(ownerPos, ownerState).contains(reservationPos);
    }

    public static void reconcileLoadedAreas(ServerLevel level, Iterable<ChunkPos> centerChunks) {
        Set<ChunkPos> chunksToReconcile = new HashSet<>();
        for (ChunkPos centerChunk : centerChunks) {
            for (int chunkX = centerChunk.x - 1; chunkX <= centerChunk.x + 1; chunkX++) {
                for (int chunkZ = centerChunk.z - 1; chunkZ <= centerChunk.z + 1; chunkZ++) {
                    chunksToReconcile.add(new ChunkPos(chunkX, chunkZ));
                }
            }
        }

        for (ChunkPos chunkPos : chunksToReconcile) {
            LevelChunk chunk = level.getChunkSource().getChunkNow(chunkPos.x, chunkPos.z);
            if (chunk != null) {
                reconcileChunk(level, chunk);
            }
        }
    }

    private static void reconcileChunk(Level level, LevelChunk chunk) {
        List<OwnerState> owners = new ArrayList<>();
        List<ReservationState> reservations = new ArrayList<>();
        LevelChunkSection[] sections = chunk.getSections();

        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            LevelChunkSection section = sections[sectionIndex];
            if (!section.maybeHas(state -> HugeMissileOverlap.isHugeMissilePart(state)
                    || isReservation(state))) {
                continue;
            }

            int sectionY = chunk.getSectionYFromSectionIndex(sectionIndex);
            int minY = SectionPos.sectionToBlockCoord(sectionY);
            int minX = chunk.getPos().getMinBlockX();
            int minZ = chunk.getPos().getMinBlockZ();
            for (int localY = 0; localY < 16; localY++) {
                for (int localZ = 0; localZ < 16; localZ++) {
                    for (int localX = 0; localX < 16; localX++) {
                        BlockState state = section.getBlockState(localX, localY, localZ);
                        if (!HugeMissileOverlap.isHugeMissilePart(state) && !isReservation(state)) {
                            continue;
                        }
                        BlockPos pos = new BlockPos(minX + localX, minY + localY, minZ + localZ);
                        if (HugeMissileOverlap.isHugeMissilePart(state)) {
                            owners.add(new OwnerState(pos, state));
                        } else {
                            reservations.add(new ReservationState(pos, state));
                        }
                    }
                }
            }
        }

        for (OwnerState owner : owners) {
            reconcileOwner(level, owner.pos(), owner.state());
        }
        for (ReservationState reservation : reservations) {
            if (!isRequiredOrOwnerUnloaded(level, reservation.pos(), reservation.state())) {
                BlockState current = level.getBlockState(reservation.pos());
                if (isReservation(current)) {
                    restoreContainedFluid(level, reservation.pos(), current);
                }
            }
        }
    }

    private static void ensureReservation(Level level, BlockPos ownerPos, BlockPos reservationPos) {
        if (!hasChunk(level, reservationPos)) {
            return;
        }

        BlockState existing = level.getBlockState(reservationPos);
        if (isReservation(existing)) {
            if (reservationBlock().getOwnerPos(existing, reservationPos).equals(ownerPos)) {
                return;
            }
            if (isRequiredOrOwnerUnloaded(level, reservationPos, existing)) {
                return;
            }
        } else {
            FluidState fluid = existing.getFluidState();
            if ((!fluid.isEmpty() && !fluid.is(FluidTags.WATER)) || !existing.canBeReplaced()) {
                return;
            }
        }

        boolean waterlogged = existing.getFluidState().is(FluidTags.WATER);
        BlockState reservation = reservationBlock().withOwner(
                reservationBlock().defaultBlockState()
                        .setValue(HugeMissileReservationBlock.WATERLOGGED, waterlogged),
                reservationPos, ownerPos);
        level.setBlock(reservationPos, reservation, Block.UPDATE_ALL);
    }

    private static void restoreContainedFluid(Level level, BlockPos pos, BlockState reservationState) {
        BlockState replacement = reservationState.getValue(HugeMissileReservationBlock.WATERLOGGED)
                ? reservationState.getFluidState().createLegacyBlock()
                : Blocks.AIR.defaultBlockState();
        level.setBlock(pos, replacement, Block.UPDATE_ALL);
    }

    private static HugeMissileReservationBlock reservationBlock() {
        return ModBlocks.HUGE_MISSILE_RESERVATION.get();
    }

    private static boolean hasChunk(LevelReader level, BlockPos pos) {
        int chunkX = SectionPos.blockToSectionCoord(pos.getX());
        int chunkZ = SectionPos.blockToSectionCoord(pos.getZ());
        if (level instanceof ServerLevel serverLevel) {
            return serverLevel.getChunkSource().getChunkNow(chunkX, chunkZ) != null;
        }
        return level.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) != null;
    }

    @Nullable
    private static Direction.Axis getAxis(BlockState state) {
        if (state.hasProperty(BlockStateProperties.AXIS)) {
            return state.getValue(BlockStateProperties.AXIS);
        }
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_AXIS)) {
            return state.getValue(BlockStateProperties.HORIZONTAL_AXIS);
        }
        if (state.hasProperty(BlockStateProperties.FACING)) {
            return state.getValue(BlockStateProperties.FACING).getAxis();
        }
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return state.getValue(BlockStateProperties.HORIZONTAL_FACING).getAxis();
        }
        return null;
    }

    private record OwnerState(BlockPos pos, BlockState state) {
    }

    private record ReservationState(BlockPos pos, BlockState state) {
    }
}
