package com.happysg.kaboom.block.missiles.util;

import com.happysg.kaboom.registry.ModParticles;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.FriendlyByteBuf;

public record MissileAttachedParticleOptions(int entityId, float back, float up, float right)
        implements ParticleOptions {

    public static final Deserializer<MissileAttachedParticleOptions> DESERIALIZER =
            new Deserializer<>() {
                @Override
                public MissileAttachedParticleOptions fromNetwork(ParticleType<MissileAttachedParticleOptions> type,
                                                                  FriendlyByteBuf buf) {
                    return new MissileAttachedParticleOptions(buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat());
                }

                @Override
                public MissileAttachedParticleOptions fromCommand(ParticleType<MissileAttachedParticleOptions> type,
                                                                  StringReader reader) throws CommandSyntaxException {
                    reader.expect(' ');
                    int id = reader.readInt();
                    reader.expect(' ');
                    float back = reader.readFloat();
                    reader.expect(' ');
                    float up = reader.readFloat();
                    reader.expect(' ');
                    float right = reader.readFloat();
                    return new MissileAttachedParticleOptions(id, back, up, right);
                }
            };

    @Override
    public ParticleType<?> getType() {
        return ModParticles.MISSILE_ATTACHED.get();
    }



    @Override
    public void writeToNetwork(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeFloat(back);
        buf.writeFloat(up);
        buf.writeFloat(right);
    }

    @Override
    public String writeToString() {
        return entityId + " " + back + " " + up + " " + right;
    }
}