package com.happysg.kaboom.client;

import com.happysg.kaboom.items.rocket.RocketGuidanceType;
import com.happysg.kaboom.items.rocket.RocketItem;
import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.TooltipHelper;
import com.simibubi.create.foundation.utility.CreateLang;
import net.createmod.catnip.lang.FontHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.ArrayList;
import java.util.List;

final class RocketTooltipHandler {
    private RocketTooltipHandler() {
    }

    static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack rocket = event.getItemStack();
        if (!(rocket.getItem() instanceof RocketItem)) {
            return;
        }

        ItemStack fuze = RocketItem.getAttachedFuze(rocket);
        RocketGuidanceType guidanceType = RocketItem.getGuidanceType(rocket);
        boolean hasFuze = !fuze.isEmpty();
        boolean hasGuidance = guidanceType != null;
        if (!hasFuze && !hasGuidance) {
            return;
        }

        boolean showFuzeInfo = hasFuze && Screen.hasShiftDown();
        boolean showGuidanceInfo = hasGuidance && !showFuzeInfo && Screen.hasControlDown();
        List<Component> modifierTooltip = new ArrayList<>();

        if (hasFuze) {
            modifierTooltip.add(modifierPrompt(
                    "item.create_kaboom.rocket.tooltip.hold_for_fuze",
                    "tooltip.keyShift",
                    showFuzeInfo
            ));
        }
        if (hasGuidance) {
            modifierTooltip.add(modifierPrompt(
                    "item.create_kaboom.rocket.tooltip.hold_for_guidance",
                    "tooltip.keyCtrl",
                    showGuidanceInfo
            ));
        }

        if (showFuzeInfo) {
            List<Component> fuzeDescription = getFuzeDescription(fuze);
            if (!fuzeDescription.isEmpty()) {
                modifierTooltip.add(CommonComponents.EMPTY);
                modifierTooltip.addAll(fuzeDescription);
            }
        } else if (showGuidanceInfo) {
            modifierTooltip.add(CommonComponents.EMPTY);
            modifierTooltip.addAll(TooltipHelper.cutTextComponent(
                    Component.translatable(guidanceType.getTranslationKey() + ".tooltip.summary"),
                    FontHelper.Palette.STANDARD_CREATE
            ));
        }

        event.getToolTip().addAll(advancedTooltipStart(event), modifierTooltip);
    }

    /**
     * Vanilla appends advanced information after normal item text. Insert the
     * modifier prompts before its durability, item-id, and component-count
     * lines instead of after them.
     */
    private static int advancedTooltipStart(ItemTooltipEvent event) {
        List<Component> tooltip = event.getToolTip();
        if (!event.getFlags().isAdvanced()) {
            return tooltip.size();
        }

        Component itemId = Component.literal(BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem()).toString())
                .withStyle(ChatFormatting.DARK_GRAY);
        int itemIdIndex = tooltip.lastIndexOf(itemId);
        if (itemIdIndex < 0) {
            return tooltip.size();
        }

        return event.getItemStack().isDamaged() ? Math.max(0, itemIdIndex - 1) : itemIdIndex;
    }

    private static MutableComponent modifierPrompt(String promptKey, String keyTranslation, boolean active) {
        Component key = CreateLang.translateDirect(keyTranslation)
                .withStyle(active ? ChatFormatting.WHITE : ChatFormatting.GRAY);
        return Component.translatable(promptKey, key).withStyle(ChatFormatting.DARK_GRAY);
    }

    private static List<Component> getFuzeDescription(ItemStack fuze) {
        ItemDescription description = ItemDescription.create(fuze.getItem(), FontHelper.Palette.STANDARD_CREATE);
        if (description == null) {
            return List.of();
        }

        List<Component> expanded = description.linesOnShift();
        for (int i = 0; i < expanded.size(); i++) {
            if (expanded.get(i).getString().isEmpty()) {
                return expanded.subList(i + 1, expanded.size());
            }
        }
        return List.of();
    }
}
