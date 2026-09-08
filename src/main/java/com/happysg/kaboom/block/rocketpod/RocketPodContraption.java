package com.happysg.kaboom.block.rocketpod;

import com.happysg.kaboom.CreateKaboom;
import com.happysg.kaboom.block.missiles.util.MissileGuidanceData;
import com.happysg.kaboom.client.RocketClientEffects;
import com.happysg.kaboom.compat.sable.SableUtils;
import com.happysg.kaboom.config.KaboomConfig;
import com.happysg.kaboom.items.rocket.RocketGuidanceLaunchResolver;
import com.happysg.kaboom.items.rocket.RocketGuidanceType;
import com.happysg.kaboom.items.rocket.RocketItem;
import com.happysg.kaboom.items.rocket.UnguidedRocketProjectile;
import com.happysg.kaboom.networking.LaunchSoundHandoffPacket;
import com.happysg.kaboom.networking.NetworkHandler;
import com.happysg.kaboom.networking.RocketPodLaunchSoundPacket;
import com.happysg.kaboom.registry.ModBlocks;
import com.happysg.kaboom.registry.ModContraptionTypes;
import com.happysg.kaboom.registry.ModParticles;
import com.simibubi.create.api.contraption.ContraptionType;
import com.simibubi.create.content.contraptions.AssemblyException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.Vec3;
import rbasamoyai.createbigcannons.cannon_control.ControlPitchContraption;
import rbasamoyai.createbigcannons.cannon_control.cannon_types.ICannonContraptionType;
import rbasamoyai.createbigcannons.cannon_control.contraption.AbstractMountedCannonContraption;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;
import rbasamoyai.createbigcannons.cannons.big_cannons.BigCannonBlock;
import rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile;

public class RocketPodContraption extends AbstractMountedCannonContraption {
    private static final int MIN_CENTERS = 1;
    private static final int MIN_TUBE_PARTS = MIN_CENTERS + 1;
    private static final int REPEATED_LAUNCH_COOLDOWN_TICKS = 10;
    private static final int POST_LAUNCH_BACKBLAST_TICKS = 5;
    private static final int BACKBLAST_PARTICLES_PER_TICK = 3;
    private static final double BACKBLAST_SPAWN_OFFSET = 0.65;
    private static final double BACKBLAST_SPAWN_RADIUS = 0.12;
    private static final double BACKBLAST_BASE_SPEED = 0.45;
    private static final double BACKBLAST_SPEED_VARIANCE = 0.10;
    private static final double BACKBLAST_SPREAD = 0.08;
    private static final String LAUNCHER_REARS_TAG = "LauncherRears";
    private static final String NEXT_LAUNCH_ID_TAG = "NextLaunchId";
    private static final String PENDING_LAUNCHES_TAG = "PendingLaunches";
    private static final String PENDING_REAR_TAG = "Rear";
    private static final String PENDING_SLOT_TAG = "Slot";
    private static final String PENDING_TICKS_TAG = "TicksRemaining";
    private static final String PENDING_LAUNCH_ID_TAG = "LaunchId";
    private static final String LAUNCH_SIGNAL_POWERED_TAG = "LaunchSignalPowered";
    private static final String WAITING_FOR_GUIDANCE_TAG = "WaitingForGuidance";
    private static final String NEXT_LAUNCH_ALLOWED_TICK_TAG = "NextLaunchAllowedTick";

    private final List<BlockPos> launcherRears = new ArrayList<>(3);
    private final List<PendingLaunch> pendingLaunches = new ArrayList<>();
    private final List<LingeringBackblast> lingeringBackblasts = new ArrayList<>();
    private long nextLaunchId;
    private long nextLaunchAllowedTick;
    private boolean launchSignalPowered;
    private boolean waitingForGuidance;

    @Override
    public boolean assemble(Level level, BlockPos mountedPos) throws AssemblyException {
        if (!isRocketPod(level.getBlockState(mountedPos))) {
            return false;
        }

        PodLine primary = findPrimaryLine(level, mountedPos);
        validateComponentAxes(level, primary);

        List<BlockPos> assembledPositions = new ArrayList<>(primary.positions());
        List<BlockPos> launcherRearWorldPositions = new ArrayList<>(3);
        launcherRearWorldPositions.add(primary.rear());
        if (primary.axis().isHorizontal()) {
            collectHorizontalBundle(level, primary, assembledPositions, launcherRearWorldPositions);
        }

        this.anchor = mountedPos;
        this.initialOrientation = primary.forward();
        this.startPos = primary.rear().subtract(mountedPos);
        calculateExtensionLengths(assembledPositions, mountedPos, primary.axis());

        this.launcherRears.clear();
        this.pendingLaunches.clear();
        this.lingeringBackblasts.clear();
        this.nextLaunchId = 0L;
        this.nextLaunchAllowedTick = 0L;
        this.launchSignalPowered = false;
        this.waitingForGuidance = false;
        for (BlockPos rearPos : launcherRearWorldPositions) {
            this.launcherRears.add(rearPos.subtract(mountedPos));
        }

        HolderLookup.Provider registries = level.registryAccess();
        for (BlockPos worldPos : assembledPositions) {
            BlockPos localPos = worldPos.subtract(mountedPos);
            BlockState state = level.getBlockState(worldPos);
            CompoundTag blockEntityNbt = this.getBlockEntityNBT(level, worldPos);
            this.blocks.put(localPos, new StructureBlockInfo(localPos, state, blockEntityNbt));
            if (blockEntityNbt != null) {
                BlockEntity blockEntity = BlockEntity.loadStatic(localPos, state, blockEntityNbt.copy(), registries);
                if (blockEntity != null) {
                    blockEntity.setLevel(level);
                    this.presentBlockEntities.put(localPos, blockEntity);
                }
            }
        }
        ensureLauncherBlockEntities(level);

        this.bounds = this.createBoundsFromExtensionLengths();
        return !this.blocks.isEmpty();
    }

