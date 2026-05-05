package com.happysg.kaboom.block.missiles.parts.guidance.gps;

import com.happysg.kaboom.CreateKaboom;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class GPSGuidancePacket implements CustomPacketPayload {
    public static final Type<GPSGuidancePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateKaboom.MODID, "gps_guidance"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GPSGuidancePacket> STREAM_CODEC =
            StreamCodec.ofMember(GPSGuidancePacket::encode, GPSGuidancePacket::decode);

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

    public static void handle(GPSGuidancePacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            BlockEntity be = player.level().getBlockEntity(msg.pos);
            if (!(be instanceof GPSGuidanceBlockEntity gps)) return;

            gps.setTarget(new Vec3(msg.x, msg.y, msg.z));
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
