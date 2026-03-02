package com.happysg.kaboom.networking;

import com.happysg.kaboom.block.missiles.parts.guidance.gps.GPSGuidancePacket;
import com.happysg.kaboom.block.missiles.util.PreciseMotionSyncPacket;
import com.happysg.kaboom.items.AltitudeFuzePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import static com.happysg.kaboom.CreateKaboom.MODID;

public class NetworkHandler {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int id = 0;
    public static void register() {
        CHANNEL.registerMessage(
                id++,
                AltitudeFuzePacket.class,
                AltitudeFuzePacket::encode,
                AltitudeFuzePacket::decode,
                AltitudeFuzePacket::handle
        );
        CHANNEL.registerMessage(
                id++,
                PreciseMotionSyncPacket.class,
                PreciseMotionSyncPacket::encode,
                PreciseMotionSyncPacket::decode,
                PreciseMotionSyncPacket::handle
        );
        CHANNEL.registerMessage(
                id++,
                GPSGuidancePacket.class,
                GPSGuidancePacket::encode,
                GPSGuidancePacket::decode,
                GPSGuidancePacket::handle
        );
        CHANNEL.registerMessage(
                id++,
                ChainSystemSyncPacket.class,
                ChainSystemSyncPacket::encode,
                ChainSystemSyncPacket::decode,
                ChainSystemSyncPacket::handle
        );
    }
}
