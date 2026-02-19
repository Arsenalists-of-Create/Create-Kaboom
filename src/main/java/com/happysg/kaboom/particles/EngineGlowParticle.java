package com.happysg.kaboom.particles;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class EngineGlowParticle extends TextureSheetParticle {
    private final SpriteSet sprites;

    protected EngineGlowParticle(ClientLevel level, double x, double y, double z,
                                 double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z, xd, yd, zd);
        this.sprites = sprites;

        this.xd = xd;
        this.yd = yd;
        this.zd = zd;

        this.lifetime = 6 + this.random.nextInt(4);
        this.quadSize = 0.18f + this.random.nextFloat() * 0.06f;
        this.alpha = 0.85f;

        this.hasPhysics = false;
        this.gravity = 0.0f;
        this.friction = 0.85f;

        this.setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        this.setSpriteFromAge(this.sprites);

        float t = (float) age / (float) lifetime; // 0..1
        // pulse a bit
        this.alpha = (1.0f - t) * 0.9f;
        this.quadSize *= (0.98f + random.nextFloat() * 0.02f);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    // FULLBRIGHT: makes it appear emissive
    @Override
    protected int getLightColor(float partialTick) {
        return 0xF000F0; // max combined light
    }

    @OnlyIn(Dist.CLIENT)
    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public Provider(SpriteSet sprites) { this.sprites = sprites; }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double xd, double yd, double zd) {
            return new EngineGlowParticle(level, x, y, z, xd, yd, zd, sprites);
        }
    }
}