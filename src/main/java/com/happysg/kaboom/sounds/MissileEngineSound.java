package com.happysg.kaboom.sounds;

import com.happysg.kaboom.block.missiles.MissileEntity;
import com.happysg.kaboom.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class MissileEngineSound extends AbstractTickableSoundInstance {

    private final MissileEntity missile;

    private static final float BASE_PITCH = 0.9f;
    private static final float MAX_SHIFT = 0.35f;
    private static final double REF_SPEED = 13.0;

    public MissileEngineSound(MissileEntity missile) {
        super(ModSounds.MISSILE_ENGINE.get(), SoundSource.AMBIENT, RandomSource.create());
        this.missile = missile;
        this.looping = true;
        this.delay = 0;
        this.volume = 8f;
        this.pitch = BASE_PITCH;
    }

    @Override
    public void tick() {
        if (missile.isRemoved() || !missile.isAlive()) {
            stop();
            return;
        }
        if (missile.getEntityData().get(MissileEntity.FUEL_MB) <= 0) {
            stop();
            return;
        }
        this.x = missile.getX();
        this.y = missile.getY();
        this.z = missile.getZ();

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Vec3 v = missile.getDeltaMovement().scale(20.0);

        Vec3 listener = mc.player.getEyePosition();

        Vec3 r = missile.position().subtract(listener);
        double dist = r.length();
        if (dist < 1e-4) dist = 1e-4;

        Vec3 rHat = r.scale(1.0 / dist);

        double radial = v.dot(rHat);

        double t = Mth.clamp(radial / REF_SPEED, -1.0, 1.0);
        float doppler = (float)(-t * MAX_SHIFT);

        float speedWhine = (float)Mth.clamp(v.length() / 120.0, 0.0, 0.15);

        float targetPitch = BASE_PITCH + doppler + speedWhine;
        this.pitch = Mth.lerp(0.25f, this.pitch, targetPitch);

        float targetVol = 0.85f + (float)Mth.clamp((-radial) / 120.0, 0.0, 0.20);
        this.volume = Mth.lerp(0.20f, this.volume, targetVol);
    }
    public void stopSound(){
        stop();
    }
}