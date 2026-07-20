package com.happysg.kaboom.items.rocket;

import com.happysg.kaboom.registry.ModProjectiles;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile;
import rbasamoyai.createbigcannons.munitions.FuzedItemMunition;
import rbasamoyai.createbigcannons.index.CBCDataComponents;

import java.util.ArrayList;
import java.util.List;

public class RocketItem extends Item implements FuzedItemMunition {
    public RocketItem(Properties properties) {
        super(properties);
    }

    // TODO: Add configurable rocket payload variants.

    public static ItemStack getAttachedFuze(ItemStack rocket) {
        return rocket.getOrDefault(CBCDataComponents.FUZE, ItemContainerContents.EMPTY).copyOne();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        ItemStack fuze = getAttachedFuze(stack);
        if (fuze.isEmpty()) {
            return;
        }

        CreateLang.builder("block")
                .translate("createbigcannons.shell.tooltip.fuze")
                .add(Component.literal(" "))
                .add(fuze.getDisplayName().copy())
                .addTo(tooltip);

        List<Component> fuzeTooltip = new ArrayList<>();
        fuze.getItem().appendHoverText(fuze, context, fuzeTooltip, flag);
        fuzeTooltip.replaceAll(component -> Component.literal("  ")
                .append(component)
                .withStyle(ChatFormatting.GRAY));
        tooltip.addAll(fuzeTooltip);
    }

    @Nullable
    public AbstractCannonProjectile createProjectile(ServerLevel level, ItemStack stack, Vec3 launchDirection) {
        UnguidedRocketProjectile projectile = ModProjectiles.UNGUIDED_ROCKET.create(level);
        if (projectile != null) {
            projectile.initialize(stack, launchDirection);
        }
        return projectile;
    }
}
