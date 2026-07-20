package com.happysg.kaboom.items.rocket;

import com.happysg.kaboom.block.missiles.MissileEntity;
import com.happysg.kaboom.client.RocketClientEffects;
import com.happysg.kaboom.interception.InterceptableOrdnance;
import com.happysg.kaboom.interception.OrdnanceInterceptionState;
import com.happysg.kaboom.registry.ModParticles;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;
import rbasamoyai.createbigcannons.CreateBigCannons;
import rbasamoyai.createbigcannons.config.CBCConfigs;
import rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile;
import rbasamoyai.createbigcannons.munitions.ProjectileContext;
import rbasamoyai.createbigcannons.munitions.ShellExplosion;
import rbasamoyai.createbigcannons.munitions.config.components.BallisticPropertiesComponent;
import rbasamoyai.createbigcannons.munitions.config.components.EntityDamagePropertiesComponent;
import rbasamoyai.createbigcannons.munitions.fuzes.FuzeItem;

import java.util.function.Predicate;

public class UnguidedRocketProjectile extends AbstractCannonProjectile implements InterceptableOrdnance {
    public static final double LAUNCH_SPEED_BLOCKS_PER_TICK = 0.1;
    public static final double BOOST_ACCELERATION_PER_TICK = 0.1;
    public static final double BOOST_DISTANCE_BLOCKS = 250.0;
    public static final int MAX_FLIGHT_TICKS = 1200;
    public static final float EXPLOSION_POWER = 4.0F;

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