    private static PodLine findPrimaryLine(Level level, BlockPos mountedPos) throws AssemblyException {
        List<PodLine> candidates = new ArrayList<>();
        for (Direction.Axis axis : Direction.Axis.values()) {
            PodLine candidate = findLineOnAxis(level, mountedPos, axis);
            if (candidate != null) {
                candidates.add(candidate);
            }
        }

        List<PodLine> alignedCandidates = candidates.stream()
                .filter(candidate -> componentsAreAligned(level, candidate))
                .toList();
        if (alignedCandidates.size() == 1) {
            return alignedCandidates.getFirst();
        }
        if (candidates.size() == 1) {
            return candidates.getFirst();
        }
        throw invalidComposition();
    }

    private static PodLine findLineOnAxis(Level level, BlockPos mountedPos, Direction.Axis axis) {
        Direction positive = Direction.get(Direction.AxisDirection.POSITIVE, axis);
        Direction negative = positive.getOpposite();

        int negativeLength = contiguousLength(level, mountedPos, negative);
        int positiveLength = contiguousLength(level, mountedPos, positive);
        int totalLength = negativeLength + 1 + positiveLength;
        if (totalLength < MIN_CENTERS + 1 || totalLength > getMaxCannonLength()) {
            return null;
        }

        List<BlockPos> positions = new ArrayList<>(totalLength);
        for (int offset = negativeLength; offset > 0; --offset) {
            positions.add(mountedPos.relative(negative, offset));
        }
        positions.add(mountedPos);
        for (int offset = 1; offset <= positiveLength; ++offset) {
            positions.add(mountedPos.relative(positive, offset));
        }

        if (matchesSequence(level, positions)) {
            return new PodLine(List.copyOf(positions), positive, axis);
        }

        Collections.reverse(positions);
        if (matchesSequence(level, positions)) {
            return new PodLine(List.copyOf(positions), negative, axis);
        }
        return null;
    }

    private static int contiguousLength(Level level, BlockPos origin, Direction direction) {
        int length = 0;
        for (int offset = 1; offset <= getMaxCannonLength(); ++offset) {
            if (!isRocketPod(level.getBlockState(origin.relative(direction, offset)))) {
                break;
            }
            length++;
        }
        return length;
    }

    private static boolean matchesSequence(Level level, List<BlockPos> positions) {
        if (positions.size() < MIN_CENTERS + 1 || positions.size() > getMaxCannonLength()) {
            return false;
        }
        if (component(level.getBlockState(positions.getFirst())) != ComponentType.REAR) {
            return false;
        }

        boolean hasFrontCap = component(level.getBlockState(positions.getLast())) == ComponentType.FRONT;
        int centersEnd = hasFrontCap ? positions.size() - 1 : positions.size();
        if (centersEnd - 1 < MIN_CENTERS) {
            return false;
        }
        for (int i = 1; i < centersEnd; ++i) {
            if (component(level.getBlockState(positions.get(i))) != ComponentType.CENTER) {
                return false;
            }
        }
        return true;
    }

    private static void validateComponentAxes(Level level, PodLine line) throws AssemblyException {
        for (BlockPos pos : line.positions()) {
            BlockState state = level.getBlockState(pos);
            if (!state.hasProperty(RocketPod.FACING) || state.getValue(RocketPod.FACING).getAxis() != line.axis()) {
                throw misalignedComponent(pos);
            }
        }
    }

    private static boolean componentsAreAligned(Level level, PodLine line) {
        for (BlockPos pos : line.positions()) {
            BlockState state = level.getBlockState(pos);
            if (!state.hasProperty(RocketPod.FACING) || state.getValue(RocketPod.FACING).getAxis() != line.axis()) {
                return false;
            }
        }
        return true;
    }

    private static void collectHorizontalBundle(Level level, PodLine primary, List<BlockPos> assembledPositions,
                                                List<BlockPos> launcherRears)
            throws AssemblyException {
        Direction left = primary.forward().getCounterClockWise();
        Direction right = primary.forward().getClockWise();

        boolean hasLeft = collectSidePod(level, primary, left, assembledPositions);
        boolean hasRight = collectSidePod(level, primary, right, assembledPositions);

        if (hasLeft) {
            launcherRears.add(primary.rear().relative(left));
        }
        if (hasRight) {
            launcherRears.add(primary.rear().relative(right));
        }

        if (hasLeft && hasPodInSideLane(level, primary, left, 2)
                || hasRight && hasPodInSideLane(level, primary, right, 2)) {
            throw invalidSideBundle();
        }
    }

    private static boolean collectSidePod(Level level, PodLine primary, Direction side,
                                          List<BlockPos> assembledPositions) throws AssemblyException {
        if (!hasPodInSideLane(level, primary, side, 1)) {
            return false;
        }

        List<BlockPos> sidePositions = new ArrayList<>(primary.positions().size());
        for (int i = 0; i < primary.positions().size(); ++i) {
            BlockPos primaryPos = primary.positions().get(i);
            BlockPos sidePos = primaryPos.relative(side);
            BlockState sideState = level.getBlockState(sidePos);
            ComponentType expected = component(level.getBlockState(primaryPos));
            if (component(sideState) != expected
                    || !sideState.hasProperty(RocketPod.FACING)
                    || sideState.getValue(RocketPod.FACING).getAxis() != primary.axis()) {
                throw invalidSideBundle();
            }
            sidePositions.add(sidePos);
        }

        BlockPos beforeRear = primary.rear().relative(primary.forward().getOpposite()).relative(side);
        BlockPos afterFront = primary.front().relative(primary.forward()).relative(side);
        if (isRocketPod(level.getBlockState(beforeRear)) || isRocketPod(level.getBlockState(afterFront))) {
            throw invalidSideBundle();
        }

        assembledPositions.addAll(sidePositions);
        return true;
    }

