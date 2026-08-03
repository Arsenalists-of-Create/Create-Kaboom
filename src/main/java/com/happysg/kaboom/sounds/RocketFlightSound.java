package com.happysg.kaboom.sounds;

import com.happysg.kaboom.items.rocket.UnguidedRocketProjectile;
import com.happysg.kaboom.registry.ModSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class RocketFlightSound extends AbstractTickableSoundInstance {
    private static final float VOLUME = 0.75F;
    private static final double MIN_MOVEMENT_SQR = 1.0E-8;

    private final UnguidedRocketProjectile rocket;
    private double previousX;
    private double previousY;
    private double previousZ;

    public RocketFlightSound(UnguidedRocketProjectile rocket) {
        super(ModSounds.ROCKET_LOOP.get(), SoundSource.AMBIENT, RandomSource.create());
        this.rocket = rocket;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.0F;
        this.pitch = 1.5F;
        updatePosition();
        rememberPosition();
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        if (this.rocket.isRemoved() || !this.rocket.isAlive() || !this.rocket.isBoosting()) {
            stop();
            return;
        }
        this.volume = hasMoved() ? VOLUME : 0.0F;
        updatePosition();
        rememberPosition();
    }

    private boolean hasMoved() {
        double dx = this.rocket.getX() - this.previousX;
        double dy = this.rocket.getY() - this.previousY;
        double dz = this.rocket.getZ() - this.previousZ;
        return dx * dx + dy * dy + dz * dz > MIN_MOVEMENT_SQR;
    }

    private void rememberPosition() {
        this.previousX = this.rocket.getX();
        this.previousY = this.rocket.getY();
        this.previousZ = this.rocket.getZ();
    }

    private void updatePosition() {
        this.x = this.rocket.getX();
        this.y = this.rocket.getY();
        this.z = this.rocket.getZ();
    }

    public void stopSound() { stop(); }
}
