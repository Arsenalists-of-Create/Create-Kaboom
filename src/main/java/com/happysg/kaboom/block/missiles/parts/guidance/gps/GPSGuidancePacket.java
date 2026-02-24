package com.happysg.kaboom.block.missiles.parts.guidance.gps;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class GPSGuidancePacket {
    private final BlockPos pos;
    private final double x;
    private final double y;
    private final double z;

    public GPSGuidancePacket(BlockPos pos, double x, double y, double z) {
        this.pos = pos;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static void encode(GPSGuidancePacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeDouble(msg.x);
        buf.writeDouble(msg.y);
        buf.writeDouble(msg.z);
    }

    public static GPSGuidancePacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        double x = buf.readDouble();
        double y = buf.readDouble();
        double z = buf.readDouble();
        return new GPSGuidancePacket(pos, x, y, z);
    }

    public static void handle(GPSGuidancePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            BlockEntity be = player.level().getBlockEntity(msg.pos);
            if (!(be instanceof GPSGuidanceBlockEntity gps)) return;

            gps.setTarget(new Vec3(msg.x, msg.y, msg.z));
            LogUtils.getLogger().warn("sending");
            gps.tx = msg.x;
            gps.ty = msg.y;
            gps.tz = msg.z;
        });
        ctx.get().setPacketHandled(true);
    }
}
