package com.happysg.kaboom.block.missiles.assembly;

import net.minecraft.world.level.Level;

public class MissileContraptionBuilder {

    public static MissileContraption build(Level level, MissileAssemblyResult result) {
        MissileContraption c = new MissileContraption();
        c.captureFromScan(level, result);
        return c;
    }
}