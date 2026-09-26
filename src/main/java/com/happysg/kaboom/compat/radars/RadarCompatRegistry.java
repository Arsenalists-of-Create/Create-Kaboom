package com.happysg.kaboom.compat.radars;

import com.happysg.kaboom.compat.Mods;
import net.neoforged.bus.api.IEventBus;

public final class RadarCompatRegistry {
    private static RadarIntegration integration = new NoRadarIntegration();

    private RadarCompatRegistry() {
    }

    public static void register(IEventBus modEventBus) {
        Mods.CREATE_RADAR.executeIfInstalled(() -> () -> install(modEventBus));
    }

    private static void install(IEventBus modEventBus) {
        integration = new CreateRadarIntegration();
        integration.register(modEventBus);
    }

    public static RadarIntegration get() {
        return integration;
    }

    public static boolean isAvailable() {
        return integration.isAvailable();
    }
}
