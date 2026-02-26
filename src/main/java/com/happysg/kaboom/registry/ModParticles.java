package com.happysg.kaboom.registry;

import com.happysg.kaboom.CreateKaboom;
import com.happysg.kaboom.block.missiles.util.MissileAttachedParticleOptions;
import com.mojang.serialization.Codec;
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
    public static final RegistryObject<ParticleType<MissileAttachedParticleOptions>> MISSILE_ATTACHED =
            PARTICLES.register("missile_attached", () ->
                    new ParticleType<>(false, MissileAttachedParticleOptions.DESERIALIZER) {
                        @Override
                        public Codec<MissileAttachedParticleOptions> codec() {
                            // If you don't care about /particle command usage, you can still supply a simple codec:
                            return Codec.unit(new MissileAttachedParticleOptions(0, 1, 0, 0));
                        }
                    });

    public static void register(IEventBus modBus) {
        PARTICLES.register(modBus);
    }
}