package com.happysg.kaboom.registry;

import com.happysg.kaboom.CreateKaboom;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, CreateKaboom.MODID);

    public static final RegistryObject<SoundEvent> MISSILE_ENGINE =
            SOUND_EVENTS.register("missile_engine",
                    () -> SoundEvent.createVariableRangeEvent(
                            new ResourceLocation(CreateKaboom.MODID, "missile_engine")
                    ));
    public static final RegistryObject<SoundEvent> MISSILE_LAUNCH =
            SOUND_EVENTS.register("missile_launch",
                    () -> SoundEvent.createVariableRangeEvent(
                            new ResourceLocation(CreateKaboom.MODID, "missile_launch")
                    ));
    public static final RegistryObject<SoundEvent> CRUISE_TURBINE =
            SOUND_EVENTS.register("cruise_turbine",
            () -> SoundEvent.createVariableRangeEvent(
                    new ResourceLocation(CreateKaboom.MODID, "cruise_turbine")
            ));
    public static final RegistryObject<SoundEvent> SMALL_EXPLOSION =
            SOUND_EVENTS.register("small_explosion",
                    () -> SoundEvent.createVariableRangeEvent(
                            new ResourceLocation(CreateKaboom.MODID, "small_explosion")
                    ));
    public static final RegistryObject<SoundEvent> BIG_EXPLOSION =
            SOUND_EVENTS.register("big_explosion",
                    () -> SoundEvent.createVariableRangeEvent(
                            new ResourceLocation(CreateKaboom.MODID, "big_explosion")
                    ));
    public static final RegistryObject<SoundEvent> WHISTLEFALL =
            SOUND_EVENTS.register("whistlefall",
                    () -> SoundEvent.createVariableRangeEvent(
                            new ResourceLocation(CreateKaboom.MODID, "whistlefall")
                    ));
    public static final RegistryObject<SoundEvent> ICBM_LOOP =
            SOUND_EVENTS.register("icbm_loop",
                    () -> SoundEvent.createVariableRangeEvent(
                            new ResourceLocation(CreateKaboom.MODID, "icbm_loop")
                    ));




    public static void register(IEventBus eventBus) {
        SOUND_EVENTS.register(eventBus);
    }
}