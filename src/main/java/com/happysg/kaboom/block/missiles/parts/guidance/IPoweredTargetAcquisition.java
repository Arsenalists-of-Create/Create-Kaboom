package com.happysg.kaboom.block.missiles.parts.guidance;

import com.happysg.kaboom.block.missiles.assembly.MissileAssemblyResult;
import net.minecraft.server.level.ServerLevel;

/**
 * Guidance that acquires its launch target while the assembled missile remains powered.
 */
public interface IPoweredTargetAcquisition {
    /**
     * Allows a guidance block to switch between normal rising-edge launch control and
     * continuous powered acquisition without changing its block entity type.
     */
    default boolean isPoweredAcquisitionEnabled() {
        return true;
    }

    /**
     * Advances target acquisition for this tick.
     *
     * @return {@code true} when the guidance has a target and launch may be attempted
     */
    boolean tickAcquisition(ServerLevel level, MissileAssemblyResult result);

    /** Resets transient acquisition state when power, guidance, or the assembly is lost. */
    void resetAcquisition();
}
