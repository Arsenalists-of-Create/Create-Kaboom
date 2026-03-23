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
    public final ConfigFloat maxSlowDown = f(0.5f,0,"maxSlowDown", "maxSlowDown in terminal phase (Acceleration)");
    public final ConfigInt boostHeight = i(120,1,"boostHeight", "boostHeight");
    public final ConfigInt cruiseHeight = i(400,0,"cruiseHeight","cruiseHeight");
}