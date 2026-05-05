package com.happysg.kaboom.particles;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class MissileSmokeParticle extends TextureSheetParticle {

    private final SpriteSet sprites;
    private final float startSize;
    private final float endSize;
    private final float startAlpha;

    protected MissileSmokeParticle(ClientLevel level,
                                   double x, double y, double z,
                                   double xd, double yd, double zd,
                                   SpriteSet sprites) {
        super(level, x, y, z, 0, 0, 0);
        this.sprites = sprites;

        this.xd = 0.0;
        this.yd = 0.0;
        this.zd = 0.0;

        this.hasPhysics = false;
        this.gravity = 0.0f;
        this.friction = 0.98f;

        this.lifetime = 60 + this.random.nextInt(40);

        this.startAlpha = 0.55f + this.random.nextFloat() * 0.20f;
        this.alpha = startAlpha;

        this.startSize = 0.35f + this.random.nextFloat() * 0.20f;
        this.endSize   = startSize * (1.6f + this.random.nextFloat() * 0.8f);
        this.quadSize = startSize;

        this.setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {

        super.tick();

        this.xd = 0.0;
        this.yd = 0.0;
        this.zd = 0.0;

        this.setSpriteFromAge(this.sprites);

        float t = (float) this.age / (float) this.lifetime;

        float eased = 1.0f - (1.0f - t) * (1.0f - t);
        this.quadSize = lerp(startSize, endSize, eased);

        float fade = 1.0f - t;
        this.alpha = startAlpha * (fade * fade);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @OnlyIn(Dist.CLIENT)
    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public Provider(SpriteSet sprites) { this.sprites = sprites; }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double xd, double yd, double zd) {
            return new MissileSmokeParticle(level, x, y, z, xd, yd, zd, sprites);
        }
    }
}