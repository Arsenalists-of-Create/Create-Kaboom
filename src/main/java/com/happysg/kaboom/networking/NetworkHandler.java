package com.happysg.kaboom.networking;

import com.happysg.kaboom.block.missiles.parts.guidance.gps.GPSGuidancePacket;
import com.happysg.kaboom.block.missiles.util.PreciseMotionSyncPacket;
import com.happysg.kaboom.items.alt_fuze.AltitudeFuzePacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public class NetworkHandler {
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(AltitudeFuzePacket.TYPE, AltitudeFuzePacket.STREAM_CODEC, AltitudeFuzePacket::handle);
        registrar.playToServer(GPSGuidancePacket.TYPE, GPSGuidancePacket.STREAM_CODEC, GPSGuidancePacket::handle);
        registrar.playToClient(PreciseMotionSyncPacket.TYPE, PreciseMotionSyncPacket.STREAM_CODEC, PreciseMotionSyncPacket::handle);
        registrar.playToClient(ChainSystemSyncPacket.TYPE, ChainSystemSyncPacket.STREAM_CODEC, ChainSystemSyncPacket::handle);
        registrar.playToClient(RocketPodLaunchSoundPacket.TYPE, RocketPodLaunchSoundPacket.STREAM_CODEC,
                RocketPodLaunchSoundPacket::handle);
        registrar.playToClient(MountedMissileLaunchEffectPacket.TYPE, MountedMissileLaunchEffectPacket.STREAM_CODEC,
                MountedMissileLaunchEffectPacket::handle);
        registrar.playToClient(LaunchSoundHandoffPacket.TYPE, LaunchSoundHandoffPacket.STREAM_CODEC,
                LaunchSoundHandoffPacket::handle);
    }

    public static void sendToServer(CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    public static void sendToPlayersTrackingEntity(Entity entity, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayersTrackingEntity(entity, payload, new CustomPacketPayload[0]);
    }
}
