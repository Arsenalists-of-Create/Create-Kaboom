package com.happysg.kaboom.block.missiles.parts.guidance.command;

import com.happysg.kaboom.items.rocket.RocketGuidanceType;
import com.happysg.kaboom.items.rocket.RocketItem;
import com.happysg.kaboom.registry.ModBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

public final class CommandGuidanceLink {
    private CommandGuidanceLink() {
    }

    public static boolean isLinkable(ItemStack stack) {
        return (stack.getItem() instanceof RocketItem
                && RocketItem.getGuidanceType(stack) == RocketGuidanceType.COMMAND
                || stack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() instanceof CommandGuidanceBlock);
    }

    @Nullable
    public static BlockPos getControllerPos(ItemStack stack) {
        if (stack.getItem() instanceof RocketItem
                && RocketItem.getGuidanceType(stack) == RocketGuidanceType.COMMAND) {
            return RocketItem.getLinkedNetworkController(stack);
        }
        if (!(stack.getItem() instanceof BlockItem blockItem)
                || !(blockItem.getBlock() instanceof CommandGuidanceBlock)) {
            return null;
        }

        CustomData blockEntityData = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (blockEntityData == null) {
            return null;
        }
        CompoundTag tag = blockEntityData.copyTag();
        return tag.contains(CommandGuidanceBlockEntity.TAG_NETWORK_CONTROLLER_POS)
                ? NbtUtils.readBlockPos(tag, CommandGuidanceBlockEntity.TAG_NETWORK_CONTROLLER_POS).orElse(null)
                : null;
    }

    public static void setControllerPos(ItemStack stack, BlockPos controllerPos) {
        if (stack.getItem() instanceof RocketItem
                && RocketItem.getGuidanceType(stack) == RocketGuidanceType.COMMAND) {
            RocketItem.setLinkedNetworkController(stack, controllerPos);
        } else if (stack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() instanceof CommandGuidanceBlock) {
            BlockItem.setBlockEntityData(
                    stack,
                    ModBlockEntityTypes.COMMAND_GUIDANCE.get(),
                    CommandGuidanceBlockEntity.tagForNetworkController(controllerPos)
            );
        }
    }
}
