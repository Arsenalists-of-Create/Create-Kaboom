package com.happysg.kaboom.networking;

import com.happysg.kaboom.block.missiles.MissileEntity;
import com.happysg.kaboom.block.missiles.chaining.client.ChainRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ChainSystemSyncPacket {

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

    public static void handle(ChainSystemSyncPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (Minecraft.getInstance().level == null) return;
            Entity entity = Minecraft.getInstance().level.getEntity(pkt.entityId);
            if (entity instanceof MissileEntity missile) {
                missile.getChainSystem().load(pkt.chainSystemTag);
                ChainRenderer.TRACKED_MISSILES.add(pkt.entityId);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
