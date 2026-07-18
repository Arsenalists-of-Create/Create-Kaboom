package com.happysg.kaboom.particles;

import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class MissileSmokeParticle extends TextureSheetParticle {
    private static final double BOUNCE_FACTOR = 0.45;
    private static final double MIN_BOUNCE_SPEED = 0.015;

    private final SpriteSet sprites;
    private final float startSize;
    private final float endSize;
    private final float startAlpha;

    protected MissileSmokeParticle(ClientLevel level,
                                   double x, double y, double z,
                                   double xd, double yd, double zd,
                                   SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;

        this.setParticleSpeed(xd, yd, zd);
        this.setSize(0.26F, 0.26F);
        this.hasPhysics = true;
        this.gravity = 0.18F;
        this.friction = 0.91F;
        this.lifetime = 80 + this.random.nextInt(41);

        this.startAlpha = 0.65F + this.random.nextFloat() * 0.15F;
        this.alpha = this.startAlpha;

        this.startSize = 0.48F + this.random.nextFloat() * 0.22F;
        this.endSize = this.startSize * (2.0F + this.random.nextFloat() * 0.7F);
        this.quadSize = this.startSize;

        this.setSpriteFromAge(this.sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.removed) {
            return;
        }

        this.setSpriteFromAge(this.sprites);
        float progress = (float) this.age / (float) this.lifetime;
        float easedGrowth = 1.0F - (1.0F - progress) * (1.0F - progress);
        this.quadSize = lerp(this.startSize, this.endSize, easedGrowth);
        float fade = 1.0F - progress;
        this.alpha = this.startAlpha * fade * fade;
    }

    @Override
    public void move(double requestedX, double requestedY, double requestedZ) {
        Vec3 resolved = Entity.collideBoundingBox(
                null,
                new Vec3(requestedX, requestedY, requestedZ),
                this.getBoundingBox(),
                this.level,
                List.of());

        if (resolved.lengthSqr() > 0.0) {
            this.setBoundingBox(this.getBoundingBox().move(resolved));
            this.setLocationFromBoundingbox();
        }

        this.onGround = requestedY != resolved.y && requestedY < 0.0;
        if (requestedX != resolved.x) {
            this.xd = bounced(this.xd);
        }
        if (requestedY != resolved.y) {
            this.yd = bounced(this.yd);
        }
        if (requestedZ != resolved.z) {
            this.zd = bounced(this.zd);
        }
    }

    private static double bounced(double velocity) {
        double bounced = -velocity * BOUNCE_FACTOR;
        return Math.abs(bounced) < MIN_BOUNCE_SPEED ? 0.0 : bounced;
    }

    private static float lerp(float start, float end, float progress) {
        return start + (end - start) * progress;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @OnlyIn(Dist.CLIENT)
    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double xd, double yd, double zd) {
            return new MissileSmokeParticle(level, x, y, z, xd, yd, zd, this.sprites);
        }
    }
}
