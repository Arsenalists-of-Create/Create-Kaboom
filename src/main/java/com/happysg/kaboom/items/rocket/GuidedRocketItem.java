package com.happysg.kaboom.items.rocket;

import net.minecraft.world.item.ItemStack;

import java.util.Objects;

public class GuidedRocketItem extends RocketItem {
    public GuidedRocketItem(Properties properties) {
        super(properties);
    }

    public ItemStack createGuidancePreset(RocketGuidanceType guidanceType) {
        return createPreset(RocketPayload.HE, guidanceType);
    }

    public ItemStack createPreset(RocketPayload payload, RocketGuidanceType guidanceType) {
        return createConfiguredPreset(payload, Objects.requireNonNull(guidanceType, "guidanceType"));
    }

    @Override
    public boolean isLaunchable(ItemStack stack) {
        return super.isLaunchable(stack) && RocketItem.getGuidanceType(stack) != null;
    }
}
