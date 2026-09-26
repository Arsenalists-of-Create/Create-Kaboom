package com.happysg.kaboom.items;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import rbasamoyai.createbigcannons.munitions.fuzes.FuzeItem;

import java.util.List;

public class MineFuze extends FuzeItem {
    public static final String TAG_SENSITIVITY = "MineSensitivity";

    public static final int DEFAULT_SENSITIVITY = 50;
    public static final int MIN_SENSITIVITY = 1;
    public static final int MAX_SENSITIVITY = 100;

    public MineFuze(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            openScreen(hand, getSensitivity(stack));
        }
        return InteractionResultHolder.success(stack);
    }

    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    private void openScreen(InteractionHand hand, int sensitivity) {
        net.minecraft.client.Minecraft.getInstance().setScreen(new MineFuzeScreen(hand, sensitivity));
    }

    public static int getSensitivity(ItemStack stack) {
        CompoundTag tag = getCustomTag(stack);
        return tag.contains(TAG_SENSITIVITY) ? tag.getInt(TAG_SENSITIVITY) : DEFAULT_SENSITIVITY;
    }

    public static void setSensitivity(ItemStack stack, int sensitivity) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = data.copyTag();
        tag.putInt(TAG_SENSITIVITY, sensitivity);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private static CompoundTag getCustomTag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null ? data.copyTag() : new CompoundTag();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        tooltipComponents.add(Component.translatable("create_kaboom.mine_fuze.tooltip.sensitivity", getSensitivity(stack)));
    }
}
