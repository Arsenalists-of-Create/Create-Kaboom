package com.happysg.kaboom.particles;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

// MissileSmokeParticle.java (client)
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
        super(level, x, y, z, 0, 0, 0); // <- don't pass velocity to super
        this.sprites = sprites;

        // LOITER: basically stationary
        this.xd = 0.0;
        this.yd = 0.0;
        this.zd = 0.0;

        this.hasPhysics = false;
        this.gravity = 0.0f;
        this.friction = 0.98f; // barely matters if velocity is zero, but harmless

        // Longer-lived “hanging cloud”
        this.lifetime = 60 + this.random.nextInt(40); // 3–5 seconds

        this.startAlpha = 0.55f + this.random.nextFloat() * 0.20f;
        this.alpha = startAlpha;

        // BIGGER
        this.startSize = 0.35f + this.random.nextFloat() * 0.20f; // was ~0.10–0.16
        this.endSize   = startSize * (1.6f + this.random.nextFloat() * 0.8f); // subtle growth
        this.quadSize = startSize;

        this.setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        // Do normal aging/expiry + position integration
        super.tick();

        // HARD LOITER: kill any drift that might sneak in
        this.xd = 0.0;
        this.yd = 0.0;
        this.zd = 0.0;

        this.setSpriteFromAge(this.sprites);

        float t = (float) this.age / (float) this.lifetime;

        // gentle growth (optional)
        float eased = 1.0f - (1.0f - t) * (1.0f - t);
        this.quadSize = lerp(startSize, endSize, eased);

        // fade out at the end (keeps the “lingering” feel)
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