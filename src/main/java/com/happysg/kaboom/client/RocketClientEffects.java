package com.happysg.kaboom.client;

import com.happysg.kaboom.items.rocket.UnguidedRocketProjectile;
import com.happysg.kaboom.sounds.RocketFlightSound;
import com.happysg.kaboom.sounds.RocketPodLaunchSound;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;

@OnlyIn(Dist.CLIENT)
public final class RocketClientEffects {
    private static final Map<LaunchSoundKey, RocketPodLaunchSound> ACTIVE_LAUNCH_SOUNDS = new HashMap<>();
    private static final Map<Integer, RocketFlightSound> ACTIVE_FLIGHT_SOUNDS = new HashMap<>();
    private static final Map<Integer, PendingPodHandoff> PENDING_HANDOFFS = new HashMap<>();
    private static final Set<Integer> HANDED_OFF_ROCKETS = new HashSet<>();

    private RocketClientEffects() {
    }

    public static void startPodLaunchSound(PitchOrientedContraptionEntity entity, BlockPos rearPos,
                                           int slot, long launchId, int ticksRemaining) {
        ACTIVE_LAUNCH_SOUNDS.entrySet().removeIf(entry -> entry.getValue().isStopped());
        LaunchSoundKey key = new LaunchSoundKey(entity.getId(), rearPos.immutable(), slot, launchId);
        RocketPodLaunchSound active = ACTIVE_LAUNCH_SOUNDS.get(key);
        if (active != null && active.isFor(entity) && !active.isStopped()) {
            return;
        }

        RocketPodLaunchSound sound = new RocketPodLaunchSound(entity, rearPos, ticksRemaining);
        ACTIVE_LAUNCH_SOUNDS.put(key, sound);
        Minecraft.getInstance().getSoundManager().play(sound);
    }

    public static void startFlightSound(UnguidedRocketProjectile rocket) {
        if (HANDED_OFF_ROCKETS.contains(rocket.getId()) || PENDING_HANDOFFS.containsKey(rocket.getId())) return;
        RocketFlightSound active = ACTIVE_FLIGHT_SOUNDS.get(rocket.getId());
        if (active != null && !active.isStopped()) return;
        playNewFlightSound(rocket);
    }

    public static void queuePodHandoff(int sourceEntityId, BlockPos rearPos, int slot,
                                       long launchId, int targetEntityId) {
        PENDING_HANDOFFS.put(targetEntityId, new PendingPodHandoff(
                new LaunchSoundKey(sourceEntityId, rearPos.immutable(), slot, launchId), 20));
    }

    public static void tickHandoffs() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        HANDED_OFF_ROCKETS.removeIf(id -> !(level.getEntity(id) instanceof UnguidedRocketProjectile rocket)
                || rocket.isRemoved() || !rocket.isBoosting());
        ACTIVE_LAUNCH_SOUNDS.entrySet().removeIf(entry -> entry.getValue().isStopped());
        ACTIVE_FLIGHT_SOUNDS.entrySet().removeIf(entry -> entry.getValue().isStopped());
        PENDING_HANDOFFS.entrySet().removeIf(entry -> {
            PendingPodHandoff pending = entry.getValue();
            if (!(level.getEntity(entry.getKey()) instanceof UnguidedRocketProjectile rocket)) {
                return --pending.retries <= 0;
            }
            RocketPodLaunchSound launchSound = ACTIVE_LAUNCH_SOUNDS.remove(pending.source);
            boolean handedOff = launchSound != null && !launchSound.isStopped();
            if (handedOff) {
                launchSound.handoff(rocket);
                HANDED_OFF_ROCKETS.add(rocket.getId());
            }
            RocketFlightSound replacement = ACTIVE_FLIGHT_SOUNDS.remove(rocket.getId());
            if (replacement != null && handedOff) replacement.stopSound();
            if (!handedOff) playNewFlightSound(rocket);
            return true;
        });
    }

    private static void playNewFlightSound(UnguidedRocketProjectile rocket) {
        RocketFlightSound sound = new RocketFlightSound(rocket);
        ACTIVE_FLIGHT_SOUNDS.put(rocket.getId(), sound);
        Minecraft.getInstance().getSoundManager().play(sound);
    }

    private record LaunchSoundKey(int entityId, BlockPos rearPos, int slot, long launchId) {
    }

    private static final class PendingPodHandoff {
        private final LaunchSoundKey source;
        private int retries;
        private PendingPodHandoff(LaunchSoundKey source, int retries) {
            this.source = source;
            this.retries = retries;
        }
    }
}
