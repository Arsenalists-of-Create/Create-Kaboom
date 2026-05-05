package com.happysg.kaboom.items;

import com.happysg.kaboom.CreateKaboom;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class AltitudeFuzePacket implements CustomPacketPayload {
    public static final Type<AltitudeFuzePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateKaboom.MODID, "altitude_fuze"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AltitudeFuzePacket> STREAM_CODEC =
            StreamCodec.ofMember(AltitudeFuzePacket::encode, AltitudeFuzePacket::decode);

    private final InteractionHand hand;
    private final int altitude;

    public AltitudeFuzePacket(InteractionHand hand, int altitude) {
        this.hand = hand;
        this.altitude = altitude;
    }

    public static void encode(AltitudeFuzePacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.hand);
        buf.writeVarInt(msg.altitude);
    }

    public static AltitudeFuzePacket decode(FriendlyByteBuf buf) {
        InteractionHand hand = buf.readEnum(InteractionHand.class);
        int altitude = buf.readVarInt();
        return new AltitudeFuzePacket(hand, altitude);
    }
    public static void handle(AltitudeFuzePacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            InteractionHand hand = msg.hand;
            ItemStack stack = player.getItemInHand(hand);
            if (stack.isEmpty()) return;

            if (!(stack.getItem() instanceof AltitudeFuze)) return;

            int clamped = Mth.clamp(msg.altitude,
                    AltitudeFuze.MIN_HEIGHT,
                    AltitudeFuze.MAX_HEIGHT);

            AltitudeFuze.setHeight(stack, clamped);

            player.setItemInHand(hand, stack);
            player.inventoryMenu.broadcastChanges();
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
