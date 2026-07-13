package com.happysg.kaboom.commands;

import com.happysg.kaboom.CreateKaboom;
import com.happysg.kaboom.block.missiles.chaining.AnchorPoint;
import com.happysg.kaboom.block.missiles.chaining.ChainLink;
import com.happysg.kaboom.block.missiles.chaining.ChainSystem;
import com.happysg.kaboom.block.missiles.parts.thrust.ThrusterBlockEntity;
import com.mojang.brigadier.Command;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class SelfChainCommand {

    private static final String TAG_LINKING_THRUSTER = "ChainLinkingThruster";
    private static final float PLAYER_CHAIN_SLACK = 2.0f;

    private SelfChainCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("kaboom_self_chain")
                        .executes(context -> chainSelf(context.getSource()))
        );
    }

    private static int chainSelf(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("[Chain] This command must be run by a player"));
            return 0;
        }

        ItemStack chainStack = findLinkingChain(player);
        if (chainStack.isEmpty()) {
            source.sendFailure(Component.literal("[Chain] Start a chain from a missile anchor first"));
            return 0;
        }

        CompoundTag tag = getCustomTag(chainStack);
        BlockPos thrusterPos = BlockPos.of(tag.getLong(TAG_LINKING_THRUSTER));
        BlockEntity be = player.level().getBlockEntity(thrusterPos);
        if (!(be instanceof ThrusterBlockEntity thrusterBE)) {
            tag.remove(TAG_LINKING_THRUSTER);
            setCustomTag(chainStack, tag);
            source.sendFailure(Component.literal("[Chain] Linked missile thruster is gone"));
            return 0;
        }

        ChainSystem chainSystem = thrusterBE.getChainSystem();
        ChainLink danglingLink = chainSystem.findDanglingChainForPlayer(player.getUUID());
        if (danglingLink == null) {
            tag.remove(TAG_LINKING_THRUSTER);
            setCustomTag(chainStack, tag);
            source.sendFailure(Component.literal("[Chain] No dangling chain found"));
            return 0;
        }

        AnchorPoint anchor = chainSystem.findAnchorForLink(danglingLink);
        float distance = 1f;
        if (anchor != null) {
            Vec3 anchorWorld = anchor.getWorldPos(thrusterPos);
            distance = (float) anchorWorld.distanceTo(player.position());
        }

        int totalChainsNeeded = Math.max(1, (int) Math.ceil(distance));
        int additionalChainsNeeded = totalChainsNeeded - 1;

        if (!player.isCreative() && additionalChainsNeeded > 0) {
            int available = countChains(player);
            if (available < additionalChainsNeeded) {
                source.sendFailure(Component.literal("[Chain] Need " + (additionalChainsNeeded - available) + " more chains"));
                return 0;
            }
            consumeChains(player, additionalChainsNeeded);
        }

        danglingLink.setTargetMobId(player.getUUID());
        danglingLink.setTargetEntityId(player.getId());
        danglingLink.setState(ChainLink.State.TETHERED);
        danglingLink.setMaxLength(distance + PLAYER_CHAIN_SLACK);

        chainSystem.clearActiveLinker();
        chainSystem.recalculateState();
        tag.remove(TAG_LINKING_THRUSTER);
        setCustomTag(chainStack, tag);

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1.0f, 0.8f);
        chainSystem.populateEntityIds(player.serverLevel());
        thrusterBE.notifyUpdate();

        source.sendSuccess(
                () -> Component.literal("[Chain] Chained yourself to the missile using " + totalChainsNeeded + " chain(s)."),
                false
        );
        return Command.SINGLE_SUCCESS;
    }

    private static ItemStack findLinkingChain(ServerPlayer player) {
        ItemStack mainHand = player.getMainHandItem();
        if (isLinkingChain(mainHand)) return mainHand;

        ItemStack offhand = player.getOffhandItem();
        if (isLinkingChain(offhand)) return offhand;

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (isLinkingChain(stack)) return stack;
        }
        return ItemStack.EMPTY;
    }

    private static boolean isLinkingChain(ItemStack stack) {
        return stack.is(Items.CHAIN) && getCustomTag(stack).contains(TAG_LINKING_THRUSTER);
    }

    private static int countChains(ServerPlayer player) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(Items.CHAIN)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void consumeChains(ServerPlayer player, int amount) {
        int remaining = amount;
        for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(Items.CHAIN)) {
                int take = Math.min(stack.getCount(), remaining);
                stack.shrink(take);
                remaining -= take;
            }
        }
    }

    private static CompoundTag getCustomTag(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    private static void setCustomTag(ItemStack stack, CompoundTag tag) {
        CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
    }
}
