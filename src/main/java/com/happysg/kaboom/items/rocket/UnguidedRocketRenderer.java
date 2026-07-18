package com.happysg.kaboom.items.rocket;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class UnguidedRocketRenderer extends EntityRenderer<UnguidedRocketProjectile> {
    private static final float MODEL_WIDTH = 4.0F / 16.0F;
    private static final float MODEL_LENGTH = 20.0F / 16.0F;
    private final BlockRenderDispatcher blockRenderer;

    public UnguidedRocketRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
        this.blockRenderer = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(UnguidedRocketProjectile entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        Vec3 heading = entity.getOrientation();
        if (heading == null || heading.lengthSqr() <= 1.0E-10) {
            heading = entity.getDeltaMovement();
        }
        if (heading.lengthSqr() <= 1.0E-10) {
            heading = new Vec3(0.0, 0.0, 1.0);
        }

        Vector3f modelForward = new Vector3f(0.0F, 0.0F, 1.0F);
        Vector3f motionForward = heading.toVector3f().normalize();

        poseStack.pushPose();
        poseStack.mulPose(new Quaternionf().rotationTo(modelForward, motionForward));
        poseStack.scale(MODEL_WIDTH, MODEL_WIDTH, MODEL_LENGTH);
        poseStack.translate(-0.5, -0.5, -0.5);
        this.blockRenderer.renderSingleBlock(
                Blocks.STONE.defaultBlockState(), poseStack, buffers,
                packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();

        super.render(entity, entityYaw, partialTicks, poseStack, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(UnguidedRocketProjectile entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
