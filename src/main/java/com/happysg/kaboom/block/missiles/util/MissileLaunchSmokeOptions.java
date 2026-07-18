package com.happysg.kaboom.block.missiles.util;

import com.happysg.kaboom.block.missiles.assembly.MissileSize;
import com.happysg.kaboom.registry.ModParticles;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record MissileLaunchSmokeOptions(MissileSize size) implements ParticleOptions {
    public static final MapCodec<MissileLaunchSmokeOptions> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(Codec.STRING.fieldOf("size").forGetter(option -> option.size.name()))
                    .apply(instance, name -> new MissileLaunchSmokeOptions(parseSize(name))));

    public static final StreamCodec<RegistryFriendlyByteBuf, MissileLaunchSmokeOptions> STREAM_CODEC =
            StreamCodec.ofMember(MissileLaunchSmokeOptions::write, MissileLaunchSmokeOptions::read);

    private static MissileSize parseSize(String name) {
        try {
            return MissileSize.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return MissileSize.SMALL;
        }
    }

    private static void write(MissileLaunchSmokeOptions option, FriendlyByteBuf buffer) {
        buffer.writeByte(option.size.ordinal());
    }

    private static MissileLaunchSmokeOptions read(FriendlyByteBuf buffer) {
        int ordinal = buffer.readUnsignedByte();
        MissileSize[] sizes = MissileSize.values();
        return new MissileLaunchSmokeOptions(ordinal < sizes.length ? sizes[ordinal] : MissileSize.SMALL);
    }

    @Override
    public ParticleType<?> getType() {
        return ModParticles.MISSILE_LAUNCH_SMOKE.get();
    }
}
