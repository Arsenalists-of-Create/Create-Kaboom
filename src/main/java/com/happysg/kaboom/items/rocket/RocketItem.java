package com.happysg.kaboom.items.rocket;

import com.happysg.kaboom.registry.ModDataComponents;
import com.happysg.kaboom.registry.ModProjectiles;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import org.jetbrains.annotations.Nullable;
import rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile;
import rbasamoyai.createbigcannons.munitions.FuzedItemMunition;
import rbasamoyai.createbigcannons.index.CBCDataComponents;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public abstract class RocketItem extends Item implements FuzedItemMunition {
    protected RocketItem(Properties properties) {
        super(properties);
    }

    public static ItemStack getAttachedFuze(ItemStack rocket) {
        return rocket.getOrDefault(CBCDataComponents.FUZE, ItemContainerContents.EMPTY).copyOne();
    }

    @Nullable
    public static RocketGuidanceType getGuidanceType(ItemStack rocket) {
        return rocket.get(ModDataComponents.ROCKET_GUIDANCE);
    }

    @Nullable
    public static BlockPos getLinkedNetworkController(ItemStack rocket) {
        return rocket.get(ModDataComponents.ROCKET_NETWORK_CONTROLLER);
    }

    public static void setLinkedNetworkController(ItemStack rocket, BlockPos controllerPos) {
        rocket.set(ModDataComponents.ROCKET_NETWORK_CONTROLLER, controllerPos.immutable());
    }

    @Nullable
    public static RocketPayload getPayload(ItemStack rocket) {
        return rocket.get(ModDataComponents.ROCKET_PAYLOAD);
    }

    public static boolean hasPayload(ItemStack rocket) {
        return getPayload(rocket) != null;
    }

    public static FluidStack getFluidContent(ItemStack rocket) {
        SimpleFluidContent stored = rocket.get(ModDataComponents.ROCKET_FLUID_CONTENT);
        if (stored == null || stored.isEmpty()) {
            return FluidStack.EMPTY;
        }
        FluidStack fluid = stored.copy();
        return fluid.copyWithAmount(Math.min(fluid.getAmount(), RocketFluidHandler.CAPACITY_MB));
    }

    public static void setFluidContent(ItemStack rocket, FluidStack fluid) {
        if (fluid.isEmpty()) {
            rocket.remove(ModDataComponents.ROCKET_FLUID_CONTENT);
            return;
        }
        rocket.set(
                ModDataComponents.ROCKET_FLUID_CONTENT,
                SimpleFluidContent.copyOf(
                        fluid.copyWithAmount(Math.min(fluid.getAmount(), RocketFluidHandler.CAPACITY_MB)))
        );
    }

    protected ItemStack createConfiguredPreset(RocketPayload payload, @Nullable RocketGuidanceType guidanceType) {
        ItemStack preset = getDefaultInstance();
        preset.set(ModDataComponents.ROCKET_PAYLOAD, Objects.requireNonNull(payload, "payload"));
        if (guidanceType == null) {
            preset.remove(ModDataComponents.ROCKET_GUIDANCE);
        } else {
            preset.set(ModDataComponents.ROCKET_GUIDANCE, guidanceType);
        }
        return preset;
    }

    /**
     * Returns whether this item stack contains every component required to be launched.
     * Subclasses can impose stronger requirements for their registered rocket type.
     */
    public boolean isLaunchable(ItemStack stack) {
        return stack.getItem() == this && hasPayload(stack);
    }

    @Override
    public Component getName(ItemStack stack) {
        if (getPayload(stack) == RocketPayload.FLUID) {
            return Component.translatable(getGuidanceType(stack) == null
                    ? "item.create_kaboom.fluid_rocket"
                    : "item.create_kaboom.guided_fluid_rocket");
        }
        if (getGuidanceType(stack) != null) {
            return Component.translatable("item.create_kaboom.guided_rocket");
        }
        return super.getName(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);

        RocketPayload payload = getPayload(stack);
        if (payload == RocketPayload.FLUID) {
            FluidStack fluid = getFluidContent(stack);
            Component fluidName = fluid.isEmpty()
                    ? Component.translatable("item.create_kaboom.rocket.fluid.empty")
                    : fluid.getHoverName();
            CreateLang.builder("item")
                    .translate("create_kaboom.rocket.tooltip.fluid")
                    .add(Component.literal(" "))
                    .add(fluidName)
                    .add(Component.literal(" (" + fluid.getAmount() + " / "
                            + RocketFluidHandler.CAPACITY_MB + " mB)"))
                    .addTo(tooltip);
        } else {
            CreateLang.builder("item")
                    .translate("create_kaboom.rocket.tooltip.payload")
                    .add(Component.literal(" "))
                    .add(payload == null
                            ? Component.translatable("item.create_kaboom.rocket.payload.none")
                            : Component.translatable(payload.getTranslationKey()))
                    .addTo(tooltip);
        }

        RocketGuidanceType guidanceType = getGuidanceType(stack);
        if (guidanceType != null) {
            tooltip.add(Component.translatable(
                    "item.create_kaboom.rocket.tooltip.guidance",
                    Component.translatable(guidanceType.getTranslationKey())
            ).withStyle(ChatFormatting.GRAY));
        }

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
        return createProjectile(level, stack, launchDirection, 0.0F);
    }

    @Nullable
    public AbstractCannonProjectile createProjectile(ServerLevel level, ItemStack stack, Vec3 launchDirection,
                                                     float inaccuracy) {
        if (!isLaunchable(stack)) {
            return null;
        }
        UnguidedRocketProjectile projectile = ModProjectiles.UNGUIDED_ROCKET.create(level);
        if (projectile != null) {
            projectile.initialize(stack, launchDirection, inaccuracy);
        }
        return projectile;
    }
}
