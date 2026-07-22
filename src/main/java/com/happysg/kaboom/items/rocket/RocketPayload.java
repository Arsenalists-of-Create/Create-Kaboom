package com.happysg.kaboom.items.rocket;

import com.happysg.kaboom.mixin.FluidBlobBurstAccessor;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import rbasamoyai.createbigcannons.CreateBigCannons;
import rbasamoyai.createbigcannons.config.CBCConfigs;
import rbasamoyai.createbigcannons.index.CBCEntityTypes;
import rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile;
import rbasamoyai.createbigcannons.munitions.ShellExplosion;
import rbasamoyai.createbigcannons.munitions.big_cannon.fluid_shell.EndFluidStack;
import rbasamoyai.createbigcannons.munitions.big_cannon.fluid_shell.FluidBlobBurst;
import rbasamoyai.createbigcannons.munitions.big_cannon.fluid_shell.FluidExplosion;
import rbasamoyai.createbigcannons.munitions.big_cannon.shrapnel.ShrapnelExplosion;
import rbasamoyai.createbigcannons.munitions.big_cannon.smoke_shell.SmokeEmitterEntity;
import rbasamoyai.createbigcannons.munitions.big_cannon.smoke_shell.SmokeExplosion;
import rbasamoyai.createbigcannons.munitions.config.components.BallisticPropertiesComponent;
import rbasamoyai.createbigcannons.munitions.fragment_burst.CBCProjectileBurst;

import java.util.Objects;
import java.util.function.IntFunction;

public enum RocketPayload implements StringRepresentable {
    HE("he") {
        @Override
        protected void detonatePayload(DetonationContext context) {
            detonateShell(context, 2.0f, 3.0f);
        }
    },
    AP("ap") {
        @Override
        protected void detonatePayload(DetonationContext context) {
            detonateShell(context, 1.25f, 1.75f);
        }
    },
    SHRAPNEL("shrapnel") {
        @Override
        protected void detonatePayload(DetonationContext context) {
            Vec3 position = context.position();
            ShrapnelExplosion explosion = new ShrapnelExplosion(
                    context.level(),
                    null,
                    context.damageSource(),
                    position.x,
                    position.y,
                    position.z,
                    0.5f,
                    0.75f,
                    explosiveInteraction()
            );
            CreateBigCannons.handleCustomExplosion(context.level(), explosion);
            CBCProjectileBurst.spawnConeBurst(
                    context.level(),
                    CBCEntityTypes.SHRAPNEL_BURST.get(),
                    position,
                    context.velocity(),
                    13,
                    0.6
            );
        }
    },
    SMOKE("smoke") {
        @Override
        protected void detonatePayload(DetonationContext context) {
            Vec3 position = context.position();
            SmokeExplosion explosion = new SmokeExplosion(
                    context.level(),
                    null,
                    position.x,
                    position.y,
                    position.z,
                    0.0f,
                    0.5f,
                    Explosion.BlockInteraction.KEEP
            );
            CreateBigCannons.handleCustomExplosion(context.level(), explosion);

            SmokeEmitterEntity emitter = CBCEntityTypes.SMOKE_EMITTER.create(context.level());
            if (emitter == null) {
                return;
            }
            emitter.setPos(position);
            emitter.setSize(2.5f);
            emitter.setDuration(300);
            context.level().addFreshEntity(emitter);
        }
    },
    FLUID("fluid") {
        @Override
        protected void detonatePayload(DetonationContext context) {
            FluidStack fluid = context.fluidContent();
            Vec3 position = context.position();
            FluidExplosion explosion = new FluidExplosion(
                    context.level(),
                    null,
                    context.damageSource(),
                    position.x,
                    position.y,
                    position.z,
                    0.5f,
                    0.75f,
                    explosiveInteraction(),
                    fluid.getFluid()
            );
            CreateBigCannons.handleCustomExplosion(context.level(), explosion);
            if (fluid.isEmpty()) {
                return;
            }

            int amountPerBlob = RocketFluidHandler.CAPACITY_MB;
            int blobCount = Math.max(1, (int) Math.ceil((double) fluid.getAmount() / amountPerBlob));
            FluidBlobBurst burst = CBCProjectileBurst.spawnConeBurst(
                    context.level(),
                    CBCEntityTypes.FLUID_BLOB_BURST.get(),
                    position,
                    context.velocity(),
                    blobCount,
                    1.0
            );
            int blobAmount = Math.min(amountPerBlob, fluid.getAmount());
            EndFluidStack endFluid = new EndFluidStack(
                    fluid.getFluid(),
                    fluid.getAmount(),
                    fluid.getComponentsPatch()
            );
            burst.setFluidStack(endFluid.copy(blobAmount));
            byte blobSize = (byte) Math.max(1, (int) Math.ceil(blobAmount / 125.0));
            ((FluidBlobBurstAccessor) burst).createKaboom$setBlobSize(blobSize);
        }
    };

    public static final Codec<RocketPayload> CODEC = StringRepresentable.fromEnum(RocketPayload::values);
    public static final BallisticPropertiesComponent AP_BALLISTIC_PROPERTIES =
            new BallisticPropertiesComponent(-0.08, 0.0, false, 1.5f, 2.0f, 0.5f, 0.7f);
    private static final IntFunction<RocketPayload> BY_ID = ByIdMap.continuous(
            RocketPayload::getId,
            values(),
            ByIdMap.OutOfBoundsStrategy.ZERO
    );
    public static final StreamCodec<ByteBuf, RocketPayload> STREAM_CODEC =
            ByteBufCodecs.idMapper(BY_ID, RocketPayload::getId);

    private final String serializedName;

    RocketPayload(String serializedName) {
        this.serializedName = serializedName;
    }

    public int getId() {
        return ordinal();
    }

    public static RocketPayload byId(int id) {
        return BY_ID.apply(id);
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    public String getTranslationKey() {
        return "item.create_kaboom.rocket.payload." + serializedName;
    }

    public Component getDisplayName() {
        return Component.translatable(getTranslationKey());
    }


    public boolean isArmorPiercing() {
        return this == AP;
    }

    public final void detonate(AbstractCannonProjectile source, Vec3 position) {
        detonate(new DetonationContext(source, position));
    }

    public final void detonate(DetonationContext context) {
        Objects.requireNonNull(context, "context");
        if (context.level().isClientSide) {
            return;
        }
        detonatePayload(context);
    }

    protected abstract void detonatePayload(DetonationContext context);

    private static void detonateShell(DetonationContext context, float blockPower, float entityPower) {
        Vec3 position = context.position();
        ShellExplosion explosion = new ShellExplosion(
                context.level(),
                context.source(),
                context.damageSource(),
                position.x,
                position.y,
                position.z,
                blockPower,
                entityPower,
                false,
                explosiveInteraction()
        );
        CreateBigCannons.handleCustomExplosion(context.level(), explosion);
    }

    private static Explosion.BlockInteraction explosiveInteraction() {
        return CBCConfigs.server().munitions.damageRestriction.get().explosiveInteraction();
    }

    public record DetonationContext(AbstractCannonProjectile source, Vec3 position) {
        public DetonationContext {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(position, "position");
        }

        public Level level() {
            return source.level();
        }

        public DamageSource damageSource() {
            return source.indirectArtilleryFire(false);
        }

        public Vec3 velocity() {
            return source.getDeltaMovement();
        }

        public FluidStack fluidContent() {
            if (source instanceof UnguidedRocketProjectile rocket) {
                return RocketItem.getFluidContent(rocket.getRocketStack());
            }
            return FluidStack.EMPTY;
        }
    }
}
