package com.happysg.kaboom.particles;

import com.happysg.kaboom.block.missiles.util.MissileAttachedParticleOptions;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class MissileAttachedParticle extends TextureSheetParticle {

    private final int entityId;
    private final float back, up, right;

    public MissileAttachedParticle(ClientLevel level, double x, double y, double z,
                                   int entityId, float back, float up, float right,
                                   SpriteSet sprites) {
        super(level, x, y, z);

        this.entityId = entityId;
        this.back = back;
        this.up = up;
        this.right = right;

        this.gravity = 0f;
        this.hasPhysics = false;
        this.friction = 1.0f;

        this.lifetime = 20 * 60 * 5; // 5 minutes (or longer)
        this.setSpriteFromAge(sprites);
        this.quadSize = 3; // tune
    }

    @Override
    public void tick() {
        Entity e = level.getEntity(entityId);
        if (e == null || !e.isAlive()) {
            remove();
            return;
        }

        // Use entity rotation. (If your OrientedContraptionEntity uses custom pitch/yaw fields,
        // swap these to your values.)
        float pitch = e.getXRot();
        float yaw = e.getYRot();

        Vec3 forward = Vec3.directionFromRotation(pitch, yaw);
        Vec3 worldUp = new Vec3(0, 1, 0);

        Vec3 rightVec = forward.cross(worldUp);
        if (rightVec.lengthSqr() < 1.0e-6) rightVec = new Vec3(1, 0, 0); // looking straight up/down fallback
        rightVec = rightVec.normalize();

        Vec3 upVec = rightVec.cross(forward).normalize();

        Vec3 pos = e.position()
                .add(forward.scale(-back))
                .add(upVec.scale(up))
                .add(rightVec.scale(right));

        // Hard snap + kill interpolation jitter
        this.x = pos.x;
        this.y = pos.y;
        this.z = pos.z;
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;

        this.xd = this.yd = this.zd = 0;

        super.tick();
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

}