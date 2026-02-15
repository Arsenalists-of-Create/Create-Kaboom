package com.happysg.kaboom.block.aerialBombs.cluster;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import rbasamoyai.createbigcannons.CreateBigCannons;
import rbasamoyai.createbigcannons.block_armor_properties.BlockArmorPropertiesHandler;
import rbasamoyai.createbigcannons.block_armor_properties.BlockArmorPropertiesProvider;
import rbasamoyai.createbigcannons.config.CBCCfgMunitions;
import rbasamoyai.createbigcannons.config.CBCConfigs;
import rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile;
import rbasamoyai.createbigcannons.munitions.ImpactExplosion;
import rbasamoyai.createbigcannons.munitions.ProjectileContext;
import rbasamoyai.createbigcannons.munitions.ShellExplosion;
import rbasamoyai.createbigcannons.munitions.big_cannon.ProjectileBlock;
import rbasamoyai.createbigcannons.munitions.big_cannon.config.BigCannonFuzePropertiesComponent;
import rbasamoyai.createbigcannons.munitions.config.components.BallisticPropertiesComponent;
import rbasamoyai.createbigcannons.munitions.config.components.EntityDamagePropertiesComponent;
import rbasamoyai.createbigcannons.munitions.fuzes.FuzeItem;
import rbasamoyai.createbigcannons.network.ClientboundPlayBlockHitEffectPacket;
import rbasamoyai.createbigcannons.utils.CBCUtils;

import javax.annotation.Nonnull;
import java.util.function.Predicate;

public class ClusterBombletProjectile extends AbstractCannonProjectile {

    // Lighter, “bomblet-ish” ballistics: slightly less gravity than your big bomb
    public static final BallisticPropertiesComponent BALLISTICS =
            new BallisticPropertiesComponent(
                    -0.05,   // gravity-ish (CBC uses its own model; you’ve already used negatives)
                    0.01,    // drag
                    false,   // can penetrate fluids etc (depends on CBC internals)
                    0.35f,   // toughness
                    1, 1,
                    0.80f    // restitution / bounce-ish
            );

    public static final EntityDamagePropertiesComponent DAMAGE =
            new EntityDamagePropertiesComponent(10, false, true, true, 1);

    private static final EntityDataAccessor<Integer> LIFE =
            SynchedEntityData.defineId(ClusterBombletProjectile.class, EntityDataSerializers.INT);

    private ItemStack fuze = ItemStack.EMPTY;

    // airburst timer (ticks); -1 means disabled
    private int explosionCountdown = -1;

    // configuration knobs
    private int size = 1;
    private float explosionPower = 1;
    private boolean causesFire = false;

    public ClusterBombletProjectile(EntityType<? extends AbstractCannonProjectile> type, Level level) {
        super(type, level);
    }

    // --- Quick setup from your cluster bomb ---
    public ClusterBombletProjectile configure(ItemStack fuzeStack, int size, float explosionPower, boolean causesFire, int airburstTicks) {
        setFuzeStack(fuzeStack);
        this.size = Math.max(1, size);
        this.explosionPower = explosionPower;
        this.causesFire = causesFire;
        this.explosionCountdown = airburstTicks <= 0 ? -1 : airburstTicks;
        return this;
    }

    @Override
    public @NotNull EntityDamagePropertiesComponent getDamageProperties() {
        return DAMAGE;
    }

    @Override
    protected @NotNull BallisticPropertiesComponent getBallisticProperties() {
        return BALLISTICS;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(LIFE, 0);
    }

    @Override
    public void tick() {
        super.tick();

        // --- client-only smoke trail ---
        if (level().isClientSide) {
            Vec3 v = getDeltaMovement();
            double speedSqr = v.lengthSqr();

            // only trail if moving a bit
            if (speedSqr > 0.01) {
                // spawn slightly behind the projectile
                Vec3 back = v.normalize().scale(-0.15);
                double px = getX() + back.x;
                double py = getY() + back.y;
                double pz = getZ() + back.z;

                // nice “wispy” smoke
                level().addParticle(
                        net.minecraft.core.particles.ParticleTypes.CAMPFIRE_COSY_SMOKE, true,
                        px, py, pz,
                        0.0, 0.01, 0.0
                );

                // occasional puff (looks more chaotic)
                if (random.nextInt(4) == 0) {
                    level().addParticle(
                            net.minecraft.core.particles.ParticleTypes.CAMPFIRE_COSY_SMOKE,
                            px, py, pz,
                            0.0, 0.02, 0.0
                    );
                }
            }
            return; // don’t run server logic on client
        }
        super.tick();

        if (level().isClientSide) return;

        entityData.set(LIFE, entityData.get(LIFE) + 1);

        // hard safety kill (10s)
        if (entityData.get(LIFE) > 400) {
            detonate(position());
            removeNextTick = true;
            return;
        }


        // --- server-side safety kill only (does NOT explode) ---
        int life = this.entityData.get(LIFE) + 1;
        this.entityData.set(LIFE, life);
        if (life > 400) {
            this.discard();
            this.removeNextTick = true;
        }
    }

