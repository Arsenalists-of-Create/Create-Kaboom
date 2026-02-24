package com.happysg.kaboom.block.missiles;

import com.happysg.kaboom.block.missiles.assembly.IMissileComponent;
import com.happysg.kaboom.block.missiles.assembly.MissileAssemblyResult;
import com.happysg.kaboom.block.missiles.util.IMissileGuidanceProvider;
import com.mojang.logging.LogUtils;

import com.simibubi.create.content.contraptions.mounted.MountedContraption;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fluids.FluidStack;
import org.slf4j.Logger;

import javax.annotation.Nullable;

public class MissileContraption extends MountedContraption {

    private static final Logger LOGGER = LogUtils.getLogger();
    public BlockState warheadState;
    public BlockPos controllerWorldPos = BlockPos.ZERO;
    private BlockPos startPos;
    private Direction initialOrientation;
    public int fuelAmountMb = 0;
    public int fuelCapacityMb = 0;
    public CompoundTag fuelFluidTag = null; // optional: store FluidStack in tag
    public BlockPos warheadLocalPos = null;
    public BlockPos capLocalPos = BlockPos.ZERO; // leading tip
    public BlockPos endLocalPos = BlockPos.ZERO; // same idea, optional
    public Vec3 guidanceTargetPoint = null; // copied at launch
    @Nullable
    public CompoundTag guidanceTag = null;



    public void captureFromScan(Level level, MissileAssemblyResult result) {
        this.controllerWorldPos = result.getControllerPos();
        this.warheadLocalPos = result.getWarhead();
        this.capLocalPos = this.warheadLocalPos; // if warhead is the nose
        // Fill the blocks map (local coords relative to controller)
        for (BlockPos worldPos : result.getBlocks()) {
            BlockState state = level.getBlockState(worldPos);
            BlockEntity be = level.getBlockEntity(worldPos);


            if (guidanceTag == null || guidanceTag.isEmpty()) {
                if (be instanceof IMissileGuidanceProvider provider) {
                    guidanceTag = provider.exportGuidance().toTag();   // <-- correct format
                    LOGGER.warn("[MISSILE CAPTURE] captured guidance via provider: {}", guidanceTag);
                } else if (state.getBlock() instanceof IMissileComponent part && part.isGuidance()) {
                    // fallback: raw BE tag (older format) if you really want
                    guidanceTag = be.saveWithoutMetadata();
                    LOGGER.warn("[MISSILE CAPTURE] captured guidance via BE raw tag: {}", guidanceTag);
                }
            }

            CompoundTag tag = be != null ? be.saveWithFullMetadata() : null;

            BlockPos localPos = worldPos.subtract(controllerWorldPos);
            this.getBlocks().put(localPos, new StructureBlockInfo(localPos, state, tag));
        }
        this.computeFuelFromCapturedBlocks(level);
        this.anchor = BlockPos.ZERO;      // local origin (since your blocks are local)
        this.startPos =BlockPos.ZERO;    // safe default; Create expects non-null
        BlockPos best = BlockPos.ZERO;
        for (BlockPos p : getBlocks().keySet()) {
            if (p.getY() > best.getY()) best = p;
        }
        this.capLocalPos = best;
        this.endLocalPos = best;
        this.initialOrientation = Direction.UP; // Direction
        this.bounds = computeAabbFromLocalBlocks();
        LOGGER.warn("[MISSILE] captured blocks={} bounds={}", this.getBlocks().size(), this.bounds);
    }

    private AABB computeAabbFromLocalBlocks() {

        // include full block volumes
        return new AABB(0, 0, 0, 1, 1, 1);
    }

    private void computeFuelFromCapturedBlocks(Level level) {
        fuelAmountMb = 0;
        fuelCapacityMb = 0;
        fuelFluidTag = null;

        FluidStack chosen = FluidStack.EMPTY;

        for (StructureBlockInfo info : getBlocks().values()) {
            BlockState state = info.state();
            if (!(state.getBlock() instanceof IMissileComponent part)) continue;
            if (!part.isFuelTank()) continue;

            CompoundTag beTag = info.nbt();

            fuelCapacityMb += part.getFuelCapacityMb(state);
            int amt = part.getFuelMb(beTag);
            fuelAmountMb += amt;

            FluidStack fs = part.getFuelFluid(beTag);
            if (!fs.isEmpty() && chosen.isEmpty()) chosen = fs.copy();
        }

        if (!chosen.isEmpty()) {
            CompoundTag t = new CompoundTag();
            chosen.writeToNBT(t);
            fuelFluidTag = t;
        }

        LOGGER.warn("[MISSILE FUEL] total={} cap={}", fuelAmountMb, fuelCapacityMb);
    }
    @Override
    public CompoundTag writeNBT(boolean clientData) {
        if (this.anchor == null) this.anchor = BlockPos.ZERO;
        if (this.startPos == null) this.startPos = BlockPos.ZERO;
        if (this.initialOrientation == null) this.initialOrientation = Direction.UP;

        CompoundTag tag = super.writeNBT(clientData);

        tag.putInt("kaboom:FuelAmountMb", fuelAmountMb);
        tag.putInt("kaboom:FuelCapacityMb", fuelCapacityMb);
        if (fuelFluidTag != null) tag.put("kaboom:FuelFluid", fuelFluidTag);

        tag.putLong("kaboom:CapLocalPos", capLocalPos.asLong());
        tag.putLong("kaboom:EndLocalPos", endLocalPos.asLong());
        if (warheadLocalPos != null) tag.putLong("kaboom:WarheadLocalPos", warheadLocalPos.asLong());
        if (guidanceTargetPoint != null) {
            tag.putDouble("GuidanceX", guidanceTargetPoint.x);
            tag.putDouble("GuidanceY", guidanceTargetPoint.y);
            tag.putDouble("GuidanceZ", guidanceTargetPoint.z);
        }
        if (guidanceTag != null && !guidanceTag.isEmpty())
            tag.put("Guidance", guidanceTag);

        return tag;
    }

    @Override
    public void readNBT(Level level, CompoundTag tag, boolean clientData) {
        super.readNBT(level, tag, clientData);

        this.fuelAmountMb = tag.getInt("kaboom:FuelAmountMb");
        this.fuelCapacityMb = tag.getInt("kaboom:FuelCapacityMb");
        this.fuelFluidTag = tag.contains("kaboom:FuelFluid") ? tag.getCompound("kaboom:FuelFluid") : null;

        if (tag.contains("kaboom:CapLocalPos")) capLocalPos = BlockPos.of(tag.getLong("kaboom:CapLocalPos"));
        if (tag.contains("kaboom:EndLocalPos")) endLocalPos = BlockPos.of(tag.getLong("kaboom:EndLocalPos"));
        warheadLocalPos = tag.contains("kaboom:WarheadLocalPos") ? BlockPos.of(tag.getLong("kaboom:WarheadLocalPos")) : null;

        if (fuelCapacityMb < 0) fuelCapacityMb = 0;
        if (fuelAmountMb < 0) fuelAmountMb = 0;
        if (fuelAmountMb > fuelCapacityMb) fuelAmountMb = fuelCapacityMb;
        if (tag.contains("GuidanceX")) {
            guidanceTargetPoint = new Vec3(
                    tag.getDouble("GuidanceX"),
                    tag.getDouble("GuidanceY"),
                    tag.getDouble("GuidanceZ")
            );
        } else {
            guidanceTargetPoint = null;
        }
        // readNBT
        guidanceTag = tag.contains("Guidance") ? tag.getCompound("Guidance") : null;
    }

}