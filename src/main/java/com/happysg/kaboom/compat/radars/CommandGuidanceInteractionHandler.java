package com.happysg.kaboom.compat.radars;

import com.happysg.kaboom.block.missiles.parts.guidance.command.CommandGuidanceBlock;
import com.happysg.kaboom.block.missiles.parts.guidance.command.CommandGuidanceLink;
import com.happysg.kaboom.items.rocket.RocketGuidanceType;
import com.happysg.kaboom.items.rocket.RocketItem;
import com.happysg.radar.block.controller.networkcontroller.NetworkFiltererBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

final class CommandGuidanceInteractionHandler {
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack held = event.getItemStack();
        boolean isCommandGuidanceBlock = held.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() instanceof CommandGuidanceBlock;
        boolean isCommandGuidanceRocket = held.getItem() instanceof RocketItem
                && RocketItem.getGuidanceType(held) == RocketGuidanceType.COMMAND;
        if (!isCommandGuidanceBlock && !isCommandGuidanceRocket) {
            return;
        }

        Level level = event.getLevel();
        BlockEntity clickedBlockEntity = level.getBlockEntity(event.getPos());
        if (!(clickedBlockEntity instanceof NetworkFiltererBlockEntity networkController)) {
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
        if (level.isClientSide) {
            return;
        }

        CommandGuidanceLink.setControllerPos(held, networkController.getBlockPos());
        event.getEntity().displayClientMessage(
                Component.literal("Paired " + (isCommandGuidanceRocket ? "command guidance rocket" : "command guidance")
                        + " to network controller at "
                        + networkController.getBlockPos().toShortString()),
                true
        );
    }
}