    @Override
    protected boolean onImpact(HitResult hitResult, ImpactResult impactResult, ProjectileContext projectileContext) {
        super.onImpact(hitResult, impactResult, projectileContext);
        detonate(hitResult.getLocation());
        return true;
    }

    @Override
    protected boolean onClip(ProjectileContext ctx, Vec3 start, Vec3 end) {
        return super.onClip(ctx, start, end);
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
        double velMag = curVel.length();
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
                ImpactExplosion explosion = new ImpactExplosion(this.level(), this, this.indirectArtilleryFire(false), spallLoc.x, spallLoc.y, spallLoc.z, 2, Level.ExplosionInteraction.NONE);
                CreateBigCannons.handleCustomExplosion(this.level(), explosion);
            }
            SoundType sound = state.getSoundType();
            if (!this.level().isClientSide)
                this.level().playSound(null, spallLoc.x, spallLoc.y, spallLoc.z, sound.getBreakSound(), SoundSource.BLOCKS,
                        sound.getVolume(), sound.getPitch());
        }
        shatter |= this.onImpact(blockHitResult, new ImpactResult(outcome, shatter), projectileContext);
        return new ImpactResult(outcome, shatter);
    }

    // Keep this consistent with your AerialBombProjectile
    @Nonnull
    protected BigCannonFuzePropertiesComponent getFuzeProperties() {
        return new BigCannonFuzePropertiesComponent(false);
    }

    // --- CBC ShellExplosion detonation (the important part) ---
    protected void detonate(Position pos) {
        if (level().isClientSide) return;

        float pwr = explosionPower / Math.max(1, size);

        ShellExplosion explosion = new ShellExplosion(
                level(),
                this,
                this.indirectArtilleryFire(false),
                pos.x(), pos.y(), pos.z(),
                pwr,
                causesFire,
                CBCConfigs.server().munitions.damageRestriction.get().explosiveInteraction()
        );

        CreateBigCannons.handleCustomExplosion(level(), explosion);
    }

    // --- Fuze + countdown API ---
    public void setFuzeStack(ItemStack stack) {
        this.fuze = (stack == null || stack.isEmpty()) ? ItemStack.EMPTY : stack.copy();
        // don’t force-reset countdown here; configure() decides
    }

    public void setExplosionCountdown(int ticks) {
        this.explosionCountdown = Math.max(ticks, -1);
    }

    protected final boolean canDetonate(Predicate<FuzeItem> cons) {
        if (!level().isClientSide && level().hasChunkAt(blockPosition()) && !isRemoved()) {
            Item item = fuze.getItem();
            if (item instanceof FuzeItem fuzeItem) {
                return cons.test(fuzeItem);
            }
        }
        return false;
    }

    // --- NBT ---
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);

        tag.put("Fuze", fuze.save(new CompoundTag()));
        if (explosionCountdown >= 0) tag.putInt("ExplosionCountdown", explosionCountdown);

        tag.putInt("Size", size);
        tag.putFloat("ExplosionPower", explosionPower);
        tag.putBoolean("CausesFire", causesFire);

        tag.putInt("Life", entityData.get(LIFE));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);

        fuze = tag.contains("Fuze", Tag.TAG_COMPOUND) ? ItemStack.of(tag.getCompound("Fuze")) : ItemStack.EMPTY;
        explosionCountdown = tag.contains("ExplosionCountdown", Tag.TAG_INT) ? tag.getInt("ExplosionCountdown") : -1;

        size = Math.max(1, tag.getInt("Size"));
        explosionPower = tag.contains("ExplosionPower", Tag.TAG_FLOAT) ? tag.getFloat("ExplosionPower") : 3.0f;
        causesFire = tag.getBoolean("CausesFire");

        if (tag.contains("Life", Tag.TAG_INT)) entityData.set(LIFE, tag.getInt("Life"));
    }


}