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
    private static final int FADE_TICKS = 5;
    private static final double MIN_MOVEMENT_SQR = 1.0E-8;

    private final PitchOrientedContraptionEntity entity;
    private final BlockPos rearPos;
    private final int fullVolumeTicks;
    private int age;
    private UnguidedRocketProjectile rocket;
    private double previousRocketX;
    private double previousRocketY;
    private double previousRocketZ;

    public RocketPodLaunchSound(PitchOrientedContraptionEntity entity, BlockPos rearPos, int ticksRemaining) {
        super(ModSounds.ROCKET_LOOP.get(), SoundSource.AMBIENT, RandomSource.create());
        this.entity = entity;
        this.rearPos = rearPos.immutable();
        this.fullVolumeTicks = Math.max(1, ticksRemaining);
        this.looping = true;
        this.delay = 0;
        this.volume = 0.0F;
        this.pitch = 2F;
        updatePosition();
    }

    @Override
    public boolean canStartSilent() {
        return true;
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
            boolean moved = hasRocketMoved();
            this.x = this.rocket.getX();
            this.y = this.rocket.getY();
            this.z = this.rocket.getZ();
            this.volume = moved ? this.volume + (0.75F - this.volume) * 0.25F : 0.0F;
            this.pitch += (1.5F - this.pitch) * 0.25F;
            rememberRocketPosition();
            return;
        }
        if (this.entity.isRemoved() || !this.entity.isAlive()) {
            stop();
            return;
        }

        updatePosition();
        ++this.age;
        this.volume = 0.0F;
        int fadeAge = this.age - this.fullVolumeTicks;
        if (fadeAge >= FADE_TICKS) {
            stop();
        }
    }

    public void handoff(UnguidedRocketProjectile rocket) {
        this.rocket = rocket;
        this.volume = 0.0F;
        rememberRocketPosition();
    }

    private boolean hasRocketMoved() {
        double dx = this.rocket.getX() - this.previousRocketX;
        double dy = this.rocket.getY() - this.previousRocketY;
        double dz = this.rocket.getZ() - this.previousRocketZ;
        return dx * dx + dy * dy + dz * dz > MIN_MOVEMENT_SQR;
    }

    private void rememberRocketPosition() {
        this.previousRocketX = this.rocket.getX();
        this.previousRocketY = this.rocket.getY();
        this.previousRocketZ = this.rocket.getZ();
    }

    private void updatePosition() {
        Vec3 position = this.entity.toGlobalVector(Vec3.atCenterOf(this.rearPos), 0);
        this.x = position.x;
        this.y = position.y;
        this.z = position.z;
    }
}
