package com.happysg.kaboom.items;

import com.happysg.kaboom.CreateKaboom;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class MineFuzePacket implements CustomPacketPayload {
    public static final Type<MineFuzePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateKaboom.MODID, "mine_fuze_packet"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MineFuzePacket> STREAM_CODEC =
            StreamCodec.ofMember(MineFuzePacket::encode, MineFuzePacket::decode);

    private final InteractionHand hand;
    private final int sensitivity;

    public MineFuzePacket(InteractionHand hand, int sensitivity) {
        this.hand = hand;
        this.sensitivity = sensitivity;
    }

    public static void encode(MineFuzePacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.hand);
        buf.writeInt(msg.sensitivity);
    }

    public static MineFuzePacket decode(FriendlyByteBuf buf) {
        InteractionHand hand = buf.readEnum(InteractionHand.class);
        int sensitivity = buf.readInt();
        return new MineFuzePacket(hand, sensitivity);
    }
    
    public static void handle(MineFuzePacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            if (player == null) return;
            ItemStack stack = player.getItemInHand(msg.hand);
            if (!(stack.getItem() instanceof MineFuze)) return;

            int clamped = net.minecraft.util.Mth.clamp(msg.sensitivity,
                    MineFuze.MIN_SENSITIVITY,
                    MineFuze.MAX_SENSITIVITY);

            MineFuze.setSensitivity(stack, clamped);

            player.inventoryMenu.broadcastChanges();
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
