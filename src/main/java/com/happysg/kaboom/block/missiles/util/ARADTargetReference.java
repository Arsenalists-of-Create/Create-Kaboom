package com.happysg.kaboom.block.missiles.util;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.Vec3;

/**
 * A server-validated reference to the radar emitter designated by an ARAD monitor.
 * The noisy world position is both the stationary aim point and a fallback for old
 * consumers; moving Sable targets additionally carry the same noisy point in the
 * target sublevel's local coordinates.
 */
public record ARADTargetReference(
        String sourceId,
        @Nullable UUID emitterId,
        @Nullable BlockPos radarPos,
        double rangeRatio,
        Vec3 noisyWorldPosition,
        @Nullable UUID targetSublevelId,
        @Nullable Vec3 targetLocalPosition
) {
    private static final double MAX_RANGE_RATIO = 1.5 + 1.0E-6;
    private static final String TAG_SOURCE_ID = "SourceId";
    private static final String TAG_EMITTER_ID = "EmitterId";
    private static final String TAG_RADAR_POS = "RadarPos";
    private static final String TAG_RANGE_RATIO = "RangeRatio";
    private static final String TAG_WORLD_POSITION = "NoisyWorldPosition";
    private static final String TAG_SUBLEVEL_ID = "TargetSublevelId";
    private static final String TAG_LOCAL_POSITION = "TargetLocalPosition";

    public ARADTargetReference {
        sourceId = sourceId == null ? "" : sourceId;
        radarPos = radarPos == null ? null : radarPos.immutable();
        noisyWorldPosition = copy(noisyWorldPosition);
        targetLocalPosition = copy(targetLocalPosition);
    }

    public boolean isValid() {
        return !sourceId.isBlank()
                && radarPos != null
                && Double.isFinite(rangeRatio)
                && rangeRatio >= 0.0
                && rangeRatio <= MAX_RANGE_RATIO
                && isFinite(noisyWorldPosition)
                && ((targetSublevelId == null && targetLocalPosition == null)
                || (targetSublevelId != null && isFinite(targetLocalPosition)));
    }

    public boolean isMovingTarget() {
        return targetSublevelId != null && isFinite(targetLocalPosition);
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        if (!isValid()) {
            return tag;
        }
        tag.putString(TAG_SOURCE_ID, sourceId);
        if (emitterId != null) {
            tag.putUUID(TAG_EMITTER_ID, emitterId);
        }
        tag.put(TAG_RADAR_POS, NbtUtils.writeBlockPos(radarPos));
        tag.putDouble(TAG_RANGE_RATIO, rangeRatio);
        tag.put(TAG_WORLD_POSITION, writeVec(noisyWorldPosition));
        if (isMovingTarget()) {
            tag.putUUID(TAG_SUBLEVEL_ID, targetSublevelId);
            tag.put(TAG_LOCAL_POSITION, writeVec(targetLocalPosition));
        }
        return tag;
    }

    @Nullable
    public static ARADTargetReference fromTag(CompoundTag tag) {
        if (tag == null || tag.isEmpty()
                || !tag.contains(TAG_SOURCE_ID, Tag.TAG_STRING)
                || (tag.contains(TAG_EMITTER_ID) && !tag.hasUUID(TAG_EMITTER_ID))
                || !tag.contains(TAG_RADAR_POS, Tag.TAG_INT_ARRAY)
                || !tag.contains(TAG_RANGE_RATIO, Tag.TAG_ANY_NUMERIC)
                || !tag.contains(TAG_WORLD_POSITION, Tag.TAG_COMPOUND)
                || tag.hasUUID(TAG_SUBLEVEL_ID) != tag.contains(TAG_LOCAL_POSITION, Tag.TAG_COMPOUND)) {
            return null;
        }
        BlockPos radarPos = NbtUtils.readBlockPos(tag, TAG_RADAR_POS).orElse(null);
        Vec3 worldPosition = readVec(tag, TAG_WORLD_POSITION);
        UUID sublevelId = tag.hasUUID(TAG_SUBLEVEL_ID) ? tag.getUUID(TAG_SUBLEVEL_ID) : null;
        Vec3 localPosition = readVec(tag, TAG_LOCAL_POSITION);
        ARADTargetReference reference = new ARADTargetReference(
                tag.getString(TAG_SOURCE_ID),
                tag.hasUUID(TAG_EMITTER_ID) ? tag.getUUID(TAG_EMITTER_ID) : null,
                radarPos,
                tag.getDouble(TAG_RANGE_RATIO),
                worldPosition,
                sublevelId,
                localPosition
        );
        return reference.isValid() ? reference : null;
    }

    private static CompoundTag writeVec(Vec3 value) {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("X", value.x);
        tag.putDouble("Y", value.y);
        tag.putDouble("Z", value.z);
        return tag;
    }

    @Nullable
    private static Vec3 readVec(CompoundTag parent, String key) {
        if (!parent.contains(key, Tag.TAG_COMPOUND)) {
            return null;
        }
        CompoundTag tag = parent.getCompound(key);
        if (!tag.contains("X", Tag.TAG_ANY_NUMERIC)
                || !tag.contains("Y", Tag.TAG_ANY_NUMERIC)
                || !tag.contains("Z", Tag.TAG_ANY_NUMERIC)) {
            return null;
        }
        Vec3 value = new Vec3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z"));
        return isFinite(value) ? value : null;
    }

    @Nullable
    private static Vec3 copy(@Nullable Vec3 value) {
        return value == null ? null : new Vec3(value.x, value.y, value.z);
    }

    private static boolean isFinite(@Nullable Vec3 value) {
        return value != null
                && Double.isFinite(value.x)
                && Double.isFinite(value.y)
                && Double.isFinite(value.z);
    }
}
