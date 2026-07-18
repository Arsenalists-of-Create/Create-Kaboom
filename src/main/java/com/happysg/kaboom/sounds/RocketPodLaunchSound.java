package com.happysg.kaboom.sounds;

import com.happysg.kaboom.items.rocket.UnguidedRocketProjectile;
import com.happysg.kaboom.registry.ModSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;

@OnlyIn(Dist.CLIENT)
public class RocketPodLaunchSound extends AbstractTickableSoundInstance {
    public static final float BASE_VOLUME = 0.5F;
    private static final int FADE_TICKS = 5;

    private final PitchOrientedContraptionEntity entity;
    private final BlockPos rearPos;
    private final int fullVolumeTicks;
    private int age;
    private UnguidedRocketProjectile rocket;

    public RocketPodLaunchSound(PitchOrientedContraptionEntity entity, BlockPos rearPos, int ticksRemaining) {
        super(ModSounds.ROCKET_LOOP.get(), SoundSource.AMBIENT, RandomSource.create());
        this.entity = entity;
        this.rearPos = rearPos.immutable();
        this.fullVolumeTicks = Math.max(1, ticksRemaining);
        this.looping = true;
        this.delay = 0;
        this.volume = BASE_VOLUME;
        this.pitch = 2F;
        updatePosition();
    }

    public boolean isFor(PitchOrientedContraptionEntity entity) {
        return this.entity == entity;
    }

    @Override
    public void tick() {
        if (this.rocket != null) {
            if (this.rocket.isRemoved() || !this.rocket.isAlive() || !this.rocket.isBoosting()) {
                stop();
                return;
            }
            this.x = this.rocket.getX();
            this.y = this.rocket.getY();
            this.z = this.rocket.getZ();
            this.volume += (0.75F - this.volume) * 0.25F;
            this.pitch += (1.5F - this.pitch) * 0.25F;
            return;
        }
        if (this.entity.isRemoved() || !this.entity.isAlive()) {
            stop();
            return;
        }

        updatePosition();
        ++this.age;
        int fadeAge = this.age - this.fullVolumeTicks;
        if (fadeAge <= 0) {
            this.volume = BASE_VOLUME;
            return;
        }
        if (fadeAge >= FADE_TICKS) {
            stop();
            return;
        }
        this.volume = BASE_VOLUME * (1.0F - (float) fadeAge / (float) FADE_TICKS);
    }

    public void handoff(UnguidedRocketProjectile rocket) {
        this.rocket = rocket;
    }

    private void updatePosition() {
        Vec3 position = this.entity.toGlobalVector(Vec3.atCenterOf(this.rearPos), 0);
        this.x = position.x;
        this.y = position.y;
        this.z = position.z;
    }
}
