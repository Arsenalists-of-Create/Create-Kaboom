package com.happysg.kaboom.block.missiles.parts.guidance.arad;

import com.happysg.kaboom.block.missiles.assembly.MissileAssemblyResult;
import com.happysg.kaboom.block.missiles.parts.guidance.IPoweredTargetAcquisition;
import com.happysg.kaboom.block.missiles.parts.guidance.radar.RadarTargeting;
import com.happysg.kaboom.block.missiles.util.ARADTargetReference;
import com.happysg.kaboom.block.missiles.util.IMissileGuidanceProvider;
import com.happysg.kaboom.block.missiles.util.MissileFlightProfile;
import com.happysg.kaboom.block.missiles.util.MissileGuidanceData;
import com.happysg.kaboom.compat.radars.RadarCompatRegistry;
import com.happysg.kaboom.compat.radars.RadarIntegration;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class ARADGuidanceBlockEntity extends BlockEntity implements IMissileGuidanceProvider, IPoweredTargetAcquisition {
    private static final double AUTO_SCAN_HALF_ANGLE_DEGREES = 50.0;
    private static final String TAG_ACQUISITION_MODE = "AcquisitionMode";
    private static final String TAG_HAS_TARGET = "HasTargetCoordinates";
    private static final String TAG_TARGET_X = "TargetX";
    private static final String TAG_TARGET_Y = "TargetY";
    private static final String TAG_TARGET_Z = "TargetZ";
    private static final String TAG_RADAR_TARGET = "RadarTargetReference";

    @Nullable
    private Vec3 targetCoordinates;
    @Nullable
    private ARADTargetReference radarTargetReference;
    private ARADTargetAcquisitionMode acquisitionMode = ARADTargetAcquisitionMode.MANUAL_DESIGNATION;
    private boolean autoTargetAcquired;

    public ARADGuidanceBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    public boolean setTargetCoordinates(@Nullable Vec3 coordinates) {
        if (acquisitionMode != ARADTargetAcquisitionMode.MANUAL_DESIGNATION) {
            return false;
        }
        if (!isFinite(coordinates)) {
            clearTargetCoordinates();
            return false;
        }

        Vec3 newTarget = new Vec3(coordinates.x, coordinates.y, coordinates.z);
        if (!Objects.equals(targetCoordinates, newTarget) || radarTargetReference != null) {
            targetCoordinates = newTarget;
            radarTargetReference = null;
            notifyTargetChanged();
        }
        return true;
    }

    public boolean setRadarTarget(@Nullable ARADTargetReference reference) {
        if (acquisitionMode != ARADTargetAcquisitionMode.MANUAL_DESIGNATION) {
            return false;
        }
        return updateRadarTarget(reference);
    }

    private boolean updateRadarTarget(@Nullable ARADTargetReference reference) {
        if (reference == null || !reference.isValid()) {
            return false;
        }

        Vec3 newTarget = reference.noisyWorldPosition();
        if (!Objects.equals(targetCoordinates, newTarget) || !Objects.equals(radarTargetReference, reference)) {
            targetCoordinates = new Vec3(newTarget.x, newTarget.y, newTarget.z);
            radarTargetReference = reference;
            notifyTargetChanged();
        }
        return true;
    }

    /** Clears a monitor-provided target without disturbing manually entered coordinates. */
    public boolean clearRadarTarget(@Nullable String sourceId) {
        if (acquisitionMode != ARADTargetAcquisitionMode.MANUAL_DESIGNATION) {
            return false;
        }
        if (radarTargetReference == null
                || (sourceId != null && !sourceId.isBlank() && !sourceId.equals(radarTargetReference.sourceId()))) {
            return false;
        }
        targetCoordinates = null;
        radarTargetReference = null;
        notifyTargetChanged();
        return true;
    }

    public void clearTargetCoordinates() {
        if (acquisitionMode == ARADTargetAcquisitionMode.MANUAL_DESIGNATION) {
            clearAllTargets();
        }
    }

    public ARADTargetAcquisitionMode getAcquisitionMode() {
        return acquisitionMode;
    }

    public boolean setAcquisitionMode(@Nullable ARADTargetAcquisitionMode mode) {
        ARADTargetAcquisitionMode newMode = mode == null
                ? ARADTargetAcquisitionMode.MANUAL_DESIGNATION
                : mode;
        if (newMode == acquisitionMode) {
            return false;
        }
        acquisitionMode = newMode;
        clearAllTargetsWithoutNotification();
        notifyTargetChanged();
        return true;
    }

    public ARADTargetAcquisitionMode toggleAcquisitionMode() {
        setAcquisitionMode(acquisitionMode.next());
        return acquisitionMode;
    }

    @Override
    public boolean isPoweredAcquisitionEnabled() {
        return acquisitionMode == ARADTargetAcquisitionMode.AUTO_SCAN;
    }

    @Override
    public boolean tickAcquisition(ServerLevel level, MissileAssemblyResult result) {
        if (!isPoweredAcquisitionEnabled()) {
            return false;
        }
        if (autoTargetAcquired && radarTargetReference != null && radarTargetReference.isValid()) {
            return true;
        }

        RadarTargeting.SensorFrame frame = RadarTargeting.sensorFrame(level, result);
        ARADTargetReference reference = RadarCompatRegistry.get().acquireAradTarget(
                level,
                new RadarIntegration.AradAcquisitionRequest(
                        frame.origin(),
                        frame.forward(),
                        frame.launcherSublevelId(),
                        AUTO_SCAN_HALF_ANGLE_DEGREES
                )
        );
        if (reference == null || !reference.isValid()) {
            clearAllTargets();
            return false;
        }
        updateRadarTarget(reference);
        autoTargetAcquired = true;
        return true;
    }

    @Override
    public void resetAcquisition() {
        if (acquisitionMode == ARADTargetAcquisitionMode.AUTO_SCAN) {
            clearAllTargets();
        }
    }

    public boolean hasValidTargetCoordinates() {
        return isFinite(targetCoordinates);
    }

    @Nullable
    public Vec3 getTargetCoordinates() {
        return targetCoordinates;
    }

    @Nullable
    public ARADTargetReference getRadarTargetReference() {
        return radarTargetReference;
    }

    @Override
    public MissileGuidanceData exportGuidance() {
        return MissileGuidanceData.arad(targetCoordinates, radarTargetReference, MissileFlightProfile.defaults());
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        boolean hasTarget = hasValidTargetCoordinates();
        tag.putBoolean(TAG_HAS_TARGET, hasTarget);
        if (hasTarget) {
            tag.putDouble(TAG_TARGET_X, targetCoordinates.x);
            tag.putDouble(TAG_TARGET_Y, targetCoordinates.y);
            tag.putDouble(TAG_TARGET_Z, targetCoordinates.z);
        }
        if (radarTargetReference != null && radarTargetReference.isValid()) {
            tag.put(TAG_RADAR_TARGET, radarTargetReference.toTag());
        }
        tag.putString(TAG_ACQUISITION_MODE, acquisitionMode.name());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        autoTargetAcquired = false;
        acquisitionMode = ARADTargetAcquisitionMode.fromName(tag.getString(TAG_ACQUISITION_MODE));
        if (tag.getBoolean(TAG_HAS_TARGET)
                && tag.contains(TAG_TARGET_X)
                && tag.contains(TAG_TARGET_Y)
                && tag.contains(TAG_TARGET_Z)) {
            Vec3 loadedTarget = new Vec3(tag.getDouble(TAG_TARGET_X), tag.getDouble(TAG_TARGET_Y), tag.getDouble(TAG_TARGET_Z));
            targetCoordinates = isFinite(loadedTarget) ? loadedTarget : null;
        } else {
            targetCoordinates = null;
        }
        boolean hasRadarTarget = tag.contains(TAG_RADAR_TARGET, Tag.TAG_COMPOUND);
        radarTargetReference = hasRadarTarget
                ? ARADTargetReference.fromTag(tag.getCompound(TAG_RADAR_TARGET))
                : null;
        if (radarTargetReference != null) {
            targetCoordinates = radarTargetReference.noisyWorldPosition();
        } else if (hasRadarTarget) {
            targetCoordinates = null;
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        loadAdditional(tag, registries);
    }

    private void notifyTargetChanged() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    private void clearAllTargets() {
        if (targetCoordinates != null || radarTargetReference != null) {
            clearAllTargetsWithoutNotification();
            notifyTargetChanged();
        }
    }

    private void clearAllTargetsWithoutNotification() {
        targetCoordinates = null;
        radarTargetReference = null;
        autoTargetAcquired = false;
    }

    private static boolean isFinite(@Nullable Vec3 coordinates) {
        return coordinates != null
                && Double.isFinite(coordinates.x)
                && Double.isFinite(coordinates.y)
                && Double.isFinite(coordinates.z);
    }
}
