package com.happysg.kaboom.mixin;

import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile;
import rbasamoyai.createbigcannons.munitions.ProjectileContext;
import rbasamoyai.createbigcannons.munitions.config.components.BallisticPropertiesComponent;

@Mixin(AbstractCannonProjectile.class)
public interface AbstractProjectileAccessor {

    @Invoker("getBallisticProperties")
    BallisticPropertiesComponent invokeGetBallisticProperties();
    @Invoker(value = "onImpact", remap = false)
    boolean invokeImpact(HitResult hitResult, AbstractCannonProjectile.ImpactResult impactResult, ProjectileContext projectileContext);
}
