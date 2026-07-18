package com.happysg.kaboom.registry;

import com.happysg.kaboom.CreateKaboom;
import com.happysg.kaboom.block.missiles.util.MissileAttachedParticleOptions;
import com.happysg.kaboom.block.missiles.util.MissileLaunchSmokeOptions;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, CreateKaboom.MODID);

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> MISSILE_SMOKE =
            PARTICLES.register("missile_smoke", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, ParticleType<MissileLaunchSmokeOptions>> MISSILE_LAUNCH_SMOKE =
            PARTICLES.register("missile_launch_smoke", () -> new ParticleType<>(false) {
                @Override
                public MapCodec<MissileLaunchSmokeOptions> codec() {
                    return MissileLaunchSmokeOptions.CODEC;
                }

                @Override
                public StreamCodec<? super RegistryFriendlyByteBuf, MissileLaunchSmokeOptions> streamCodec() {
                    return MissileLaunchSmokeOptions.STREAM_CODEC;
                }
            });
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> ROCKET_LAUNCH_SMOKE =
            PARTICLES.register("rocket_launch_smoke", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, ParticleType<MissileAttachedParticleOptions>> MISSILE_ATTACHED =
            PARTICLES.register("missile_attached", () ->
                    new ParticleType<>(false) {
                        @Override
                        public MapCodec<MissileAttachedParticleOptions> codec() {
                            return MissileAttachedParticleOptions.CODEC;
                        }

                        @Override
                        public StreamCodec<? super RegistryFriendlyByteBuf, MissileAttachedParticleOptions> streamCodec() {
                            return MissileAttachedParticleOptions.STREAM_CODEC;
                        }
                    });

    public static void register(IEventBus modBus) {
        PARTICLES.register(modBus);
    }
}
