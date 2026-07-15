package com.happysg.kaboom.block.missiles.parts.warhead;

import com.happysg.kaboom.block.aerialBombs.baseTypes.AerialBombProjectile;
import com.happysg.kaboom.mixin.FuzeMixin;
import com.happysg.kaboom.registry.ModProjectiles;
import net.minecraft.core.Position;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.fluids.FluidStack;
import rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile.ImpactResult;
import rbasamoyai.createbigcannons.munitions.ProjectileContext;
import rbasamoyai.createbigcannons.munitions.big_cannon.FuzedBigCannonProjectile;
import rbasamoyai.createbigcannons.munitions.big_cannon.config.BigCannonFuzePropertiesComponent;
import rbasamoyai.createbigcannons.munitions.big_cannon.config.BigCannonProjectilePropertiesComponent;
import rbasamoyai.createbigcannons.munitions.config.components.BallisticPropertiesComponent;
import rbasamoyai.createbigcannons.munitions.config.components.EntityDamagePropertiesComponent;

public class MissileWarheadProjectile extends FuzedBigCannonProjectile {
    private static final EntityDataAccessor<BlockState> WARHEAD_STATE =
            SynchedEntityData.defineId(MissileWarheadProjectile.class, EntityDataSerializers.BLOCK_STATE);
    private static final EntityDataAccessor<Integer> BOMB_TYPE =
            SynchedEntityData.defineId(MissileWarheadProjectile.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> BOMB_SIZE =
            SynchedEntityData.defineId(MissileWarheadProjectile.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<CompoundTag> PAYLOAD_FLUID =
            SynchedEntityData.defineId(MissileWarheadProjectile.class, EntityDataSerializers.COMPOUND_TAG);

    private boolean processingImpact;
    private boolean nextDetonationIsImpact;

    public MissileWarheadProjectile(EntityType<? extends MissileWarheadProjectile> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(WARHEAD_STATE, Blocks.IRON_BLOCK.defaultBlockState());
        builder.define(BOMB_TYPE, AerialBombProjectile.BombType.HE.ordinal());
        builder.define(BOMB_SIZE, 2);
        builder.define(PAYLOAD_FLUID, new CompoundTag());
    }

    public void configure(BlockState state, AerialBombProjectile.BombType bombType, int bombSize, FluidStack payload) {
        this.entityData.set(WARHEAD_STATE, state);
        this.entityData.set(BOMB_TYPE, bombType.ordinal());
        this.entityData.set(BOMB_SIZE, Math.max(1, bombSize));

        CompoundTag payloadTag = new CompoundTag();
        if (payload != null && !payload.isEmpty()) {
            payloadTag = (CompoundTag) payload.saveOptional(this.registryAccess());
        }
        this.entityData.set(PAYLOAD_FLUID, payloadTag);
    }

    public void markNextDetonationAsImpact() {
        this.nextDetonationIsImpact = true;
    }

    private AerialBombProjectile.BombType getBombType() {
        AerialBombProjectile.BombType[] values = AerialBombProjectile.BombType.values();
        int ordinal = this.entityData.get(BOMB_TYPE);
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : AerialBombProjectile.BombType.HE;
    }

    private FluidStack getPayloadFluid() {
        return FluidStack.parseOptional(this.registryAccess(), this.entityData.get(PAYLOAD_FLUID));
    }

    @Override
    public BlockState getRenderedBlockState() {
        return this.entityData.get(WARHEAD_STATE);
    }

    @Override
    public EntityDamagePropertiesComponent getDamageProperties() {
        return AerialBombProjectile.DAMAGE_PROPERTIES;
    }

    @Override
    protected BallisticPropertiesComponent getBallisticProperties() {
        return AerialBombProjectile.BALLISTIC_PROPERTIES;
    }

    @Override
    protected BigCannonProjectilePropertiesComponent getBigCannonProjectileProperties() {
        return BigCannonProjectilePropertiesComponent.DEFAULT;
    }

    @Override
    protected BigCannonFuzePropertiesComponent getFuzeProperties() {
        return new BigCannonFuzePropertiesComponent(false);
    }

    @Override
    protected boolean onImpact(HitResult hitResult, ImpactResult impactResult, ProjectileContext context) {
        this.processingImpact = true;
        try {
            return super.onImpact(hitResult, impactResult, context);
        } finally {
            this.processingImpact = false;
        }
    }

    @Override
    protected void detonate(Position position) {
        if (this.level().isClientSide) {
            return;
        }

        AerialBombProjectile payload = ModProjectiles.AERIAL_BOMB_PROJECTILE.create(this.level());
        if (payload == null) {
            return;
        }

        payload.setPos(position.x(), position.y(), position.z());
        payload.setDeltaMovement(this.getDeltaMovement());
        payload.setOwner(this.getOwner());
        payload.setBombType(getBombType());
        payload.setSize(this.entityData.get(BOMB_SIZE));
        payload.setPayloadFluid(getPayloadFluid());
        ItemStack fuze = ((FuzeMixin) (Object) this).getFuze();
        payload.setFuzeStack(fuze.isEmpty() ? ItemStack.EMPTY : fuze.copy());
        payload.detonateAsWarhead(position, this.processingImpact || this.nextDetonationIsImpact);
        this.discard();
    }
}
