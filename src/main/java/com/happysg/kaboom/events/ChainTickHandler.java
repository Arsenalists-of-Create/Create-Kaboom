package com.happysg.kaboom.events;

import com.happysg.kaboom.block.missiles.parts.thrust.ThrusterBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public class ChainTickHandler {

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
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
