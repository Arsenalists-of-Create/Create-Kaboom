package com.happysg.kaboom.block.missiles;

import com.happysg.kaboom.CreateKaboom;
import com.happysg.kaboom.block.missiles.assembly.IMissileComponent;
import com.happysg.kaboom.block.missiles.assembly.MissileAssembler;
import com.happysg.kaboom.block.missiles.assembly.MissileAssemblyResult;
import com.happysg.kaboom.block.missiles.assembly.MissileLaunchHelper;
import com.happysg.kaboom.block.missiles.assembly.MissileSize;
import com.happysg.kaboom.block.missiles.parts.thrust.ThrusterBlockEntity;
import com.happysg.kaboom.block.missiles.parts.fuel.MissileFuelTankBlockEntity;
import com.happysg.kaboom.block.missiles.util.IMissileGuidanceProvider;
import com.happysg.kaboom.client.MissileClientEffects;
import com.happysg.kaboom.config.KaboomConfig;
import com.happysg.kaboom.networking.MountedMissileLaunchEffectPacket;
import com.happysg.kaboom.networking.NetworkHandler;
import com.happysg.kaboom.registry.ModContraptionTypes;
import com.simibubi.create.api.contraption.ContraptionType;
import com.simibubi.create.content.contraptions.AssemblyException;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import rbasamoyai.createbigcannons.cannon_control.ControlPitchContraption;
import rbasamoyai.createbigcannons.cannon_control.cannon_types.ICannonContraptionType;
import rbasamoyai.createbigcannons.cannon_control.contraption.AbstractMountedCannonContraption;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;
import rbasamoyai.createbigcannons.index.CBCBlocks;

public class MissileContraption extends AbstractMountedCannonContraption {
    public BlockState warheadState;
    public BlockPos controllerWorldPos = BlockPos.ZERO;
    public Direction assemblyDirection = Direction.UP;
    public int fuelAmountMb = 0;
    public int fuelCapacityMb = 0;
    public MissileSize missileSize = MissileSize.SMALL;
    public int fuelTankCount = 1;
    public CompoundTag fuelFluidTag = null;
    public BlockPos warheadLocalPos = null;
    public BlockPos capLocalPos = BlockPos.ZERO;
    public BlockPos endLocalPos = BlockPos.ZERO;
    public Vec3 guidanceTargetPoint = null;
    @Nullable
    public CompoundTag guidanceTag = null;
    @Nullable
    public CompoundTag chainSystemTag = null;
    private int launchTicksRemaining;
    private transient boolean clientLaunchEffectStarted;
    @Nullable
    private transient Vec3 lastNozzleWorldPosition;
    private transient Vec3 observedNozzleVelocity = Vec3.ZERO;

    @Override
    public ContraptionType getType() {
        return ModContraptionTypes.MISSILE.value();
    }

    @Override
    public boolean assemble(Level level, BlockPos mountedPos) throws AssemblyException {
        if (CBCBlocks.CANNON_CARRIAGE.has(level.getBlockState(mountedPos.below()))) {
            throw unsupportedMount();
        }

        BlockPos controllerPos = MissileAssembler.findControllerFromComponent(level, mountedPos);
        if (controllerPos == null) {
            throw invalidMissile();
        }

        MissileAssemblyResult result = MissileAssembler.scan(level, controllerPos);
        if (!result.isValid() || !result.getBlocks().contains(mountedPos)) {
            throw invalidMissile();
        }
        if (result.getMissileSize() == MissileSize.HUGE) {
            throw unsupportedSize();
        }
        if (result.getBlocks().size() > getMaxCannonLength()) {
            throw missileTooLong();
        }
        validateWarheadAxis(level, result);

        this.captureFromScan(level, result, mountedPos, true);
        return !this.blocks.isEmpty();
    }

    public void captureFromScan(Level level, MissileAssemblyResult result) {
        this.captureFromScan(level, result, result.getControllerPos(), false);
    }

