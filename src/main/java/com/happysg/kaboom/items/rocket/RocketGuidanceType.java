package com.happysg.kaboom.items.rocket;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;

import java.util.function.IntFunction;

public enum RocketGuidanceType implements StringRepresentable {
    COMMAND("command"),
    RADAR("radar"),
    ARAD("arad");

    public static final Codec<RocketGuidanceType> CODEC = StringRepresentable.fromEnum(RocketGuidanceType::values);

    private static final IntFunction<RocketGuidanceType> BY_ID = ByIdMap.continuous(
            RocketGuidanceType::ordinal,
            values(),
            ByIdMap.OutOfBoundsStrategy.ZERO
    );

    public static final StreamCodec<ByteBuf, RocketGuidanceType> STREAM_CODEC = ByteBufCodecs.idMapper(
            BY_ID,
            RocketGuidanceType::ordinal
    );

    private final String serializedName;

    RocketGuidanceType(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    public String getTranslationKey() {
        return "item.create_kaboom.rocket.guidance." + serializedName;
    }
}
