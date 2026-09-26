package com.happysg.kaboom.client;

import com.happysg.kaboom.block.missiles.parts.guidance.gps.GPSScreen;
import com.happysg.kaboom.items.alt_fuze.AltitudeFuzeScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ClientScreenOpener {
    private ClientScreenOpener() {
    }

    public static void openAltitudeFuze(InteractionHand hand, int height) {
        Minecraft.getInstance().setScreen(new AltitudeFuzeScreen(hand, height));
    }

    public static void openGpsGuidance(BlockPos pos) {
        Minecraft.getInstance().setScreen(new GPSScreen(pos));
    }
}
