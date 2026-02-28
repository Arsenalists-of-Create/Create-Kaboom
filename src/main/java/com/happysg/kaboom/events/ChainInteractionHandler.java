package com.happysg.kaboom.events;

import com.happysg.kaboom.block.missiles.assembly.IMissileComponent;
import com.happysg.kaboom.block.missiles.assembly.MissileAssembler;
import com.happysg.kaboom.block.missiles.chaining.AnchorPoint;
import com.happysg.kaboom.block.missiles.chaining.ChainLink;
import com.happysg.kaboom.block.missiles.chaining.ChainSystem;
import com.happysg.kaboom.block.missiles.parts.thrust.ThrusterBlockEntity;
import com.simibubi.create.AllItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import rbasamoyai.createbigcannons.munitions.big_cannon.SimpleShellBlock;

import javax.annotation.Nullable;

public class ChainInteractionHandler {

    private static final String TAG_LINKING_THRUSTER = "ChainLinkingThruster";

    //Wrench: place anchors

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onWrenchRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        Level level = player.level();

        ItemStack held = player.getItemInHand(event.getHand());
        if (!AllItems.WRENCH.isIn(held)) return;

        BlockPos clickedPos = event.getPos();
        if (!isMissileBlock(level, clickedPos)) return;

        // Let wrench handle non-missile blocks normally
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);

        if (level.isClientSide) return;

        Direction clickedFace = event.getFace();
        if (clickedFace == null) clickedFace = Direction.UP;

        ThrusterBlockEntity thrusterBE = findThrusterBE(level, clickedPos);
        if (thrusterBE == null) {
            player.displayClientMessage(Component.literal("[Chain] No thruster found in missile structure"), true);
            return;
        }

        BlockPos thrusterPos = thrusterBE.getBlockPos();
        ChainSystem chainSystem = thrusterBE.getChainSystem();
        BlockPos blockOffset = clickedPos.subtract(thrusterPos);

        AnchorPoint anchor = new AnchorPoint(blockOffset, clickedFace);
        chainSystem.addAnchor(anchor);

        player.displayClientMessage(Component.literal("[Chain] Anchor placed!"), true);
        level.playSound(null, clickedPos.getX(), clickedPos.getY(), clickedPos.getZ(),
                SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.5f, 1.2f);

        thrusterBE.notifyUpdate();
    }

    // Chain: link anchors to mobs

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        Level level = player.level();

        ItemStack held = player.getItemInHand(event.getHand());
        if (!held.is(Items.CHAIN)) return;

        BlockPos clickedPos = event.getPos();
        if (!isMissileBlock(level, clickedPos)) return;

        // Cancel on BOTH client and server to prevent vanilla chain block placement
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.CONSUME);

        if (level.isClientSide) return;

        ThrusterBlockEntity thrusterBE = findThrusterBE(level, clickedPos);
        if (thrusterBE == null) {
            player.displayClientMessage(Component.literal("[Chain] No thruster found in missile structure"), true);
            return;
        }

        BlockPos thrusterPos = thrusterBE.getBlockPos();
        ChainSystem chainSystem = thrusterBE.getChainSystem();
        BlockPos blockOffset = clickedPos.subtract(thrusterPos);

        AnchorPoint nearestAnchor = chainSystem.findNearestAnchorWithoutChain(blockOffset, 3.0);
        if (nearestAnchor == null) {
            player.displayClientMessage(Component.literal("[Chain] Place an anchor first"), true);
            return;
        }

        // Consume 1 chain
        if (!player.isCreative()) {
            held.shrink(1);
        }

        ChainLink link = new ChainLink(nearestAnchor.getId());
        nearestAnchor.setLink(link);
        chainSystem.setActiveLinker(player.getUUID(), link.getId());

        // Store thruster position on the chain item NBT so we can find it on mob click
        // After shrink the held stack may be empty, so find the next chain in inventory
        ItemStack currentHeld = player.getItemInHand(event.getHand());
        if (!currentHeld.isEmpty() && currentHeld.is(Items.CHAIN)) {
            currentHeld.getOrCreateTag().putLong(TAG_LINKING_THRUSTER, thrusterPos.asLong());
        } else {
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack s = player.getInventory().getItem(i);
                if (s.is(Items.CHAIN)) {
                    s.getOrCreateTag().putLong(TAG_LINKING_THRUSTER, thrusterPos.asLong());
                    break;
                }
            }
        }

        level.playSound(null, clickedPos.getX(), clickedPos.getY(), clickedPos.getZ(),
                SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1.0f, 1.0f);

        player.displayClientMessage(Component.literal("[Chain] Chain started! Click a mob to link."), true);
        chainSystem.populateEntityIds((net.minecraft.server.level.ServerLevel) level);
        thrusterBE.notifyUpdate();
    }

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        Level level = player.level();

        if (!(event.getTarget() instanceof Mob mob)) return;

        ItemStack held = player.getItemInHand(event.getHand());
        if (!held.is(Items.CHAIN)) return;

        CompoundTag tag = held.getTag();
        if (tag == null || !tag.contains(TAG_LINKING_THRUSTER)) return;

        // Cancel on both sides
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.CONSUME);

        if (level.isClientSide) return;

        BlockPos thrusterPos = BlockPos.of(tag.getLong(TAG_LINKING_THRUSTER));
        if (!(level.getBlockEntity(thrusterPos) instanceof ThrusterBlockEntity thrusterBE)) {
            tag.remove(TAG_LINKING_THRUSTER);
            return;
        }

        ChainSystem chainSystem = thrusterBE.getChainSystem();
        ChainLink danglingLink = chainSystem.findDanglingChainForPlayer(player.getUUID());
        if (danglingLink == null) {
            tag.remove(TAG_LINKING_THRUSTER);
            return;
        }

        // Calculate distance from anchor to mob
        AnchorPoint anchor = chainSystem.findAnchorForLink(danglingLink);
        float distance = 1f;
        if (anchor != null) {
            Vec3 anchorWorld = anchor.getWorldPos(thrusterPos);
            distance = (float) anchorWorld.distanceTo(mob.position());
        }

        int totalChainsNeeded = Math.max(1, (int) Math.ceil(distance));
        int additionalChainsNeeded = totalChainsNeeded - 1; // 1 already consumed on block click

        // Count available chains across inventory
        if (!player.isCreative() && additionalChainsNeeded > 0) {
            int available = countChains(player);
            if (available < additionalChainsNeeded) {
                player.displayClientMessage(
                        Component.literal("[Chain] Need " + (additionalChainsNeeded - available) + " more chains"), true);
                return;
            }
            consumeChains(player, additionalChainsNeeded);
        }

        // Complete the link
        danglingLink.setTargetMobId(mob.getUUID());
        danglingLink.setTargetEntityId(mob.getId());
        danglingLink.setState(ChainLink.State.TETHERED);
        danglingLink.setMaxLength(distance);

        chainSystem.clearActiveLinker();
        // Force state recalculation so the system transitions to TETHERING
        chainSystem.recalculateState();
        tag.remove(TAG_LINKING_THRUSTER);

        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(),
                SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1.0f, 0.8f);

        player.displayClientMessage(
                Component.literal("[Chain] Mob linked! Used " + totalChainsNeeded + " chain(s)."), true);
        chainSystem.populateEntityIds((net.minecraft.server.level.ServerLevel) level);
        thrusterBE.notifyUpdate();
    }

    private int countChains(Player player) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.is(Items.CHAIN)) {
                count += s.getCount();
            }
        }
        return count;
    }

    private void consumeChains(Player player, int amount) {
        int remaining = amount;
        for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.is(Items.CHAIN)) {
                int take = Math.min(s.getCount(), remaining);
                s.shrink(take);
                remaining -= take;
            }
        }
    }

    // Shared utility methods

    public static boolean isMissileBlock(Level level, BlockPos pos) {
        var block = level.getBlockState(pos).getBlock();
        return block instanceof IMissileComponent || block instanceof SimpleShellBlock<?>;
    }

    @Nullable
    public static ThrusterBlockEntity findThrusterBE(Level level, BlockPos anyMissileBlock) {
        BlockPos cursor = anyMissileBlock;
        for (int i = 0; i < MissileAssembler.MAX_VERTICAL_SCAN; i++) {
            BlockPos below = cursor.below();
            var block = level.getBlockState(below).getBlock();
            if (block instanceof IMissileComponent || block instanceof SimpleShellBlock<?>) {
                cursor = below;
            } else {
                break;
            }
        }

        BlockPos controller = MissileAssembler.findControllerThruster(level, cursor);
        if (controller != null) {
            BlockEntity be = level.getBlockEntity(controller);
            if (be instanceof ThrusterBlockEntity thrusterBE) return thrusterBE;
        }

        BlockEntity be = level.getBlockEntity(cursor);
        if (be instanceof ThrusterBlockEntity thrusterBE) return thrusterBE;

        return null;
    }
}
