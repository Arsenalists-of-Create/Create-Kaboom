package com.happysg.kaboom.block.missiles.util;

import java.util.Locale;

public enum MissileGuidanceType {
    GPS,
    COMMAND,
    RADAR,
    ARAD,
    UNKNOWN;

    public boolean isInterceptor() {
        return this == COMMAND || this == RADAR;
    }

    public static MissileGuidanceType fromName(String raw) {
        if (raw == null || raw.isBlank()) return UNKNOWN;
        try {
            return valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return UNKNOWN;
        }
    }
}
