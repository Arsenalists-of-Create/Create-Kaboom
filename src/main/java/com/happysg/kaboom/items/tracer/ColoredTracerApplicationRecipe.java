package com.happysg.kaboom.items.tracer;

import com.happysg.kaboom.registry.ModDataComponents;
import com.happysg.kaboom.registry.ModRecipeSerializers;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import rbasamoyai.createbigcannons.munitions.autocannon.AutocannonAmmoItem;

public class ColoredTracerApplicationRecipe extends CustomRecipe {
    public ColoredTracerApplicationRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return findInputs(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        RecipeInputs recipeInputs = findInputs(input);
        if (recipeInputs == null) {
            return ItemStack.EMPTY;
        }

        ItemStack result = recipeInputs.ammunition.copyWithCount(1);
        result.set(ModDataComponents.TRACER_COLOR, recipeInputs.color);
        return result;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.COLORED_TRACER_APPLICATION.get();
    }

    @Nullable
    private static RecipeInputs findInputs(CraftingInput input) {
        ItemStack ammunition = ItemStack.EMPTY;
        TracerColor color = null;

        for (int slot = 0; slot < input.size(); ++slot) {
            ItemStack stack = input.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }

            if (stack.getItem() instanceof AutocannonAmmoItem ammoItem) {
                if (!ammunition.isEmpty() || ammoItem.isTracer(stack)) {
                    return null;
                }
                ammunition = stack;
                continue;
            }

            if (stack.getItem() instanceof ColoredTracerTipItem tracerTip) {
                if (color != null) {
                    return null;
                }
                color = tracerTip.getTracerColor();
                continue;
            }

            return null;
        }

        return ammunition.isEmpty() || color == null ? null : new RecipeInputs(ammunition, color);
    }

    private record RecipeInputs(ItemStack ammunition, TracerColor color) {
    }
}
