package com.happysg.kaboom.mixin;

import com.happysg.kaboom.block.aerialBombs.baseTypes.AerialBombProjectile;
import net.minecraft.world.level.ClipContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile;

@Mixin(AbstractCannonProjectile.class)
public abstract class AbstractCannonProjectileMixin {
    @ModifyArg(
            method = "clipAndDamage",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;clip(Lnet/minecraft/world/level/ClipContext;)Lnet/minecraft/world/phys/BlockHitResult;"
            ),
            index = 0,
            require = 2,
            remap = false
    )
    private ClipContext createKaboom$configureBombLaunchCollision(ClipContext context) {
        return (Object) this instanceof AerialBombProjectile bomb
                ? bomb.configureLaunchCollisionContext(context)
                : context;
    }
}
