package com.happysg.kaboom.registry;

import com.happysg.kaboom.CreateKaboom;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, CreateKaboom.MODID);

    public static final RegistryObject<SimpleParticleType> MISSILE_SMOKE =
            PARTICLES.register("missile_smoke", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> ENGINE_GLOW =
            PARTICLES.register("engine_glow", () -> new SimpleParticleType(false));

    public static void register(IEventBus modBus) {
        PARTICLES.register(modBus);
    }
}