    private static boolean hasPodInSideLane(Level level, PodLine primary, Direction side, int distance) {
        for (BlockPos primaryPos : primary.positions()) {
            if (isRocketPod(level.getBlockState(primaryPos.relative(side, distance)))) {
                return true;
            }
        }

        BlockPos beforeRear = primary.rear().relative(primary.forward().getOpposite()).relative(side, distance);
        BlockPos afterFront = primary.front().relative(primary.forward()).relative(side, distance);
        return isRocketPod(level.getBlockState(beforeRear)) || isRocketPod(level.getBlockState(afterFront));
    }

    private void calculateExtensionLengths(List<BlockPos> positions, BlockPos mountedPos, Direction.Axis axis) {
        Direction positive = Direction.get(Direction.AxisDirection.POSITIVE, axis);
        int minimum = 0;
        int maximum = 0;
        for (BlockPos pos : positions) {
            BlockPos local = pos.subtract(mountedPos);
            int coordinate = local.getX() * positive.getStepX()
                    + local.getY() * positive.getStepY()
                    + local.getZ() * positive.getStepZ();
            minimum = Math.min(minimum, coordinate);
            maximum = Math.max(maximum, coordinate);
        }
        this.backExtensionLength = -minimum;
        this.frontExtensionLength = maximum;
    }

    private static boolean isRocketPod(BlockState state) {
        return component(state) != ComponentType.NONE;
    }

    private static ComponentType component(BlockState state) {
        Block block = state.getBlock();
        if (block == ModBlocks.ROCKET_POD_REAR.get()) {
            return ComponentType.REAR;
        }
        if (block == ModBlocks.ROCKET_POD_CENTER.get()) {
            return ComponentType.CENTER;
        }
        if (block == ModBlocks.ROCKET_POD_FRONT.get()) {
            return ComponentType.FRONT;
        }
        return ComponentType.NONE;
    }

    @Override
    public CompoundTag writeNBT(HolderLookup.Provider registries, boolean spawnPacket) {
        CompoundTag tag = super.writeNBT(registries, spawnPacket);
        tag.putLongArray(LAUNCHER_REARS_TAG, this.launcherRears.stream().mapToLong(BlockPos::asLong).toArray());
        tag.putLong(NEXT_LAUNCH_ID_TAG, this.nextLaunchId);
        tag.putLong(NEXT_LAUNCH_ALLOWED_TICK_TAG, this.nextLaunchAllowedTick);
        tag.putBoolean(LAUNCH_SIGNAL_POWERED_TAG, this.launchSignalPowered);
        tag.putBoolean(WAITING_FOR_GUIDANCE_TAG, this.waitingForGuidance);

        ListTag pendingTag = new ListTag();
        for (PendingLaunch pending : this.pendingLaunches) {
            CompoundTag launchTag = new CompoundTag();
            launchTag.putLong(PENDING_REAR_TAG, pending.rearPos.asLong());
            launchTag.putByte(PENDING_SLOT_TAG, (byte) pending.slot);
            launchTag.putInt(PENDING_TICKS_TAG, pending.ticksRemaining);
            launchTag.putLong(PENDING_LAUNCH_ID_TAG, pending.launchId);
            pendingTag.add(launchTag);
        }
        tag.put(PENDING_LAUNCHES_TAG, pendingTag);
        return tag;
    }

    @Override
    public void readNBT(Level level, CompoundTag tag, boolean spawnData) {
        super.readNBT(level, tag, spawnData);
        this.launcherRears.clear();
        if (tag.contains(LAUNCHER_REARS_TAG, Tag.TAG_LONG_ARRAY)) {
            for (long packedPos : tag.getLongArray(LAUNCHER_REARS_TAG)) {
                BlockPos rearPos = BlockPos.of(packedPos);
                if (isRearBlock(rearPos)) {
                    this.launcherRears.add(rearPos);
                }
            }
        }
        if (this.launcherRears.isEmpty()) {
            rebuildLauncherRears();
        }
        ensureLauncherBlockEntities(level);

        this.nextLaunchId = Math.max(0L, tag.getLong(NEXT_LAUNCH_ID_TAG));
        this.nextLaunchAllowedTick = Math.max(
                0L, tag.getLong(NEXT_LAUNCH_ALLOWED_TICK_TAG));
        this.launchSignalPowered = tag.getBoolean(LAUNCH_SIGNAL_POWERED_TAG);
        this.waitingForGuidance = this.launchSignalPowered && tag.getBoolean(WAITING_FOR_GUIDANCE_TAG);
        this.pendingLaunches.clear();
        this.lingeringBackblasts.clear();
        if (tag.contains(PENDING_LAUNCHES_TAG, Tag.TAG_LIST)) {
            ListTag pendingTag = tag.getList(PENDING_LAUNCHES_TAG, Tag.TAG_COMPOUND);
            for (int index = 0; index < pendingTag.size(); ++index) {
                CompoundTag launchTag = pendingTag.getCompound(index);
                BlockPos rearPos = BlockPos.of(launchTag.getLong(PENDING_REAR_TAG));
                int slot = launchTag.getByte(PENDING_SLOT_TAG) & 255;
                int ticksRemaining = launchTag.getInt(PENDING_TICKS_TAG);
                long launchId = launchTag.contains(PENDING_LAUNCH_ID_TAG, Tag.TAG_LONG)
                        ? launchTag.getLong(PENDING_LAUNCH_ID_TAG)
                        : this.nextLaunchId++;
                if (!isRearBlock(rearPos)
                        || slot >= RocketPodBlockEntity.SLOT_COUNT
                        || ticksRemaining < 1
                        || launchId < 0L
                        || isSlotReserved(rearPos, slot)
                        || !(this.presentBlockEntities.get(rearPos) instanceof RocketPodBlockEntity rear)
                        || !RocketPodBlockEntity.isRocket(rear.getRocket(slot))) {
                    continue;
                }
                this.pendingLaunches.add(new PendingLaunch(rearPos, slot, ticksRemaining, launchId));
                this.nextLaunchId = Math.max(this.nextLaunchId, launchId + 1L);
            }
        }
    }

