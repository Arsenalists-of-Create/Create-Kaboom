package com.happysg.kaboom.mixin;

import com.happysg.kaboom.block.rocketpod.RocketPodContraption;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import rbasamoyai.createbigcannons.cannon_control.contraption.AbstractMountedCannonContraption;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;
import rbasamoyai.createbigcannons.cannons.big_cannons.breeches.quickfiring_breech.CannonMountPoint;

@Mixin(CannonMountPoint.class)
public abstract class RocketPodCannonMountPointMixin {
    @Inject(method = "getInsertedResultAndDoSomething", at = @At("HEAD"), cancellable = true, remap = false)
    private void createKaboom$insertRocket(ItemStack stack, boolean simulate,
                                           AbstractMountedCannonContraption cannon,
                                           PitchOrientedContraptionEntity entity,
                                           CallbackInfoReturnable<ItemStack> callback) {
        if (cannon instanceof RocketPodContraption rocketPod) {
            callback.setReturnValue(rocketPod.insertRocket(stack, simulate, entity));
        }
    }
}
