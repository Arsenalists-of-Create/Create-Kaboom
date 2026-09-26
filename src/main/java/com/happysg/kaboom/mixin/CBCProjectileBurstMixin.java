package com.happysg.kaboom.mixin;

import com.happysg.kaboom.interception.InterceptableOrdnance;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import rbasamoyai.createbigcannons.munitions.big_cannon.shrapnel.ShrapnelBurst;
import rbasamoyai.createbigcannons.munitions.fragment_burst.CBCProjectileBurst;

@Mixin(CBCProjectileBurst.class)
public abstract class CBCProjectileBurstMixin {
    @Inject(method = "canHitEntity", at = @At("HEAD"), cancellable = true, remap = false)
    private void createKaboom$allowShrapnelToHitInterceptableOrdnance(Entity target,
                                                                      CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ShrapnelBurst
                && target instanceof InterceptableOrdnance
                && target.isAlive()) {
            cir.setReturnValue(true);
        }
    }
}