    private void rebuildLauncherRears() {
        addLauncherRearIfPresent(this.startPos);
        if (this.initialOrientation.getAxis().isHorizontal()) {
            addLauncherRearIfPresent(this.startPos.relative(this.initialOrientation.getCounterClockWise()));
            addLauncherRearIfPresent(this.startPos.relative(this.initialOrientation.getClockWise()));
        }
    }

    private void addLauncherRearIfPresent(BlockPos pos) {
        if (isRearBlock(pos) && !this.launcherRears.contains(pos)) {
            this.launcherRears.add(pos.immutable());
        }
    }

    private boolean isRearBlock(BlockPos pos) {
        StructureBlockInfo info = this.blocks.get(pos);
        return info != null && info.state().getBlock() == ModBlocks.ROCKET_POD_REAR.get();
    }

    private void ensureLauncherBlockEntities(Level level) {
        for (BlockPos rearPos : this.launcherRears) {
            if (this.presentBlockEntities.get(rearPos) instanceof RocketPodBlockEntity) {
                continue;
            }
            StructureBlockInfo info = this.blocks.get(rearPos);
            if (info == null || info.state().getBlock() != ModBlocks.ROCKET_POD_REAR.get()) {
                continue;
            }
            RocketPodBlockEntity rear = new RocketPodBlockEntity(
                    com.happysg.kaboom.registry.ModBlockEntityTypes.ROCKET_POD_REAR.get(), rearPos, info.state());
            rear.setLevel(level);
            this.presentBlockEntities.put(rearPos, rear);
        }
    }

    public ItemStack insertRocket(ItemStack stack, boolean simulate, PitchOrientedContraptionEntity entity) {
        if (!RocketPodBlockEntity.isRocket(stack)) {
            return stack;
        }
        for (BlockPos rearPos : this.launcherRears) {
            if (!(this.presentBlockEntities.get(rearPos) instanceof RocketPodBlockEntity rear)) {
                continue;
            }
            ItemStack remainder = rear.insertRocket(stack, simulate);
            if (remainder.getCount() == stack.getCount()) {
                continue;
            }
            if (!simulate) {
                syncRear(rearPos, entity);
                Vec3 soundPos = entity.toGlobalVector(Vec3.atCenterOf(rearPos), 0);
                entity.level().playSound(null, soundPos.x, soundPos.y, soundPos.z,
                        SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.NEUTRAL, 1.0F, 1.0F);
            }
            return remainder;
        }
        return stack;
    }

    public ItemStack extractNextRocket(boolean simulate, PitchOrientedContraptionEntity entity) {
        LoadedRocket loadedRocket = extractNextRocketWithSource(simulate, entity);
        return loadedRocket == null ? ItemStack.EMPTY : loadedRocket.stack();
    }

    public ItemStack extractNextRocket(BlockPos rearPos, boolean simulate, PitchOrientedContraptionEntity entity) {
        if (!(this.presentBlockEntities.get(rearPos) instanceof RocketPodBlockEntity rear)) {
            return ItemStack.EMPTY;
        }
        for (int slot = 0; slot < RocketPodBlockEntity.SLOT_COUNT; ++slot) {
            if (isSlotReserved(rearPos, slot)) {
                continue;
            }
            ItemStack rocket = rear.extractRocket(slot, simulate);
            if (rocket.isEmpty()) {
                continue;
            }
            if (!simulate) {
                syncRear(rearPos, entity);
            }
            return rocket;
        }
        return ItemStack.EMPTY;
    }

    private LoadedRocket extractNextRocketWithSource(boolean simulate, PitchOrientedContraptionEntity entity) {
        for (BlockPos rearPos : this.launcherRears) {
            if (!(this.presentBlockEntities.get(rearPos) instanceof RocketPodBlockEntity rear)) {
                continue;
            }
            for (int slot = 0; slot < RocketPodBlockEntity.SLOT_COUNT; ++slot) {
                if (isSlotReserved(rearPos, slot)) {
                    continue;
                }
                ItemStack rocket = rear.extractRocket(slot, simulate);
                if (rocket.isEmpty()) {
                    continue;
                }
                if (!simulate) {
                    syncRear(rearPos, entity);
                }
                return new LoadedRocket(rearPos, rocket);
            }
        }
        return null;
    }

    private boolean isSlotReserved(BlockPos rearPos, int slot) {
        for (PendingLaunch pending : this.pendingLaunches) {
            if (pending.slot == slot && pending.rearPos.equals(rearPos)) {
                return true;
            }
        }
        return false;
    }

