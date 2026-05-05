package com.happysg.kaboom.registry;

import com.happysg.kaboom.CreateKaboom;
import com.happysg.kaboom.particles.MissileAttachedParticleProvider;
import com.happysg.kaboom.particles.MissileSmokeParticle;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;

@EventBusSubscriber(modid = CreateKaboom.MODID, value = Dist.CLIENT)
public class ModClient {
    @SubscribeEvent
    public static void registerParticleFactories(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.MISSILE_SMOKE.get(), MissileSmokeParticle.Provider::new);
        event.registerSpriteSet(ModParticles.MISSILE_ATTACHED.get(), MissileAttachedParticleProvider::new);
    }
}
