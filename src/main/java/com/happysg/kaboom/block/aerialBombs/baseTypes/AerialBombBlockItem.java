package com.happysg.kaboom.block.aerialBombs.baseTypes;

import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.List;

public class AerialBombBlockItem extends BlockItem {

    public AerialBombBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    public static ItemStack getFuze(ItemStack bomb, HolderLookup.Provider registries) {
        CustomData data = bomb.get(DataComponents.BLOCK_ENTITY_DATA);
        if (data == null) {
            return ItemStack.EMPTY;
        }

        CompoundTag tag = data.copyTag();
        ItemStack fuze = AerialBombBlockEntity.readFuze(tag, 0, registries);
        if (!fuze.isEmpty()) {
            return fuze;
        }

        // Read the short-lived merged format so items created by affected builds are not lost.
        if (tag.contains("KaboomFuzes", Tag.TAG_LIST)) {
            ListTag list = tag.getList("KaboomFuzes", Tag.TAG_COMPOUND);
            if (!list.isEmpty()) {
                return ItemStack.parseOptional(registries, list.getCompound(0).getCompound("Fuze"));
            }
        }
        return ItemStack.EMPTY;
    }

    public static void setFuze(ItemStack bomb, ItemStack fuze, BlockEntityType<?> blockEntityType,
                               HolderLookup.Provider registries) {
        CompoundTag tag = AerialBombBlockEntity.singleFuzeTag(fuze, registries);
        BlockItem.setBlockEntityData(bomb, blockEntityType, tag);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        ItemStack fuze = getFuze(stack, context.registries());
        if (fuze.isEmpty()) {
            return;
        }

        tooltip.add(Component.translatable("block.createbigcannons.shell.tooltip.fuze")
                .append(" [")
                .append(fuze.getHoverName())
                .append("]")
                .withStyle(ChatFormatting.WHITE));
    }
}
