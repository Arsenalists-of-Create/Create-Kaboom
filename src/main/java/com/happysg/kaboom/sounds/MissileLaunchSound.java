package com.happysg.kaboom.sounds;

import com.happysg.kaboom.block.missiles.MissileEntity;
import com.happysg.kaboom.registry.ModSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;

@OnlyIn(Dist.CLIENT)
public class MissileLaunchSound extends AbstractTickableSoundInstance {
    public static final float BASE_VOLUME = 0.35F;
    private static final int FADE_TICKS = 3;

    private final PitchOrientedContraptionEntity entity;
    private final Vec3 localNozzle;
    private final int fullVolumeTicks;
    private int age;
    private int removalFadeTicks = -1;
    private MissileEntity missile;

    public MissileLaunchSound(PitchOrientedContraptionEntity entity, Vec3 localNozzle, int ticksRemaining) {
        super(ModSounds.MISSILE_ENGINE.get(), SoundSource.AMBIENT, RandomSource.create());
        this.entity = entity;
        this.localNozzle = localNozzle;
        this.fullVolumeTicks = Math.max(1, ticksRemaining);
        this.looping = true;
        this.delay = 0;
        this.volume = BASE_VOLUME;
        this.pitch = 1.0F;
        updatePosition();
    }

    public boolean isFor(PitchOrientedContraptionEntity entity) {
        return this.entity == entity;
    }

    @Override
    public void tick() {
        if (this.missile != null) {
            if (this.missile.isRemoved() || !this.missile.isAlive()
                    || this.missile.getEntityData().get(MissileEntity.FUEL_MB) <= 0) {
                stop();
                return;
            }
            this.x = this.missile.getX();
            this.y = this.missile.getY();
            this.z = this.missile.getZ();
            this.volume += (0.85F - this.volume) * 0.2F;
            return;
        }
        if (this.entity.isRemoved() || !this.entity.isAlive()) {
            if (this.removalFadeTicks < 0) this.removalFadeTicks = 0;
            if (++this.removalFadeTicks >= FADE_TICKS) {
                stop();
                return;
            }
            this.volume = BASE_VOLUME * (1.0F - (float)this.removalFadeTicks / (float)FADE_TICKS);
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

    public void handoff(MissileEntity missile) {
        this.missile = missile;
        this.removalFadeTicks = -1;
    }

    private void updatePosition() {
        Vec3 position = this.entity.toGlobalVector(this.localNozzle, 0);
        this.x = position.x;
        this.y = position.y;
        this.z = position.z;
    }
}
