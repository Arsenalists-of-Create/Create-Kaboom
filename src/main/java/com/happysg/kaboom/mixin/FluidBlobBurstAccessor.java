package com.happysg.kaboom.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import rbasamoyai.createbigcannons.munitions.big_cannon.fluid_shell.FluidBlobBurst;

@Mixin(FluidBlobBurst.class)
public interface FluidBlobBurstAccessor {
    @Invoker(value = "setBlobSize", remap = false)
    void createKaboom$setBlobSize(byte blobSize);
}
