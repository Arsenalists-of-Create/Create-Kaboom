package com.happysg.kaboom.block.missiles.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PreciseMotionSyncPacket {

    private final int entityId;
    private final double x, y, z;
    private final double dx, dy, dz;
    private final float yRot, xRot;
    private final boolean onGround;
    private final int lerpSteps;

    public PreciseMotionSyncPacket(int entityId,
                                   double x, double y, double z,
                                   double dx, double dy, double dz,
                                   float yRot, float xRot,
                                   boolean onGround,
                                   int lerpSteps) {
        this.entityId = entityId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
        this.yRot = yRot;
        this.xRot = xRot;
        this.onGround = onGround;
        this.lerpSteps = lerpSteps;
    }

    public static void encode(PreciseMotionSyncPacket pkt, FriendlyByteBuf buf) {
        buf.writeVarInt(pkt.entityId);
        buf.writeDouble(pkt.x);
        buf.writeDouble(pkt.y);
        buf.writeDouble(pkt.z);
        buf.writeDouble(pkt.dx);
        buf.writeDouble(pkt.dy);
        buf.writeDouble(pkt.dz);
        buf.writeFloat(pkt.yRot);
        buf.writeFloat(pkt.xRot);
        buf.writeBoolean(pkt.onGround);
        buf.writeVarInt(pkt.lerpSteps);
    }

    public static PreciseMotionSyncPacket decode(FriendlyByteBuf buf) {
        return new PreciseMotionSyncPacket(
                buf.readVarInt(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readFloat(), buf.readFloat(),
                buf.readBoolean(),
                buf.readVarInt()
        );
    }

    public static void handle(PreciseMotionSyncPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Entity entity = Minecraft.getInstance().level.getEntity(pkt.entityId);
            if (entity != null) {
                entity.lerpTo(pkt.x, pkt.y, pkt.z, pkt.yRot, pkt.xRot, pkt.lerpSteps, false);
                entity.lerpMotion(pkt.dx, pkt.dy, pkt.dz);
                entity.setOnGround(pkt.onGround);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}