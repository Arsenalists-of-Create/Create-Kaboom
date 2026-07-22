package com.happysg.kaboom.registry;

import com.happysg.kaboom.CreateKaboom;
import com.happysg.kaboom.items.tracer.ColoredTracerApplicationRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRecipeSerializers {
    private static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, CreateKaboom.MODID);

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<ColoredTracerApplicationRecipe>>
            COLORED_TRACER_APPLICATION = RECIPE_SERIALIZERS.register(
                    "colored_tracer_application",
                    () -> new SimpleCraftingRecipeSerializer<>(ColoredTracerApplicationRecipe::new)
            );

    private ModRecipeSerializers() {
    }

    public static void register(IEventBus eventBus) {
        RECIPE_SERIALIZERS.register(eventBus);
    }
}
