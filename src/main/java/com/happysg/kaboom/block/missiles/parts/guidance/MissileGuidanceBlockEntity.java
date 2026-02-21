package com.happysg.kaboom.block.missiles.parts.guidance;

import com.happysg.kaboom.block.missiles.MissileEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

// HeatseekerGuidanceBlockEntity.java (or whatever block in the stack will own the GUI)
public class MissileGuidanceBlockEntity extends BlockEntity {

    // Hardcoded target for now (world coords)
    private static final Vec3 HARDCODED_TARGET = new Vec3(0.5, 80.0, 5000.5);

    // Later these become GUI-editable fields
    private Vec3 targetPoint = HARDCODED_TARGET;

    public MissileGuidanceBlockEntity(BlockEntityType<?> pType, BlockPos pPos, BlockState pBlockState) {
        super(pType, pPos, pBlockState);
    }

    /** Call this when you spawn the missile on the server. */
    public void applyTargetTo(MissileEntity missile) {
        missile.setTargetPoint(targetPoint);
    }

    // (Optional) for GUI later
    public void setTargetPoint(Vec3 p) {
        this.targetPoint = p;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public Vec3 getTargetPoint() {
        return targetPoint;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putDouble("TargetX", targetPoint.x);
        tag.putDouble("TargetY", targetPoint.y);
        tag.putDouble("TargetZ", targetPoint.z);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("TargetX")) {
            targetPoint = new Vec3(tag.getDouble("TargetX"), tag.getDouble("TargetY"), tag.getDouble("TargetZ"));
        } else {
            targetPoint = HARDCODED_TARGET;
        }
    }
}