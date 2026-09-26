package com.happysg.kaboom.block.missiles.util;

import com.happysg.kaboom.block.missiles.assembly.MissileAssemblyExceptionDisplay;

public interface IMissileGuidanceProvider extends MissileAssemblyExceptionDisplay {
    MissileGuidanceData exportGuidance();
}
