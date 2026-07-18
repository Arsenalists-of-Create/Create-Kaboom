package com.happysg.kaboom.mixin;

import com.happysg.kaboom.compat.cbc.MountedMissileController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;

@Mixin(CannonMountBlockEntity.class)
public abstract class CannonMountMissileReleaseMixin implements MountedMissileController {
    @Shadow(remap = false)
    protected PitchOrientedContraptionEntity mountedContraption;

    @Shadow(remap = false)
    private boolean running;

    @Override
    public boolean createKaboom$releaseMountedMissile(PitchOrientedContraptionEntity entity) {
        if (this.mountedContraption != entity) {
            return false;
        }
        this.mountedContraption = null;
        this.running = false;
        CannonMountBlockEntity self = (CannonMountBlockEntity) (Object) this;
        self.setChanged();
        self.sendData();
        return true;
    }
}
