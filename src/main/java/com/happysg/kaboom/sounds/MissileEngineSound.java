package com.happysg.kaboom.sounds;

import com.happysg.kaboom.block.missiles.MissileEntity;
import com.happysg.kaboom.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class MissileEngineSound extends AbstractTickableSoundInstance {

    private final MissileEntity missile;

    // Tuneables
    private static final float BASE_PITCH = 0.1f;
    private static final float MAX_SHIFT = 0.35f;   // max +/- pitch shift
    private static final double REF_SPEED = 13.0;   // blocks/sec that equals MAX_SHIFT

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

        this.x = missile.getX();
        this.y = missile.getY();
        this.z = missile.getZ();

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Missile velocity (blocks/tick) -> blocks/sec
        Vec3 v = missile.getDeltaMovement().scale(20.0);

        // Listener position
        Vec3 listener = mc.player.getEyePosition();

        // Vector from listener to missile
        Vec3 r = missile.position().subtract(listener);
        double dist = r.length();
        if (dist < 1e-4) dist = 1e-4;

        Vec3 rHat = r.scale(1.0 / dist);

        // Radial speed: +away, -toward
        double radial = v.dot(rHat);

        // Doppler-ish pitch shift: toward => pitch up, away => pitch down
        double t = Mth.clamp(radial / REF_SPEED, -1.0, 1.0);
        float doppler = (float)(-t * MAX_SHIFT);

        // Optional: add a tiny speed-based whine on top
        float speedWhine = (float)Mth.clamp(v.length() / 120.0, 0.0, 0.15);

        // Smooth it so it doesn’t wobble/jitter
        float targetPitch = BASE_PITCH + doppler + speedWhine;
        this.pitch = Mth.lerp(0.25f, this.pitch, targetPitch);

        // Optional: small volume bump when approaching
        float targetVol = 0.85f + (float)Mth.clamp((-radial) / 120.0, 0.0, 0.20);
        this.volume = Mth.lerp(0.20f, this.volume, targetVol);
    }
}