package com.happysg.kaboom.events;

import com.happysg.kaboom.block.missiles.parts.thrust.ThrusterBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class ChainTickHandler {

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        var pending = ThrusterBlockEntity.PENDING_ENFORCEMENTS;
        if (pending.isEmpty()) return;

        for (var entry : pending) {
            BlockEntity be = entry.level().getBlockEntity(entry.pos());
            if (be instanceof ThrusterBlockEntity thruster) {
                thruster.getChainSystem().enforceConstraintsFromBlock(entry.pos(), entry.level());
            }
        }
        pending.clear();
    }
}