    private void captureFromScan(Level level, MissileAssemblyResult result, BlockPos localOrigin,
                                 boolean captureMountedBlockEntities) {
        this.blocks.clear();
        this.presentBlockEntities.clear();
        this.controllerWorldPos = result.getControllerPos();
        this.assemblyDirection = result.getAssemblyDirection();
        this.initialOrientation = this.assemblyDirection;
        this.missileSize = result.getMissileSize() == null ? MissileSize.SMALL : result.getMissileSize();
        this.fuelTankCount = Math.max(1, result.getFuelTankCount());
        this.warheadLocalPos = result.getWarheadLocal();
        this.capLocalPos = this.warheadLocalPos;
        this.endLocalPos = this.warheadLocalPos;
        this.warheadState = level.getBlockState(result.getWarhead());
        this.startPos = result.getControllerPos().subtract(localOrigin);
        this.anchor = captureMountedBlockEntities ? localOrigin : BlockPos.ZERO;

        HolderLookup.Provider registries = level.registryAccess();
        for (BlockPos worldPos : result.getBlocks()) {
            BlockState state = level.getBlockState(worldPos);
            BlockEntity blockEntity = level.getBlockEntity(worldPos);
            if (this.guidanceTag == null || this.guidanceTag.isEmpty()) {
                if (blockEntity instanceof IMissileGuidanceProvider provider) {
                    this.guidanceTag = provider.exportGuidance().toTag();
                } else if (blockEntity != null && state.getBlock() instanceof IMissileComponent part && part.isGuidance()) {
                    this.guidanceTag = blockEntity.saveWithoutMetadata(registries);
                }
            }
            if (worldPos.equals(result.getControllerPos()) && blockEntity instanceof ThrusterBlockEntity thruster) {
                this.chainSystemTag = thruster.getChainSystem().save();
            }

            CompoundTag blockEntityTag = blockEntity == null ? null : blockEntity.saveWithFullMetadata(registries);
            BlockPos localPos = worldPos.subtract(localOrigin);
            this.blocks.put(localPos, new StructureBlockInfo(localPos, state, blockEntityTag));

            if (captureMountedBlockEntities && blockEntityTag != null) {
                BlockEntity captured = BlockEntity.loadStatic(localPos, state, blockEntityTag.copy(), registries);
                if (captured != null) {
                    captured.setLevel(level);
                    this.presentBlockEntities.put(localPos, captured);
                }
            }
        }

        this.computeFuelFromCapturedBlocks(level);
        this.calculateExtensionLengths();
        this.bounds = this.createBoundsFromExtensionLengths();
    }

    public MissileContraption createFlightCopy(Level level) {
        this.refreshCapturedBlockEntities(level.registryAccess());
        if (this.presentBlockEntities.get(this.startPos) instanceof ThrusterBlockEntity thruster) {
            this.chainSystemTag = thruster.getChainSystem().save();
        }

        MissileContraption flight = new MissileContraption();
        flight.warheadState = this.warheadState;
        flight.controllerWorldPos = BlockPos.ZERO;
        flight.assemblyDirection = this.assemblyDirection;
        flight.initialOrientation = this.assemblyDirection;
        flight.missileSize = this.missileSize;
        flight.fuelTankCount = this.fuelTankCount;
        flight.fuelAmountMb = this.fuelAmountMb;
        flight.fuelCapacityMb = this.fuelCapacityMb;
        flight.fuelFluidTag = copyTag(this.fuelFluidTag);
        flight.warheadLocalPos = this.warheadLocalPos;
        flight.capLocalPos = this.capLocalPos;
        flight.endLocalPos = this.endLocalPos;
        flight.guidanceTargetPoint = this.guidanceTargetPoint;
        flight.guidanceTag = copyTag(this.guidanceTag);
        flight.chainSystemTag = copyTag(this.chainSystemTag);

        BlockPos controllerOffset = this.startPos;
        for (StructureBlockInfo info : this.blocks.values()) {
            BlockPos flightPos = info.pos().subtract(controllerOffset);
            CompoundTag blockEntityTag = copyTag(info.nbt());
            if (blockEntityTag != null) {
                blockEntityTag.remove("x");
                blockEntityTag.remove("y");
                blockEntityTag.remove("z");
            }
            flight.blocks.put(flightPos, new StructureBlockInfo(flightPos, info.state(), blockEntityTag));
        }

        flight.anchor = BlockPos.ZERO;
        flight.startPos = BlockPos.ZERO;
        flight.calculateExtensionLengths();
        flight.bounds = flight.createBoundsFromExtensionLengths();
        return flight;
    }

