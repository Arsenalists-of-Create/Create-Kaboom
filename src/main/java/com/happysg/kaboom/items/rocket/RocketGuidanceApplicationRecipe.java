package com.happysg.kaboom.items.rocket;

import com.happysg.kaboom.block.missiles.parts.guidance.command.CommandGuidanceLink;
import com.happysg.kaboom.registry.ModBlocks;
import com.happysg.kaboom.registry.ModDataComponents;
import com.happysg.kaboom.registry.ModItems;
import com.happysg.kaboom.registry.ModRecipeSerializers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class RocketGuidanceApplicationRecipe extends CustomRecipe {
    public RocketGuidanceApplicationRecipe(CraftingBookCategory category) {
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

        ItemStack result = recipeInputs.rocket().transmuteCopy(ModItems.GUIDED_ROCKET.get(), 1);
        result.set(ModDataComponents.ROCKET_GUIDANCE, recipeInputs.guidanceType());
        result.remove(ModDataComponents.ROCKET_NETWORK_CONTROLLER);

        if (recipeInputs.guidanceType() == RocketGuidanceType.COMMAND) {
            BlockPos controllerPos = CommandGuidanceLink.getControllerPos(recipeInputs.guidanceUnit());
            if (controllerPos != null) {
                RocketItem.setLinkedNetworkController(result, controllerPos);
            }
        }
        return result;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.ROCKET_GUIDANCE_APPLICATION.get();
    }

    @Nullable
    private static RecipeInputs findInputs(CraftingInput input) {
        ItemStack rocket = ItemStack.EMPTY;
        ItemStack guidanceUnit = ItemStack.EMPTY;
        RocketGuidanceType guidanceType = null;

        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack stack = input.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }

            if (stack.getItem() instanceof UnguidedRocketItem rocketItem) {
                if (!rocket.isEmpty() || !rocketItem.isLaunchable(stack)) {
                    return null;
                }
                rocket = stack;
                continue;
            }

            RocketGuidanceType stackGuidanceType = guidanceType(stack);
            if (stackGuidanceType == null || !guidanceUnit.isEmpty()) {
                return null;
            }
            guidanceUnit = stack;
            guidanceType = stackGuidanceType;
        }

        return rocket.isEmpty() || guidanceUnit.isEmpty() || guidanceType == null
                ? null
                : new RecipeInputs(rocket, guidanceUnit, guidanceType);
    }

    @Nullable
    private static RocketGuidanceType guidanceType(ItemStack stack) {
        if (stack.is(ModBlocks.COMMAND_GUIDANCE_SMALL.asItem())) {
            return RocketGuidanceType.COMMAND;
        }
        if (stack.is(ModBlocks.RADAR_GUIDANCE_SMALL.asItem())) {
            return RocketGuidanceType.RADAR;
        }
        return null;
    }

    private record RecipeInputs(ItemStack rocket, ItemStack guidanceUnit, RocketGuidanceType guidanceType) {
    }
}
