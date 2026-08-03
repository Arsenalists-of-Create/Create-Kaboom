package com.happysg.kaboom.events;

import com.happysg.kaboom.items.rocket.UnguidedRocketItem;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public class ShoulderRocketInteractionHandler {
    @SubscribeEvent
    public void onLivingDamage(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof Player player
                && event.getNewDamage() > 0.0F
                && event.getSource().is(DamageTypeTags.IS_FIRE)) {
            UnguidedRocketItem.applyMishapFireTickDamage(player);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        beginShoulderUse(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        beginShoulderUse(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        beginShoulderUse(event);
    }

    private static void beginShoulderUse(PlayerInteractEvent event) {
        Player player = event.getEntity();
        InteractionHand rocketHand = findRocketHand(player);
        if (rocketHand == null) {
            return;
        }
        ItemStack rocketStack = player.getItemInHand(rocketHand);
        if (!(rocketStack.getItem() instanceof UnguidedRocketItem rocketItem)
                || !rocketItem.canStartShoulderUse(player, rocketHand)) {
            return;
        }

        player.startUsingItem(rocketHand);
        InteractionResult result = InteractionResult.sidedSuccess(event.getLevel().isClientSide);
        if (event instanceof PlayerInteractEvent.RightClickBlock rightClickBlock) {
            rightClickBlock.setCanceled(true);
            rightClickBlock.setCancellationResult(result);
        } else if (event instanceof PlayerInteractEvent.EntityInteractSpecific entityInteract) {
            entityInteract.setCanceled(true);
            entityInteract.setCancellationResult(result);
        } else if (event instanceof PlayerInteractEvent.RightClickItem rightClickItem) {
            rightClickItem.setCanceled(true);
            rightClickItem.setCancellationResult(result);
        }
    }

    private static InteractionHand findRocketHand(Player player) {
        if (UnguidedRocketItem.hasShoulderPair(player, InteractionHand.MAIN_HAND)) {
            return InteractionHand.MAIN_HAND;
        }
        if (UnguidedRocketItem.hasShoulderPair(player, InteractionHand.OFF_HAND)) {
            return InteractionHand.OFF_HAND;
        }
        return null;
    }
}
