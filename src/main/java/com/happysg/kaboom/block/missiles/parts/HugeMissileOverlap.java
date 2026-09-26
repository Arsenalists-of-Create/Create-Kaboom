package com.happysg.kaboom.block.missiles.parts;

import com.happysg.kaboom.block.missiles.assembly.IMissileComponent;
import com.happysg.kaboom.block.missiles.assembly.MissileSize;
import com.happysg.kaboom.block.missiles.parts.warhead.AbstractMissileWarhead;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Collision-volume overlap checks shared by huge missile placement and collision handling. */
public final class HugeMissileOverlap {
    /** Current solid huge-part models extend at most one cell beyond their source block. */
    private static final int SEARCH_RADIUS = 1;

    private HugeMissileOverlap() {
    }

    public static boolean isHugeMissilePart(BlockState state) {
        if (state.getBlock() instanceof IMissileComponent component) {
            return component.getMissileSize() == MissileSize.HUGE;
        }
        return state.getBlock() instanceof AbstractMissileWarhead warhead
                && warhead.getMissileSize() == MissileSize.HUGE;
    }

    public static boolean hasPlacementConflict(LevelAccessor level,
                                               Map<BlockPos, BlockState> placements,
                                               @Nullable Entity placer) {
        CollisionContext context = placer == null
                ? CollisionContext.empty()
                : CollisionContext.of(placer);
        List<PlacedShape> placedShapes = new ArrayList<>(placements.size());

        for (Map.Entry<BlockPos, BlockState> placement : placements.entrySet()) {
            VoxelShape shape = worldCollisionShape(
                    level, placement.getKey(), placement.getValue(), context);
            if (!shape.isEmpty()) {
                placedShapes.add(new PlacedShape(
                        placement.getKey(), placement.getValue(), shape));
            }
        }

        for (PlacedShape placed : placedShapes) {
            boolean conflict = isHugeMissilePart(placed.state())
                    ? intersectsExistingBlock(level, placed, placements, context)
                    : intersectsExistingHugePart(level, placed, placements, context);
            if (conflict) {
                return true;
            }
        }

        for (int first = 0; first < placedShapes.size(); first++) {
            PlacedShape a = placedShapes.get(first);
            for (int second = first + 1; second < placedShapes.size(); second++) {
                PlacedShape b = placedShapes.get(second);
                if ((isHugeMissilePart(a.state()) || isHugeMissilePart(b.state()))
                        && intersects(a.worldShape(), b.worldShape())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean intersectsExistingHugePart(BlockGetter level,
                                                      PlacedShape placed,
                                                      Map<BlockPos, BlockState> placements,
                                                      CollisionContext context) {
        for (BlockPos candidatePos : nearbyPositions(placed.pos())) {
            if (placements.containsKey(candidatePos)) {
                continue;
            }
            BlockState candidateState = level.getBlockState(candidatePos);
            if (!isHugeMissilePart(candidateState)) {
                continue;
            }
            VoxelShape candidateShape = worldCollisionShape(
                    level, candidatePos, candidateState, context);
            if (intersects(placed.worldShape(), candidateShape)) {
                return true;
            }
        }
        return false;
    }

    private static boolean intersectsExistingBlock(BlockGetter level,
                                                   PlacedShape placed,
                                                   Map<BlockPos, BlockState> placements,
                                                   CollisionContext context) {
        for (BlockPos candidatePos : nearbyPositions(placed.pos())) {
            if (placements.containsKey(candidatePos)) {
                continue;
            }
            BlockState candidateState = level.getBlockState(candidatePos);
            VoxelShape candidateShape = worldCollisionShape(
                    level, candidatePos, candidateState, context);
            if (intersects(placed.worldShape(), candidateShape)) {
                return true;
            }
        }
        return false;
    }

    private static Iterable<BlockPos> nearbyPositions(BlockPos center) {
        return BlockPos.betweenClosed(
                center.offset(-SEARCH_RADIUS, -SEARCH_RADIUS, -SEARCH_RADIUS),
                center.offset(SEARCH_RADIUS, SEARCH_RADIUS, SEARCH_RADIUS));
    }

    private static VoxelShape worldCollisionShape(BlockGetter level, BlockPos pos,
                                                  BlockState state, CollisionContext context) {
        return state.getCollisionShape(level, pos, context)
                .move(pos.getX(), pos.getY(), pos.getZ());
    }

    private static boolean intersects(VoxelShape first, VoxelShape second) {
        return !first.isEmpty() && !second.isEmpty()
                && Shapes.joinIsNotEmpty(first, second, BooleanOp.AND);
    }

    private record PlacedShape(BlockPos pos, BlockState state, VoxelShape worldShape) {
    }
}
