package com.happysg.kaboom.sounds;

import com.happysg.kaboom.block.missiles.parts.thrust.ThrusterBlockEntity;
import com.happysg.kaboom.block.missiles.MissileEntity;
import com.happysg.kaboom.compat.sable.SableUtils;
import com.happysg.kaboom.registry.ModSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.DirectionalBlock;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class BlockMissileLaunchSound extends AbstractTickableSoundInstance {
    private static final int FADE_TICKS = 3;
    private final ThrusterBlockEntity thruster;
    private MissileEntity missile;
    private int fadeTicks;

    public BlockMissileLaunchSound(ThrusterBlockEntity thruster) {
        super(ModSounds.MISSILE_ENGINE.get(), SoundSource.AMBIENT, RandomSource.create());
        this.thruster = thruster;
        this.looping = true;
        this.volume = 8F;
        this.pitch = 1.0F;
        this.updatePosition();
    }

    @Override
    public void tick() {
        if (this.missile != null) {
            if (this.missile.isRemoved() || !this.missile.isAlive()
                    || this.missile.getEntityData().get(MissileEntity.FUEL_MB) <= 0) {
                this.stop();
                return;
            }
            this.x = this.missile.getX();
            this.y = this.missile.getY();
            this.z = this.missile.getZ();
            this.volume += (0.85F - this.volume) * 0.2F;
            return;
        }
        if (this.thruster.isRemoved() || this.thruster.getLaunchTicksRemaining() <= 0) {
            if (++this.fadeTicks >= FADE_TICKS) {
                this.stop();
                return;
            }
            this.volume = 0.35F * (1.0F - (float)this.fadeTicks / (float)FADE_TICKS);
            return;
        }
        this.updatePosition();
    }

    public void handoff(MissileEntity missile) {
        this.missile = missile;
        this.fadeTicks = 0;
    }

    private void updatePosition() {
        if (this.thruster.getLevel() == null) return;
        Direction direction = this.thruster.getBlockState().hasProperty(DirectionalBlock.FACING)
                ? this.thruster.getBlockState().getValue(DirectionalBlock.FACING) : Direction.UP;
        SableUtils.LaunchKinematics pose = SableUtils.getLaunchKinematics(this.thruster.getLevel(),
                this.thruster.getBlockPos(), this.thruster.getBlockPos().getCenter(),
                Vec3.atLowerCornerOf(direction.getNormal()));
        Vec3 nozzle = pose.position().add(pose.direction().scale(-0.55));
        this.x = nozzle.x;
        this.y = nozzle.y;
        this.z = nozzle.z;
    }
}