    private void refreshCapturedBlockEntities(HolderLookup.Provider registries) {
        for (Map.Entry<BlockPos, BlockEntity> entry : this.presentBlockEntities.entrySet()) {
            StructureBlockInfo info = this.blocks.get(entry.getKey());
            if (info == null) {
                continue;
            }
            CompoundTag tag = entry.getValue().saveWithFullMetadata(registries);
            tag.remove("x");
            tag.remove("y");
            tag.remove("z");
            this.blocks.put(entry.getKey(), new StructureBlockInfo(info.pos(), info.state(), tag));
        }
    }

    private void calculateExtensionLengths() {
        Direction positive = Direction.get(Direction.AxisDirection.POSITIVE, this.assemblyDirection.getAxis());
        int minimum = 0;
        int maximum = 0;
        for (BlockPos localPos : this.blocks.keySet()) {
            int coordinate = localPos.getX() * positive.getStepX()
                    + localPos.getY() * positive.getStepY()
                    + localPos.getZ() * positive.getStepZ();
            minimum = Math.min(minimum, coordinate);
            maximum = Math.max(maximum, coordinate);
        }
        this.backExtensionLength = -minimum;
        this.frontExtensionLength = maximum;
    }

    private void computeFuelFromCapturedBlocks(Level level) {
        this.fuelAmountMb = 0;
        this.fuelCapacityMb = 0;
        this.fuelFluidTag = null;
        FluidStack chosen = FluidStack.EMPTY;

        for (StructureBlockInfo info : this.blocks.values()) {
            Block block = info.state().getBlock();
            if (block instanceof IMissileComponent part && part.isFuelTank()) {
                this.fuelCapacityMb += part.getFuelCapacityMb(info.state());
                this.fuelAmountMb += part.getFuelMb(info.nbt());
                FluidStack fluid = part.getFuelFluid(info.nbt(), level.registryAccess());
                if (!fluid.isEmpty() && chosen.isEmpty()) {
                    chosen = fluid.copy();
                }
            }
        }

        if (!chosen.isEmpty()) {
            this.fuelFluidTag = (CompoundTag) chosen.saveOptional(level.registryAccess());
        }
    }

    @Override
    public void onRedstoneUpdate(ServerLevel level, PitchOrientedContraptionEntity entity, boolean togglePower,
                                 int firePower, ControlPitchContraption controller) {
        if (togglePower && firePower > 0) {
            this.fireShot(level, entity);
        }
    }

    @Override
    public void fireShot(ServerLevel level, PitchOrientedContraptionEntity entity) {
        if (this.launchTicksRemaining > 0) {
            return;
        }
        if (!KaboomConfig.server().delayedMissileLaunch.get()) {
            if (MissileLaunchHelper.canBeginMountedLaunch(level, this, entity)) {
                MissileLaunchHelper.launchMounted(level, this, entity);
            }
            return;
        }
        if (!MissileLaunchHelper.canBeginMountedLaunch(level, this, entity)) return;
        this.launchTicksRemaining = this.missileSize.launchDelayTicks();
        Vec3 localNozzle = this.localNozzlePosition();
        Vec3 localForward = Vec3.atLowerCornerOf(this.assemblyDirection.getNormal());
        NetworkHandler.sendToPlayersTrackingEntity(entity, new MountedMissileLaunchEffectPacket(
                entity.getId(), localNozzle, localForward, this.missileSize, this.launchTicksRemaining));
    }

    @Override
    public void tick(Level level, PitchOrientedContraptionEntity entity) {
        super.tick(level, entity);
        Vec3 nozzleNow = this.nozzleWorldPosition(entity);
        if (this.lastNozzleWorldPosition != null) {
            Vec3 observed = nozzleNow.subtract(this.lastNozzleWorldPosition);
            if (Double.isFinite(observed.x) && Double.isFinite(observed.y) && Double.isFinite(observed.z)
                    && observed.lengthSqr() < 4096.0) {
                this.observedNozzleVelocity = observed;
            }
        }
        this.lastNozzleWorldPosition = nozzleNow;
        if (this.launchTicksRemaining <= 0) return;
        if (level.isClientSide) {
            if (!this.clientLaunchEffectStarted) {
                this.clientLaunchEffectStarted = true;
                MissileClientEffects.startMountedLaunch(entity, this.localNozzlePosition(),
                        Vec3.atLowerCornerOf(this.assemblyDirection.getNormal()),
                        this.missileSize, this.launchTicksRemaining);
            }
            --this.launchTicksRemaining;
            return;
        }
        this.burnMountedFuel(level, Math.max(1, KaboomConfig.server().maxFuelBurnPerTick.get()));
        --this.launchTicksRemaining;
        if (this.launchTicksRemaining == 0 && level instanceof ServerLevel serverLevel) {
            MissileLaunchHelper.launchMounted(serverLevel, this, entity);
        }
    }

