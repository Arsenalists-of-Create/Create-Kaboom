package com.happysg.kaboom.events;

import com.happysg.kaboom.block.missiles.parts.guidance.command.CommandGuidanceBlock;
import com.happysg.kaboom.block.missiles.parts.guidance.command.CommandGuidanceBlockEntity;
import com.happysg.kaboom.registry.ModBlockEntityTypes;
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

public class CommandGuidanceInteractionHandler {
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack held = event.getItemStack();
        if (!(held.getItem() instanceof BlockItem blockItem) || !(blockItem.getBlock() instanceof CommandGuidanceBlock)) {
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

        BlockItem.setBlockEntityData(
                held,
                ModBlockEntityTypes.COMMAND_GUIDANCE.get(),
                CommandGuidanceBlockEntity.tagForNetworkController(networkController.getBlockPos())
        );

        event.getEntity().displayClientMessage(
                Component.literal("Paired command guidance to network controller at " + networkController.getBlockPos().toShortString()),
                true
        );
    }
}
