package com.happysg.kaboom.block.missiles.parts.guidance.arad;

import java.util.Locale;

public enum ARADTargetAcquisitionMode {
    MANUAL_DESIGNATION,
    AUTO_SCAN;

    public ARADTargetAcquisitionMode next() {
        return this == MANUAL_DESIGNATION ? AUTO_SCAN : MANUAL_DESIGNATION;
    }

    public String translationKey() {
        return switch (this) {
            case MANUAL_DESIGNATION -> "message.create_kaboom.arad_guidance.mode.manual_designation";
            case AUTO_SCAN -> "message.create_kaboom.arad_guidance.mode.auto_scan";
        };
    }

    public static ARADTargetAcquisitionMode fromName(String name) {
        if (name == null || name.isBlank()) {
            return MANUAL_DESIGNATION;
        }
        try {
            return valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return MANUAL_DESIGNATION;
        }
    }
}
