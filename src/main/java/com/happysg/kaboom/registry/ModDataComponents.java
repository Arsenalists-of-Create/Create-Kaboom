package com.happysg.kaboom.registry;

import com.happysg.kaboom.CreateKaboom;
import com.happysg.kaboom.items.rocket.RocketGuidanceType;
import com.happysg.kaboom.items.rocket.RocketPayload;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModDataComponents {
    public static final DeferredRegister.DataComponents DATA_COMPONENTS = DeferredRegister.createDataComponents(
            Registries.DATA_COMPONENT_TYPE,
            CreateKaboom.MODID
    );

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<RocketGuidanceType>> ROCKET_GUIDANCE =
            DATA_COMPONENTS.registerComponentType("rocket_guidance", builder -> builder
                    .persistent(RocketGuidanceType.CODEC)
                    .networkSynchronized(RocketGuidanceType.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<RocketPayload>> ROCKET_PAYLOAD =
            DATA_COMPONENTS.registerComponentType("rocket_payload", builder -> builder
                    .persistent(RocketPayload.CODEC)
                    .networkSynchronized(RocketPayload.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<FluidStack>> ROCKET_FLUID_CONTENT =
            DATA_COMPONENTS.registerComponentType("rocket_fluid_content", builder -> builder
                    .persistent(FluidStack.CODEC)
                    .networkSynchronized(FluidStack.STREAM_CODEC));

    private ModDataComponents() {
    }

    public static void register(IEventBus eventBus) {
        CreateKaboom.getLogger().info("Registering Data Components!");
        DATA_COMPONENTS.register(eventBus);
    }
}
