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

public record LaunchSoundHandoffPacket(byte kind, int sourceEntityId, BlockPos sourcePos,
                                       int slot, long launchId, int targetEntityId)
        implements CustomPacketPayload {
    public static final byte FREE_MISSILE = 0;
    public static final byte MOUNTED_MISSILE = 1;
    public static final byte ROCKET_POD = 2;
    public static final Type<LaunchSoundHandoffPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateKaboom.MODID, "launch_sound_handoff"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LaunchSoundHandoffPacket> STREAM_CODEC =
            StreamCodec.ofMember(LaunchSoundHandoffPacket::encode, LaunchSoundHandoffPacket::decode);

    public static LaunchSoundHandoffPacket freeMissile(BlockPos sourcePos, int targetEntityId) {
        return new LaunchSoundHandoffPacket(FREE_MISSILE, -1, sourcePos.immutable(), -1, -1L, targetEntityId);
    }

    public static LaunchSoundHandoffPacket mountedMissile(int sourceEntityId, int targetEntityId) {
        return new LaunchSoundHandoffPacket(MOUNTED_MISSILE, sourceEntityId, BlockPos.ZERO, -1, -1L, targetEntityId);
    }

    public static LaunchSoundHandoffPacket rocketPod(int sourceEntityId, BlockPos rearPos, int slot,
                                                      long launchId, int targetEntityId) {
        return new LaunchSoundHandoffPacket(ROCKET_POD, sourceEntityId, rearPos.immutable(),
                slot, launchId, targetEntityId);
    }

    private static void encode(LaunchSoundHandoffPacket packet, FriendlyByteBuf buffer) {
        buffer.writeByte(packet.kind);
        buffer.writeVarInt(packet.sourceEntityId);
        buffer.writeBlockPos(packet.sourcePos);
        buffer.writeByte(packet.slot);
        buffer.writeVarLong(packet.launchId);
        buffer.writeVarInt(packet.targetEntityId);
    }

    private static LaunchSoundHandoffPacket decode(FriendlyByteBuf buffer) {
        return new LaunchSoundHandoffPacket(buffer.readByte(), buffer.readVarInt(), buffer.readBlockPos(),
                buffer.readByte(), buffer.readVarLong(), buffer.readVarInt());
    }

    public static void handle(LaunchSoundHandoffPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientPacketHandlers.handleLaunchSoundHandoff(packet.kind,
                packet.sourceEntityId, packet.sourcePos, packet.slot, packet.launchId, packet.targetEntityId));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
