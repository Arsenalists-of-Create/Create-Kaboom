package com.happysg.kaboom.block.aerialBombs.baseTypes;

import com.happysg.kaboom.CreateKaboom;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class AerialBombFuzeRenderer implements BlockEntityRenderer<AerialBombBlockEntity> {
    private static final ResourceLocation FUZE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(CreateKaboom.MODID, "textures/block/fuze.png");

    private static final float[][] HEAVY_POSITIONS = {{0.5F, 0.5F}};
    private static final float[][] STANDARD_POSITIONS = {
            {0.75F, 0.25F},
            {0.75F, 0.75F},
            {0.25F, 0.25F},
            {0.25F, 0.75F}
    };
    private static final float[][] TINY_POSITIONS = {
            {0.84375F, 0.15625F},
            {0.84375F, 0.5F},
            {0.84375F, 0.84375F},
            {0.5F, 0.15625F},
            {0.5F, 0.5F},
            {0.5F, 0.84375F},
            {0.15625F, 0.15625F},
            {0.15625F, 0.5F},
            {0.15625F, 0.84375F}
    };

    public AerialBombFuzeRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(AerialBombBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        BlockState state = blockEntity.getBlockState();
        if (!(state.getBlock() instanceof AerialBombBlock bomb)) {
            return;
        }

        Direction facing = state.getValue(AerialBombBlock.FACING);
        float[][] positions = positionsFor(bomb.getBombSize());
        int slots = Math.min(blockEntity.getVisibleFuzeSlots(state), positions.length);
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityCutout(FUZE_TEXTURE));

        for (int i = 0; i < slots; i++) {
            if (blockEntity.getFuze(i).isEmpty()) {
                continue;
            }

            float scale = bomb.getBombSize() >= 4 ? 0.09F : 0.125F;
            drawFuze(consumer, poseStack, facing, positions[i][0], positions[i][1], scale, scale, 0.0625F, packedLight);
        }
    }

    private static float[][] positionsFor(int bombSize) {
        return switch (bombSize) {
            case 1 -> HEAVY_POSITIONS;
            case 2 -> STANDARD_POSITIONS;
            default -> TINY_POSITIONS;
        };
    }

    private static void drawFuze(VertexConsumer consumer, PoseStack poseStack, Direction facing,
                                 float x, float y, float width, float height, float depth, int packedLight) {
        Vector3f normal = step(facing);
        Vector3f right = step(facing.getClockWise());
        Vector3f up = new Vector3f(0, 1, 0);
        float front = 0.5F + depth * 0.5F + 0.002F;

        Vector3f center = new Vector3f(0.5F, 0, 0.5F)
                .add(new Vector3f(right).mul(x - 0.5F))
                .add(new Vector3f(up).mul(y))
                .add(new Vector3f(normal).mul(front));

        Vector3f r = new Vector3f(right).mul(width * 0.5F);
        Vector3f u = new Vector3f(up).mul(height * 0.5F);
        Vector3f n = new Vector3f(normal).mul(depth * 0.5F);

        Vector3f p000 = new Vector3f(center).sub(r).sub(u).sub(n);
        Vector3f p001 = new Vector3f(center).sub(r).sub(u).add(n);
        Vector3f p010 = new Vector3f(center).sub(r).add(u).sub(n);
        Vector3f p011 = new Vector3f(center).sub(r).add(u).add(n);
        Vector3f p100 = new Vector3f(center).add(r).sub(u).sub(n);
        Vector3f p101 = new Vector3f(center).add(r).sub(u).add(n);
        Vector3f p110 = new Vector3f(center).add(r).add(u).sub(n);
        Vector3f p111 = new Vector3f(center).add(r).add(u).add(n);

        quad(consumer, poseStack, p001, p101, p111, p011, normal, packedLight);
        quad(consumer, poseStack, p100, p000, p010, p110, new Vector3f(normal).negate(), packedLight);
        quad(consumer, poseStack, p000, p001, p011, p010, new Vector3f(right).negate(), packedLight);
        quad(consumer, poseStack, p101, p100, p110, p111, right, packedLight);
        quad(consumer, poseStack, p010, p011, p111, p110, up, packedLight);
        quad(consumer, poseStack, p000, p100, p101, p001, new Vector3f(up).negate(), packedLight);
    }

    private static Vector3f step(Direction direction) {
        return new Vector3f(direction.getStepX(), direction.getStepY(), direction.getStepZ());
    }

    private static void quad(VertexConsumer consumer, PoseStack poseStack, Vector3f a, Vector3f b,
                             Vector3f c, Vector3f d, Vector3f normal, int packedLight) {
        Matrix4f matrix = poseStack.last().pose();
        vertex(consumer, matrix, a, 0, 1, normal, packedLight);
        vertex(consumer, matrix, b, 1, 1, normal, packedLight);
        vertex(consumer, matrix, c, 1, 0, normal, packedLight);
        vertex(consumer, matrix, d, 0, 0, normal, packedLight);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, Vector3f pos, float u, float v,
                               Vector3f normal, int packedLight) {
        consumer.addVertex(matrix, pos.x(), pos.y(), pos.z())
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(normal.x(), normal.y(), normal.z());
    }
}