    public Vec3 localNozzlePosition() {
        Vec3 center = Vec3.atCenterOf(this.startPos);
        Vec3 backward = Vec3.atLowerCornerOf(this.assemblyDirection.getOpposite().getNormal());
        return center.add(backward.scale(0.55));
    }

    public Vec3 nozzleWorldPosition(PitchOrientedContraptionEntity entity) {
        return entity.toGlobalVector(this.localNozzlePosition(), 0);
    }

    public Vec3 observedNozzleVelocity() {
        return this.observedNozzleVelocity;
    }

    private void burnMountedFuel(Level level, int requested) {
        int remaining = Math.min(requested, this.fuelAmountMb);
        for (BlockEntity blockEntity : this.presentBlockEntities.values()) {
            if (remaining <= 0) break;
            if (blockEntity instanceof MissileFuelTankBlockEntity tank) {
                remaining -= tank.getTank().drain(remaining, IFluidHandler.FluidAction.EXECUTE).getAmount();
            }
        }
        int burned = Math.min(requested, this.fuelAmountMb) - remaining;
        if (burned <= 0) burned = Math.min(requested, this.fuelAmountMb);
        this.fuelAmountMb = Math.max(0, this.fuelAmountMb - burned);
        this.refreshCapturedBlockEntities(level.registryAccess());
    }

    @Override
    public float getWeightForStress() {
        return this.blocks.size() * (this.missileSize == MissileSize.LARGE ? 2.0F : 1.0F);
    }

    @Override
    public Vec3 getInteractionVec(PitchOrientedContraptionEntity entity) {
        return entity.toGlobalVector(Vec3.atCenterOf(this.startPos), 0);
    }

    @Override
    public ICannonContraptionType getCannonType() {
        return ModContraptionTypes.MISSILE_CANNON_TYPE;
    }

    @Override
    public CompoundTag writeNBT(HolderLookup.Provider registries, boolean clientData) {
        CompoundTag tag = super.writeNBT(registries, clientData);
        tag.putInt("kaboom:FuelAmountMb", this.fuelAmountMb);
        tag.putInt("kaboom:FuelCapacityMb", this.fuelCapacityMb);
        tag.putString("kaboom:MissileSize", this.missileSize.name());
        tag.putInt("kaboom:FuelTankCount", this.fuelTankCount);
        tag.putInt("kaboom:LaunchTicksRemaining", this.launchTicksRemaining);
        tag.putInt("kaboom:AssemblyDirection", this.assemblyDirection.get3DDataValue());
        if (this.fuelFluidTag != null) {
            tag.put("kaboom:FuelFluid", this.fuelFluidTag);
        }
        tag.putLong("kaboom:CapLocalPos", this.capLocalPos.asLong());
        tag.putLong("kaboom:EndLocalPos", this.endLocalPos.asLong());
        if (this.warheadLocalPos != null) {
            tag.putLong("kaboom:WarheadLocalPos", this.warheadLocalPos.asLong());
        }
        if (this.guidanceTargetPoint != null) {
            tag.putDouble("GuidanceX", this.guidanceTargetPoint.x);
            tag.putDouble("GuidanceY", this.guidanceTargetPoint.y);
            tag.putDouble("GuidanceZ", this.guidanceTargetPoint.z);
        }
        if (this.guidanceTag != null && !this.guidanceTag.isEmpty()) {
            tag.put("Guidance", this.guidanceTag);
        }
        if (this.chainSystemTag != null && !this.chainSystemTag.isEmpty()) {
            tag.put("kaboom:ChainSystem", this.chainSystemTag);
        }
        return tag;
    }

