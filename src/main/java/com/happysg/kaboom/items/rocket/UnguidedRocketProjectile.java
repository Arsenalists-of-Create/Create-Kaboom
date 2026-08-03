package com.happysg.kaboom.items.rocket;

import com.happysg.kaboom.CreateKaboom;
import com.happysg.kaboom.block.missiles.nav.ARADNavigation;
import com.happysg.kaboom.block.missiles.nav.MissileNavigation;
import com.happysg.kaboom.block.missiles.nav.MovingTargetInterceptorNavigation;
import com.happysg.kaboom.block.missiles.util.MissileGuidanceData;
import com.happysg.kaboom.block.missiles.util.MissileGuidanceType;
import com.happysg.kaboom.block.missiles.util.MissileTargetSpec;
import com.happysg.kaboom.client.RocketClientEffects;
import com.happysg.kaboom.config.KaboomConfig;
import com.happysg.kaboom.interception.InterceptableOrdnance;
import com.happysg.kaboom.interception.OrdnanceInterceptionState;
import com.happysg.kaboom.registry.ModParticles;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;
import rbasamoyai.createbigcannons.CreateBigCannons;
import rbasamoyai.createbigcannons.block_armor_properties.BlockArmorPropertiesHandler;
import rbasamoyai.createbigcannons.block_armor_properties.BlockArmorPropertiesProvider;
import rbasamoyai.createbigcannons.config.CBCCfgMunitions;
import rbasamoyai.createbigcannons.config.CBCConfigs;
import rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile;
import rbasamoyai.createbigcannons.munitions.ImpactExplosion;
import rbasamoyai.createbigcannons.munitions.ProjectileContext;
import rbasamoyai.createbigcannons.munitions.big_cannon.ProjectileBlock;
import rbasamoyai.createbigcannons.munitions.config.components.BallisticPropertiesComponent;
import rbasamoyai.createbigcannons.munitions.config.components.EntityDamagePropertiesComponent;
import rbasamoyai.createbigcannons.munitions.fuzes.FuzeItem;
import rbasamoyai.createbigcannons.network.ClientboundPlayBlockHitEffectPacket;
import rbasamoyai.createbigcannons.utils.CBCUtils;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import javax.annotation.Nullable;

