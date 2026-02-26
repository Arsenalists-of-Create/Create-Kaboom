package com.happysg.kaboom.particles;

import com.happysg.kaboom.block.missiles.util.MissileAttachedParticleOptions;
import com.happysg.kaboom.particles.MissileAttachedParticle;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class MissileAttachedParticleProvider implements ParticleProvider<MissileAttachedParticleOptions> {
    private final SpriteSet sprites;

    public MissileAttachedParticleProvider(SpriteSet sprites) {
        this.sprites = sprites;
    }

    @Override
    public Particle createParticle(MissileAttachedParticleOptions data, ClientLevel level,
                                   double x, double y, double z, double xd, double yd, double zd) {
        return new MissileAttachedParticle(level, x, y, z,
                data.entityId(), data.back(), data.up(), data.right(), sprites);
    }
}