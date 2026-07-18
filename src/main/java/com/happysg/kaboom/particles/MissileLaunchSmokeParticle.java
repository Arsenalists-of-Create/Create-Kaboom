package com.happysg.kaboom.particles;

import com.happysg.kaboom.block.missiles.assembly.MissileSize;
import com.happysg.kaboom.block.missiles.util.MissileLaunchSmokeOptions;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class MissileLaunchSmokeParticle extends TextureSheetParticle {
    private static final double BOUNCE_FACTOR = 0.45;
    private static final double MIN_BOUNCE_SPEED = 0.015;
    private final SpriteSet sprites;
    private final float startSize;
    private final float endSize;
    private final float startAlpha;
    private final double climbSpeed;

    protected MissileLaunchSmokeParticle(ClientLevel level, double x, double y, double z,
                                         double xd, double yd, double zd, SpriteSet sprites,
                                         MissileSize size) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.climbSpeed = size.launchSmokeClimbSpeed();
        float scale = size.launchSmokeScale();
        this.setParticleSpeed(xd, yd, zd);
        this.setSize(0.26F * scale, 0.26F * scale);
        this.hasPhysics = true;
        this.gravity = 0.18F;
        this.friction = 0.91F;
        this.lifetime = 80 + this.random.nextInt(41);
        this.startAlpha = 0.65F + this.random.nextFloat() * 0.15F;
        this.alpha = this.startAlpha;
        this.startSize = (0.48F + this.random.nextFloat() * 0.22F) * scale;
        this.endSize = this.startSize * (2.0F + this.random.nextFloat() * 0.7F);
        this.quadSize = this.startSize;
        this.setSpriteFromAge(this.sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.removed) return;
        this.setSpriteFromAge(this.sprites);
        float progress = (float)this.age / (float)this.lifetime;
        float growth = 1.0F - (1.0F - progress) * (1.0F - progress);
        this.quadSize = this.startSize + (this.endSize - this.startSize) * growth;
        float fade = 1.0F - progress;
        this.alpha = this.startAlpha * fade * fade;
    }

    @Override
    public void move(double requestedX, double requestedY, double requestedZ) {
        Vec3 resolved = Entity.collideBoundingBox(null, new Vec3(requestedX, requestedY, requestedZ),
                this.getBoundingBox(), this.level, List.of());
        if (resolved.lengthSqr() > 0.0) {
            this.setBoundingBox(this.getBoundingBox().move(resolved));
            this.setLocationFromBoundingbox();
        }
        boolean hitX = requestedX != resolved.x;
        boolean hitY = requestedY != resolved.y;
        boolean hitZ = requestedZ != resolved.z;
        this.onGround = hitY && requestedY < 0.0;
        if (hitX || hitZ) {
            this.xd = hitX ? this.xd * 0.35 : this.xd;
            this.zd = hitZ ? this.zd * 0.35 : this.zd;
            this.yd = Math.max(this.yd, this.climbSpeed);
        }
        if (hitY) this.yd = bounced(this.yd);
    }

    private static double bounced(double velocity) {
        double value = -velocity * BOUNCE_FACTOR;
        return Math.abs(value) < MIN_BOUNCE_SPEED ? 0.0 : value;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static class Provider implements ParticleProvider<MissileLaunchSmokeOptions> {
        private final SpriteSet sprites;
        public Provider(SpriteSet sprites) { this.sprites = sprites; }
        @Override
        public Particle createParticle(MissileLaunchSmokeOptions option, ClientLevel level,
                                       double x, double y, double z, double xd, double yd, double zd) {
            return new MissileLaunchSmokeParticle(level, x, y, z, xd, yd, zd, this.sprites, option.size());
        }
    }
}
