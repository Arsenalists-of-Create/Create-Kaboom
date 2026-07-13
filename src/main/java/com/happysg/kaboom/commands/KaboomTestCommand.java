package com.happysg.kaboom.commands;

import com.happysg.kaboom.CreateKaboom;
import com.happysg.kaboom.explosion.KaboomExplosionEngine;
import com.happysg.kaboom.explosion.KaboomExplosionProfile;
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

@EventBusSubscriber(modid = CreateKaboom.MODID)
public final class KaboomTestCommand {

    private static final float BASE_TEST_YIELD = 8.0F;
    private static final int BASE_MAX_BLOCK_CHANGES = 80_000;
    private static final int BASE_MAX_DETACHED_FRAGMENT_BLOCKS = 20_000;
    private static final double DEFAULT_FORWARD_DISTANCE = 8.0D;

    private KaboomTestCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("kaboom_test")
                        .requires(source -> source.hasPermission(2))

                        // /kaboom_test
                        .executes(context -> {
                            CommandSourceStack source = context.getSource();
                            Vec3 position = source.getPosition()
                                    .add(getHorizontalLookDirection(source).scale(DEFAULT_FORWARD_DISTANCE));

                            return explode(source, position, highExplosiveShellProfile(1.0F));
                        })

                        // /kaboom_test <x> <y> <z>
                        .then(Commands.argument("position", Vec3Argument.vec3())
                                .executes(context -> explode(
                                        context.getSource(),
                                        Vec3Argument.getVec3(context, "position"),
                                        highExplosiveShellProfile(1.0F)
                                ))

                                // /kaboom_test <x> <y> <z> <scale>
                                .then(Commands.argument(
                                        "scale",
                                        FloatArgumentType.floatArg(0.1F, 5.0F)
                                ).executes(context -> explode(
                                        context.getSource(),
                                        Vec3Argument.getVec3(context, "position"),
                                        highExplosiveShellProfile(
                                                FloatArgumentType.getFloat(context, "scale")
                                        )
                                )))
                        )
        );
    }

    private static int explode(
            CommandSourceStack source,
            Vec3 position,
            KaboomExplosionProfile profile
    ) {
        ServerLevel level = source.getLevel();

        KaboomExplosionEngine.Submission submission = KaboomExplosionEngine.explode(
                level,
                position,
                profile,
                source.getEntity()
        );

        return switch (submission.status()) {
            case QUEUED -> {
                source.sendSuccess(
                        () -> Component.literal(
                                "Kaboom queued: yield="
                                        + String.format("%.3f", profile.yield())
                                        + ", terrain capture radius="
                                        + submission.captureHalfExtent()
                                        + " blocks"
                        ),
                        true
                );
                yield 1;
            }

            case EFFECTS_ONLY -> {
                source.sendSuccess(
                        () -> Component.literal(
                                "Kaboom detonated with terrain damage disabled."
                        ),
                        true
                );
                yield 1;
            }

            case REJECTED_BUSY -> {
                source.sendFailure(Component.literal(
                        "Kaboom detonated, but terrain work was skipped because all explosion workers are busy."
                ));
                yield 0;
            }

            case REJECTED_NO_LOADED_CHUNKS -> {
                source.sendFailure(Component.literal(
                        "Kaboom detonated, but terrain work was skipped because the blast area includes unloaded chunks."
                ));
                yield 0;
            }

            case REJECTED_SNAPSHOT_FAILURE -> {
                source.sendFailure(Component.literal(
                        "Kaboom detonated, but terrain snapshot capture failed. Check the server log."
                ));
                yield 0;
            }
        };
    }

    private static KaboomExplosionProfile highExplosiveShellProfile(float scale) {
        // Explosion radius is proportional to cubeRoot(yield), so yield must scale cubically
        // for "scale" to retain its old visual meaning.
        float scaleCubed = scale * scale * scale;

        return KaboomExplosionProfile.builder(BASE_TEST_YIELD * scaleCubed)
                .terrainDamage(true)
                .entityDamage(true)
                .shockwave(true)
                .debris(true)
                .scorchSurface(true)
                .pruneDetachedFragments(true)

                // The rewritten engine's intended smaller crater / wider destruction profile.
                .craterRadiusScale(0.66F)
                .craterDepthScale(0.42F)
                .shockwaveRadiusScale(3.25F)

                .maxCaptureRadius(64)
                .maxBlockChanges(Math.round(BASE_MAX_BLOCK_CHANGES * scaleCubed))
                .maxDetachedFragmentBlocks(
                        Math.round(BASE_MAX_DETACHED_FRAGMENT_BLOCKS * scaleCubed)
                )
                .build();
    }

    private static Vec3 getHorizontalLookDirection(CommandSourceStack source) {
        if (source.getEntity() == null) {
            return new Vec3(0.0D, 0.0D, 1.0D);
        }

        Vec3 look = source.getEntity().getLookAngle();
        Vec3 horizontalLook = new Vec3(look.x, 0.0D, look.z);

        if (horizontalLook.lengthSqr() < 1.0E-8D) {
            return new Vec3(0.0D, 0.0D, 1.0D);
        }

        return horizontalLook.normalize();
    }
}