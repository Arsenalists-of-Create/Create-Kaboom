package com.happysg.kaboom.block.aerialBombs;

import com.happysg.kaboom.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import rbasamoyai.createbigcannons.CreateBigCannons;
import rbasamoyai.createbigcannons.block_armor_properties.BlockArmorPropertiesHandler;
import rbasamoyai.createbigcannons.block_armor_properties.BlockArmorPropertiesProvider;
import rbasamoyai.createbigcannons.config.CBCCfgMunitions;
import rbasamoyai.createbigcannons.config.CBCConfigs;
import rbasamoyai.createbigcannons.index.CBCBlocks;
import rbasamoyai.createbigcannons.index.CBCMunitionPropertiesHandlers;
import rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile;
import rbasamoyai.createbigcannons.munitions.ImpactExplosion;
import rbasamoyai.createbigcannons.munitions.ProjectileContext;
import rbasamoyai.createbigcannons.munitions.ShellExplosion;
import rbasamoyai.createbigcannons.munitions.big_cannon.FuzedBigCannonProjectile;
import rbasamoyai.createbigcannons.munitions.big_cannon.ProjectileBlock;
import rbasamoyai.createbigcannons.munitions.big_cannon.config.BigCannonCommonShellProperties;
import rbasamoyai.createbigcannons.munitions.big_cannon.config.BigCannonFuzePropertiesComponent;
import rbasamoyai.createbigcannons.munitions.big_cannon.config.BigCannonProjectilePropertiesComponent;
import rbasamoyai.createbigcannons.munitions.config.components.BallisticPropertiesComponent;
import rbasamoyai.createbigcannons.munitions.config.components.EntityDamagePropertiesComponent;
import rbasamoyai.createbigcannons.munitions.fuzes.FuzeItem;
import rbasamoyai.createbigcannons.network.ClientboundPlayBlockHitEffectPacket;
import rbasamoyai.createbigcannons.utils.CBCUtils;

import javax.annotation.Nonnull;
import java.util.function.Predicate;

public class AerialBombProjectile extends AbstractCannonProjectile {

    public static final BallisticPropertiesComponent BALLISTIC_PROPERTIES = new BallisticPropertiesComponent(-0.1,.01,false,2.0f,1,1,0.70f);
    public static final EntityDamagePropertiesComponent DAMAGE_PROPERTIES = new EntityDamagePropertiesComponent(30,false,true,true,2);

    protected static final EntityDataAccessor<Integer> TIME_REQUIRED = SynchedEntityData.defineId(AerialBombProjectile.class, EntityDataSerializers.INT);
    protected static final EntityDataAccessor<Integer> TIME = SynchedEntityData.defineId(AerialBombProjectile.class, EntityDataSerializers.INT);
    protected static final EntityDataAccessor<BlockState> STATE = SynchedEntityData.defineId(AerialBombProjectile.class, EntityDataSerializers.BLOCK_STATE);

    private ItemStack fuze;
    private int explosionCountdown;

    public AerialBombProjectile(EntityType<? extends AbstractCannonProjectile> type, Level level) {
        super(type, level);
        this.fuze = ItemStack.EMPTY;
        this.explosionCountdown = -1;
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
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(TIME, 0);
        this.entityData.define(TIME_REQUIRED, 10);
        this.entityData.define(STATE, ModBlocks.HEAVY_AERIAL_BOMB.getDefaultState());
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

    public void tick() {
        super.tick();

        if (!this.level().isClientSide && this.explosionCountdown > 0) {
            --this.explosionCountdown;
        }


        if(!level().isClientSide) {
            this.entityData.set(TIME, this.entityData.get(TIME) + 1);
        }

        if (this.canDetonate((fz) -> fz.onProjectileTick(this.fuze, this)) || !this.level().isClientSide && this.explosionCountdown == 0) {
            this.detonate(this.position());
            this.removeNextTick = true;
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
        double velMag = curVel.length();
        double mass = this.getProjectileMass();
        double bonusMomentum = 1 + Math.max(0, (velMag - CBCConfigs.SERVER.munitions.minVelocityForPenetrationBonus.getF())
                * CBCConfigs.SERVER.munitions.penetrationBonusScale.getF());
        double incidentVel = velMag * incidence;
        double momentum = mass * incidentVel * bonusMomentum;

        double toughness = blockArmor.toughness(this.level(), state, pos, true);
        double toughnessPenalty = toughness - momentum;
        double hardnessPenalty = blockArmor.hardness(this.level(), state, pos, true) - ballistics.penetration();
        double bounceBonus = Math.max(1 - hardnessPenalty, 0);

        double projectileDeflection = ballistics.deflection();
        double baseChance = CBCConfigs.SERVER.munitions.baseProjectileBounceChance.getF();
        double bounceChance = projectileDeflection < 1e-2d || incidence > projectileDeflection ? 0 : Math.max(baseChance, 1 - incidence / projectileDeflection) * bounceBonus;

        boolean surfaceImpact = this.canHitSurface();
        boolean canBounce = CBCConfigs.SERVER.munitions.projectilesCanBounce.get();
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
        if (this.canDetonate((fz) -> fz.onProjectileImpact(this.fuze, this, hitResult, impactResult, baseFuze))) {
            this.detonate(hitResult.getLocation());
            return true;
        } else {
            return false;
        }
    }

    @Nonnull
    protected BigCannonFuzePropertiesComponent getFuzeProperties(){
        return new BigCannonFuzePropertiesComponent(false);
    }

    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("Fuze", this.fuze.save(new CompoundTag()));
        if (this.explosionCountdown >= 0) {
            tag.putInt("ExplosionCountdown", this.explosionCountdown);
        }

    }

    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.fuze = ItemStack.of(tag.getCompound("Fuze"));
        this.explosionCountdown = tag.contains("ExplosionCountdown", 3) ? tag.getInt("ExplosionCountdown") : -1;
    }

    protected final boolean canDetonate(Predicate<FuzeItem> cons) {
        boolean var10000;
        if (!this.level().isClientSide && this.level().hasChunkAt(this.blockPosition()) && !this.isRemoved()) {
            Item var3 = this.fuze.getItem();
            if (var3 instanceof FuzeItem) {
                FuzeItem fuzeItem = (FuzeItem)var3;
                if (cons.test(fuzeItem)) {
                    var10000 = true;
                    return var10000;
                }
            }
        }

        var10000 = false;
        return var10000;
    }

    /** @deprecated */
    @Deprecated
    protected void detonate() {
        this.detonate(this.position());
    }

    protected void detonate(Position position) {
        ShellExplosion explosion = new ShellExplosion(this.level(), this, this.indirectArtilleryFire(false), position.x(), position.y(), position.z(), 3, false, ((CBCCfgMunitions.GriefState)CBCConfigs.SERVER.munitions.damageRestriction.get()).explosiveInteraction());
        CreateBigCannons.handleCustomExplosion(this.level(), explosion);
    }

    public boolean canLingerInGround() {
        boolean var10000;
        if (!this.level().isClientSide && this.level().hasChunkAt(this.blockPosition())) {
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
}
