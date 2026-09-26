package com.happysg.kaboom.items.tracer;

import net.minecraft.world.item.Item;

import java.util.Objects;

public class ColoredTracerTipItem extends Item {
    private final TracerColor tracerColor;

    public ColoredTracerTipItem(Properties properties, TracerColor tracerColor) {
        super(properties);
        this.tracerColor = Objects.requireNonNull(tracerColor, "tracerColor");
    }

    public TracerColor getTracerColor() {
        return tracerColor;
    }
}
