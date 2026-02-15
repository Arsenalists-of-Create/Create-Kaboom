package com.happysg.kaboom.networking;

import com.happysg.kaboom.items.AltitudeFuzePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
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
    }
}
