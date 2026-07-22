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
import com.happysg.kaboom.block.missiles.util.MissileGuidanceData;
import com.happysg.kaboom.block.missiles.util.MissileGuidanceType;
import com.happysg.kaboom.client.MissileClientEffects;
import com.happysg.kaboom.config.KaboomConfig;
import com.happysg.kaboom.networking.MountedMissileLaunchEffectPacket;
import com.happysg.kaboom.networking.NetworkHandler;
import com.happysg.kaboom.registry.ModContraptionTypes;
import com.simibubi.create.api.contraption.ContraptionType;
import com.simibubi.create.content.contraptions.AssemblyException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
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
    private static final String MOUNTED_MISSILES_TAG = "kaboom:MountedMissiles";

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
    private final List<MountedMissileData> mountedMissiles = new ArrayList<>(3);
    private transient int diagnosticTicks;

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
            if (result.getFailure() != null) {
                throw result.getFailure();
            }
            throw invalidMissile();
        }
        if (result.getMissileSize() == MissileSize.HUGE) {
            throw unsupportedSize();
        }
        if (result.getBlocks().size() > getMaxCannonLength()) {
            throw missileTooLong();
        }
        validateWarheadAxis(level, result);

        List<MissileAssemblyResult> groupedResults = collectMissileBundle(level, result);
        this.blocks.clear();
        this.presentBlockEntities.clear();
        this.mountedMissiles.clear();
        this.anchor = mountedPos;
        this.initialOrientation = result.getAssemblyDirection();
        for (MissileAssemblyResult groupedResult : groupedResults) {
            this.mountedMissiles.add(this.captureMissile(level, groupedResult, mountedPos, true));
        }
        this.applyActiveMissile();
        this.calculateExtensionLengths();
        this.bounds = this.createBoundsFromExtensionLengths();
        return !this.blocks.isEmpty();
    }

    public void captureFromScan(Level level, MissileAssemblyResult result) {
        this.blocks.clear();
        this.presentBlockEntities.clear();
        this.mountedMissiles.clear();
        this.anchor = BlockPos.ZERO;
        this.initialOrientation = result.getAssemblyDirection();
        this.mountedMissiles.add(this.captureMissile(level, result, result.getControllerPos(), false));
        this.applyActiveMissile();
        this.calculateExtensionLengths();
        this.bounds = this.createBoundsFromExtensionLengths();
    }

    private MountedMissileData captureMissile(Level level, MissileAssemblyResult result, BlockPos localOrigin,
                                               boolean captureMountedBlockEntities) {
        MountedMissileData missile = new MountedMissileData();
        missile.controllerLocalPos = result.getControllerPos().subtract(localOrigin);
        missile.assemblyDirection = result.getAssemblyDirection();
        missile.missileSize = result.getMissileSize() == null ? MissileSize.SMALL : result.getMissileSize();
        missile.fuelTankCount = Math.max(1, result.getFuelTankCount());
        missile.warheadLocalPos = result.getWarheadLocal();
        missile.capLocalPos = missile.warheadLocalPos;
        missile.endLocalPos = missile.warheadLocalPos;

        HolderLookup.Provider registries = level.registryAccess();
        for (BlockPos worldPos : result.getBlocks()) {
            BlockState state = level.getBlockState(worldPos);
            BlockEntity blockEntity = level.getBlockEntity(worldPos);
            if (missile.guidanceTag == null || missile.guidanceTag.isEmpty()) {
                if (blockEntity instanceof IMissileGuidanceProvider provider) {
                    missile.guidanceTag = provider.exportGuidance().toTag();
                } else if (blockEntity != null && state.getBlock() instanceof IMissileComponent part && part.isGuidance()) {
                    missile.guidanceTag = blockEntity.saveWithoutMetadata(registries);
                }
            }
            if (worldPos.equals(result.getControllerPos()) && blockEntity instanceof ThrusterBlockEntity thruster) {
                missile.chainSystemTag = thruster.getChainSystem().save();
            }

            CompoundTag blockEntityTag = blockEntity == null ? null : blockEntity.saveWithFullMetadata(registries);
            BlockPos localPos = worldPos.subtract(localOrigin);
            missile.localBlocks.add(localPos.immutable());
            this.blocks.put(localPos, new StructureBlockInfo(localPos, state, blockEntityTag));

            if (captureMountedBlockEntities && blockEntityTag != null) {
                BlockEntity captured = BlockEntity.loadStatic(localPos, state, blockEntityTag.copy(), registries);
                if (captured != null) {
                    captured.setLevel(level);
                    this.presentBlockEntities.put(localPos, captured);
                }
            }
        }
        computeFuel(level, missile);
        return missile;
    }

    private static List<MissileAssemblyResult> collectMissileBundle(Level level,
                                                                    MissileAssemblyResult primary)
            throws AssemblyException {
        List<MissileAssemblyResult> results = new ArrayList<>(3);
        results.add(primary);
        Direction forward = primary.getAssemblyDirection();
        if (!forward.getAxis().isHorizontal()) {
            return results;
        }

        MissileGuidanceType guidanceType = guidanceType(level, primary);
        Direction left = forward.getCounterClockWise();
        Direction right = forward.getClockWise();
        collectSideMissile(level, primary, left, guidanceType, results);
        collectSideMissile(level, primary, right, guidanceType, results);
        return results;
    }

    private static void collectSideMissile(Level level, MissileAssemblyResult primary, Direction side,
                                           MissileGuidanceType guidanceType,
                                           List<MissileAssemblyResult> results) throws AssemblyException {
        MissileAssemblyResult sideMissile = findAdjacentMissile(level, primary, side);
        if (sideMissile == null) {
            return;
        }
        validateGroupedMissile(level, sideMissile, primary.getAssemblyDirection(), guidanceType);
        if (findAdjacentMissile(level, sideMissile, side) != null) {
            throw invalidMissileBundle();
        }
        results.add(sideMissile);
    }

    @Nullable
    private static MissileAssemblyResult findAdjacentMissile(Level level, MissileAssemblyResult source,
                                                              Direction side) throws AssemblyException {
        Set<BlockPos> controllers = new LinkedHashSet<>();
        for (BlockPos sourcePos : source.getBlocks()) {
            BlockPos candidatePos = sourcePos.relative(side);
            if (!MissileAssembler.isMissileStructureBlock(level.getBlockState(candidatePos))) {
                continue;
            }
            BlockPos controller = MissileAssembler.findControllerFromComponent(level, candidatePos);
            if (controller == null) {
                throw invalidMissileBundle();
            }
            controllers.add(controller.immutable());
        }
        if (controllers.isEmpty()) {
            return null;
        }
        if (controllers.size() != 1) {
            throw invalidMissileBundle();
        }
        MissileAssemblyResult result = MissileAssembler.scan(level, controllers.iterator().next());
        if (!result.isValid()) {
            throw invalidMissileBundle();
        }
        return result;
    }

    private static void validateGroupedMissile(Level level, MissileAssemblyResult result,
                                               Direction requiredDirection,
                                               MissileGuidanceType requiredGuidance) throws AssemblyException {
        if (result.getAssemblyDirection() != requiredDirection
                || guidanceType(level, result) != requiredGuidance) {
            throw invalidMissileBundle();
        }
        if (result.getMissileSize() == MissileSize.HUGE) {
            throw unsupportedSize();
        }
        if (result.getBlocks().size() > getMaxCannonLength()) {
            throw missileTooLong();
        }
        validateWarheadAxis(level, result);
    }

    private static MissileGuidanceType guidanceType(Level level, MissileAssemblyResult result)
            throws AssemblyException {
        BlockEntity blockEntity = level.getBlockEntity(result.guidance());
        if (!(blockEntity instanceof IMissileGuidanceProvider provider)) {
            throw invalidMissileBundle();
        }
        MissileGuidanceData guidance = provider.exportGuidance();
        return guidance == null ? MissileGuidanceType.UNKNOWN : guidance.guidanceType();
    }

    public MissileContraption createFlightCopy(Level level) {
        this.refreshCapturedBlockEntities(level.registryAccess());
        if (this.presentBlockEntities.get(this.startPos) instanceof ThrusterBlockEntity thruster) {
            this.chainSystemTag = thruster.getChainSystem().save();
        }
        this.syncActiveMissile();

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
        Set<BlockPos> activeBlocks = this.mountedMissiles.isEmpty()
                ? this.blocks.keySet()
                : Set.copyOf(this.mountedMissiles.getFirst().localBlocks);
        for (BlockPos localPos : activeBlocks) {
            StructureBlockInfo info = this.blocks.get(localPos);
            if (info == null || info.state().isAir()) {
                continue;
            }
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
        Iterable<BlockPos> positions = this.mountedMissiles.isEmpty()
                ? this.blocks.keySet()
                : this.mountedMissiles.stream().flatMap(missile -> missile.localBlocks.stream()).toList();
        for (BlockPos localPos : positions) {
            int coordinate = localPos.getX() * positive.getStepX()
                    + localPos.getY() * positive.getStepY()
                    + localPos.getZ() * positive.getStepZ();
            minimum = Math.min(minimum, coordinate);
            maximum = Math.max(maximum, coordinate);
        }
        this.backExtensionLength = -minimum;
        this.frontExtensionLength = maximum;
    }

    private void computeFuel(Level level, MountedMissileData missile) {
        missile.fuelAmountMb = 0;
        missile.fuelCapacityMb = 0;
        missile.fuelFluidTag = null;
        FluidStack chosen = FluidStack.EMPTY;

        for (BlockPos localPos : missile.localBlocks) {
            StructureBlockInfo info = this.blocks.get(localPos);
            if (info == null) continue;
            Block block = info.state().getBlock();
            if (block instanceof IMissileComponent part && part.isFuelTank()) {
                missile.fuelCapacityMb += part.getFuelCapacityMb(info.state());
                missile.fuelAmountMb += part.getFuelMb(info.nbt());
                FluidStack fluid = part.getFuelFluid(info.nbt(), level.registryAccess());
                if (!fluid.isEmpty() && chosen.isEmpty()) {
                    chosen = fluid.copy();
                }
            }
        }

        if (!chosen.isEmpty()) {
            missile.fuelFluidTag = (CompoundTag) chosen.saveOptional(level.registryAccess());
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
        int launchDelayTicks = KaboomConfig.server().missileLaunchDelayTicks(this.missileSize);
        if (launchDelayTicks <= 0) {
            if (MissileLaunchHelper.canBeginMountedLaunch(level, this, entity)) {
                MissileLaunchHelper.launchMounted(level, this, entity);
            }
            return;
        }
        if (!MissileLaunchHelper.canBeginMountedLaunch(level, this, entity)) return;
        this.launchTicksRemaining = launchDelayTicks;
        this.clientLaunchEffectStarted = false;
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
        if (this.launchTicksRemaining <= 0
                && !level.isClientSide && level instanceof ServerLevel serverLevel
                && ++this.diagnosticTicks >= 10) {
            this.diagnosticTicks = 0;
            if (entity.getController() instanceof com.happysg.kaboom.compat.cbc.MountedMissileController controller) {
                controller.createKaboom$setMissileDiagnostic(
                        MissileLaunchHelper.diagnoseMountedLaunch(serverLevel, this, entity));
            }
        }
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

    public boolean hasAdditionalMountedMissiles() {
        return this.mountedMissiles.size() > 1;
    }

    public void finishGroupedLaunch(PitchOrientedContraptionEntity entity) {
        if (this.mountedMissiles.size() <= 1) {
            return;
        }
        MountedMissileData launched = this.mountedMissiles.removeFirst();
        for (BlockPos localPos : launched.localBlocks) {
            this.presentBlockEntities.remove(localPos);
            entity.setBlock(localPos, new StructureBlockInfo(localPos, Blocks.AIR.defaultBlockState(), null));
        }
        this.applyActiveMissile();
        this.calculateExtensionLengths();
        this.bounds = this.createBoundsFromExtensionLengths();
        this.invalidateColliders();
        this.launchTicksRemaining = 0;
        this.clientLaunchEffectStarted = false;
        this.lastNozzleWorldPosition = this.nozzleWorldPosition(entity);
    }

    private void burnMountedFuel(Level level, int requested) {
        int remaining = Math.min(requested, this.fuelAmountMb);
        Iterable<BlockPos> activeBlocks = this.mountedMissiles.isEmpty()
                ? this.presentBlockEntities.keySet()
                : this.mountedMissiles.getFirst().localBlocks;
        for (BlockPos localPos : activeBlocks) {
            if (remaining <= 0) break;
            BlockEntity blockEntity = this.presentBlockEntities.get(localPos);
            if (blockEntity instanceof MissileFuelTankBlockEntity tank) {
                remaining -= tank.getTank().drain(remaining, IFluidHandler.FluidAction.EXECUTE).getAmount();
            }
        }
        int burned = Math.min(requested, this.fuelAmountMb) - remaining;
        if (burned <= 0) burned = Math.min(requested, this.fuelAmountMb);
        this.fuelAmountMb = Math.max(0, this.fuelAmountMb - burned);
        this.syncActiveMissile();
        this.refreshCapturedBlockEntities(level.registryAccess());
    }

    @Override
    public float getWeightForStress() {
        if (this.mountedMissiles.isEmpty()) {
            return this.blocks.size() * (this.missileSize == MissileSize.LARGE ? 2.0F : 1.0F);
        }
        float weight = 0.0F;
        for (MountedMissileData missile : this.mountedMissiles) {
            weight += missile.localBlocks.size() * (missile.missileSize == MissileSize.LARGE ? 2.0F : 1.0F);
        }
        return weight;
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
        this.syncActiveMissile();
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
        if (!this.mountedMissiles.isEmpty()) {
            ListTag missilesTag = new ListTag();
            for (MountedMissileData missile : this.mountedMissiles) {
                missilesTag.add(missile.write());
            }
            tag.put(MOUNTED_MISSILES_TAG, missilesTag);
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

        this.mountedMissiles.clear();
        if (tag.contains(MOUNTED_MISSILES_TAG, Tag.TAG_LIST)) {
            ListTag missilesTag = tag.getList(MOUNTED_MISSILES_TAG, Tag.TAG_COMPOUND);
            for (int index = 0; index < missilesTag.size(); ++index) {
                MountedMissileData missile = MountedMissileData.read(missilesTag.getCompound(index));
                missile.localBlocks.removeIf(pos -> !this.blocks.containsKey(pos)
                        || this.blocks.get(pos).state().isAir());
                if (!missile.localBlocks.isEmpty()) {
                    this.mountedMissiles.add(missile);
                }
            }
        }
        if (this.mountedMissiles.isEmpty() && !this.blocks.isEmpty()) {
            MountedMissileData legacy = new MountedMissileData();
            legacy.localBlocks.addAll(this.blocks.entrySet().stream()
                    .filter(entry -> !entry.getValue().state().isAir())
                    .map(Map.Entry::getKey)
                    .map(BlockPos::immutable)
                    .toList());
            legacy.copyFrom(this);
            this.mountedMissiles.add(legacy);
        }
        this.applyActiveMissile();
        this.calculateExtensionLengths();
        this.bounds = this.createBoundsFromExtensionLengths();
    }

    private void applyActiveMissile() {
        if (this.mountedMissiles.isEmpty()) {
            return;
        }
        MountedMissileData active = this.mountedMissiles.getFirst();
        this.startPos = active.controllerLocalPos;
        this.assemblyDirection = active.assemblyDirection;
        this.initialOrientation = active.assemblyDirection;
        this.missileSize = active.missileSize;
        this.fuelTankCount = active.fuelTankCount;
        this.fuelAmountMb = active.fuelAmountMb;
        this.fuelCapacityMb = active.fuelCapacityMb;
        this.fuelFluidTag = copyTag(active.fuelFluidTag);
        this.warheadLocalPos = active.warheadLocalPos;
        this.capLocalPos = active.capLocalPos;
        this.endLocalPos = active.endLocalPos;
        this.guidanceTargetPoint = active.guidanceTargetPoint;
        this.guidanceTag = copyTag(active.guidanceTag);
        this.chainSystemTag = copyTag(active.chainSystemTag);
        StructureBlockInfo warhead = active.warheadLocalPos == null ? null : this.blocks.get(
                active.controllerLocalPos.offset(active.warheadLocalPos));
        this.warheadState = warhead == null ? Blocks.AIR.defaultBlockState() : warhead.state();
    }

    private void syncActiveMissile() {
        if (!this.mountedMissiles.isEmpty()) {
            this.mountedMissiles.getFirst().copyFrom(this);
        }
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

    private static AssemblyException invalidMissileBundle() {
        return new AssemblyException(Component.translatable(
                "exception." + CreateKaboom.MODID + ".cannon_mount.invalidMissileBundle"));
    }

    private static final class MountedMissileData {
        private final List<BlockPos> localBlocks = new ArrayList<>();
        private BlockPos controllerLocalPos = BlockPos.ZERO;
        private Direction assemblyDirection = Direction.UP;
        private MissileSize missileSize = MissileSize.SMALL;
        private int fuelTankCount = 1;
        private int fuelAmountMb;
        private int fuelCapacityMb;
        @Nullable
        private CompoundTag fuelFluidTag;
        @Nullable
        private BlockPos warheadLocalPos;
        private BlockPos capLocalPos = BlockPos.ZERO;
        private BlockPos endLocalPos = BlockPos.ZERO;
        @Nullable
        private Vec3 guidanceTargetPoint;
        @Nullable
        private CompoundTag guidanceTag;
        @Nullable
        private CompoundTag chainSystemTag;

        private void copyFrom(MissileContraption contraption) {
            this.controllerLocalPos = contraption.startPos;
            this.assemblyDirection = contraption.assemblyDirection;
            this.missileSize = contraption.missileSize;
            this.fuelTankCount = contraption.fuelTankCount;
            this.fuelAmountMb = contraption.fuelAmountMb;
            this.fuelCapacityMb = contraption.fuelCapacityMb;
            this.fuelFluidTag = copyTag(contraption.fuelFluidTag);
            this.warheadLocalPos = contraption.warheadLocalPos;
            this.capLocalPos = contraption.capLocalPos;
            this.endLocalPos = contraption.endLocalPos;
            this.guidanceTargetPoint = contraption.guidanceTargetPoint;
            this.guidanceTag = copyTag(contraption.guidanceTag);
            this.chainSystemTag = copyTag(contraption.chainSystemTag);
        }

        private CompoundTag write() {
            CompoundTag tag = new CompoundTag();
            tag.putLongArray("Blocks", this.localBlocks.stream().mapToLong(BlockPos::asLong).toArray());
            tag.putLong("Controller", this.controllerLocalPos.asLong());
            tag.putInt("Direction", this.assemblyDirection.get3DDataValue());
            tag.putString("Size", this.missileSize.name());
            tag.putInt("FuelTanks", this.fuelTankCount);
            tag.putInt("Fuel", this.fuelAmountMb);
            tag.putInt("FuelCapacity", this.fuelCapacityMb);
            if (this.fuelFluidTag != null) tag.put("FuelFluid", this.fuelFluidTag.copy());
            if (this.warheadLocalPos != null) tag.putLong("Warhead", this.warheadLocalPos.asLong());
            tag.putLong("Cap", this.capLocalPos.asLong());
            tag.putLong("End", this.endLocalPos.asLong());
            if (this.guidanceTargetPoint != null) {
                tag.putDouble("GuidanceX", this.guidanceTargetPoint.x);
                tag.putDouble("GuidanceY", this.guidanceTargetPoint.y);
                tag.putDouble("GuidanceZ", this.guidanceTargetPoint.z);
            }
            if (this.guidanceTag != null) tag.put("Guidance", this.guidanceTag.copy());
            if (this.chainSystemTag != null) tag.put("ChainSystem", this.chainSystemTag.copy());
            return tag;
        }

        private static MountedMissileData read(CompoundTag tag) {
            MountedMissileData missile = new MountedMissileData();
            for (long packedPos : tag.getLongArray("Blocks")) {
                missile.localBlocks.add(BlockPos.of(packedPos));
            }
            missile.controllerLocalPos = BlockPos.of(tag.getLong("Controller"));
            missile.assemblyDirection = Direction.from3DDataValue(tag.getInt("Direction"));
            try {
                missile.missileSize = MissileSize.valueOf(tag.getString("Size"));
            } catch (IllegalArgumentException ignored) {
            }
            missile.fuelTankCount = Math.max(1, tag.getInt("FuelTanks"));
            missile.fuelCapacityMb = Math.max(0, tag.getInt("FuelCapacity"));
            missile.fuelAmountMb = Math.max(0, Math.min(tag.getInt("Fuel"), missile.fuelCapacityMb));
            missile.fuelFluidTag = tag.contains("FuelFluid") ? tag.getCompound("FuelFluid") : null;
            missile.warheadLocalPos = tag.contains("Warhead") ? BlockPos.of(tag.getLong("Warhead")) : null;
            missile.capLocalPos = tag.contains("Cap") ? BlockPos.of(tag.getLong("Cap")) : BlockPos.ZERO;
            missile.endLocalPos = tag.contains("End") ? BlockPos.of(tag.getLong("End")) : BlockPos.ZERO;
            missile.guidanceTargetPoint = tag.contains("GuidanceX")
                    ? new Vec3(tag.getDouble("GuidanceX"), tag.getDouble("GuidanceY"), tag.getDouble("GuidanceZ"))
                    : null;
            missile.guidanceTag = tag.contains("Guidance") ? tag.getCompound("Guidance") : null;
            missile.chainSystemTag = tag.contains("ChainSystem") ? tag.getCompound("ChainSystem") : null;
            return missile;
        }
    }
}
