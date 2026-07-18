package com.happysg.kaboom.networking;

import com.happysg.kaboom.CreateKaboom;
import com.happysg.kaboom.client.ClientPacketHandlers;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class ChainSystemSyncPacket implements CustomPacketPayload {
    public static final Type<ChainSystemSyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateKaboom.MODID, "chain_system_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ChainSystemSyncPacket> STREAM_CODEC =
            StreamCodec.ofMember(ChainSystemSyncPacket::encode, ChainSystemSyncPacket::decode);

    private final int entityId;
    private final CompoundTag chainSystemTag;

    public ChainSystemSyncPacket(int entityId, CompoundTag chainSystemTag) {
        this.entityId = entityId;
        this.chainSystemTag = chainSystemTag;
    }

    public static void encode(ChainSystemSyncPacket pkt, FriendlyByteBuf buf) {
        buf.writeVarInt(pkt.entityId);
        buf.writeNbt(pkt.chainSystemTag);
    }

    public static ChainSystemSyncPacket decode(FriendlyByteBuf buf) {
        return new ChainSystemSyncPacket(buf.readVarInt(), buf.readNbt());
    }

    public static void handle(ChainSystemSyncPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientPacketHandlers.handleChainSync(pkt.entityId, pkt.chainSystemTag));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
