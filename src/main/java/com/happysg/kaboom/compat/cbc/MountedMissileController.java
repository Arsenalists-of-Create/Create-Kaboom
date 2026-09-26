package com.happysg.kaboom.compat.cbc;

import com.simibubi.create.content.contraptions.AssemblyException;
import javax.annotation.Nullable;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;

public interface MountedMissileController {
    boolean createKaboom$releaseMountedMissile(PitchOrientedContraptionEntity entity);

    void createKaboom$setMissileDiagnostic(@Nullable AssemblyException diagnostic);
}
