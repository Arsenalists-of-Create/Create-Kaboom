package com.happysg.kaboom.block.missiles;

import com.happysg.kaboom.block.missiles.assembly.IMissileComponent;
import com.happysg.kaboom.block.missiles.assembly.MissileAssemblyResult;
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
import net.minecraftforge.fluids.FluidStack;
import org.slf4j.Logger;

public class MissileContraption extends MountedContraption {

    private static final Logger LOGGER = LogUtils.getLogger();
    public BlockState warheadState;
    public BlockPos controllerWorldPos = BlockPos.ZERO;
    private BlockPos startPos;
    private Object initialOrientation;
    public int fuelAmountMb = 0;
    public int fuelCapacityMb = 0;
    public CompoundTag fuelFluidTag = null; // optional: store FluidStack in tag
    public void captureFromScan(Level level, MissileAssemblyResult result) {
        this.controllerWorldPos = result.getControllerPos();

        // Fill the blocks map (local coords relative to controller)
        for (BlockPos worldPos : result.getBlocks()) {
            BlockState state = level.getBlockState(worldPos);
            BlockEntity be = level.getBlockEntity(worldPos);

            CompoundTag tag = be != null ? be.saveWithFullMetadata() : null;
            if (tag != null) {
                // contraptions don’t want absolute x/y/z baked into the saved tag
                tag.remove("x");
                tag.remove("y");
                tag.remove("z");
            }

            BlockPos localPos = worldPos.subtract(controllerWorldPos);
            this.getBlocks().put(localPos, new StructureBlockInfo(localPos, state, tag));
        }
        this.computeFuelFromCapturedBlocks(level);
        this.anchor = BlockPos.ZERO;      // local origin (since your blocks are local)
        this.startPos =BlockPos.ZERO;    // safe default; Create expects non-null

        this.initialOrientation = Direction.UP; // Direction
        this.bounds = computeAabbFromLocalBlocks();
        LOGGER.warn("[MISSILE] captured blocks={} bounds={}", this.getBlocks().size(), this.bounds);
    }

    private AABB computeAabbFromLocalBlocks() {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        for (BlockPos p : this.getBlocks().keySet()) {
            minX = Math.min(minX, p.getX());
            minY = Math.min(minY, p.getY());
            minZ = Math.min(minZ, p.getZ());
            maxX = Math.max(maxX, p.getX());
            maxY = Math.max(maxY, p.getY());
            maxZ = Math.max(maxZ, p.getZ());
        }

        // include full block volumes
        return new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
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

        return tag;
    }
    @Override
    public void readNBT(Level level, CompoundTag tag, boolean clientData) {
        super.readNBT(level, tag, clientData);

        this.fuelAmountMb = tag.getInt("kaboom:FuelAmountMb");
        this.fuelCapacityMb = tag.getInt("kaboom:FuelCapacityMb");
        this.fuelFluidTag = tag.contains("kaboom:FuelFluid") ? tag.getCompound("kaboom:FuelFluid") : null;

        // Optional: sanity clamp
        if (fuelCapacityMb < 0) fuelCapacityMb = 0;
        if (fuelAmountMb < 0) fuelAmountMb = 0;
        if (fuelAmountMb > fuelCapacityMb) fuelAmountMb = fuelCapacityMb;
    }
}