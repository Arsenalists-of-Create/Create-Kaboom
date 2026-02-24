package com.happysg.kaboom.block.missiles.parts.guidance.gps;



import com.happysg.kaboom.block.missiles.util.IMissileGuidanceProvider;
import com.happysg.kaboom.block.missiles.util.MissileFlightProfile;
import com.happysg.kaboom.block.missiles.util.MissileGuidanceData;
import com.happysg.kaboom.block.missiles.util.MissileTargetSpec;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class GPSGuidanceBlockEntity extends BlockEntity implements IMissileGuidanceProvider {
    public GPSGuidanceBlockEntity(BlockEntityType<?> pType, BlockPos pPos, BlockState pBlockState) {
        super(pType, pPos, pBlockState);
    }
    // hardcode now
    public double tx = 0.5, ty = 80.0, tz = -5000.5;
    private boolean highArc = false;

    // flight profile (GUI later edits these)
    private MissileFlightProfile profile = MissileFlightProfile.defaults();

    @Override
    public MissileGuidanceData exportGuidance() {
        MissileTargetSpec target = MissileTargetSpec.point(new Vec3(tx, ty, tz), highArc);
        return new MissileGuidanceData(target, profile);
    }

    // (Optional) setters for GUI later
    public void setTarget(Vec3 p, boolean highArc) {
        this.tx = p.x;
        this.ty = p.y;
        this.tz = p.z;
        this.highArc = highArc;
        LogUtils.getLogger().warn(" "+ tx +ty + tz);
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public void setTarget(Vec3 p) {
        setTarget(p, this.highArc);
    }

    public Vec3 getTarget() {
        return new Vec3(tx, ty, tz);
    }

    public boolean isHighArc() {
        return highArc;
    }
    public void setProfile(MissileFlightProfile p) { this.profile=p; setChanged(); }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putDouble("TX", tx); tag.putDouble("TY", ty); tag.putDouble("TZ", tz);
        tag.putBoolean("HighArc", highArc);
        tag.put("Profile", profile.toTag());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("TX")) { tx=tag.getDouble("TX"); ty=tag.getDouble("TY"); tz=tag.getDouble("TZ"); }
        if (tag.contains("HighArc")) highArc = tag.getBoolean("HighArc");
        if (tag.contains("Profile")) profile = MissileFlightProfile.fromTag(tag.getCompound("Profile"));
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        load(tag);
    }
}
