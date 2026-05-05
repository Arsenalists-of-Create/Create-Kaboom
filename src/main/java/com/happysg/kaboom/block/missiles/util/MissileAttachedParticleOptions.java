package com.happysg.kaboom.block.missiles.util;

import com.happysg.kaboom.registry.ModParticles;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record MissileAttachedParticleOptions(int entityId, float back, float up, float right)
        implements ParticleOptions {

    public static final MapCodec<MissileAttachedParticleOptions> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.INT.fieldOf("entity_id").forGetter(MissileAttachedParticleOptions::entityId),
            Codec.FLOAT.fieldOf("back").forGetter(MissileAttachedParticleOptions::back),
            Codec.FLOAT.fieldOf("up").forGetter(MissileAttachedParticleOptions::up),
            Codec.FLOAT.fieldOf("right").forGetter(MissileAttachedParticleOptions::right)
    ).apply(instance, MissileAttachedParticleOptions::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, MissileAttachedParticleOptions> STREAM_CODEC =
            StreamCodec.ofMember(MissileAttachedParticleOptions::write, MissileAttachedParticleOptions::read);

    public static void write(MissileAttachedParticleOptions options, FriendlyByteBuf buf) {
        buf.writeVarInt(options.entityId);
        buf.writeFloat(options.back);
        buf.writeFloat(options.up);
        buf.writeFloat(options.right);
    }

    public static MissileAttachedParticleOptions read(FriendlyByteBuf buf) {
        return new MissileAttachedParticleOptions(buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat());
    }

    @Override
    public ParticleType<?> getType() {
        return ModParticles.MISSILE_ATTACHED.get();
    }
}
