package com.happysg.kaboom.networking;

import com.happysg.kaboom.CreateKaboom;
import com.happysg.kaboom.client.ClientPacketHandlers;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class RocketPodLaunchSoundPacket implements CustomPacketPayload {
    public static final Type<RocketPodLaunchSoundPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateKaboom.MODID, "rocket_pod_launch_sound"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RocketPodLaunchSoundPacket> STREAM_CODEC =
            StreamCodec.ofMember(RocketPodLaunchSoundPacket::encode, RocketPodLaunchSoundPacket::decode);

    private final int entityId;
    private final BlockPos rearPos;
    private final int slot;
    private final long launchId;
    private final int ticksRemaining;

    public RocketPodLaunchSoundPacket(int entityId, BlockPos rearPos, int slot,
                                     long launchId, int ticksRemaining) {
        this.entityId = entityId;
        this.rearPos = rearPos.immutable();
        this.slot = slot;
        this.launchId = launchId;
        this.ticksRemaining = ticksRemaining;
    }

    public static void encode(RocketPodLaunchSoundPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.entityId);
        buf.writeBlockPos(packet.rearPos);
        buf.writeByte(packet.slot);
        buf.writeVarLong(packet.launchId);
        buf.writeVarInt(packet.ticksRemaining);
    }

    public static RocketPodLaunchSoundPacket decode(FriendlyByteBuf buf) {
        return new RocketPodLaunchSoundPacket(
                buf.readVarInt(),
                buf.readBlockPos(),
                buf.readUnsignedByte(),
                buf.readVarLong(),
                buf.readVarInt());
    }

    public static void handle(RocketPodLaunchSoundPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientPacketHandlers.handleRocketPodLaunchSound(
                packet.entityId,
                packet.rearPos,
                packet.slot,
                packet.launchId,
                packet.ticksRemaining));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
