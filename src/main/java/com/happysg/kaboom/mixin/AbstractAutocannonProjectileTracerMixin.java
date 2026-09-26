package com.happysg.kaboom.mixin;

import com.happysg.kaboom.items.tracer.ColoredTracerProjectile;
import com.happysg.kaboom.items.tracer.TracerColor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rbasamoyai.createbigcannons.munitions.autocannon.AbstractAutocannonProjectile;

@Mixin(AbstractAutocannonProjectile.class)
public abstract class AbstractAutocannonProjectileTracerMixin implements ColoredTracerProjectile {
    @Unique
    private static final String CREATE_KABOOM_TRACER_COLOR_TAG = "CreateKaboomTracerColor";

    @Unique
    @Nullable
    private TracerColor createKaboom$tracerColor;

    @Override
    @Nullable
    public TracerColor createKaboom$getTracerColor() {
        return createKaboom$tracerColor;
    }

    @Override
    public void createKaboom$setTracerColor(@Nullable TracerColor color) {
        createKaboom$tracerColor = color;
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"), remap = false)
    private void createKaboom$saveTracerColor(CompoundTag tag, CallbackInfo ci) {
        if (createKaboom$tracerColor != null) {
            tag.putString(CREATE_KABOOM_TRACER_COLOR_TAG, createKaboom$tracerColor.getSerializedName());
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"), remap = false)
    private void createKaboom$loadTracerColor(CompoundTag tag, CallbackInfo ci) {
        createKaboom$tracerColor = tag.contains(CREATE_KABOOM_TRACER_COLOR_TAG)
                ? TracerColor.bySerializedName(tag.getString(CREATE_KABOOM_TRACER_COLOR_TAG))
                : null;
    }

    @Inject(method = "baseWriteSpawnData", at = @At("TAIL"), remap = false)
    private void createKaboom$writeTracerColor(RegistryFriendlyByteBuf buffer, CallbackInfo ci) {
        buffer.writeVarInt(createKaboom$tracerColor == null ? 0 : createKaboom$tracerColor.ordinal() + 1);
    }

    @Inject(method = "baseReadSpawnData", at = @At("TAIL"), remap = false)
    private void createKaboom$readTracerColor(RegistryFriendlyByteBuf buffer, CallbackInfo ci) {
        createKaboom$tracerColor = TracerColor.byNetworkId(buffer.readVarInt() - 1);
    }
}