public class UnguidedRocketProjectile extends AbstractCannonProjectile
        implements InterceptableOrdnance, MissileNavigation.FlightAccess {
    public static final double LAUNCH_SPEED_BLOCKS_PER_TICK = 0.1;
    public static final double BOOST_ACCELERATION_PER_TICK = 0.1;
    public static final double BOOST_DISTANCE_BLOCKS = 250.0;
    public static final double RADAR_ACQUISITION_RANGE_BLOCKS =
            RocketGuidanceLaunchResolver.ACQUISITION_RANGE_BLOCKS;
    public static final int MAX_FLIGHT_TICKS = 1200;

    private static final int NO_PAYLOAD_ID = -1;
    private static final double GUIDANCE_BOOST_DISTANCE_BLOCKS = 5.0;

    private static final int FORCEFUL_EXHAUST_TICKS = 40;
    private static final int MODERATE_EXHAUST_TICKS = 60;
    private static final int FORCEFUL_EXHAUST_COUNT = 3;
    private static final int MODERATE_EXHAUST_COUNT = 2;
    private static final int DISTANT_EXHAUST_COUNT = 1;
    private static final double EXHAUST_OFFSET = 0.70;
    private static final double EXHAUST_RADIUS = 0.08;
    private static final double EXHAUST_BASE_SPEED = 0.45;
    private static final double EXHAUST_SPEED_VARIANCE = 0.10;
    private static final double EXHAUST_SPREAD = 0.08;
    private static final double EXHAUST_MOTION_INHERITANCE = 0.20;

    private static final String ROCKET_STACK_TAG = "RocketStack";
    private static final String FUZE_TAG = "Fuze";
    private static final String BOOST_DISTANCE_TAG = "BoostDistance";
    private static final String FLIGHT_AGE_TAG = "FlightAge";
    private static final String BOOSTING_TAG = "Boosting";
    private static final String BOOST_DIRECTION_X_TAG = "BoostDirectionX";
    private static final String BOOST_DIRECTION_Y_TAG = "BoostDirectionY";
    private static final String BOOST_DIRECTION_Z_TAG = "BoostDirectionZ";
    private static final String GUIDANCE_DATA_TAG = "kaboom:RocketGuidanceData";

    private static final int VIRTUAL_MOTOR_FUEL_MB = Integer.MAX_VALUE;
    private static final int GUIDANCE_TARGET_CHUNK_RADIUS = 1;
    private static final TicketType<UUID> ROCKET_GUIDANCE_CHUNK_TICKET = TicketType.create(
            "create_kaboom:rocket_guidance",
            Comparator.<UUID>naturalOrder());

    private static final EntityDataAccessor<Boolean> BOOSTING =
            SynchedEntityData.defineId(UnguidedRocketProjectile.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Vector3f> BOOST_DIRECTION =
            SynchedEntityData.defineId(UnguidedRocketProjectile.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Boolean> GUIDED =
            SynchedEntityData.defineId(UnguidedRocketProjectile.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Vector3f> GUIDANCE_ACCELERATION =
            SynchedEntityData.defineId(UnguidedRocketProjectile.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Integer> PAYLOAD_ID =
            SynchedEntityData.defineId(UnguidedRocketProjectile.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<ItemStack> ROCKET_STACK =
            SynchedEntityData.defineId(UnguidedRocketProjectile.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Integer> EXHAUST_STAGE =
            SynchedEntityData.defineId(UnguidedRocketProjectile.class, EntityDataSerializers.INT);

    private static final EntityDamagePropertiesComponent DAMAGE_PROPERTIES =
            EntityDamagePropertiesComponent.DEFAULT;

    private final OrdnanceInterceptionState interceptionState = new OrdnanceInterceptionState();
    private final MovingTargetInterceptorNavigation interceptorNavigation =
            new MovingTargetInterceptorNavigation();
    private final ARADNavigation aradNavigation = new ARADNavigation();
    private final Set<Long> forcedGuidanceChunks = new HashSet<>();
    private MissileNavigation.Command guidanceCommand = MissileNavigation.Command.none("unguided");
    @Nullable
    private MissileGuidanceData guidanceData;
    @Nullable
    private RocketPayload payload;
    private ItemStack fuze = ItemStack.EMPTY;
    private double boostDistance;
    private int flightAge;
    private boolean detonated;
    private boolean startedFlightSound;
    private boolean radarLaunchNotified;

    public UnguidedRocketProjectile(EntityType<? extends AbstractCannonProjectile> type, Level level) {
        super(type, level);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return this.interceptionState.hurt(this, source, amount, () -> this.detonate(this.position()));
    }

    @Override
    public Level guidanceLevel() {
        return this.level();
    }

    @Override
    public int guidanceTickCount() {
        return this.tickCount;
    }

    @Override
    public int guidanceEntityId() {
        return this.getId();
    }

    @Override
    public UUID guidanceUuid() {
        return this.getUUID();
    }

    @Override
    public Vec3 guidanceVelocity() {
        return this.getDeltaMovement();
    }

    @Override
    public int guidanceFuelMb() {
        return this.isBoosting() ? VIRTUAL_MOTOR_FUEL_MB : 0;
    }

    @Override
    public int guidanceFuelCapacityMb() {
        return VIRTUAL_MOTOR_FUEL_MB;
    }

    @Override
    public double guidanceAccelerationPerTick() {
        return configuredBoostAcceleration();
    }

    @Override
    public boolean guidanceHasFuze() {
        return this.fuze.getItem() instanceof FuzeItem;
    }

    @Override
    public void guidanceSetFuelMb(int mb) {
        if (mb <= 0) {
            this.entityData.set(BOOSTING, false);
        }
    }

    @Override
    public void guidanceBurnFuel(int mb) {
        // Rocket propulsion is distance-limited rather than fluid-fuel-limited.
        // Solver burn requests therefore leave the virtual motor available until
        // the existing 250-block boost distance is exhausted.
    }

    @Override
    public void guidanceSyncState(int stateOrdinal) {
        // Rocket navigation state is server-authoritative and has no client UI.
    }

    public void initialize(ItemStack stack, Vec3 launchDirection) {
        initialize(stack, launchDirection, 0.0F);
    }

    public void initialize(ItemStack stack, Vec3 launchDirection, float inaccuracy) {
        Vec3 direction = safeDirection(launchDirection);
        this.setRocketStack(stack);
        this.setPayload(RocketItem.getPayload(this.getRocketStack()));
        if (this.payload != null && this.payload.isArmorPiercing()) {
            this.setProjectileMass(RocketPayload.AP_BALLISTIC_PROPERTIES.durabilityMass());
        }
        this.fuze = RocketItem.getAttachedFuze(stack);
        this.boostDistance = 0.0;
        this.flightAge = 0;
        this.detonated = false;
        this.entityData.set(BOOSTING, configuredBoostDistance() > 0.0);
        this.entityData.set(EXHAUST_STAGE, 0);
        this.shoot(direction.x, direction.y, direction.z,
                (float) configuredInitialVelocity(), sanitizeInaccuracy(inaccuracy));
        Vec3 actualDirection = safeDirection(this.getDeltaMovement());
        this.entityData.set(BOOST_DIRECTION, actualDirection.toVector3f());
        this.setOrientation(actualDirection);
    }

    /**
     * Installs launch-resolved runtime guidance. Call after positioning the
     * projectile so solver launch-state uses the actual world-space muzzle.
     */
    public void setGuidanceData(@Nullable MissileGuidanceData data) {
        if (!this.level().isClientSide && this.guidanceData != null) {
            this.interceptorNavigation.clearRadarRwrEmitter(this);
            this.releaseGuidanceChunks();
        }
        if (!isSupportedGuidance(data)) {
            this.guidanceData = null;
            this.interceptorNavigation.configure(null);
            this.guidanceCommand = MissileNavigation.Command.none("unguided");
            this.radarLaunchNotified = false;
            this.entityData.set(GUIDED, false);
            this.entityData.set(GUIDANCE_ACCELERATION, new Vector3f());
            return;
        }

        this.guidanceData = data;
        Vec3 launchDirection = this.getBoostDirection();
        this.entityData.set(GUIDED, true);
        this.entityData.set(GUIDANCE_ACCELERATION,
                launchDirection.scale(configuredBoostAcceleration()).toVector3f());
        if (data.guidanceType() == MissileGuidanceType.ARAD) {
            this.aradNavigation.initialize(launchDirection, this.position(), this);
            this.aradNavigation.configure(data, this.position(), this);
        } else {
            this.interceptorNavigation.initialize(launchDirection, this.position(), this);
            this.interceptorNavigation.configure(
                    data,
                    data.guidanceType() == MissileGuidanceType.RADAR
                            ? MovingTargetInterceptorNavigation.RadarSeekerProfile.configuredRocket(
                            RADAR_ACQUISITION_RANGE_BLOCKS)
                            : null,
                    GUIDANCE_BOOST_DISTANCE_BLOCKS);
        }
        this.guidanceCommand = MissileNavigation.Command.none("initialized");
        this.radarLaunchNotified = false;
    }

    @Nullable
    public String getRadarChaffTargetId() {
        return this.interceptorNavigation.getRadarChaffTargetId();
    }

    public void applyRadarChaffSuppression(String targetId, long untilTick) {
        this.interceptorNavigation.applyRadarChaffSuppression(targetId, untilTick);
    }

    public ItemStack getRocketStack() {
        return this.entityData.get(ROCKET_STACK).copy();
    }

    private void setRocketStack(ItemStack stack) {
        this.entityData.set(ROCKET_STACK, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
    }

    public boolean isBoosting() {
        return this.entityData.get(BOOSTING);
    }

    public double getBoostDistance() {
        return this.boostDistance;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return true;
    }

    @Override
    public void tick() {
        if (this.removeNextTick) {
            super.tick();
            return;
        }
        Vec3 previousPosition = this.position();
        if (!this.level().isClientSide && this.guidanceData != null) {
            this.tickGuidanceChunkLoading();
            if (this.guidanceData.guidanceType() == MissileGuidanceType.RADAR
                    && !this.radarLaunchNotified) {
                this.interceptorNavigation.onRadarMissileLaunched(
                        this, this.position(), this.getBoostDirection());
                this.radarLaunchNotified = true;
            }

            this.guidanceCommand = this.tickGuidance(this.position(), this.getDeltaMovement());
            Vec3 appliedDelta = this.guidanceCommand.appliedDeltaV();
            this.entityData.set(GUIDANCE_ACCELERATION,
                    (isFinite(appliedDelta) ? appliedDelta : Vec3.ZERO).toVector3f());
            this.tickGuidanceChunkLoading();
            if (this.shouldDetonateAfterGuidanceFailure()) {
                this.detonate(this.position());
                return;
            }
        }
        super.tick();

        if (this.level().isClientSide) {
            Vec3 currentPosition = this.position();
            boolean moved = previousPosition.distanceToSqr(currentPosition) > 1.0E-8;
            if (!this.isRemoved() && !this.removeNextTick && this.isBoosting() && moved) {
                if (!this.startedFlightSound) {
                    this.startedFlightSound = true;
                    RocketClientEffects.startFlightSound(this);
                }
                spawnBoostParticlesClient(previousPosition, currentPosition);
            }
            return;
        }
        if (this.isRemoved() || this.removeNextTick) {
            return;
        }

        if (this.guidanceData != null && isUsableDirection(this.getDeltaMovement())) {
            this.setOrientation(this.getDeltaMovement().normalize());
        }

        if (this.canDetonate(fuzeItem -> fuzeItem.onProjectileTick(this.fuze, this))) {
            this.detonate(this.position());
            return;
        }

        ++this.flightAge;
        updateExhaustStage();
        if (this.isBoosting()) {
            double traveled = previousPosition.distanceTo(this.position());
            if (Double.isFinite(traveled)) {
                this.boostDistance += traveled;
            }
            if (this.boostDistance >= configuredBoostDistance()) {
                this.entityData.set(BOOSTING, false);
            }
        }

        if (this.flightAge >= MAX_FLIGHT_TICKS) {
            if (this.canDetonate(fuzeItem -> fuzeItem.onProjectileExpiry(this.fuze, this))) {
                this.detonate(this.position());
            } else {
                this.discard();
            }
        }
    }

    @Override
    protected Vec3 getForces(Vec3 position, Vec3 velocity) {
        if (this.isBoosting()) {
            if (this.level().isClientSide && this.entityData.get(GUIDED)) {
                Vec3 syncedAcceleration = new Vec3(this.entityData.get(GUIDANCE_ACCELERATION));
                return isFinite(syncedAcceleration) ? syncedAcceleration : Vec3.ZERO;
            }
            if (this.guidanceData != null) {
                return this.guidanceCommand.appliedDeltaV();
            }
            return this.getBoostDirection().scale(configuredBoostAcceleration());
        }
        return super.getForces(position, velocity);
    }

    private MissileNavigation.Command tickGuidance(Vec3 position, Vec3 velocity) {
        return this.guidanceData != null
                && this.guidanceData.guidanceType() == MissileGuidanceType.ARAD
                ? this.aradNavigation.tick(this, position, velocity)
                : this.interceptorNavigation.tick(this, position, velocity);
    }

    private boolean shouldDetonateAfterGuidanceFailure() {
        return this.guidanceData != null
                && (this.guidanceData.guidanceType() == MissileGuidanceType.ARAD
                ? this.aradNavigation.shouldDetonateAfterRunaway()
                : this.interceptorNavigation.shouldDetonateAfterRunaway());
    }

    private static boolean isSupportedGuidance(@Nullable MissileGuidanceData data) {
        if (data == null || data.guidanceType() == null) {
            return false;
        }
        return data.guidanceType() == MissileGuidanceType.ARAD
                || data.guidanceType() == MissileGuidanceType.COMMAND
                || data.guidanceType() == MissileGuidanceType.RADAR;
    }

    private void tickGuidanceChunkLoading() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        Set<Long> wanted = new HashSet<>();
        if (this.hasActiveGuidanceChunkDependencies()) {
            this.addGuidanceChunkDependencies(wanted);
        }

        for (long key : wanted) {
            if (this.forcedGuidanceChunks.add(key)) {
                ChunkPos chunk = new ChunkPos(key);
                serverLevel.getChunkSource().addRegionTicket(
                        ROCKET_GUIDANCE_CHUNK_TICKET, chunk, 2, this.getUUID(), true);
                serverLevel.getChunk(chunk.x, chunk.z);
            }
        }

        this.forcedGuidanceChunks.removeIf(key -> {
            if (wanted.contains(key)) {
                return false;
            }
            ChunkPos chunk = new ChunkPos(key);
            serverLevel.getChunkSource().removeRegionTicket(
                    ROCKET_GUIDANCE_CHUNK_TICKET, chunk, 2, this.getUUID(), true);
            return true;
        });
    }

    private boolean hasActiveGuidanceChunkDependencies() {
        if (this.guidanceData == null) {
            return false;
        }
        return this.guidanceData.guidanceType() == MissileGuidanceType.ARAD
                ? !this.aradNavigation.isRunaway() && !this.aradNavigation.isAborted()
                : !this.interceptorNavigation.isRunaway() && !this.interceptorNavigation.isAborted();
    }

    private void addGuidanceChunkDependencies(Set<Long> chunks) {
        this.addChunk(chunks, this.guidanceData.networkControllerPos());
        this.addChunk(chunks, this.guidanceData.radarGuidancePos());
        if (this.guidanceData.aradTargetReference() != null) {
            this.addChunk(chunks, this.guidanceData.aradTargetReference().radarPos());
            this.addChunk(chunks, this.guidanceData.aradTargetReference().noisyWorldPosition());
        }

        MissileTargetSpec target = this.guidanceData.target();
        if (target != null && target.type() == MissileTargetSpec.TargetType.POINT) {
            this.addChunk(chunks, target.point());
        }

        Vec3 resolvedTarget = this.guidanceData.guidanceType() == MissileGuidanceType.ARAD
                ? this.aradNavigation.targetPosition()
                : this.interceptorNavigation.targetPosition();
        this.addTargetChunks(chunks, resolvedTarget);
    }

    private void addChunk(Set<Long> chunks, @Nullable BlockPos position) {
        if (position != null) {
            chunks.add(new ChunkPos(position).toLong());
        }
    }

    private void addChunk(Set<Long> chunks, @Nullable Vec3 position) {
        if (isFinite(position)) {
            chunks.add(new ChunkPos(BlockPos.containing(position)).toLong());
        }
    }

    private void addTargetChunks(Set<Long> chunks, @Nullable Vec3 position) {
        if (!isFinite(position)) {
            return;
        }
        ChunkPos center = new ChunkPos(BlockPos.containing(position));
        for (int x = -GUIDANCE_TARGET_CHUNK_RADIUS; x <= GUIDANCE_TARGET_CHUNK_RADIUS; ++x) {
            for (int z = -GUIDANCE_TARGET_CHUNK_RADIUS; z <= GUIDANCE_TARGET_CHUNK_RADIUS; ++z) {
                chunks.add(ChunkPos.asLong(center.x + x, center.z + z));
            }
        }
    }

    private Vec3 getBoostDirection() {
        Vec3 direction = new Vec3(this.entityData.get(BOOST_DIRECTION));
        if (isUsableDirection(direction)) {
            return direction.normalize();
        }
        return safeDirection(this.getOrientation());
    }

    private static Vec3 safeDirection(Vec3 direction) {
        return isUsableDirection(direction) ? direction.normalize() : new Vec3(0.0, 1.0, 0.0);
    }

    private static double configuredInitialVelocity() {
        return configuredNonNegative(
                KaboomConfig.server().rocketInitialVelocity.getF(),
                LAUNCH_SPEED_BLOCKS_PER_TICK);
    }

    private static double configuredBoostAcceleration() {
        return configuredNonNegative(
                KaboomConfig.server().rocketAccelerationPerTick.getF(),
                BOOST_ACCELERATION_PER_TICK);
    }

    private static double configuredBoostDistance() {
        return configuredNonNegative(
                KaboomConfig.server().rocketBoostDistance.getF(),
                BOOST_DISTANCE_BLOCKS);
    }

    private static double configuredNonNegative(float configured, double fallback) {
        return Float.isFinite(configured) ? Math.max(0.0, configured) : fallback;
    }

    private static float sanitizeInaccuracy(float inaccuracy) {
        return Float.isFinite(inaccuracy) ? Math.max(0.0F, inaccuracy) : 0.0F;
    }

    private static boolean isUsableDirection(Vec3 direction) {
        return direction != null
                && Double.isFinite(direction.x)
                && Double.isFinite(direction.y)
                && Double.isFinite(direction.z)
                && direction.lengthSqr() > 1.0E-10;
    }

    private static boolean isFinite(@Nullable Vec3 position) {
        return position != null
                && Double.isFinite(position.x)
                && Double.isFinite(position.y)
                && Double.isFinite(position.z);
    }

    private void updateExhaustStage() {
        int stage = exhaustStageForAge(this.flightAge);
        if (this.entityData.get(EXHAUST_STAGE) != stage) {
            this.entityData.set(EXHAUST_STAGE, stage);
        }
    }

    private static int exhaustStageForAge(int age) {
        if (age < FORCEFUL_EXHAUST_TICKS) {
            return 0;
        }
        return age < MODERATE_EXHAUST_TICKS ? 1 : 2;
    }

    private int getExhaustParticleCount() {
        return switch (this.entityData.get(EXHAUST_STAGE)) {
            case 0 -> FORCEFUL_EXHAUST_COUNT;
            case 1 -> MODERATE_EXHAUST_COUNT;
            default -> DISTANT_EXHAUST_COUNT;
        };
    }

    private void spawnBoostParticlesClient(Vec3 previousPosition, Vec3 currentPosition) {
        Vec3 observedMotion = currentPosition.subtract(previousPosition);
        Vec3 forward = this.getOrientation();
        if (!isUsableDirection(forward)) {
            forward = observedMotion;
        }
        forward = safeDirection(forward);
        Vec3 backward = forward.scale(-1.0);

        Vec3 referenceUp = Math.abs(backward.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 right = backward.cross(referenceUp).normalize();
        Vec3 up = right.cross(backward).normalize();
        Vec3 inheritedMotion = observedMotion.scale(EXHAUST_MOTION_INHERITANCE);
        int particleCount = getExhaustParticleCount();

        for (int particle = 0; particle < particleCount; ++particle) {
            double pathProgress = (particle + this.random.nextDouble()) / particleCount;
            Vec3 pathPosition = previousPosition.lerp(currentPosition, pathProgress);
            double angle = this.random.nextDouble() * Math.PI * 2.0;
            double radius = Math.sqrt(this.random.nextDouble()) * EXHAUST_RADIUS;
            Vec3 radialOffset = right.scale(Math.cos(angle) * radius)
                    .add(up.scale(Math.sin(angle) * radius));
            Vec3 spawnPosition = pathPosition.add(backward.scale(EXHAUST_OFFSET)).add(radialOffset);

            double exhaustSpeed = EXHAUST_BASE_SPEED
                    + (this.random.nextDouble() * 2.0 - 1.0) * EXHAUST_SPEED_VARIANCE;
            Vec3 spread = right.scale((this.random.nextDouble() * 2.0 - 1.0) * EXHAUST_SPREAD)
                    .add(up.scale((this.random.nextDouble() * 2.0 - 1.0) * EXHAUST_SPREAD));
            Vec3 particleMotion = inheritedMotion.add(backward.scale(exhaustSpeed)).add(spread);

            this.level().addParticle(ModParticles.ROCKET_LAUNCH_SMOKE.get(), true,
                    spawnPosition.x, spawnPosition.y, spawnPosition.z,
                    particleMotion.x, particleMotion.y, particleMotion.z);
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(BOOSTING, true);
        builder.define(BOOST_DIRECTION, new Vector3f(0.0F, 1.0F, 0.0F));
        builder.define(GUIDED, false);
        builder.define(GUIDANCE_ACCELERATION, new Vector3f());
        builder.define(PAYLOAD_ID, NO_PAYLOAD_ID);
        builder.define(ROCKET_STACK, ItemStack.EMPTY);
        builder.define(EXHAUST_STAGE, 0);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (PAYLOAD_ID.equals(key)) {
            this.payload = payloadById(this.entityData.get(PAYLOAD_ID));
        }
    }

    @Override
    public @NotNull EntityDamagePropertiesComponent getDamageProperties() {
        return DAMAGE_PROPERTIES;
    }

    @Override
    protected @NotNull BallisticPropertiesComponent getBallisticProperties() {
        return this.payload != null && this.payload.isArmorPiercing()
                ? RocketPayload.AP_BALLISTIC_PROPERTIES
                : RocketPayload.STANDARD_BALLISTIC_PROPERTIES;
    }

    @Override
    protected ImpactResult calculateBlockPenetration(ProjectileContext projectileContext, BlockState state,
                                                      BlockHitResult blockHitResult) {
        if (this.payload != null && this.payload.isArmorPiercing()) {
            return this.calculateArmorPiercingBlockPenetration(projectileContext, state, blockHitResult);
        }
        ImpactResult result = new ImpactResult(ImpactResult.KinematicOutcome.STOP, false);
        boolean shouldRemove = this.onImpact(blockHitResult, result, projectileContext);
        return new ImpactResult(ImpactResult.KinematicOutcome.STOP, shouldRemove);
    }

    private ImpactResult calculateArmorPiercingBlockPenetration(ProjectileContext projectileContext,
                                                                 BlockState state,
                                                                 BlockHitResult blockHitResult) {
        BlockPos pos = blockHitResult.getBlockPos();
        Vec3 hitLocation = blockHitResult.getLocation();
        BallisticPropertiesComponent ballistics = RocketPayload.AP_BALLISTIC_PROPERTIES;
        BlockArmorPropertiesProvider blockArmor = BlockArmorPropertiesHandler.getProperties(state);
        boolean unbreakable = projectileContext.griefState() == CBCCfgMunitions.GriefState.NO_DAMAGE
                || state.getDestroySpeed(this.level(), pos) == -1;

        Vec3 acceleration = this.getForces(this.position(), this.getDeltaMovement());
        Vec3 currentVelocity = this.getDeltaMovement().add(acceleration);
        Vec3 normal = CBCUtils.getSurfaceNormalVector(this.level(), blockHitResult);
        double incidence = Math.max(0.0, currentVelocity.normalize().dot(normal.reverse()));
        double velocityMagnitude = currentVelocity.length();
        double mass = this.getProjectileMass();

        double bonusMomentum = 1.0 + Math.max(0.0,
                (velocityMagnitude - CBCConfigs.server().munitions.minVelocityForPenetrationBonus.getF())
                        * CBCConfigs.server().munitions.penetrationBonusScale.getF());
        double incidentVelocity = velocityMagnitude * incidence;
        double momentum = mass * incidentVelocity * bonusMomentum;

        double toughness = blockArmor.toughness(this.level(), state, pos, true);
        double toughnessPenalty = toughness - momentum;
        double hardnessPenalty = blockArmor.hardness(this.level(), state, pos, true)
                - ballistics.penetration();
        double bounceBonus = Math.max(1.0 - hardnessPenalty, 0.0);

        double projectileDeflection = ballistics.deflection();
        double baseChance = CBCConfigs.server().munitions.baseProjectileBounceChance.getF();
        double bounceChance = projectileDeflection < 1.0E-2 || incidence > projectileDeflection
                ? 0.0
                : Math.max(baseChance, 1.0 - incidence / projectileDeflection) * bounceBonus;

        boolean surfaceImpact = this.canHitSurface();
        boolean canBounce = CBCConfigs.server().munitions.projectilesCanBounce.get();
        boolean blockBroken = toughnessPenalty < 1.0E-2 && !unbreakable;
        ImpactResult.KinematicOutcome outcome;
        if (surfaceImpact && canBounce && this.level().getRandom().nextDouble() < bounceChance) {
            outcome = ImpactResult.KinematicOutcome.BOUNCE;
        } else if (blockBroken && !this.level().isClientSide) {
            outcome = ImpactResult.KinematicOutcome.PENETRATE;
        } else {
            outcome = ImpactResult.KinematicOutcome.STOP;
        }

        boolean shatter = surfaceImpact
                && outcome != ImpactResult.KinematicOutcome.BOUNCE
                && hardnessPenalty > ballistics.toughness();
        float durabilityPenalty = ((float) Math.max(0.0, hardnessPenalty) + 1.0F)
                * (float) toughness / (float) incidentVelocity;

        state.onProjectileHit(this.level(), state, blockHitResult, this);
        if (!this.level().isClientSide) {
            boolean bounced = outcome == ImpactResult.KinematicOutcome.BOUNCE;
            Vec3 effectDirection;
            if (bounced) {
                double elasticity = 1.7;
                effectDirection = currentVelocity.subtract(
                        normal.scale(normal.dot(currentVelocity) * elasticity));
            } else {
                effectDirection = currentVelocity.reverse();
            }
            for (BlockState containedState : blockArmor.containedBlockStates(
                    this.level(), state, pos.immutable(), true)) {
                projectileContext.addPlayedEffect(new ClientboundPlayBlockHitEffectPacket(
                        containedState,
                        this.getType(),
                        bounced,
                        true,
                        hitLocation.x,
                        hitLocation.y,
                        hitLocation.z,
                        (float) effectDirection.x,
                        (float) effectDirection.y,
                        (float) effectDirection.z));
            }
        }

        if (blockBroken) {
            this.setProjectileMass(incidentVelocity < 1.0E-4
                    ? 0.0F
                    : Math.max(this.getProjectileMass() - durabilityPenalty, 0.0F));
            this.level().setBlock(pos, Blocks.AIR.defaultBlockState(), ProjectileBlock.UPDATE_ALL_IMMEDIATE);

            if (surfaceImpact) {
                float spentMomentum = (float) toughness / (float) momentum;
                float overPenetrationPower = spentMomentum < 0.15F
                        ? 0.5F - 0.5F * spentMomentum
                        : 0.0F;
                if (overPenetrationPower > 0.0F && outcome == ImpactResult.KinematicOutcome.PENETRATE) {
                    projectileContext.queueExplosion(pos, overPenetrationPower);
                }
            }
        } else {
            if (outcome == ImpactResult.KinematicOutcome.STOP) {
                this.setProjectileMass(0.0F);
            } else {
                this.setProjectileMass(incidentVelocity < 1.0E-4
                        ? 0.0F
                        : Math.max(this.getProjectileMass() - durabilityPenalty / 2.0F, 0.0F));
            }
            Vec3 spallLocation = hitLocation.add(currentVelocity.normalize().scale(2.0));
            if (!this.level().isClientSide) {
                ImpactExplosion explosion = new ImpactExplosion(
                        this.level(),
                        this,
                        this.indirectArtilleryFire(false),
                        spallLocation.x,
                        spallLocation.y,
                        spallLocation.z,
                        0.5F,
                        0.5F,
                        Explosion.BlockInteraction.KEEP);
                CreateBigCannons.handleCustomExplosion(this.level(), explosion);
            }
            SoundType sound = state.getSoundType(this.level(), pos, this);
            if (!this.level().isClientSide) {
                this.level().playSound(
                        null,
                        spallLocation.x,
                        spallLocation.y,
                        spallLocation.z,
                        sound.getBreakSound(),
                        SoundSource.BLOCKS,
                        sound.getVolume(),
                        sound.getPitch());
            }
        }

        ImpactResult impact = new ImpactResult(outcome, shatter);
        boolean fuzeTriggered = this.onImpact(blockHitResult, impact, projectileContext);
        return new ImpactResult(outcome, shatter || fuzeTriggered);
    }

    @Override
    protected boolean onHitEntity(Entity entity, ProjectileContext projectileContext) {
        return this.onImpact(new EntityHitResult(entity),
                new ImpactResult(ImpactResult.KinematicOutcome.STOP, false), projectileContext);
    }

    @Override
    protected boolean onClip(ProjectileContext projectileContext, Vec3 start, Vec3 end) {
        if (super.onClip(projectileContext, start, end)) {
            return true;
        }
        if (this.canDetonate(fuzeItem -> fuzeItem.onProjectileClip(
                this.fuze, this, start, end, projectileContext, false))) {
            this.detonate(projectileContext.getDetonationPositionForClip());
            return true;
        }
        return false;
    }

    @Override
    protected boolean onImpact(HitResult hitResult, ImpactResult impactResult,
                               ProjectileContext projectileContext) {
        super.onImpact(hitResult, impactResult, projectileContext);
        ImpactResult fuzeImpact = new ImpactResult(impactResult.kinematics(), false);
        if (this.canDetonate(fuzeItem -> fuzeItem.onProjectileImpact(
                this.fuze, this, hitResult, fuzeImpact, false))) {
            this.detonate(hitResult.getLocation());
            return true;
        }
        return false;
    }

    @Override
    public boolean canLingerInGround() {
        if (this.level().isClientSide || !this.level().isLoaded(this.blockPosition())) {
            return false;
        }
        return this.fuze.getItem() instanceof FuzeItem fuzeItem
                && fuzeItem.canLingerInGround(this.fuze, this);
    }

    private boolean canDetonate(Predicate<FuzeItem> condition) {
        if (this.level().isClientSide
                || !this.level().isLoaded(this.blockPosition())
                || this.isRemoved()
                || !(this.fuze.getItem() instanceof FuzeItem fuzeItem)) {
            return false;
        }
        return condition.test(fuzeItem);
    }

    private void setPayload(@Nullable RocketPayload payload) {
        this.payload = payload;
        this.entityData.set(PAYLOAD_ID, payload == null ? NO_PAYLOAD_ID : payload.getId());
    }

    @Nullable
    private static RocketPayload payloadById(int id) {
        return id >= 0 && id < RocketPayload.values().length
                ? RocketPayload.byId(id)
                : null;
    }

    private void detonate(Vec3 position) {
        if (this.detonated) {
            return;
        }
        this.detonated = true;
        this.removeNextTick = true;
        if (this.level().isClientSide) {
            return;
        }

        if (this.payload != null) {
            this.payload.detonate(this, position);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        this.interceptionState.save(tag);
        tag.put(ROCKET_STACK_TAG, this.getRocketStack().saveOptional(this.registryAccess()));
        tag.put(FUZE_TAG, this.fuze.saveOptional(this.registryAccess()));
        tag.putDouble(BOOST_DISTANCE_TAG, this.boostDistance);
        tag.putInt(FLIGHT_AGE_TAG, this.flightAge);
        tag.putBoolean(BOOSTING_TAG, this.isBoosting());
        Vec3 direction = this.getBoostDirection();
        tag.putDouble(BOOST_DIRECTION_X_TAG, direction.x);
        tag.putDouble(BOOST_DIRECTION_Y_TAG, direction.y);
        tag.putDouble(BOOST_DIRECTION_Z_TAG, direction.z);
        if (this.guidanceData != null) {
            tag.put(GUIDANCE_DATA_TAG, this.guidanceData.toTag());
            if (this.guidanceData.guidanceType() == MissileGuidanceType.ARAD) {
                this.aradNavigation.write(tag);
            } else {
                this.interceptorNavigation.write(tag);
            }
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.interceptionState.load(tag);
        ItemStack rocketStack = tag.contains(ROCKET_STACK_TAG, Tag.TAG_COMPOUND)
                ? ItemStack.parseOptional(this.registryAccess(), tag.getCompound(ROCKET_STACK_TAG))
                : ItemStack.EMPTY;
        this.setRocketStack(rocketStack);
        this.setPayload(RocketItem.getPayload(this.getRocketStack()));
        this.fuze = tag.contains(FUZE_TAG, Tag.TAG_COMPOUND)
                ? ItemStack.parseOptional(this.registryAccess(), tag.getCompound(FUZE_TAG))
                : RocketItem.getAttachedFuze(this.getRocketStack());
        this.boostDistance = Math.max(0.0, tag.getDouble(BOOST_DISTANCE_TAG));
        this.flightAge = Math.max(0, tag.getInt(FLIGHT_AGE_TAG));
        this.entityData.set(EXHAUST_STAGE, exhaustStageForAge(this.flightAge));
        this.entityData.set(BOOSTING, tag.contains(BOOSTING_TAG) ? tag.getBoolean(BOOSTING_TAG)
                : this.boostDistance < configuredBoostDistance());
        Vec3 direction = new Vec3(
                tag.getDouble(BOOST_DIRECTION_X_TAG),
                tag.getDouble(BOOST_DIRECTION_Y_TAG),
                tag.getDouble(BOOST_DIRECTION_Z_TAG));
        this.entityData.set(BOOST_DIRECTION, safeDirection(direction).toVector3f());

        this.setGuidanceData(null);
        if (tag.contains(GUIDANCE_DATA_TAG, Tag.TAG_COMPOUND)) {
            try {
                MissileGuidanceData savedGuidance = MissileGuidanceData.fromTag(
                        tag.getCompound(GUIDANCE_DATA_TAG));
                if (isSupportedGuidance(savedGuidance)) {
                    this.setGuidanceData(savedGuidance);
                    if (savedGuidance.guidanceType() == MissileGuidanceType.ARAD) {
                        this.aradNavigation.read(tag, this, this.position());
                    } else {
                        this.interceptorNavigation.read(tag, this, this.position());
                    }
                }
            } catch (RuntimeException exception) {
                this.setGuidanceData(null);
                CreateKaboom.getLogger().warn(
                        "Ignored invalid saved rocket guidance for projectile {}", this.getUUID(), exception);
            }
        }
    }

    private void releaseGuidanceChunks() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            this.forcedGuidanceChunks.clear();
            return;
        }
        for (long key : this.forcedGuidanceChunks) {
            ChunkPos chunk = new ChunkPos(key);
            serverLevel.getChunkSource().removeRegionTicket(
                    ROCKET_GUIDANCE_CHUNK_TICKET, chunk, 2, this.getUUID(), true);
        }
        this.forcedGuidanceChunks.clear();
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!this.level().isClientSide && this.level() instanceof ServerLevel) {
            this.interceptorNavigation.clearRadarRwrEmitter(this);
            this.releaseGuidanceChunks();
        }
        super.remove(reason);
    }
}