    public void syncRear(BlockPos rearPos, PitchOrientedContraptionEntity entity) {
        BlockEntity blockEntity = this.presentBlockEntities.get(rearPos);
        StructureBlockInfo info = this.blocks.get(rearPos);
        if (blockEntity != null && info != null) {
            BigCannonBlock.writeAndSyncSingleBlockData(blockEntity, info, entity, this);
        }
    }

    @Override
    public void onRedstoneUpdate(ServerLevel level, PitchOrientedContraptionEntity entity, boolean togglePower,
                                 int firePower, ControlPitchContraption controller) {
        this.launchSignalPowered = firePower > 0;
        if (!this.launchSignalPowered) {
            this.waitingForGuidance = false;
        } else if (togglePower) {
            fireShot(level, entity);
        }
    }

    @Override
    public void fireShot(ServerLevel level, PitchOrientedContraptionEntity entity) {
        if (level.getGameTime() < this.nextLaunchAllowedTick) {
            return;
        }
        LaunchCandidate candidate = selectNextLaunchCandidate(false);
        if (candidate == null
                || !(this.presentBlockEntities.get(candidate.rearPos)
                instanceof RocketPodBlockEntity rear)) {
            return;
        }

        int launchDelayTicks = KaboomConfig.server().rocketLaunchDelayTicks();
        ItemStack rocket = rear.getRocket(candidate.slot);
        if (!RocketPodBlockEntity.isLoadableRocket(rocket)) {
            return;
        }
        RocketLaunchFrame launchFrame = createLaunchFrame(
                level, entity, candidate.rearPos, candidate.slot);
        RocketGuidanceLaunchResolver.Resolution guidance =
                RocketGuidanceLaunchResolver.resolve(
                        level, entity, rocket,
                        launchFrame.spawnPosition, launchFrame.launchDirection);
        if (!guidance.accepted()) {
            this.waitingForGuidance = this.launchSignalPowered;
            return;
        }
        this.waitingForGuidance = false;
        long launchId = this.nextLaunchId++;
        if (launchDelayTicks <= 0) {
            spawnBackblast(level, entity, candidate.rearPos);
            if (fireRocketFromSlot(
                    level, entity, rear, candidate.rearPos, candidate.slot,
                    launchId, launchFrame, guidance.guidanceData())) {
                this.startRepeatedLaunchCooldown(level);
            }
            return;
        }
        PendingLaunch pending = new PendingLaunch(
                candidate.rearPos, candidate.slot, launchDelayTicks, launchId);
        this.pendingLaunches.add(pending);
        this.startRepeatedLaunchCooldown(level);
        NetworkHandler.sendToPlayersTrackingEntity(entity,
                new RocketPodLaunchSoundPacket(
                        entity.getId(), candidate.rearPos, candidate.slot,
                        pending.launchId, launchDelayTicks));
    }

    private void startRepeatedLaunchCooldown(ServerLevel level) {
        this.nextLaunchAllowedTick = level.getGameTime()
                + REPEATED_LAUNCH_COOLDOWN_TICKS;
    }

    private List<BlockPos> getLaunchOrderedRears() {
        if (!this.initialOrientation.getAxis().isHorizontal() || this.launcherRears.size() <= 1) {
            return this.launcherRears;
        }

        List<BlockPos> ordered = new ArrayList<>(this.launcherRears.size());
        addLaunchRearIfPresent(ordered, this.startPos.relative(this.initialOrientation.getCounterClockWise()));
        addLaunchRearIfPresent(ordered, this.startPos);
        addLaunchRearIfPresent(ordered, this.startPos.relative(this.initialOrientation.getClockWise()));
        for (BlockPos rearPos : this.launcherRears) {
            if (!ordered.contains(rearPos)) {
                ordered.add(rearPos);
            }
        }
        return ordered;
    }

    private void addLaunchRearIfPresent(List<BlockPos> ordered, BlockPos rearPos) {
        if (this.launcherRears.contains(rearPos)) {
            ordered.add(rearPos);
        }
    }

    @Nullable
    private LaunchCandidate selectNextLaunchCandidate(boolean includePending) {
        if (includePending) {
            PendingLaunch best = null;
            for (PendingLaunch pending : this.pendingLaunches) {
                if (!(this.presentBlockEntities.get(pending.rearPos)
                        instanceof RocketPodBlockEntity rear)
                        || !RocketPodBlockEntity.isLoadableRocket(
                        rear.getRocket(pending.slot))) {
                    continue;
                }
                if (best == null
                        || pending.ticksRemaining < best.ticksRemaining
                        || pending.ticksRemaining == best.ticksRemaining
                        && pending.launchId < best.launchId) {
                    best = pending;
                }
            }
            if (best != null) {
                return new LaunchCandidate(
                        best.rearPos, best.slot, true,
                        best.ticksRemaining, best.launchId);
            }
        }

        List<BlockPos> orderedRears = getLaunchOrderedRears();
        for (int firstSlot = 0;
             firstSlot < RocketPodBlockEntity.SLOT_COUNT;
             firstSlot += 2) {
            for (BlockPos rearPos : orderedRears) {
                if (!(this.presentBlockEntities.get(rearPos)
                        instanceof RocketPodBlockEntity rear)) {
                    continue;
                }
                int slotsEnd = Math.min(
                        firstSlot + 2, RocketPodBlockEntity.SLOT_COUNT);
                for (int slot = firstSlot; slot < slotsEnd; ++slot) {
                    if (!isSlotReserved(rearPos, slot)
                            && RocketPodBlockEntity.isLoadableRocket(
                            rear.getRocket(slot))) {
                        return new LaunchCandidate(
                                rearPos, slot, false, 0, -1L);
                    }
                }
            }
        }
        return null;
    }

