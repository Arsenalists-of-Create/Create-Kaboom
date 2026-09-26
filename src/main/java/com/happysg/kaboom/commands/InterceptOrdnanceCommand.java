package com.happysg.kaboom.commands;

import com.happysg.kaboom.CreateKaboom;
import com.happysg.kaboom.interception.InterceptableOrdnance;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = CreateKaboom.MODID)
public final class InterceptOrdnanceCommand {
    private static final float INTERCEPTION_EXPLOSION_POWER = 2.0F;

    private InterceptOrdnanceCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("kaboom_intercept")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> interceptAll(context.getSource()))
        );
    }

    private static int interceptAll(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        List<InterceptionTarget> targets = findActiveTargets(level);
        if (targets.isEmpty()) {
            source.sendFailure(Component.literal(
                    "No active missiles, rockets, or aerial bombs were found in this dimension."
            ));
            return 0;
        }

        Entity commandEntity = source.getEntity();
        DamageSource fallbackDamageSource = level.damageSources().explosion(null, commandEntity);

        int resolved = 0;
        int explosionsTriggered = 0;
        for (InterceptionTarget target : targets) {
            Entity entity = target.entity();
            if (!entity.isAlive()) {
                ++resolved;
                continue;
            }

            Vec3 position = target.position();
            level.explode(
                    commandEntity,
                    position.x,
                    position.y,
                    position.z,
                    INTERCEPTION_EXPLOSION_POWER,
                    Level.ExplosionInteraction.NONE
            );
            ++explosionsTriggered;

            if (entity.isAlive()) {
                entity.hurt(fallbackDamageSource, Float.MAX_VALUE);
            }
            if (!entity.isAlive()) {
                ++resolved;
            }
        }

        int targetCount = targets.size();
        int resolvedCount = resolved;
        int explosionCount = explosionsTriggered;
        if (resolvedCount == targetCount) {
            source.sendSuccess(
                    () -> Component.literal(
                            "Resolved " + targetCount + " active ordnance entities with "
                                    + explosionCount + " interception explosions."
                    ),
                    true
            );
        } else {
            source.sendFailure(Component.literal(
                    "Triggered " + explosionCount + " interception explosions, but only "
                            + resolvedCount + " of " + targetCount + " ordnance entities were resolved."
            ));
        }
        return resolvedCount;
    }

    private static List<InterceptionTarget> findActiveTargets(ServerLevel level) {
        List<InterceptionTarget> targets = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof InterceptableOrdnance && entity.isAlive()) {
                targets.add(new InterceptionTarget(entity, entity.position()));
            }
        }
        return targets;
    }

    private record InterceptionTarget(Entity entity, Vec3 position) {
    }
}
