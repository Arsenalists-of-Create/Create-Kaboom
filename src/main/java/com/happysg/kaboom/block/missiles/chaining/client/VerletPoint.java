package com.happysg.kaboom.block.missiles.chaining.client;

import net.minecraft.world.phys.Vec3;

public class VerletPoint {

    public static final double RADIUS = 0.08;
    public static final double FRICTION = 0.65;

    public Vec3 pos;
    public Vec3 oldPos;
    public Vec3 renderPrevPos;
    public boolean pinned;
    public boolean onSurface;

    public VerletPoint(Vec3 position) {
        this.pos = position;
        this.oldPos = position;
        this.renderPrevPos = position;
        this.pinned = false;
        this.onSurface = false;
    }

    public void integrate(Vec3 gravity) {
        if (pinned) return;
        Vec3 vel = pos.subtract(oldPos).scale(0.98);
        oldPos = pos;
        pos = pos.add(vel).add(gravity);
    }

    public void applyFriction() {
        if (pinned || !onSurface) return;
        Vec3 vel = pos.subtract(oldPos);
        oldPos = pos.subtract(vel.scale(FRICTION));
    }

    public void saveRenderSnapshot() {
        renderPrevPos = pos;
    }

    public Vec3 lerpedPos(float partialTick) {
        return renderPrevPos.lerp(pos, partialTick);
    }
}
