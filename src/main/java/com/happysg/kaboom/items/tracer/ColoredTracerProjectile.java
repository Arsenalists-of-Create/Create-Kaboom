package com.happysg.kaboom.items.tracer;

import org.jetbrains.annotations.Nullable;

public interface ColoredTracerProjectile {
    @Nullable
    TracerColor createKaboom$getTracerColor();

    void createKaboom$setTracerColor(@Nullable TracerColor color);
}
