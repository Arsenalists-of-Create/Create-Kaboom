package com.happysg.kaboom.registry;

import com.happysg.kaboom.CreateKaboom;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, CreateKaboom.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> MISSILE_ENGINE =
            SOUND_EVENTS.register("missile_engine",
                    () -> SoundEvent.createVariableRangeEvent(
                            ResourceLocation.fromNamespaceAndPath(CreateKaboom.MODID, "missile_engine")
                    ));
    public static final DeferredHolder<SoundEvent, SoundEvent> MISSILE_LAUNCH =
            SOUND_EVENTS.register("missile_launch",
                    () -> SoundEvent.createVariableRangeEvent(
                            ResourceLocation.fromNamespaceAndPath(CreateKaboom.MODID, "missile_launch")
                    ));
    public static final DeferredHolder<SoundEvent, SoundEvent> CRUISE_TURBINE =
            SOUND_EVENTS.register("cruise_turbine",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateKaboom.MODID, "cruise_turbine")
            ));
    public static final DeferredHolder<SoundEvent, SoundEvent> SMALL_EXPLOSION =
            SOUND_EVENTS.register("small_explosion",
                    () -> SoundEvent.createVariableRangeEvent(
                            ResourceLocation.fromNamespaceAndPath(CreateKaboom.MODID, "small_explosion")
                    ));
    public static final DeferredHolder<SoundEvent, SoundEvent> BIG_EXPLOSION =
            SOUND_EVENTS.register("big_explosion",
                    () -> SoundEvent.createVariableRangeEvent(
                            ResourceLocation.fromNamespaceAndPath(CreateKaboom.MODID, "big_explosion")
                    ));
    public static final DeferredHolder<SoundEvent, SoundEvent> WHISTLEFALL =
            SOUND_EVENTS.register("whistlefall",
                    () -> SoundEvent.createVariableRangeEvent(
                            ResourceLocation.fromNamespaceAndPath(CreateKaboom.MODID, "whistlefall")
                    ));
    public static final DeferredHolder<SoundEvent, SoundEvent> ICBM_LOOP =
            SOUND_EVENTS.register("icbm_loop",
                    () -> SoundEvent.createVariableRangeEvent(
                            ResourceLocation.fromNamespaceAndPath(CreateKaboom.MODID, "icbm_loop")
                    ));

    public static void register(IEventBus eventBus) {
        SOUND_EVENTS.register(eventBus);
    }
}
