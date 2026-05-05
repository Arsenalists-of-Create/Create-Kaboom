package com.happysg.kaboom.block.aerialBombs.baseTypes;

import com.happysg.kaboom.block.aerialBombs.cluster.ClusterBombletProjectile;
import com.happysg.kaboom.registry.ModBlocks;
import com.happysg.kaboom.registry.ModProjectiles;
import com.happysg.kaboom.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.NotNull;
import rbasamoyai.createbigcannons.CreateBigCannons;
import rbasamoyai.createbigcannons.block_armor_properties.BlockArmorPropertiesHandler;
import rbasamoyai.createbigcannons.block_armor_properties.BlockArmorPropertiesProvider;
import rbasamoyai.createbigcannons.config.CBCCfgMunitions;
import rbasamoyai.createbigcannons.config.CBCConfigs;
import rbasamoyai.createbigcannons.index.CBCEntityTypes;
import rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile;
import rbasamoyai.createbigcannons.munitions.ImpactExplosion;
import rbasamoyai.createbigcannons.munitions.ProjectileContext;
import rbasamoyai.createbigcannons.munitions.ShellExplosion;
import rbasamoyai.createbigcannons.munitions.big_cannon.ProjectileBlock;
import rbasamoyai.createbigcannons.munitions.big_cannon.config.BigCannonFuzePropertiesComponent;
import rbasamoyai.createbigcannons.munitions.big_cannon.fluid_shell.*;
import rbasamoyai.createbigcannons.munitions.big_cannon.solid_shot.SolidShotProjectile;
import rbasamoyai.createbigcannons.munitions.config.FluidDragHandler;
import rbasamoyai.createbigcannons.munitions.config.components.BallisticPropertiesComponent;
import rbasamoyai.createbigcannons.munitions.config.components.EntityDamagePropertiesComponent;
import rbasamoyai.createbigcannons.munitions.fuzes.FuzeItem;
import rbasamoyai.createbigcannons.network.ClientboundPlayBlockHitEffectPacket;
import rbasamoyai.createbigcannons.utils.CBCUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Predicate;

public class AerialBombProjectile extends AbstractCannonProjectile {

    public static final BallisticPropertiesComponent BALLISTIC_PROPERTIES = new BallisticPropertiesComponent(-0.1, .01, false, 2.0f, 1, 1, 0.70f);
    public static final EntityDamagePropertiesComponent DAMAGE_PROPERTIES = new EntityDamagePropertiesComponent(30, false, true, true, 2);

    protected static final EntityDataAccessor<Integer> TIME_REQUIRED = SynchedEntityData.defineId(AerialBombProjectile.class, EntityDataSerializers.INT);
    protected static final EntityDataAccessor<Integer> TIME = SynchedEntityData.defineId(AerialBombProjectile.class, EntityDataSerializers.INT);
    protected static final EntityDataAccessor<BlockState> STATE = SynchedEntityData.defineId(AerialBombProjectile.class, EntityDataSerializers.BLOCK_STATE);
    private EndFluidStack fluidStack;
    private ItemStack fuze;
    private int explosionCountdown;
    private BombType type;
    private int size;
    private int count;
    boolean onImpact = false;
    private int apRemaining = 0;

    public AerialBombProjectile(EntityType<? extends AbstractCannonProjectile> type, Level level) {
        super(type, level);
        this.fuze = ItemStack.EMPTY;
        this.explosionCountdown = -1;
    }

    public void setFluidStack(EndFluidStack fstack) {
        this.fluidStack = fstack;
    }

    @Override
    public @NotNull EntityDamagePropertiesComponent getDamageProperties() {
        return DAMAGE_PROPERTIES;
    }

    @Override
    protected @NotNull BallisticPropertiesComponent getBallisticProperties() {
        return BALLISTIC_PROPERTIES;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(TIME, 0);
        builder.define(TIME_REQUIRED, 10);
        builder.define(STATE, ModBlocks.HEAVY_AERIAL_BOMB.getDefaultState());
        builder.define(PAYLOAD_FLUID, new CompoundTag());
    }

    public void setState(BlockState pState) {
        this.entityData.set(STATE, pState);
    }

    public BlockState getState() {
        return this.entityData.get(STATE);
    }

    public int getTime() {
        return this.entityData.get(TIME);
    }

