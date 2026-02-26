package com.happysg.kaboom.registry;

import com.happysg.kaboom.CreateKaboom;
import com.happysg.kaboom.particles.MissileAttachedParticleProvider;
import com.happysg.kaboom.particles.MissileSmokeParticle;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CreateKaboom.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ModClient {
    @SubscribeEvent
    public static void registerParticleFactories(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.MISSILE_SMOKE.get(), MissileSmokeParticle.Provider::new);
        event.registerSpriteSet(ModParticles.MISSILE_ATTACHED.get(), MissileAttachedParticleProvider::new);
    }
}