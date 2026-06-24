package com.happysg.kaboom.commands;

import com.happysg.kaboom.CreateKaboom;
import com.happysg.kaboom.explosion.KaboomExplosionEngine;
import com.mojang.brigadier.arguments.FloatArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Locale;

@EventBusSubscriber(modid = CreateKaboom.MODID)
public final class KaboomTestCommand {

    private KaboomTestCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("kaboom_test")
                        .requires(source -> source.hasPermission(2))

                        // /kaboom_test
                        // Detonates 8 blocks in front of the command user.
                        .executes(context -> {
                            CommandSourceStack source = context.getSource();
                            Vec3 start = source.getPosition();

                            Vec3 forward = source.getEntity() != null
                                    ? source.getEntity().getLookAngle()
                                    : new Vec3(0.0D, 0.0D, 1.0D);

                            Vec3 position = start.add(
                                    forward.x * 8.0D,
                                    0.0D,
                                    forward.z * 8.0D
                            );

                            return explode(
                                    source,
                                    position,
                                    KaboomExplosionEngine.BlastProfile.highExplosiveShell()
                            );
                        })

                        // /kaboom_test <x> <y> <z>
                        .then(Commands.argument("position", Vec3Argument.vec3())
                                .executes(context -> explode(
                                        context.getSource(),
                                        Vec3Argument.getVec3(context, "position"),
                                        KaboomExplosionEngine.BlastProfile.highExplosiveShell()
                                ))

                                // /kaboom_test <x> <y> <z> <scale>
                                .then(Commands.argument(
                                        "scale",
                                        FloatArgumentType.floatArg(0.1F, 5.0F)
                                ).executes(context -> explode(
                                        context.getSource(),
                                        Vec3Argument.getVec3(context, "position"),
                                        scaledProfile(
                                                FloatArgumentType.getFloat(
                                                        context,
                                                        "scale"
                                                )
                                        )
                                )))
                        )
        );
    }

    private static int explode(
            CommandSourceStack source,
            Vec3 position,
            KaboomExplosionEngine.BlastProfile profile
    ) {
        ServerLevel level = source.getLevel();

        KaboomExplosionEngine.BlastResult result =
                KaboomExplosionEngine.detonate(
                        level,
                        position,
                        profile,
                        source.getEntity(),
                        level.damageSources().generic(),
                        completion -> source.sendSuccess(
                                () -> Component.literal(
                                        String.format(
                                                Locale.ROOT,
                                                "Kaboom finished: changed=%d/%d, total=%.3f ms, "
                                                        + "terrain work=%.3f ms, ticks=%d",
                                                completion.actualChanges(),
                                                completion.plannedChanges(),
                                                completion.totalWallNanos()
                                                        / 1_000_000.0D,
                                                completion.terrainWorkNanos()
                                                        / 1_000_000.0D,
                                                completion.ticks()
                                        )
                                ),
                                true
                        )
                );

        if (!result.queued()) {
            source.sendFailure(
                    Component.literal(
                            String.format(
                                    Locale.ROOT,
                                    "Kaboom probe was cancelled or was not captured; "
                                            + "no terrain queued (probe=%.3f ms)",
                                    result.probeAndPlanNanos() / 1_000_000.0D
                            )
                    )
            );

            return 0;
        }

        source.sendSuccess(
                () -> Component.literal(
                        String.format(
                                Locale.ROOT,
                                "Kaboom queued: planned=%d, probe+plan=%.3f ms",
                                result.plannedChanges(),
                                result.probeAndPlanNanos() / 1_000_000.0D
                        )
                ),
                true
        );

        return result.plannedChanges();
    }

    private static KaboomExplosionEngine.BlastProfile scaledProfile(
            float scale
    ) {
        KaboomExplosionEngine.BlastProfile base =
                KaboomExplosionEngine.BlastProfile.highExplosiveShell();

        return new KaboomExplosionEngine.BlastProfile(
                base.surfaceRadius() * scale,
                base.buriedRadius() * scale,

                Math.max(1, Math.round(base.surfaceMaxDepth() * scale)),
                Math.max(1, Math.round(base.buriedMaxDepth() * scale)),
                Math.max(1, Math.round(base.surfaceUpwardRange() * scale)),

                base.edgeRoughnessBlocks() * scale,
                base.depthRoughnessBlocks() * scale,

                base.foliageRadius() * scale,
                Math.max(1, Math.round(base.foliageVerticalRange() * scale)),
                base.foliageStripThreshold(),

                base.maxBlockResistance() * scale,
                base.grassDestroyThreshold(),
                base.grassDirtChanceInner(),
                base.grassDirtChanceOuter(),

                base.masonryCrackThreshold(),
                base.masonryDestroyThreshold(),

                base.woodIgniteThreshold(),
                base.woodIgniteChance(),
                base.allowFire(),

                base.entityRadius() * scale,
                base.entityDamage() * scale,
                base.entityKnockback() * scale,
                base.coveredDamageMultiplier(),

                base.buriedModeThreshold(),
                base.burialProbeHeight(),
                base.blocksForFullBurial(),

                Math.max(
                        200,
                        Math.round(base.maxBlockChanges() * scale * scale)
                )
        );
    }
}