    @Override
    public void readNBT(Level level, CompoundTag tag, boolean clientData) {
        super.readNBT(level, tag, clientData);
        this.restoreWeightMetadata(tag);
        this.fuelAmountMb = tag.getInt("kaboom:FuelAmountMb");
        this.fuelCapacityMb = tag.getInt("kaboom:FuelCapacityMb");
        this.launchTicksRemaining = Math.max(0, tag.getInt("kaboom:LaunchTicksRemaining"));
        this.clientLaunchEffectStarted = false;
        this.assemblyDirection = tag.contains("kaboom:AssemblyDirection")
                ? Direction.from3DDataValue(tag.getInt("kaboom:AssemblyDirection"))
                : this.initialOrientation;
        this.fuelFluidTag = tag.contains("kaboom:FuelFluid") ? tag.getCompound("kaboom:FuelFluid") : null;
        if (tag.contains("kaboom:CapLocalPos")) {
            this.capLocalPos = BlockPos.of(tag.getLong("kaboom:CapLocalPos"));
        }
        if (tag.contains("kaboom:EndLocalPos")) {
            this.endLocalPos = BlockPos.of(tag.getLong("kaboom:EndLocalPos"));
        }
        this.warheadLocalPos = tag.contains("kaboom:WarheadLocalPos")
                ? BlockPos.of(tag.getLong("kaboom:WarheadLocalPos"))
                : null;
        this.fuelCapacityMb = Math.max(0, this.fuelCapacityMb);
        this.fuelAmountMb = Math.max(0, Math.min(this.fuelAmountMb, this.fuelCapacityMb));
        this.guidanceTargetPoint = tag.contains("GuidanceX")
                ? new Vec3(tag.getDouble("GuidanceX"), tag.getDouble("GuidanceY"), tag.getDouble("GuidanceZ"))
                : null;
        this.guidanceTag = tag.contains("Guidance") ? tag.getCompound("Guidance") : null;
        this.chainSystemTag = tag.contains("kaboom:ChainSystem") ? tag.getCompound("kaboom:ChainSystem") : null;
    }

    private void restoreWeightMetadata(CompoundTag tag) {
        MissileSize derivedSize = null;
        int derivedFuelTankCount = 0;
        for (StructureBlockInfo info : this.blocks.values()) {
            if (info.state().getBlock() instanceof IMissileComponent part) {
                if (derivedSize == null && part.isThruster()) {
                    derivedSize = part.getMissileSize();
                }
                if (part.isFuelTank()) {
                    derivedFuelTankCount++;
                }
            }
        }

        this.missileSize = derivedSize == null ? MissileSize.SMALL : derivedSize;
        if (tag.contains("kaboom:MissileSize")) {
            try {
                this.missileSize = MissileSize.valueOf(tag.getString("kaboom:MissileSize"));
            } catch (IllegalArgumentException ignored) {
            }
        }
        this.fuelTankCount = tag.contains("kaboom:FuelTankCount")
                ? Math.max(1, tag.getInt("kaboom:FuelTankCount"))
                : Math.max(1, derivedFuelTankCount);
    }

    private static void validateWarheadAxis(Level level, MissileAssemblyResult result) throws AssemblyException {
        BlockState warhead = level.getBlockState(result.getWarhead());
        if (!warhead.hasProperty(BlockStateProperties.FACING)
                || warhead.getValue(BlockStateProperties.FACING).getAxis() != result.getAssemblyDirection().getAxis()) {
            BlockPos pos = result.getWarhead();
            throw new AssemblyException(Component.translatable(
                    "exception." + CreateKaboom.MODID + ".cannon_mount.misalignedMissileComponent",
                    pos.getX(), pos.getY(), pos.getZ()));
        }
    }

    @Nullable
    private static CompoundTag copyTag(@Nullable CompoundTag tag) {
        return tag == null ? null : tag.copy();
    }

    private static AssemblyException invalidMissile() {
        return new AssemblyException(Component.translatable(
                "exception." + CreateKaboom.MODID + ".cannon_mount.invalidMissile"));
    }

    private static AssemblyException unsupportedSize() {
        return new AssemblyException(Component.translatable(
                "exception." + CreateKaboom.MODID + ".cannon_mount.unsupportedMissileSize"));
    }

    private static AssemblyException missileTooLong() {
        return new AssemblyException(Component.translatable(
                "exception." + CreateKaboom.MODID + ".cannon_mount.missileTooLong", getMaxCannonLength()));
    }

    private static AssemblyException unsupportedMount() {
        return new AssemblyException(Component.translatable(
                "exception." + CreateKaboom.MODID + ".cannon_mount.unsupportedMissileMount"));
    }
}
