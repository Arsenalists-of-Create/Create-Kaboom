package com.happysg.kaboom.items;

import com.happysg.kaboom.items.AltitudeFuze;
import com.mojang.logging.LogUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class AltitudeFuzePacket {

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
    public static void handle(AltitudeFuzePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            InteractionHand hand = msg.hand;
            ItemStack stack = player.getItemInHand(hand);
            if (stack.isEmpty()) return;

            if (!(stack.getItem() instanceof AltitudeFuze)) return;

            int clamped = Mth.clamp(msg.altitude,
                    AltitudeFuze.MIN_HEIGHT,
                    AltitudeFuze.MAX_HEIGHT);

            AltitudeFuze.setHeight(stack, clamped);

            // Force the modified stack back into the slot (helps with some sync edge cases)
            player.setItemInHand(hand, stack);
            LogUtils.getLogger().warn("new_value"+msg.altitude);
            // Sync to client
            player.inventoryMenu.broadcastChanges();

            // Optional: quick sanity ping in actionbar (remove once confirmed)
            // player.displayClientMessage(Component.literal("Altitude set to " + clamped), true);
        });

        ctx.get().setPacketHandled(true);
    }

}