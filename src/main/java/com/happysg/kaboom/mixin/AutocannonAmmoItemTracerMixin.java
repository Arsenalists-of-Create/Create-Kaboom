package com.happysg.kaboom.mixin;

import com.happysg.kaboom.items.tracer.ColoredTracerProjectile;
import com.happysg.kaboom.items.tracer.TracerColor;
import com.happysg.kaboom.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import rbasamoyai.createbigcannons.munitions.autocannon.AbstractAutocannonProjectile;
import rbasamoyai.createbigcannons.munitions.autocannon.AutocannonCartridgeItem;
import rbasamoyai.createbigcannons.munitions.autocannon.bullet.MachineGunRoundItem;

import java.util.List;

@Mixin({AutocannonCartridgeItem.class, MachineGunRoundItem.class})
public abstract class AutocannonAmmoItemTracerMixin {
    @Inject(method = "appendHoverText", at = @At("TAIL"), remap = false)
    private void createKaboom$appendTracerColorTooltip(ItemStack stack, Item.TooltipContext context,
                                                        List<Component> tooltip, TooltipFlag flag,
                                                        CallbackInfo ci) {
        TracerColor color = stack.get(ModDataComponents.TRACER_COLOR);
        if (color != null) {
            tooltip.add(Component.translatable(
                    "item.create_kaboom.colored_tracer.tooltip",
                    Component.translatable(color.getTranslationKey())
            ).withStyle(ChatFormatting.GRAY));
        }
    }

    @Inject(method = "getAutocannonProjectile", at = @At("RETURN"), remap = false)
    private void createKaboom$transferTracerColor(ItemStack stack, Level level,
                                                   CallbackInfoReturnable<AbstractAutocannonProjectile> cir) {
        TracerColor color = stack.get(ModDataComponents.TRACER_COLOR);
        AbstractAutocannonProjectile projectile = cir.getReturnValue();
        if (color != null && projectile instanceof ColoredTracerProjectile coloredProjectile) {
            coloredProjectile.createKaboom$setTracerColor(color);
        }
    }

    @Inject(method = "setTracer", at = @At("TAIL"), remap = false)
    private void createKaboom$removeColorWithTracer(ItemStack stack, boolean tracer, CallbackInfo ci) {
        if (!tracer) {
            stack.remove(ModDataComponents.TRACER_COLOR);
        }
    }
}
