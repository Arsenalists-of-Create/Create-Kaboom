package com.happysg.kaboom.block.missiles.chaining.client;

import com.happysg.kaboom.block.missiles.MissileEntity;
import com.happysg.kaboom.block.missiles.chaining.AnchorPoint;
import com.happysg.kaboom.block.missiles.chaining.ChainLink;
import com.happysg.kaboom.block.missiles.chaining.ChainSystem;
import com.happysg.kaboom.block.missiles.parts.thrust.ThrusterBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorRenderer;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChainRenderer {

    public static final Set<BlockPos> TRACKED_THRUSTERS = ConcurrentHashMap.newKeySet();
    public static final Set<Integer> TRACKED_MISSILES = ConcurrentHashMap.newKeySet();

    private static final ModelResourceLocation ANCHOR_MODEL_LOC =
            ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath("create_kaboom", "block/chain_anchor"));

    private final VerletChainManager verletManager = new VerletChainManager();
    private long lastTickedGameTime = -1;
    private final Set<UUID> activeLinkIdsThisFrame = new HashSet<>();

    @SubscribeEvent
    public void onRenderWorld(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;

        Vec3 camera = event.getCamera().getPosition();
        PoseStack ms = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);

        long gameTime = level.getGameTime();
        if (gameTime != lastTickedGameTime) {
            lastTickedGameTime = gameTime;
            verletManager.tick(level);
        }

        activeLinkIdsThisFrame.clear();

        Iterator<BlockPos> thrusterIt = TRACKED_THRUSTERS.iterator();
        while (thrusterIt.hasNext()) {
            BlockPos pos = thrusterIt.next();
            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof ThrusterBlockEntity thrusterBE)) {
                thrusterIt.remove();
                continue;
            }
            ChainSystem cs = thrusterBE.getChainSystem();
            renderChainSystem(ms, bufferSource, cs, pos, null, camera, partialTick, level);
        }

        Iterator<Integer> missileIt = TRACKED_MISSILES.iterator();
        while (missileIt.hasNext()) {
            int entityId = missileIt.next();
            Entity entity = level.getEntity(entityId);
            if (!(entity instanceof MissileEntity missile)) {
                missileIt.remove();
                continue;
            }
            ChainSystem cs = missile.getChainSystem();
            renderChainSystem(ms, bufferSource, cs, null, missile, camera, partialTick, level);
        }

        verletManager.pruneStaleChains(activeLinkIdsThisFrame);

        bufferSource.endBatch();
    }

    private void renderChainSystem(PoseStack ms, MultiBufferSource buffer, ChainSystem cs,
                                   BlockPos thrusterPos, MissileEntity missile,
                                   Vec3 camera, float partialTick, Level level) {
        boolean isWinching = cs.isWinching();
        UUID winchTargetMob = cs.getWinchTargetMob();

        for (AnchorPoint anchor : cs.getAnchors()) {
            Vec3 anchorWorld;
            if (missile != null) {
                Vec3 local = anchor.toContraptionLocalVec3();
                anchorWorld = missile.toGlobalVector(local, partialTick);
            } else if (thrusterPos != null) {
                anchorWorld = anchor.getWorldPos(thrusterPos);
            } else {
                continue;
            }

            renderAnchor(ms, buffer, anchorWorld, anchor.getFace(), camera, level, missile, partialTick);

            ChainLink link = anchor.getLink();
            if (link == null) continue;

            activeLinkIdsThisFrame.add(link.getId());

            if (link.getState() == ChainLink.State.SECURED) {
                if (link.getTargetEntityId() != -1) {
                    Entity mob = level.getEntity(link.getTargetEntityId());
                    if (mob != null) {
                        renderStraightChainToMob(ms, buffer, anchorWorld, mob, partialTick, level, camera);
                    }
                }
            } else if (link.getState() == ChainLink.State.DANGLING) {

                UUID activeChainId = cs.getActiveLinkerChainId();
                int linkerEntityId = cs.getActiveLinkerEntityId();
                boolean heldByPlayer = activeChainId != null
                        && activeChainId.equals(link.getId())
                        && linkerEntityId != -1;

                if (heldByPlayer) {

                    Entity linkerEntity = level.getEntity(linkerEntityId);
                    if (linkerEntity instanceof Player player) {
                        Vec3 handPos = getPlayerHandPos(player, partialTick);
                        VerletChain chain = verletManager.getOrCreateChain(link, anchorWorld, handPos);
                        chain.updateEndpoints(anchorWorld, handPos, ChainLink.State.TETHERED, false);
                        renderVerletChain(ms, buffer, chain, partialTick, level, camera);
                    } else {

                        VerletChain chain = verletManager.getOrCreateDanglingChain(link, anchorWorld);
                        Vec3 danglingEnd = anchorWorld.add(0, -1, 0);
                        chain.updateEndpoints(anchorWorld, danglingEnd, ChainLink.State.DANGLING, false);
                        renderVerletChain(ms, buffer, chain, partialTick, level, camera);
                    }
                } else {

                    VerletChain chain = verletManager.getOrCreateDanglingChain(link, anchorWorld);
                    Vec3 danglingEnd = anchorWorld.add(0, -1, 0);
                    boolean linkWinching = isWinching && link.getTargetMobId() != null
                            && link.getTargetMobId().equals(winchTargetMob);
                    chain.updateEndpoints(anchorWorld, danglingEnd, ChainLink.State.DANGLING, linkWinching);
                    renderVerletChain(ms, buffer, chain, partialTick, level, camera);
                }
            } else if (link.getTargetEntityId() != -1) {

                Entity mob = level.getEntity(link.getTargetEntityId());
                if (mob != null) {
                    AABB aabb = mob.getBoundingBox();
                    Vec3 attachPoint = new Vec3(
                            Mth.clamp(anchorWorld.x, aabb.minX, aabb.maxX),
                            Mth.clamp(anchorWorld.y, aabb.minY, aabb.maxY),
                            Mth.clamp(anchorWorld.z, aabb.minZ, aabb.maxZ)
                    );
                    boolean linkWinching = isWinching && link.getTargetMobId() != null
                            && link.getTargetMobId().equals(winchTargetMob);
                    VerletChain chain = verletManager.getOrCreateFixedChain(link, anchorWorld, attachPoint);
                    chain.updateEndpoints(anchorWorld, attachPoint, link.getState(), linkWinching);
                    renderVerletChain(ms, buffer, chain, partialTick, level, camera);
                }
            }
        }
    }

    private void renderVerletChain(PoseStack ms, MultiBufferSource buffer, VerletChain chain,
                                   float partialTick, Level level, Vec3 camera) {
        List<VerletPoint> points = chain.getPoints();
        if (points.size() < 2) return;

        for (int i = 0; i < points.size() - 1; i++) {
            Vec3 p1 = points.get(i).lerpedPos(partialTick);
            Vec3 p2 = points.get(i + 1).lerpedPos(partialTick);

            Vec3 diff = p2.subtract(p1);
            double segLen = diff.length();
            if (segLen < 1e-4) continue;

            float yaw = (float) (Mth.RAD_TO_DEG * Mth.atan2(diff.x, diff.z));
            double hDist = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
            float pitch = (float) (Mth.RAD_TO_DEG * Mth.atan2(diff.y, hDist));

            BlockPos lightPos = BlockPos.containing(p1);
            int light = LightTexture.pack(
                    level.getBrightness(LightLayer.BLOCK, lightPos),
                    level.getBrightness(LightLayer.SKY, lightPos));
            boolean far = camera.distanceToSqr(p1) > 48 * 48;

            ms.pushPose();
            setupChainTransformForSegment(ms, p1, camera, yaw, pitch, i % 2 == 1);
            ChainConveyorRenderer.renderChain(ms, buffer, 0f, (float) segLen, light, light, far);
            ms.popPose();
        }
    }

    private void renderStraightChainToMob(PoseStack ms, MultiBufferSource buffer, Vec3 anchorWorld,
                                           Entity mob, float partialTick, Level level, Vec3 camera) {
        AABB aabb = mob.getBoundingBox();
        Vec3 attachPoint = new Vec3(
                Mth.clamp(anchorWorld.x, aabb.minX, aabb.maxX),
                Mth.clamp(anchorWorld.y, aabb.minY, aabb.maxY),
                Mth.clamp(anchorWorld.z, aabb.minZ, aabb.maxZ)
        );

        Vec3 diff = attachPoint.subtract(anchorWorld);
        double length = diff.length();
        if (length < 0.01) return;

        float yaw = (float) (Mth.RAD_TO_DEG * Mth.atan2(diff.x, diff.z));
        double hDist = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
        float pitch = (float) (Mth.RAD_TO_DEG * Mth.atan2(diff.y, hDist));

        BlockPos lightPos1 = BlockPos.containing(anchorWorld);
        BlockPos lightPos2 = BlockPos.containing(attachPoint);
        int light1 = LightTexture.pack(
                level.getBrightness(LightLayer.BLOCK, lightPos1),
                level.getBrightness(LightLayer.SKY, lightPos1));
        int light2 = LightTexture.pack(
                level.getBrightness(LightLayer.BLOCK, lightPos2),
                level.getBrightness(LightLayer.SKY, lightPos2));

        boolean far = camera.distanceToSqr(anchorWorld) > 48 * 48;

        ms.pushPose();
        setupChainTransform(ms, anchorWorld, camera, yaw, pitch);
        ChainConveyorRenderer.renderChain(ms, buffer, 0f, (float) length, light1, light2, far);
        ms.popPose();
    }

    private static Vec3 getPlayerHandPos(Player player, float partialTick) {
        Vec3 pos = player.getPosition(partialTick);
        float yaw = (float) Math.toRadians(Mth.lerp(partialTick, player.yBodyRotO, player.yBodyRot));
        double sideX = -Math.cos(yaw) * 0.35;
        double sideZ = Math.sin(yaw) * 0.35;
        return pos.add(sideX, player.getEyeHeight() * 0.65, sideZ);
    }

    private void renderAnchor(PoseStack ms, MultiBufferSource buffer, Vec3 worldPos,
                              Direction face, Vec3 camera, Level level,
                              MissileEntity missile, float partialTick) {
        ms.pushPose();
        ms.translate(worldPos.x - camera.x, worldPos.y - camera.y, worldPos.z - camera.z);

        if (missile != null) {
            float yaw = -Mth.lerp(partialTick, missile.yRotO, missile.getYRot());
            float pitch = Mth.lerp(partialTick, missile.xRotO, missile.getXRot());
            var ts = TransformStack.of(ms);
            ts.rotateYDegrees(yaw);
            ts.rotateXDegrees(pitch);
        }

        ms.translate(
                face.getStepX() * -0.125f,
                face.getStepY() * -0.125f,
                face.getStepZ() * -0.125f
        );

        BlockPos lightPos = BlockPos.containing(worldPos);
        int light = LightTexture.pack(
                level.getBrightness(LightLayer.BLOCK, lightPos),
                level.getBrightness(LightLayer.SKY, lightPos)
        );

        ms.translate(-0.5, -0.5, -0.0625);

        BakedModel model = Minecraft.getInstance().getModelManager().getModel(ANCHOR_MODEL_LOC);
        VertexConsumer vc = buffer.getBuffer(RenderType.solid());
        Minecraft.getInstance().getBlockRenderer().getModelRenderer()
                .renderModel(ms.last(), vc, null, model, 1f, 1f, 1f, light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, RenderType.solid());

        ms.popPose();
    }

    private void setupChainTransform(PoseStack ms, Vec3 anchorWorld, Vec3 camera,
                                     float yaw, float pitch) {
        ms.translate(
                anchorWorld.x - camera.x - 0.5,
                anchorWorld.y - camera.y - 0.5,
                anchorWorld.z - camera.z - 0.5
        );
        var ts = TransformStack.of(ms);
        ts.center();
        ts.rotateYDegrees(yaw);
        ts.rotateXDegrees(90 - pitch);
        ts.rotateYDegrees(45);
        ts.translate(0, 8.0 / 16.0, 0);
        ts.uncenter();
    }

    private void setupChainTransformForSegment(PoseStack ms, Vec3 segStart, Vec3 camera,
                                               float yaw, float pitch, boolean alternate) {
        ms.translate(
                segStart.x - camera.x - 0.5,
                segStart.y - camera.y - 0.5,
                segStart.z - camera.z - 0.5
        );
        var ts = TransformStack.of(ms);
        ts.center();
        ts.rotateYDegrees(yaw);
        ts.rotateXDegrees(90 - pitch);
        ts.rotateYDegrees(alternate ? 135 : 45);
        ts.translate(0, 8.0 / 16.0, 0);
        ts.uncenter();
    }
}
