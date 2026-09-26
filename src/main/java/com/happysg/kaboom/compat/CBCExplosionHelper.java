package com.happysg.kaboom.compat;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import rbasamoyai.createbigcannons.CreateBigCannons;
import rbasamoyai.createbigcannons.munitions.ImpactExplosion;
import rbasamoyai.createbigcannons.munitions.ShellExplosion;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public class CBCExplosionHelper {

    private static void handleExplosion(Level level, Object expl) {
        try {
            for (Method m : CreateBigCannons.class.getMethods()) {
                if (m.getName().equals("handleCustomExplosion") && m.getParameterCount() == 2) {
                    m.invoke(null, level, expl);
                    return;
                }
            }
        } catch (Throwable ignored) {}
    }

    public static void createShellExplosion(
            Level level,
            Entity exploder,
            DamageSource damageSource,
            double x, double y, double z,
            float radius,
            boolean isPlume,
            Explosion.BlockInteraction blockInteraction
    ) {
        if (level.isClientSide) return;
        try {
            boolean handled = false;
            for (Constructor<?> ctor : ShellExplosion.class.getConstructors()) {
                Class<?>[] pts = ctor.getParameterTypes();
                if (pts.length == 10 && pts[0] == Level.class && pts[6] == float.class && pts[7] == float.class) {
                    Object expl = ctor.newInstance(level, exploder, damageSource, x, y, z, radius, radius, isPlume, blockInteraction);
                    handleExplosion(level, expl);
                    handled = true;
                    break;
                } else if (pts.length == 9 && pts[0] == Level.class && pts[6] == float.class && pts[7] == boolean.class) {
                    Object expl = ctor.newInstance(level, exploder, damageSource, x, y, z, radius, isPlume, blockInteraction);
                    handleExplosion(level, expl);
                    handled = true;
                    break;
                }
            }
            if (!handled) {
                level.explode(exploder, damageSource, null, x, y, z, radius, isPlume, Level.ExplosionInteraction.BLOCK);
            }
        } catch (Throwable t) {
            level.explode(exploder, damageSource, null, x, y, z, radius, isPlume, Level.ExplosionInteraction.BLOCK);
        }
    }

    public static void createImpactExplosion(
            Level level,
            Entity exploder,
            DamageSource damageSource,
            double x, double y, double z,
            float radius,
            Explosion.BlockInteraction blockInteraction
    ) {
        if (level.isClientSide) return;
        try {
            boolean handled = false;
            for (Constructor<?> ctor : ImpactExplosion.class.getConstructors()) {
                Class<?>[] pts = ctor.getParameterTypes();
                if (pts.length == 8 && pts[0] == Level.class && pts[6] == float.class && pts[7] == float.class) {
                    Object expl = ctor.newInstance(level, exploder, damageSource, x, y, z, radius, radius, blockInteraction);
                    handleExplosion(level, expl);
                    handled = true;
                    break;
                } else if (pts.length == 7 && pts[0] == Level.class && pts[6] == float.class) {
                    Object expl = ctor.newInstance(level, exploder, damageSource, x, y, z, radius, blockInteraction);
                    handleExplosion(level, expl);
                    handled = true;
                    break;
                }
            }
            if (!handled) {
                level.explode(exploder, damageSource, null, x, y, z, radius, false, Level.ExplosionInteraction.BLOCK);
            }
        } catch (Throwable t) {
            level.explode(exploder, damageSource, null, x, y, z, radius, false, Level.ExplosionInteraction.BLOCK);
        }
    }
}