    @Nullable
    public NextRocketLaunch getNextRocketLaunch(
            ServerLevel level,
            PitchOrientedContraptionEntity entity
    ) {
        LaunchCandidate candidate = selectNextLaunchCandidate(true);
        if (candidate == null
                || !(this.presentBlockEntities.get(candidate.rearPos)
                instanceof RocketPodBlockEntity rear)) {
            return null;
        }
        ItemStack rocket = rear.getRocket(candidate.slot);
        if (!RocketPodBlockEntity.isLoadableRocket(rocket)) {
            return null;
        }
        RocketLaunchFrame frame = createLaunchFrame(
                level, entity, candidate.rearPos, candidate.slot);
        return new NextRocketLaunch(
                candidate.rearPos,
                candidate.slot,
                rocket,
                frame.spawnPosition,
                frame.launchDirection,
                frame.carrierVelocity,
                candidate.pending,
                this.launchSignalPowered,
                candidate.ticksRemaining,
                candidate.launchId
        );
    }

    public boolean linkNextCommandRocket(
            PitchOrientedContraptionEntity entity,
            BlockPos expectedRear,
            int expectedSlot,
            ItemStack expectedRocket,
            BlockPos networkControllerPos
    ) {
        if (entity == null || expectedRear == null || expectedRocket == null
                || networkControllerPos == null) {
            return false;
        }
        LaunchCandidate candidate = selectNextLaunchCandidate(false);
        if (candidate == null
                || candidate.pending
                || candidate.slot != expectedSlot
                || !candidate.rearPos.equals(expectedRear)
                || !(this.presentBlockEntities.get(candidate.rearPos)
                instanceof RocketPodBlockEntity rear)) {
            return false;
        }

        ItemStack current = rear.getRocket(candidate.slot);
        if (!ItemStack.isSameItemSameComponents(current, expectedRocket)
                || RocketItem.getGuidanceType(current)
                != RocketGuidanceType.COMMAND) {
            return false;
        }
        if (RocketItem.getLinkedNetworkController(current) != null) {
            return true;
        }

        ItemStack linked = current.copyWithCount(1);
        RocketItem.setLinkedNetworkController(linked, networkControllerPos);
        if (!rear.replaceRocket(candidate.slot, current, linked)) {
            return false;
        }
        syncRear(candidate.rearPos, entity);
        return true;
    }

