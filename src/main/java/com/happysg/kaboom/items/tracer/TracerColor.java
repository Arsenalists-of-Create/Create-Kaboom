package com.happysg.kaboom.items.tracer;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.Nullable;

import java.util.function.IntFunction;

public enum TracerColor implements StringRepresentable {
    BLUE("blue", 0, 200, 255, 0, 100, 255),
    RED("red", 255, 100, 100, 255, 0, 0),
    GREEN("green", 120, 255, 120, 0, 255, 0),
    PINK("pink", 255, 100, 255, 180, 0, 255),
    WHITE("white", 255, 255, 255, 255, 235, 201),
    ORANGE("orange", 255, 150, 0, 255, 80, 0);

    public static final Codec<TracerColor> CODEC = StringRepresentable.fromEnum(TracerColor::values);

    private static final IntFunction<TracerColor> BY_ID = ByIdMap.continuous(
            TracerColor::ordinal,
            values(),
            ByIdMap.OutOfBoundsStrategy.ZERO
    );

    public static final StreamCodec<ByteBuf, TracerColor> STREAM_CODEC = ByteBufCodecs.idMapper(
            BY_ID,
            TracerColor::ordinal
    );

    private final String serializedName;
    private final int insideRed;
    private final int insideGreen;
    private final int insideBlue;
    private final int outsideRed;
    private final int outsideGreen;
    private final int outsideBlue;

    TracerColor(String serializedName, int insideRed, int insideGreen, int insideBlue,
                int outsideRed, int outsideGreen, int outsideBlue) {
        this.serializedName = serializedName;
        this.insideRed = insideRed;
        this.insideGreen = insideGreen;
        this.insideBlue = insideBlue;
        this.outsideRed = outsideRed;
        this.outsideGreen = outsideGreen;
        this.outsideBlue = outsideBlue;
    }

    public int insideRed() {
        return insideRed;
    }

    public int insideGreen() {
        return insideGreen;
    }

    public int insideBlue() {
        return insideBlue;
    }

    public int outsideRed() {
        return outsideRed;
    }

    public int outsideGreen() {
        return outsideGreen;
    }

    public int outsideBlue() {
        return outsideBlue;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    public String getTranslationKey() {
        return "item.create_kaboom.tracer_color." + serializedName;
    }

    @Nullable
    public static TracerColor bySerializedName(String name) {
        for (TracerColor color : values()) {
            if (color.serializedName.equals(name)) {
                return color;
            }
        }
        return null;
    }

    @Nullable
    public static TracerColor byNetworkId(int id) {
        return id >= 0 && id < values().length ? values()[id] : null;
    }
}
