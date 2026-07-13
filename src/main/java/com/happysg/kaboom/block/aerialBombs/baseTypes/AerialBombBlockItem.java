package com.happysg.kaboom.block.aerialBombs.baseTypes;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;

public class AerialBombBlockItem extends BlockItem {

    public AerialBombBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        net.minecraft.world.item.component.CustomData customData = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (customData != null) {
            CompoundTag tag = customData.copyTag();
            if (tag.contains("KaboomFuzes")) {
                ListTag list = tag.getList("KaboomFuzes", 10);
                List<ItemStack> fuzes = new ArrayList<>();
                for (int i = 0; i < list.size(); i++) {
                    CompoundTag fuzeTag = list.getCompound(i);
                    ItemStack fuze = ItemStack.parseOptional(context.registries(), fuzeTag.getCompound("Fuze"));
                    if (!fuze.isEmpty()) {
                        fuzes.add(fuze);
                    }
                }
                if (!fuzes.isEmpty()) {
                    for (ItemStack fuze : fuzes) {
                        tooltip.add(Component.translatable("block.createbigcannons.shell.tooltip.fuze")
                                .append(" [")
                                .append(fuze.getHoverName())
                                .append("]")
                                .withStyle(ChatFormatting.WHITE));
                    }
                }
            }
        }
    }
}
