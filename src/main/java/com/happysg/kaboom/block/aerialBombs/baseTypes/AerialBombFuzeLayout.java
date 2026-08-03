package com.happysg.kaboom.block.aerialBombs.baseTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Canonical mapping between an aerial bomb's logical slot and its model-space center.
 * Logical slots are always contiguous and ordered oldest to newest.
 */
public final class AerialBombFuzeLayout {
    private static final SlotCenter[][] HEAVY_LAYOUTS = {
            {},
            {center(8, 8)}
    };

    // Slots are oldest to newest. Because the newest slot launches first, multi-row layouts
    // are stored top-to-bottom and model-left-to-right so release order is the reverse:
    // screen-right to screen-left across the bottom row, then upward one row at a time.
    private static final SlotCenter[][] SMALL_LAYOUTS = {
            {},
            {center(8, 12)},
            {center(12, 12), center(4, 12)},
            {center(12, 12), center(4, 12), center(12, 4)},
            {center(12, 12), center(4, 12), center(12, 4), center(4, 4)}
    };

    private static final SlotCenter[][] TINY_LAYOUTS = {
            {},
            {center(8, 2.5)},
            {center(13.5, 13.5), center(8, 13.5)},
            {center(13.5, 13.5), center(8, 13.5), center(2.5, 13.5)},
            {center(13.5, 13.5), center(8, 13.5), center(2.5, 13.5), center(13.5, 8)},
            {center(13.5, 13.5), center(8, 13.5), center(2.5, 13.5), center(13.5, 8), center(8, 8)},
            {center(13.5, 13.5), center(8, 13.5), center(2.5, 13.5), center(13.5, 8), center(8, 8), center(2.5, 8)},
            {center(13.5, 13.5), center(8, 13.5), center(2.5, 13.5), center(13.5, 8), center(8, 8), center(2.5, 8), center(13.5, 2.5)},
            {center(13.5, 13.5), center(8, 13.5), center(2.5, 13.5), center(13.5, 8), center(8, 8), center(2.5, 8), center(13.5, 2.5), center(8, 2.5)},
            {center(13.5, 13.5), center(8, 13.5), center(2.5, 13.5), center(13.5, 8), center(8, 8), center(2.5, 8), center(13.5, 2.5), center(8, 2.5), center(2.5, 2.5)}
    };

    private AerialBombFuzeLayout() {
    }

    public static int capacity(BlockState state) {
        if (!(state.getBlock() instanceof AerialBombBlock bomb)) {
            return 0;
        }
        return capacity(bomb);
    }

    public static int capacity(AerialBombBlock bomb) {
        if (bomb.getBombSize() <= 1) {
            return 1;
        }
        return bomb.getBombSize() == 2 ? 4 : 9;
    }

    public static int activeSlotCount(BlockState state) {
        if (!(state.getBlock() instanceof AerialBombBlock)) {
            return 0;
        }
        int count = state.hasProperty(AerialBombBlock.COUNT) ? state.getValue(AerialBombBlock.COUNT) : 1;
        return Mth.clamp(count, 0, capacity(state));
    }

    public static int launchSlot(BlockState state) {
        return activeSlotCount(state) - 1;
    }

    public static SlotCenter[] activeCenters(BlockState state) {
        if (!(state.getBlock() instanceof AerialBombBlock bomb)) {
            return HEAVY_LAYOUTS[0];
        }

        SlotCenter[][] layouts = layoutsFor(bomb);
        int count = Mth.clamp(activeSlotCount(state), 0, layouts.length - 1);
        return layouts[count];
    }

    public static int selectedSlot(BlockState state, BlockPos pos, BlockHitResult hit) {
        if (!(state.getBlock() instanceof AerialBombBlock)
                || hit.getDirection() != state.getValue(AerialBombBlock.FACING)) {
            return -1;
        }

        SlotCenter[] centers = activeCenters(state);
        if (centers.length == 0) {
            return -1;
        }

        Direction facing = state.getValue(AerialBombBlock.FACING);
        Vec3 location = hit.getLocation();
        double localX = location.x - pos.getX();
        double localY = location.y - pos.getY();
        double localZ = location.z - pos.getZ();
        double horizontal = switch (facing) {
            case NORTH -> localX;
            case SOUTH -> 1.0 - localX;
            case EAST -> localZ;
            case WEST -> 1.0 - localZ;
            default -> 0.5;
        };

        int selected = 0;
        double closestDistance = Double.MAX_VALUE;
        for (int slot = 0; slot < centers.length; slot++) {
            SlotCenter center = centers[slot];
            double dx = horizontal * 16.0 - center.horizontalPixels();
            double dy = localY * 16.0 - center.verticalPixels();
            double distance = dx * dx + dy * dy;
            if (distance < closestDistance) {
                closestDistance = distance;
                selected = slot;
            }
        }
        return selected;
    }

    public static Vec3 offsetFromBlockCenter(Direction facing, SlotCenter center, double forward) {
        Vec3 right = new Vec3(facing.getClockWise().step());
        return new Vec3(facing.step()).scale(forward)
                .add(right.scale((center.horizontalPixels() - 8.0) / 16.0))
                .add(0, (center.verticalPixels() - 8.0) / 16.0, 0);
    }

    private static SlotCenter[][] layoutsFor(AerialBombBlock bomb) {
        if (bomb.getBombSize() <= 1) {
            return HEAVY_LAYOUTS;
        }
        return bomb.getBombSize() == 2 ? SMALL_LAYOUTS : TINY_LAYOUTS;
    }

    private static SlotCenter center(double horizontalPixels, double verticalPixels) {
        return new SlotCenter(horizontalPixels, verticalPixels);
    }

    public record SlotCenter(double horizontalPixels, double verticalPixels) {
        public float horizontal() {
            return (float) (horizontalPixels / 16.0);
        }

        public float vertical() {
            return (float) (verticalPixels / 16.0);
        }
    }
}