    public int getTimeRequired() {
        return this.entityData.get(TIME_REQUIRED);
    }

    public void setFuze(ItemStack stack) {
        this.fuze = stack != null && !stack.isEmpty() ? stack : ItemStack.EMPTY;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public enum BombType {
        HE,
        AP,
        FLUID,
        FRAG,
        INCENDIARY,
        CLUSTER
    }

    public void setBombType(BombType type) {
        this.type = type;
        if (type == BombType.AP) {
            apRemaining = 20;
        }
    }

    public void tick() {
        super.tick();

        if (!this.level().isClientSide && this.explosionCountdown > 0) {
            --this.explosionCountdown;
        }

        if (!level().isClientSide) {
            this.entityData.set(TIME, this.entityData.get(TIME) + 1);
        }

        if (this.canDetonate((fz) -> fz.onProjectileTick(this.fuze, this)) || !this.level().isClientSide && this.explosionCountdown == 0) {
            this.detonate(this.position());
        }

    }

    @Override
    protected ImpactResult calculateBlockPenetration(ProjectileContext projectileContext, BlockState state, BlockHitResult blockHitResult) {
        BlockPos pos = blockHitResult.getBlockPos();
        Vec3 hitLoc = blockHitResult.getLocation();

        BallisticPropertiesComponent ballistics = this.getBallisticProperties();
        BlockArmorPropertiesProvider blockArmor = BlockArmorPropertiesHandler.getProperties(state);
        boolean unbreakable = projectileContext.griefState() == CBCCfgMunitions.GriefState.NO_DAMAGE || state.getDestroySpeed(this.level(), pos) == -1;

        Vec3 accel = this.getForces(this.position(), this.getDeltaMovement());
        Vec3 curVel = this.getDeltaMovement().add(accel);

        Vec3 normal = CBCUtils.getSurfaceNormalVector(this.level(), blockHitResult);
        double incidence = Math.max(0, curVel.normalize().dot(normal.reverse()));
        double velMag = (curVel.length());
        if(type == BombType.AP) velMag = velMag*((double) 17 /size);
        double mass = this.getProjectileMass();

        double bonusMomentum = 1 + Math.max(0, (velMag - CBCConfigs.server().munitions.minVelocityForPenetrationBonus.getF())
                * CBCConfigs.server().munitions.penetrationBonusScale.getF());
        double incidentVel = velMag * incidence;
        double momentum = mass * incidentVel * bonusMomentum;

        double toughness = blockArmor.toughness(this.level(), state, pos, true);
        double toughnessPenalty = toughness - momentum;
        double hardnessPenalty = blockArmor.hardness(this.level(), state, pos, true) - ballistics.penetration();
        double bounceBonus = Math.max(1 - hardnessPenalty, 0);

        double projectileDeflection = ballistics.deflection();
        double baseChance = CBCConfigs.server().munitions.baseProjectileBounceChance.getF();
        double bounceChance = projectileDeflection < 1e-2d || incidence > projectileDeflection ? 0 : Math.max(baseChance, 1 - incidence / projectileDeflection) * bounceBonus;

        boolean surfaceImpact = this.canHitSurface();
        boolean canBounce = CBCConfigs.server().munitions.projectilesCanBounce.get();
        boolean blockBroken = toughnessPenalty < 1e-2d && !unbreakable;
        ImpactResult.KinematicOutcome outcome;
        if (surfaceImpact && canBounce && this.level().getRandom().nextDouble() < bounceChance) {
            outcome = ImpactResult.KinematicOutcome.BOUNCE;
        } else if (blockBroken && !this.level().isClientSide) {
            outcome = ImpactResult.KinematicOutcome.PENETRATE;
        } else {
            outcome = ImpactResult.KinematicOutcome.STOP;
        }
        boolean shatter = surfaceImpact && outcome != ImpactResult.KinematicOutcome.BOUNCE && hardnessPenalty > ballistics.toughness();
        float durabilityPenalty = ((float) Math.max(0, hardnessPenalty) + 1) * (float) toughness / (float) incidentVel;

        state.onProjectileHit(this.level(), state, blockHitResult, this);
        if (!this.level().isClientSide) {
            boolean bounced = outcome == ImpactResult.KinematicOutcome.BOUNCE;
            Vec3 effectNormal;
            if (bounced) {
                double elasticity = 1.7f;
                effectNormal = curVel.subtract(normal.scale(normal.dot(curVel) * elasticity));
            } else {
                effectNormal = curVel.reverse();
            }
            for (BlockState state1 : blockArmor.containedBlockStates(this.level(), state, pos.immutable(), true)) {
                projectileContext.addPlayedEffect(new ClientboundPlayBlockHitEffectPacket(state1, this.getType(), bounced, true,
                        hitLoc.x, hitLoc.y, hitLoc.z, (float) effectNormal.x, (float) effectNormal.y, (float) effectNormal.z));
            }
        }
        if (blockBroken) {
            this.setProjectileMass(incidentVel < 1e-4d ? 0 : Math.max(this.getProjectileMass() - durabilityPenalty, 0));
            this.level().setBlock(pos, Blocks.AIR.defaultBlockState(), ProjectileBlock.UPDATE_ALL_IMMEDIATE);

            if (surfaceImpact) {
                float f = (float) toughness / (float) momentum;
                float overPenetrationPower = f < 0.15f ? 2 - 2 * f : 0;
                if (overPenetrationPower > 0 && outcome == ImpactResult.KinematicOutcome.PENETRATE)
                    projectileContext.queueExplosion(pos, overPenetrationPower);
            }
        } else {
            if (outcome == ImpactResult.KinematicOutcome.STOP) {
                this.setProjectileMass(0);
            } else {
                this.setProjectileMass(incidentVel < 1e-4d ? 0 : Math.max(this.getProjectileMass() - durabilityPenalty / 2f, 0));
            }
            Vec3 spallLoc = hitLoc.add(curVel.normalize().scale(2));
            if (!this.level().isClientSide) {
                ImpactExplosion explosion = new ImpactExplosion(this.level(), this, this.indirectArtilleryFire(false), spallLoc.x, spallLoc.y, spallLoc.z, 2, Explosion.BlockInteraction.KEEP);
                CreateBigCannons.handleCustomExplosion(this.level(), explosion);
            }
            SoundType sound = state.getSoundType(this.level(), pos, this);
            if (!this.level().isClientSide)
                this.level().playSound(null, spallLoc.x, spallLoc.y, spallLoc.z, sound.getBreakSound(), SoundSource.BLOCKS,
                        sound.getVolume(), sound.getPitch());
        }
        shatter |= this.onImpact(blockHitResult, new ImpactResult(outcome, shatter), projectileContext);
        return new ImpactResult(outcome, shatter);
    }

    protected boolean onClip(ProjectileContext ctx, Vec3 start, Vec3 end) {
        if (super.onClip(ctx, start, end)) {
            return true;
        } else {
            boolean baseFuze = this.getFuzeProperties().baseFuze();
            if (this.canDetonate((fz) -> fz.onProjectileClip(this.fuze, this, start, end, ctx, baseFuze))) {
                this.detonate(start);
                return true;
            } else {
                return false;
            }
        }
    }

    protected boolean onImpact(HitResult hitResult, AbstractCannonProjectile.ImpactResult impactResult, ProjectileContext projectileContext) {
        super.onImpact(hitResult, impactResult, projectileContext);
        boolean baseFuze = this.getFuzeProperties().baseFuze();
        ImpactResult fuzeImpact = new ImpactResult(impactResult.kinematics(), false);
        this.onImpact = true;
        if (this.canDetonate((fz) -> fz.onProjectileImpact(this.fuze, this, hitResult, fuzeImpact, baseFuze))) {
            this.detonate(hitResult.getLocation());
            return true;
        } else {
            return false;
        }
    }
    public void setFuzeStack(ItemStack stack) {
        this.fuze = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();

        this.explosionCountdown = -1;
    }

    public void setFuzeStackUnsafe(ItemStack stack) {
        this.fuze = stack.copy();
        this.explosionCountdown = -1;
    }

    @Nonnull
    protected BigCannonFuzePropertiesComponent getFuzeProperties() {
        return new BigCannonFuzePropertiesComponent(false);
    }

    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("Fuze", this.fuze.saveOptional(this.registryAccess()));
        tag.put("PayloadFluid", this.entityData.get(PAYLOAD_FLUID));
        if (this.explosionCountdown >= 0) {
            tag.putInt("ExplosionCountdown", this.explosionCountdown);
        }

    }

    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.fuze = ItemStack.parseOptional(this.registryAccess(), tag.getCompound("Fuze"));
        this.explosionCountdown = tag.contains("ExplosionCountdown", 3) ? tag.getInt("ExplosionCountdown") : -1;
        if (tag.contains("PayloadFluid", Tag.TAG_COMPOUND))
            this.entityData.set(PAYLOAD_FLUID, tag.getCompound("PayloadFluid"));

    }

    protected final boolean canDetonate(Predicate<FuzeItem> cons) {
        if (!this.level().isClientSide && this.level().isLoaded(this.blockPosition()) && !this.isRemoved()) {
            Item item = this.fuze.getItem();
            if (item instanceof FuzeItem fuzeItem) {
                return cons.test(fuzeItem);
            }
        }

        return false;
    }

    @Deprecated
    protected void detonate() {
        this.detonate(this.position());
    }

    protected void detonate(Position position) {
        if (type == null || this.level().isClientSide) {
            return;
        }

        this.removeNextTick = true;

        switch (type) {
            case HE -> {
                ShellExplosion explosion = new ShellExplosion(this.level(), this, this.indirectArtilleryFire(false), position.x(), position.y(), position.z(), (float) 25 / size, false, CBCConfigs.server().munitions.damageRestriction.get().explosiveInteraction());
                CreateBigCannons.handleCustomExplosion(this.level(), explosion);
            }
            case AP -> {
                ShellExplosion explosion = new ShellExplosion(this.level(), this, this.indirectArtilleryFire(false), position.x(), position.y(), position.z(), (float) 15 / size, false, CBCConfigs.server().munitions.damageRestriction.get().explosiveInteraction());
                CreateBigCannons.handleCustomExplosion(this.level(), explosion);
            }
            case FRAG -> {
                ShellExplosion explosion = new ShellExplosion(
                        this.level(), this, this.indirectArtilleryFire(false),
                        position.x(), position.y(), position.z(),
                        4.0f / size,
                        false,
                        CBCConfigs.server()
                                .munitions.damageRestriction.get()
                                .explosiveInteraction()
                );
                CreateBigCannons.handleCustomExplosion(this.level(), explosion);
                if (!this.level().isClientSide) {
                    boolean noDamage = false;
                    if (!noDamage) {

                        final double breakRadius = (double) 22 / size;
                        final double breakRadiusSqr = breakRadius * breakRadius;

                        BlockPos center = BlockPos.containing(position.x(), position.y(), position.z());
                        int r = Mth.ceil(breakRadius);

                        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {

                            double dx = (pos.getX() + 0.5) - position.x();
                            double dy = (pos.getY() + 0.5) - position.y();
                            double dz = (pos.getZ() + 0.5) - position.z();
                            if (dx * dx + dy * dy + dz * dz > breakRadiusSqr) continue;

                            BlockState state = level().getBlockState(pos);
                            if (state.isAir()) continue;
                            if (state.getDestroySpeed(level(), pos) < 0) continue;
                            if (!state.is(ModTags.Blocks.FRAG_SHATTERS)) continue;

                            level().destroyBlock(pos, false, this);
                        }
                    }
                }
                spawnCutterBomblets(new Vec3(position.x(), position.y(), position.z()), size);
                if (!this.level().isClientSide) {
                    final double radius = (double) 60 / size;
                    final float maxDamage = 60;

                    final double k = Math.log(10.0) / radius;

                    AABB box = new AABB(
                            position.x() - radius, position.y() - radius, position.z() - radius,
                            position.x() + radius, position.y() + radius, position.z() + radius
                    );

                    List<LivingEntity> entities = this.level().getEntitiesOfClass(LivingEntity.class, box);

                    for (LivingEntity e : entities) {
                        if (this.getOwner() != null && e == this.getOwner()) continue;
                        Vec3 origin = new Vec3(position.x(), position.y(), position.z());
                        Vec3 target = e.getEyePosition();

                        if (!hasBlastLineOfSight(origin, target))
                            continue;

                        double d = e.distanceToSqr(position.x(), position.y(), position.z());
                        double dist = Math.sqrt(d);
                        if (dist > radius) continue;

                        double scale = Math.exp(-k * dist);
                        float damage = (float) (maxDamage * scale);

                        if (damage < 1.0f) continue;

                        e.hurt(this.damageSources().explosion(this, this.getOwner()), damage);
                    }
                }

            }
            case INCENDIARY -> {
                ShellExplosion explosion = new ShellExplosion(this.level(), this, this.indirectArtilleryFire(false), position.x(), position.y(), position.z(), 20 / size, true, CBCConfigs.server().munitions.damageRestriction.get().explosiveInteraction());
                CreateBigCannons.handleCustomExplosion(this.level(), explosion);
                int fireRadius = 40 / size;

                AABB area = new AABB(
                        position.x() - fireRadius,
                        position.y() - fireRadius,
                        position.z() - fireRadius,
                        position.x() + fireRadius,
                        position.y() + fireRadius,
                        position.z() + fireRadius
                );

                int attempts = 750 / size;

                BlockPos center = BlockPos.containing(position);
                RandomSource random = this.level().getRandom();

                for (int i = 0; i < attempts; i++) {

                    double angle = random.nextDouble() * Math.PI * 2;
                    double distance = Math.sqrt(random.nextDouble()) * fireRadius;
                    int offsetX = (int) (Math.cos(angle) * distance);
                    int offsetZ = (int) (Math.sin(angle) * distance);

                    BlockPos groundCheck = center.offset(offsetX, 5, offsetZ);

                    for (int y = 0; y < 10; y++) {
                        BlockPos below = groundCheck.below(y);

                        if (this.level().getBlockState(below).isCollisionShapeFullBlock(this.level(), below)) {

                            BlockPos firePos = below.above();

                            if (this.level().isEmptyBlock(firePos)) {
                                this.level().setBlockAndUpdate(
                                        firePos,
                                        Blocks.FIRE.defaultBlockState()
                                );
                            }
                            break;
                        }
                    }
                }

                List<LivingEntity> entities = this.level().getEntitiesOfClass(LivingEntity.class, area);

                for (LivingEntity entity : entities) {
                    double distance = entity.distanceToSqr(position.x(), position.y(), position.z());
                    if (distance <= fireRadius * fireRadius) {

                        double dist = Math.sqrt(distance);
                        float damage = (float) (20.0 * (1.0 - dist / fireRadius));

                        entity.hurt(this.damageSources().explosion(this, this.getOwner()), damage);
                        entity.igniteForSeconds(6);
                    }
                }
            }
            case FLUID -> {

                ShellExplosion explosion = new ShellExplosion(
                        this.level(), this, this.indirectArtilleryFire(false),
                        position.x(), position.y(), position.z(),
                        2.5f / size, false,
                        CBCConfigs.server().munitions.damageRestriction.get().explosiveInteraction()
                );
                CreateBigCannons.handleCustomExplosion(this.level(), explosion);

                spillFluid(position, getPayloadFluid());
            }
            case CLUSTER -> {
                if (!level().isClientSide) {

                    Vec3 origin = new Vec3(position.x(), position.y(), position.z());

                    boolean detonatedOnImpact = this.onImpact;
                    ShellExplosion explosion = new ShellExplosion(this.level(), this, this.indirectArtilleryFire(false), position.x(), position.y(), position.z(), 10, false, CBCConfigs.server().munitions.damageRestriction.get().explosiveInteraction());
                    CreateBigCannons.handleCustomExplosion(this.level(), explosion);

                    int total = 20 + random.nextInt(10);
                    int outerCount = (int) Math.ceil(total * 0.55);
                    int innerCount = total - outerCount;

                    Vec3 inheritedBase = this.getDeltaMovement().scale(0.35);
                    if (!detonatedOnImpact) {
                        inheritedBase = new Vec3(inheritedBase.x, inheritedBase.y * 0.6, inheritedBase.z);
                    }

                    Vec3 finalInheritedBase = inheritedBase;
                    java.util.function.BiConsumer<Double, Double> spawnOne = (alpha, speed) -> {
                        ClusterBombletProjectile sub = ModProjectiles.CLUSTER_BOMBLET.get().create(level());
                        if (sub == null) return;

                        sub.configure(
                                this.fuze,
                                this.size,
                                3.0f,
                                false,
                                30 + random.nextInt(30)
                        );

                        sub.setPos(origin.x, origin.y + 3, origin.z);

                        double theta = random.nextDouble() * Math.PI * 2.0;

                        Vec3 dir = new Vec3(
                                Math.sin(alpha) * Math.cos(theta),
                                Math.cos(alpha),
                                Math.sin(alpha) * Math.sin(theta)
                        );

                        Vec3 outward = dir.scale(speed);

                        double yBias = detonatedOnImpact
                                ? (0.55 + random.nextDouble() * 0.45)
                                : (0.05 + random.nextDouble() * 0.18);

                        outward = outward.add(0, yBias, 0);

                        sub.setDeltaMovement(finalInheritedBase.add(outward));

                        if (this.getOwner() != null)
                            sub.setOwner(this.getOwner());

                        level().addFreshEntity(sub);
                    };

                    for (int i = 0; i < outerCount; i++) {
                        double alpha;
                        double speed;

                        if (detonatedOnImpact) {

                            double max = Math.toRadians(28.0);
                            alpha = Math.sqrt(random.nextDouble()) * max;
                            speed = 0.75 + random.nextDouble() * 0.90;
                        } else {

                            double min = Math.toRadians(35.0);
                            double max = Math.toRadians(90.0);
                            double t = Math.sqrt(random.nextDouble());
                            alpha = min + (max - min) * t;
                            speed = 0.60 + random.nextDouble() * 1.05;
                        }

                        spawnOne.accept(alpha, speed);
                    }

                    for (int i = 0; i < innerCount; i++) {
                        double alpha;
                        double speed;

                        if (detonatedOnImpact) {

                            double max = Math.toRadians(22.0);
                            alpha = Math.pow(random.nextDouble(), 2.2) * max;
                            speed = 0.55 + random.nextDouble() * 0.65;
                        } else {

                            double min = Math.toRadians(35.0);
                            double max = Math.toRadians(90.0);
                            double t = Math.pow(random.nextDouble(), 2.0);
                            alpha = min + (max - min) * t;
                            speed = 0.50 + random.nextDouble() * 0.80;
                        }

                        spawnOne.accept(alpha, speed);
                    }
                }
            }
        }

    }
    private boolean hasBlastLineOfSight(Vec3 from, Vec3 to) {
        Level level = this.level();

        Vec3 dir = to.subtract(from);
        double len = dir.length();
        if (len < 1e-6) return true;

        Vec3 step = dir.scale(1.0 / len);

        double stepSize = 0.3;
        int steps = (int) Math.ceil(len / stepSize);

        Vec3 p = from;

        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        BlockPos lastPos = null;

        for (int i = 0; i <= steps; i++) {
            mp.set(p.x, p.y, p.z);

            if (lastPos == null || !mp.equals(lastPos)) {
                lastPos = mp.immutable();

                BlockState state = level.getBlockState(mp);

                if (!state.isAir() && !state.is(ModTags.Blocks.BLAST_TRANSPARENT)) {

                    return false;
                }
            }

            p = p.add(step.scale(stepSize));
        }

        return true;
    }

    public boolean canLingerInGround() {
        boolean var10000;
        if (!this.level().isClientSide && this.level().isLoaded(this.blockPosition())) {
            Item var2 = this.fuze.getItem();
            if (var2 instanceof FuzeItem) {
                FuzeItem fuzeItem = (FuzeItem)var2;
                if (fuzeItem.canLingerInGround(this.fuze, this)) {
                    var10000 = true;
                    return var10000;
                }
            }
        }

        var10000 = false;
        return var10000;
    }

    public void setExplosionCountdown(int value) {
        this.explosionCountdown = Math.max(value, -1);
    }

    public int getExplosionCountdown() {
        return this.explosionCountdown;
    }

    public Direction getFacing() {
        return this.getState().getValue(BlockStateProperties.HORIZONTAL_FACING);
    }
    private static final EntityDataAccessor<CompoundTag> PAYLOAD_FLUID =
            SynchedEntityData.defineId(AerialBombProjectile.class, EntityDataSerializers.COMPOUND_TAG);

    public void setPayloadFluid(FluidStack stack) {
        CompoundTag tag = new CompoundTag();
        if (!stack.isEmpty())
            tag = (CompoundTag) stack.saveOptional(this.registryAccess());
        this.entityData.set(PAYLOAD_FLUID, tag);
    }

    public FluidStack getPayloadFluid() {
        CompoundTag tag = this.entityData.get(PAYLOAD_FLUID);
        return FluidStack.parseOptional(this.registryAccess(), tag);
    }

    private void spillFluid(Position position, FluidStack payload) {
        Level level = this.level();
        if (level.isClientSide) return;
        if (payload.isEmpty()) return;

        Fluid fluid = payload.getFluid();
        int totalMb = payload.getAmount();

        int radius = 20 / size;
        int attempts = Math.min(240, 30 + totalMb / 40);

        int remainingSources = Math.max(1, Math.min(16, totalMb / 1000));

        BlockState placeState = getPlaceableFluidBlockState(fluid);
        if (placeState == null) {
            return;
        }

        BlockPos center = BlockPos.containing(position);
        RandomSource random = level.getRandom();

        for (int i = 0; i < attempts && remainingSources > 0; i++) {

            int ox, oz;
            int tries = 0;
            do {
                ox = random.nextInt(radius * 2 + 1) - radius;
                oz = random.nextInt(radius * 2 + 1) - radius;
                tries++;

            } while (tries < 8 && (ox * ox + oz * oz) > radius * radius);

            if ((ox * ox + oz * oz) > radius * radius) continue;

            BlockPos start = center.offset(ox, 8, oz);

            BlockPos ground = null;
            for (int y = 0; y < 20; y++) {
                BlockPos below = start.below(y);
                if (level.getBlockState(below).isCollisionShapeFullBlock(level, below)) {
                    ground = below;
                    break;
                }
            }
            if (ground == null) continue;

            BlockPos placePos = ground.above();
            if (!level.getBlockState(placePos).canBeReplaced()) continue;
            if (!placeState.canSurvive(level, placePos)) continue;

            level.setBlock(placePos, placeState, 3);

            FluidState fs = level.getFluidState(placePos);
            if (!fs.isEmpty() && fs.getType() == fluid) {
                remainingSources--;
            }
        }

        if (fluid == Fluids.LAVA || fluid == Fluids.FLOWING_LAVA) {
            int fireAttempts = 500 / size;
            for (int i = 0; i < fireAttempts; i++) {
                BlockPos p = center.offset(
                        random.nextInt(radius * 2 + 1) - radius,
                        0,
                        random.nextInt(radius * 2 + 1) - radius
                );
                if (level.isEmptyBlock(p) && Blocks.FIRE.defaultBlockState().canSurvive(level, p)) {
                    level.setBlock(p, Blocks.FIRE.defaultBlockState(), 3);
                }
            }
        }
    }

    @Nullable
    private static BlockState getPlaceableFluidBlockState(Fluid fluid) {
        BlockState legacy = fluid.defaultFluidState().createLegacyBlock();
        if (legacy.getBlock() != Blocks.AIR) {
            return legacy;
        }

        for (Block b : BuiltInRegistries.BLOCK) {
            if (b instanceof LiquidBlock lb && lb.fluid == fluid) {
                return b.defaultBlockState();
            }
        }

        return null;
    }

    private void spawnCutterBomblets(Vec3 origin, int size) {
        if (level().isClientSide) return;

        var random = this.level().random;

        int count = 60 + random.nextInt(25);
        int maxTravelBlocks = 35 + random.nextInt(25);

        Vec3 inherited = this.getDeltaMovement().scale(0.25);
        double maxInherit = 0.8;
        if (inherited.length() > maxInherit)
            inherited = inherited.normalize().scale(maxInherit);
        for (int i = 0; i < count; i++) {
            ClusterBombletProjectile sub = ModProjectiles.CLUSTER_BOMBLET.get().create(level());
            if (sub == null) continue;

            sub.configureCutter(ItemStack.EMPTY, size, maxTravelBlocks);

            sub.setPos(origin.x, origin.y + 1.5, origin.z);
            double theta = random.nextDouble() * Math.PI * 2.0;

            double alpha = Math.toRadians(25.0) + (Math.toRadians(90.0) - Math.toRadians(25.0)) * Math.sqrt(random.nextDouble());

            Vec3 dir = new Vec3(
                    Math.sin(alpha) * Math.cos(theta),
                    Math.cos(alpha),
                    Math.sin(alpha) * Math.sin(theta)
            );

            double speed = 0.65 + random.nextDouble() * 1.2;
            Vec3 outward = dir.scale(speed);

            outward = outward.add(0, 0.08 + random.nextDouble() * 0.18, 0);

            sub.setDeltaMovement(inherited.add(outward));

            if (this.getOwner() != null)
                sub.setOwner(this.getOwner());

            level().addFreshEntity(sub);
        }
    }

}