    private static final EntityDataAccessor<Boolean> BOOSTING =
            SynchedEntityData.defineId(UnguidedRocketProjectile.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Vector3f> BOOST_DIRECTION =
            SynchedEntityData.defineId(UnguidedRocketProjectile.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Integer> EXHAUST_STAGE =
            SynchedEntityData.defineId(UnguidedRocketProjectile.class, EntityDataSerializers.INT);

    private static final EntityDamagePropertiesComponent DAMAGE_PROPERTIES =
            EntityDamagePropertiesComponent.DEFAULT;

    private final OrdnanceInterceptionState interceptionState = new OrdnanceInterceptionState();
    private ItemStack rocketStack = ItemStack.EMPTY;
    private ItemStack fuze = ItemStack.EMPTY;
    private double boostDistance;
    private int flightAge;
    private boolean detonated;
    private boolean startedFlightSound;

    public UnguidedRocketProjectile(EntityType<? extends AbstractCannonProjectile> type, Level level) {
        super(type, level);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return this.interceptionState.hurt(this, source, amount, () -> this.detonate(this.position()));
    }

    public void initialize(ItemStack stack, Vec3 launchDirection) {
        Vec3 direction = safeDirection(launchDirection);
        this.rocketStack = stack.copyWithCount(1);
        this.fuze = RocketItem.getAttachedFuze(stack);
        this.boostDistance = 0.0;
        this.flightAge = 0;
        this.detonated = false;
        this.entityData.set(BOOSTING, true);
        this.entityData.set(BOOST_DIRECTION, direction.toVector3f());
        this.entityData.set(EXHAUST_STAGE, 0);
        this.setOrientation(direction);
        this.shoot(direction.x, direction.y, direction.z,
                (float) LAUNCH_SPEED_BLOCKS_PER_TICK, 0.0F);
    }

    public ItemStack getRocketStack() {
        return this.rocketStack.copy();
    }

    public boolean isBoosting() {
        return this.entityData.get(BOOSTING);
    }

    public double getBoostDistance() {
        return this.boostDistance;
    }

    @Override
    public void tick() {
        Vec3 previousPosition = this.position();
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
            if (this.boostDistance >= BOOST_DISTANCE_BLOCKS) {
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
            return this.getBoostDirection().scale(BOOST_ACCELERATION_PER_TICK);
        }
        return super.getForces(position, velocity);
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

    private static boolean isUsableDirection(Vec3 direction) {
        return direction != null
                && Double.isFinite(direction.x)
                && Double.isFinite(direction.y)
                && Double.isFinite(direction.z)
                && direction.lengthSqr() > 1.0E-10;
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
        builder.define(EXHAUST_STAGE, 0);
    }

    @Override
    public @NotNull EntityDamagePropertiesComponent getDamageProperties() {
        return DAMAGE_PROPERTIES;
    }

    @Override
    protected @NotNull BallisticPropertiesComponent getBallisticProperties() {
        return MissileEntity.BALLISTIC_PROPERTIES;
    }

    @Override
    protected ImpactResult calculateBlockPenetration(ProjectileContext projectileContext, BlockState state,
                                                      BlockHitResult blockHitResult) {
        ImpactResult result = new ImpactResult(ImpactResult.KinematicOutcome.STOP, false);
        boolean shouldRemove = this.onImpact(blockHitResult, result, projectileContext);
        return new ImpactResult(ImpactResult.KinematicOutcome.STOP, shouldRemove);
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

    private void detonate(Vec3 position) {
        if (this.detonated) {
            return;
        }
        this.detonated = true;
        this.removeNextTick = true;
        if (this.level().isClientSide) {
            return;
        }

        ShellExplosion explosion = new ShellExplosion(
                this.level(), this, this.indirectArtilleryFire(false),
                position.x, position.y, position.z,
                EXPLOSION_POWER, EXPLOSION_POWER, false,
                CBCConfigs.server().munitions.damageRestriction.get().explosiveInteraction());
        CreateBigCannons.handleCustomExplosion(this.level(), explosion);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        this.interceptionState.save(tag);
        tag.put(ROCKET_STACK_TAG, this.rocketStack.saveOptional(this.registryAccess()));
        tag.put(FUZE_TAG, this.fuze.saveOptional(this.registryAccess()));
        tag.putDouble(BOOST_DISTANCE_TAG, this.boostDistance);
        tag.putInt(FLIGHT_AGE_TAG, this.flightAge);
        tag.putBoolean(BOOSTING_TAG, this.isBoosting());
        Vec3 direction = this.getBoostDirection();
        tag.putDouble(BOOST_DIRECTION_X_TAG, direction.x);
        tag.putDouble(BOOST_DIRECTION_Y_TAG, direction.y);
        tag.putDouble(BOOST_DIRECTION_Z_TAG, direction.z);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.interceptionState.load(tag);
        this.rocketStack = tag.contains(ROCKET_STACK_TAG, Tag.TAG_COMPOUND)
                ? ItemStack.parseOptional(this.registryAccess(), tag.getCompound(ROCKET_STACK_TAG))
                : ItemStack.EMPTY;
        this.fuze = tag.contains(FUZE_TAG, Tag.TAG_COMPOUND)
                ? ItemStack.parseOptional(this.registryAccess(), tag.getCompound(FUZE_TAG))
                : RocketItem.getAttachedFuze(this.rocketStack);
        this.boostDistance = Math.max(0.0, tag.getDouble(BOOST_DISTANCE_TAG));
        this.flightAge = Math.max(0, tag.getInt(FLIGHT_AGE_TAG));
        this.entityData.set(EXHAUST_STAGE, exhaustStageForAge(this.flightAge));
        this.entityData.set(BOOSTING, tag.contains(BOOSTING_TAG) ? tag.getBoolean(BOOSTING_TAG)
                : this.boostDistance < BOOST_DISTANCE_BLOCKS);
        Vec3 direction = new Vec3(
                tag.getDouble(BOOST_DIRECTION_X_TAG),
                tag.getDouble(BOOST_DIRECTION_Y_TAG),
                tag.getDouble(BOOST_DIRECTION_Z_TAG));
        this.entityData.set(BOOST_DIRECTION, safeDirection(direction).toVector3f());
    }
}
