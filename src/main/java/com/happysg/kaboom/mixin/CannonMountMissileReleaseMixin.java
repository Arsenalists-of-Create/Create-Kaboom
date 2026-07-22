package com.happysg.kaboom.mixin;

import com.happysg.kaboom.compat.cbc.MountedMissileController;
import com.simibubi.create.content.contraptions.AssemblyException;
import java.util.Objects;
import javax.annotation.Nullable;
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

    @Shadow(remap = false)
    private AssemblyException lastException;

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

    @Override
    public void createKaboom$setMissileDiagnostic(@Nullable AssemblyException diagnostic) {
        if (Objects.equals(component(this.lastException), component(diagnostic))) {
            return;
        }
        this.lastException = diagnostic;
        CannonMountBlockEntity self = (CannonMountBlockEntity) (Object) this;
        self.setChanged();
        self.sendData();
    }

    private static Object component(@Nullable AssemblyException exception) {
        return exception == null ? null : exception.component;
    }
}
