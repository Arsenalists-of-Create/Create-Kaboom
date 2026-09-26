package com.happysg.kaboom.mixin;

import com.happysg.kaboom.block.missiles.assembly.MissileAssemblyExceptionDisplay;
import org.spongepowered.asm.mixin.Mixin;
import rbasamoyai.createbigcannons.munitions.big_cannon.FuzedBlockEntity;

@Mixin(FuzedBlockEntity.class)
public abstract class FuzedBlockEntityMissileOverlayMixin implements MissileAssemblyExceptionDisplay {
}
