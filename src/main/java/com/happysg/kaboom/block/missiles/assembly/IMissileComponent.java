package com.happysg.kaboom.block.missiles.assembly;

import com.happysg.kaboom.block.missiles.parts.ThrusterBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

public interface IMissileComponent {
    public enum MissilePartType {
        THRUSTER,
        FUEL_TANK,
        GUIDANCE
    }



    MissilePartType getPartType();

        default boolean isFuelTank() {
            return getPartType() == MissilePartType.FUEL_TANK;
        }

        default boolean isThruster() {
            return getPartType() == MissilePartType.THRUSTER;
        }

        default boolean isGuidance() {
            return getPartType() == MissilePartType.GUIDANCE;
        }
    }

