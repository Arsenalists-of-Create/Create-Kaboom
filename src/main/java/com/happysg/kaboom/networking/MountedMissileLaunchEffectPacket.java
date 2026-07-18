package com.happysg.kaboom.networking;

import com.happysg.kaboom.CreateKaboom;
import com.happysg.kaboom.block.missiles.assembly.MissileSize;
import com.happysg.kaboom.client.ClientPacketHandlers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record MountedMissileLaunchEffectPacket(int entityId, Vec3 localNozzle, Vec3 localForward,
                                               MissileSize size, int ticksRemaining)
        implements CustomPacketPayload {
    public static final Type<MountedMissileLaunchEffectPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateKaboom.MODID, "mounted_missile_launch_effect"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MountedMissileLaunchEffectPacket> STREAM_CODEC =
            StreamCodec.ofMember(MountedMissileLaunchEffectPacket::encode, MountedMissileLaunchEffectPacket::decode);

    private static void encode(MountedMissileLaunchEffectPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.entityId);
        buffer.writeVec3(packet.localNozzle);
        buffer.writeFloat((float)packet.localForward.x);
        buffer.writeFloat((float)packet.localForward.y);
        buffer.writeFloat((float)packet.localForward.z);
        buffer.writeByte(packet.size.ordinal());
        buffer.writeVarInt(packet.ticksRemaining);
    }

    private static MountedMissileLaunchEffectPacket decode(FriendlyByteBuf buffer) {
        int entityId = buffer.readVarInt();
        Vec3 nozzle = buffer.readVec3();
        Vec3 forward = new Vec3(buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
        int ordinal = buffer.readUnsignedByte();
        MissileSize[] sizes = MissileSize.values();
        MissileSize size = ordinal < sizes.length ? sizes[ordinal] : MissileSize.SMALL;
        return new MountedMissileLaunchEffectPacket(entityId, nozzle, forward, size, buffer.readVarInt());
    }

    public static void handle(MountedMissileLaunchEffectPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientPacketHandlers.handleMountedMissileLaunchEffect(packet.entityId,
                packet.localNozzle, packet.localForward, packet.size, packet.ticksRemaining));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
