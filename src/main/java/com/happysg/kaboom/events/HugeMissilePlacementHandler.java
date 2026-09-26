package com.happysg.kaboom.events;

import com.happysg.kaboom.CreateKaboom;
import com.happysg.kaboom.block.missiles.parts.HugeMissileOverlap;
import com.happysg.kaboom.block.missiles.parts.HugeMissileReservations;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

@EventBusSubscriber(modid = CreateKaboom.MODID)
public final class HugeMissilePlacementHandler {
    private static final Map<ServerLevel, Set<ChunkPos>> PENDING_CHUNK_RECONCILIATIONS =
            new ConcurrentHashMap<>();

    private HugeMissilePlacementHandler() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }

        Map<BlockPos, BlockState> placements = new LinkedHashMap<>();
        boolean replacedReservation = false;
        if (event instanceof BlockEvent.EntityMultiPlaceEvent multiPlaceEvent) {
            for (BlockSnapshot snapshot : multiPlaceEvent.getReplacedBlockSnapshots()) {
                placements.put(snapshot.getPos(), snapshot.getCurrentState());
                replacedReservation |= HugeMissileReservations.isReservation(snapshot.getState());
            }
        } else {
            BlockSnapshot snapshot = event.getBlockSnapshot();
            placements.put(snapshot.getPos(), snapshot.getCurrentState());
            replacedReservation = HugeMissileReservations.isReservation(snapshot.getState());
        }

        if (replacedReservation
                || !HugeMissileReservations.canReservePlacements(event.getLevel(), placements)
                || HugeMissileOverlap.hasPlacementConflict(
                        event.getLevel(), placements, event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (event.getLevel() instanceof Level level
                && HugeMissileOverlap.isHugeMissilePart(event.getState())) {
            HugeMissileReservations.reconcileOwner(level, event.getPos(), event.getState());
        }
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        PENDING_CHUNK_RECONCILIATIONS.compute(level, (ignored, pendingChunks) -> {
            Set<ChunkPos> chunks = pendingChunks == null
                    ? ConcurrentHashMap.newKeySet()
                    : pendingChunks;
            chunks.add(event.getChunk().getPos());
            return chunks;
        });
    }

    @SubscribeEvent
    public static void onLevelTickPost(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Set<ChunkPos> pendingChunks = PENDING_CHUNK_RECONCILIATIONS.remove(level);
        if (pendingChunks == null) {
            return;
        }
        HugeMissileReservations.reconcileLoadedAreas(level, pendingChunks);
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            PENDING_CHUNK_RECONCILIATIONS.remove(level);
        }
    }
}