    @Override
    public void tick(Level level, PitchOrientedContraptionEntity entity) {
        super.tick(level, entity);
        if (level.isClientSide) {
            if (this.pendingLaunches.isEmpty()) {
                return;
            }
            for (PendingLaunch pending : this.pendingLaunches) {
                if (pending.clientSoundStarted) {
                    continue;
                }
                pending.clientSoundStarted = true;
                RocketClientEffects.startPodLaunchSound(
                        entity, pending.rearPos, pending.slot, pending.launchId, pending.ticksRemaining);
            }
            return;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (this.pendingLaunches.isEmpty() && this.lingeringBackblasts.isEmpty()
                && !this.waitingForGuidance) {
            return;
        }

        Iterator<LingeringBackblast> backblastIterator = this.lingeringBackblasts.iterator();
        while (backblastIterator.hasNext()) {
            LingeringBackblast backblast = backblastIterator.next();
            spawnBackblast(serverLevel, entity, backblast.rearPos);
            if (--backblast.ticksRemaining <= 0) {
                backblastIterator.remove();
            }
        }

        Iterator<PendingLaunch> iterator = this.pendingLaunches.iterator();
        while (iterator.hasNext()) {
            PendingLaunch pending = iterator.next();
            if (!(this.presentBlockEntities.get(pending.rearPos) instanceof RocketPodBlockEntity rear)
                    || !RocketPodBlockEntity.isRocket(rear.getRocket(pending.slot))) {
                iterator.remove();
                continue;
            }
            if (!RocketPodBlockEntity.isLoadableRocket(rear.getRocket(pending.slot))) {
                iterator.remove();
                continue;
            }

            spawnBackblast(serverLevel, entity, pending.rearPos);
            --pending.ticksRemaining;
            if (pending.ticksRemaining > 0) {
                continue;
            }

            ItemStack preview = rear.getRocket(pending.slot);
            if (!RocketPodBlockEntity.isLoadableRocket(preview)) {
                iterator.remove();
                continue;
            }
            RocketLaunchFrame launchFrame = createLaunchFrame(
                    serverLevel, entity, pending.rearPos, pending.slot);
            RocketGuidanceLaunchResolver.Resolution guidance = RocketGuidanceLaunchResolver.resolve(
                    serverLevel, entity, preview, launchFrame.spawnPosition, launchFrame.launchDirection);
            iterator.remove();
            if (!guidance.accepted()) {
                this.waitingForGuidance = this.launchSignalPowered;
                continue;
            }

            fireRocketFromSlot(
                    serverLevel, entity, rear, pending.rearPos, pending.slot,
                    pending.launchId, launchFrame, guidance.guidanceData());
        }

        if (this.waitingForGuidance && this.launchSignalPowered) {
            fireShot(serverLevel, entity);
        }
    }

    private boolean fireRocketFromSlot(ServerLevel level, PitchOrientedContraptionEntity entity,
                                       RocketPodBlockEntity rear, BlockPos rearPos, int slot,
                                       long launchId, RocketLaunchFrame launchFrame,
                                       MissileGuidanceData guidanceData) {
        ItemStack rocket = rear.extractRocket(slot, false);
        if (rocket.isEmpty()) {
            return false;
        }
        boolean fired = onRocketFired(
                level, entity, rearPos, slot, launchId,
                rocket, launchFrame, guidanceData);
        if (!fired) {
            if (!rear.restoreRocket(slot, rocket)) {
                CreateKaboom.getLogger().error(
                        "Failed to restore rocket to launcher slot {} after projectile spawn failure",
                        slot);
            }
            syncRear(rearPos, entity);
            return false;
        }

        this.lingeringBackblasts.add(
                new LingeringBackblast(rearPos, POST_LAUNCH_BACKBLAST_TICKS));
        syncRear(rearPos, entity);
        return true;
    }

    private void spawnBackblast(ServerLevel level, PitchOrientedContraptionEntity entity, BlockPos rearPos) {
        Vec3 rearCenter = entity.toGlobalVector(Vec3.atCenterOf(rearPos), 0);
        Vec3 behindCenter = entity.toGlobalVector(
                Vec3.atCenterOf(rearPos.relative(this.initialOrientation.getOpposite())), 0);
        Vec3 backward = behindCenter.subtract(rearCenter).normalize();
        if (backward.lengthSqr() < 1.0e-6) {
            return;
        }

        Vec3 referenceUp = Math.abs(backward.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 right = backward.cross(referenceUp).normalize();
        Vec3 up = right.cross(backward).normalize();
        Vec3 spawnCenter = rearCenter.add(backward.scale(BACKBLAST_SPAWN_OFFSET));

        for (int particle = 0; particle < BACKBLAST_PARTICLES_PER_TICK; ++particle) {
            double angle = level.random.nextDouble() * Math.PI * 2.0;
            double radius = Math.sqrt(level.random.nextDouble()) * BACKBLAST_SPAWN_RADIUS;
            Vec3 radialOffset = right.scale(Math.cos(angle) * radius)
                    .add(up.scale(Math.sin(angle) * radius));
            Vec3 spawnPos = spawnCenter.add(radialOffset);

            double speed = BACKBLAST_BASE_SPEED
                    + (level.random.nextDouble() * 2.0 - 1.0) * BACKBLAST_SPEED_VARIANCE;
            Vec3 spread = right.scale((level.random.nextDouble() * 2.0 - 1.0) * BACKBLAST_SPREAD)
                    .add(up.scale((level.random.nextDouble() * 2.0 - 1.0) * BACKBLAST_SPREAD));
            Vec3 motion = entity.getDeltaMovement().add(backward.scale(speed)).add(spread);

            level.sendParticles(ModParticles.ROCKET_LAUNCH_SMOKE.get(),
                    spawnPos.x, spawnPos.y, spawnPos.z,
                    0, motion.x, motion.y, motion.z, 1.0);
        }
    }

    protected boolean onRocketFired(ServerLevel level, PitchOrientedContraptionEntity entity,
                                    BlockPos rearPos, int slot, long launchId, ItemStack rocket,
                                    RocketLaunchFrame launchFrame,
                                    MissileGuidanceData guidanceData) {
        if (!(rocket.getItem() instanceof RocketItem rocketItem)) {
            return false;
        }
        AbstractCannonProjectile projectile = rocketItem.createProjectile(
                level, rocket, launchFrame.launchDirection, getRocketInaccuracy(rearPos));
        if (projectile == null) {
            return false;
        }

        projectile.setPos(launchFrame.spawnPosition);
        projectile.setDeltaMovement(
                projectile.getDeltaMovement().add(launchFrame.carrierVelocity));
        if (projectile instanceof UnguidedRocketProjectile rocketProjectile) {
            rocketProjectile.setGuidanceData(guidanceData);
        }
        projectile.addUntouchableEntity(entity, 1);
        Entity vehicle = entity.getVehicle();
        if (vehicle != null) {
            projectile.addUntouchableEntity(vehicle, 1);
        }
        boolean added = level.addFreshEntity(projectile);
        if (!added) {
            projectile.discard();
            return false;
        }
        if (projectile instanceof UnguidedRocketProjectile) {
            NetworkHandler.sendToPlayersTrackingEntity(projectile,
                    LaunchSoundHandoffPacket.rocketPod(entity.getId(), rearPos, slot,
                            launchId, projectile.getId()));
        }
        projectile.xRotO = projectile.getXRot();
        projectile.yRotO = projectile.getYRot();
        return true;
    }

    private RocketLaunchFrame createLaunchFrame(ServerLevel level, PitchOrientedContraptionEntity entity,
                                                BlockPos rearPos, int slot) {
        BlockPos frontPos = findLauncherEnd(rearPos);
        Vec3 frontCenter = entity.toGlobalVector(Vec3.atCenterOf(frontPos), 0);
        Vec3 centeredMuzzle = Vec3.atCenterOf(frontPos.relative(this.initialOrientation));
        Vec3 mountedMuzzleCenter = entity.toGlobalVector(centeredMuzzle, 0);
        Vec3 mountedSpawnPosition = entity.toGlobalVector(centeredMuzzle.add(getTubeOffset(slot)), 0);
        Vec3 mountedDirection = mountedMuzzleCenter.subtract(frontCenter).normalize();

        BlockPos sourcePos = entity.blockPosition();
        if (entity.getController() instanceof BlockEntity controller) {
            sourcePos = controller.getBlockPos();
        }
        SableUtils.LaunchKinematics launch = SableUtils.getLaunchKinematics(
                level, sourcePos, mountedSpawnPosition, mountedDirection);
        return new RocketLaunchFrame(
                launch.position(), launch.direction(), launch.carrierVelocity());
    }

    private float getRocketInaccuracy(BlockPos rearPos) {
        float baseInaccuracy = KaboomConfig.server().rocketPodBaseInaccuracyMultiplier.getF();
        if (!Float.isFinite(baseInaccuracy) || baseInaccuracy <= 0.0F) {
            return 0.0F;
        }

        int partCount = Math.max(MIN_TUBE_PARTS, countTubeParts(rearPos));
        double lengthRatio = (double) MIN_TUBE_PARTS / partCount;
        return (float) (baseInaccuracy * Math.pow(lengthRatio, 4.0));
    }

    private int countTubeParts(BlockPos rearPos) {
        int partCount = 0;
        for (int offset = 0; offset < getMaxCannonLength(); ++offset) {
            StructureBlockInfo info = this.blocks.get(
                    rearPos.relative(this.initialOrientation, offset));
            if (info == null || component(info.state()) == ComponentType.NONE) {
                break;
            }
            partCount++;
        }
        return partCount;
    }

    private Vec3 getTubeOffset(int slot) {
        Vec3 forward = Vec3.atLowerCornerOf(this.initialOrientation.getNormal());
        Vec3 referenceUp = this.initialOrientation.getAxis().isVertical()
                ? new Vec3(0.0, 0.0, -1.0)
                : new Vec3(0.0, 1.0, 0.0);
        Vec3 right = forward.cross(referenceUp).normalize();
        Vec3 up = right.cross(forward).normalize();

        double horizontalOffset = (slot & 1) == 0 ? -0.25 : 0.25;
        double verticalOffset = slot < 2 ? 0.25 : -0.25;
        return right.scale(horizontalOffset).add(up.scale(verticalOffset));
    }

    private BlockPos findLauncherEnd(BlockPos rearPos) {
        BlockPos endPos = rearPos;
        for (int offset = 1; offset < getMaxCannonLength(); ++offset) {
            BlockPos nextPos = rearPos.relative(this.initialOrientation, offset);
            StructureBlockInfo info = this.blocks.get(nextPos);
            if (info == null) {
                break;
            }
            ComponentType type = component(info.state());
            if (type == ComponentType.CENTER) {
                endPos = nextPos;
                continue;
            }
            if (type == ComponentType.FRONT) {
                endPos = nextPos;
            }
            break;
        }
        return endPos;
    }

    @Override
    public float getWeightForStress() {
        return this.blocks.size();
    }

    @Override
    public Vec3 getInteractionVec(PitchOrientedContraptionEntity entity) {
        return entity.toGlobalVector(Vec3.atCenterOf(this.startPos), 0);
    }

    @Override
    public ICannonContraptionType getCannonType() {
        return ModContraptionTypes.ROCKET_POD_CANNON_TYPE;
    }

    @Override
    public ContraptionType getType() {
        return ModContraptionTypes.ROCKET_POD.value();
    }

    private static AssemblyException invalidComposition() {
        return new AssemblyException(Component.translatable(
                "exception." + CreateKaboom.MODID + ".cannon_mount.invalidRocketPod"));
    }

    private static AssemblyException misalignedComponent(BlockPos pos) {
        return new AssemblyException(Component.translatable(
                "exception." + CreateKaboom.MODID + ".cannon_mount.misalignedRocketPod",
                pos.getX(), pos.getY(), pos.getZ()));
    }

    private static AssemblyException invalidSideBundle() {
        return new AssemblyException(Component.translatable(
                "exception." + CreateKaboom.MODID + ".cannon_mount.invalidRocketPodBundle"));
    }

    private enum ComponentType {
        REAR,
        CENTER,
        FRONT,
        NONE
    }

    private record PodLine(List<BlockPos> positions, Direction forward, Direction.Axis axis) {
        private BlockPos rear() {
            return this.positions.getFirst();
        }

        private BlockPos front() {
            return this.positions.getLast();
        }
    }

    private record LoadedRocket(BlockPos rearPos, ItemStack stack) {
    }

    public record NextRocketLaunch(
            BlockPos rearPos,
            int slot,
            ItemStack rocket,
            Vec3 spawnPosition,
            Vec3 launchDirection,
            Vec3 carrierVelocity,
            boolean pending,
            boolean fireSignalPowered,
            int ticksRemaining,
            long launchId
    ) {
        public NextRocketLaunch {
            rearPos = rearPos.immutable();
            rocket = rocket.copyWithCount(1);
        }
    }

    private record LaunchCandidate(
            BlockPos rearPos,
            int slot,
            boolean pending,
            int ticksRemaining,
            long launchId
    ) {
    }

    protected record RocketLaunchFrame(
            Vec3 spawnPosition,
            Vec3 launchDirection,
            Vec3 carrierVelocity
    ) {
    }

    private static final class PendingLaunch {
        private final BlockPos rearPos;
        private final int slot;
        private final long launchId;
        private int ticksRemaining;
        private boolean clientSoundStarted;

        private PendingLaunch(BlockPos rearPos, int slot, int ticksRemaining, long launchId) {
            this.rearPos = rearPos.immutable();
            this.slot = slot;
            this.ticksRemaining = ticksRemaining;
            this.launchId = launchId;
        }
    }

    private static final class LingeringBackblast {
        private final BlockPos rearPos;
        private int ticksRemaining;

        private LingeringBackblast(BlockPos rearPos, int ticksRemaining) {
            this.rearPos = rearPos.immutable();
            this.ticksRemaining = ticksRemaining;
        }
    }
}
