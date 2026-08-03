package com.happysg.kaboom.block.missiles.parts.guidance.command;

import com.happysg.kaboom.registry.ModRecipeSerializers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class CommandGuidanceLinkRecipe extends CustomRecipe {
    public CommandGuidanceLinkRecipe(CraftingBookCategory category) {
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

        ItemStack result = recipeInputs.unlinked().copyWithCount(1);
        CommandGuidanceLink.setControllerPos(result, recipeInputs.controllerPos());
        return result;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        RecipeInputs recipeInputs = findInputs(input);
        if (recipeInputs != null) {
            remaining.set(recipeInputs.linkedSlot(), recipeInputs.linked().copyWithCount(1));
        }
        return remaining;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.COMMAND_GUIDANCE_LINK.get();
    }

    @Nullable
    private static RecipeInputs findInputs(CraftingInput input) {
        ItemStack linked = ItemStack.EMPTY;
        ItemStack unlinked = ItemStack.EMPTY;
        BlockPos controllerPos = null;
        int linkedSlot = -1;

        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack stack = input.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (!CommandGuidanceLink.isLinkable(stack)) {
                return null;
            }

            BlockPos stackControllerPos = CommandGuidanceLink.getControllerPos(stack);
            if (stackControllerPos == null) {
                if (!unlinked.isEmpty()) {
                    return null;
                }
                unlinked = stack;
            } else {
                if (!linked.isEmpty()) {
                    return null;
                }
                linked = stack;
                linkedSlot = slot;
                controllerPos = stackControllerPos;
            }
        }

        return linked.isEmpty() || unlinked.isEmpty() || controllerPos == null
                ? null
                : new RecipeInputs(linked, unlinked, linkedSlot, controllerPos);
    }

    private record RecipeInputs(ItemStack linked, ItemStack unlinked, int linkedSlot, BlockPos controllerPos) {
    }
}
