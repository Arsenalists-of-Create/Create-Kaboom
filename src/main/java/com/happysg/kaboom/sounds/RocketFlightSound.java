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

    private final UnguidedRocketProjectile rocket;

    public RocketFlightSound(UnguidedRocketProjectile rocket) {
        super(ModSounds.ROCKET_LOOP.get(), SoundSource.AMBIENT, RandomSource.create());
        this.rocket = rocket;
        this.looping = true;
        this.delay = 0;
        this.volume = VOLUME;
        this.pitch = 1.5F;
        updatePosition();
    }

    @Override
    public void tick() {
        if (this.rocket.isRemoved() || !this.rocket.isAlive() || !this.rocket.isBoosting()) {
            stop();
            return;
        }
        updatePosition();
    }

    private void updatePosition() {
        this.x = this.rocket.getX();
        this.y = this.rocket.getY();
        this.z = this.rocket.getZ();
    }

    public void stopSound() { stop(); }
}
