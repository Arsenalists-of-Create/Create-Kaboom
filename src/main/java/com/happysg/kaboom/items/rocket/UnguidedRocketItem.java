package com.happysg.kaboom.items.rocket;

import net.minecraft.world.item.ItemStack;

public class UnguidedRocketItem extends RocketItem {
    public UnguidedRocketItem(Properties properties) {
        super(properties);
    }

    public ItemStack createPayloadPreset(RocketPayload payload) {
        return createConfiguredPreset(payload, null);
    }
}
