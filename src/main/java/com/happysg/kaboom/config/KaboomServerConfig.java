package com.happysg.kaboom.config;

import net.createmod.catnip.config.ConfigBase;

public class KaboomServerConfig extends ConfigBase {
    @Override
    public String getName() {
        return "Kaboom Server";
    }
    public final ConfigInt maxMissileSpeed = i(10,1,"maxMissileSpeed", "maxMissileSpeed");
    public final ConfigFloat maxMissileAccel =f(0.5f,0,"maxMissileAccel","maxMissileAccel");
    public final ConfigInt maxFuelBurnPerTick = i(1,1,"maxFuelBurnPerTick","maxFuelBurnPerTick");

}