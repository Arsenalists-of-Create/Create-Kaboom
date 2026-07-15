package com.happysg.kaboom.block.aerialBombs.baseTypes;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class FallingAerialBombRenderer<T extends AerialBombProjectile> extends EntityRenderer<T> {
    private final BlockRenderDispatcher dispatcher;

    public FallingAerialBombRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.5F;
        this.dispatcher = context.getBlockRenderDispatcher();

    }

    public void render(T entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        BlockState renderState = entity.getState();
        int count =1;

        renderState = renderState.setValue(AerialBombBlock.COUNT,1);
        if (renderState.getRenderShape() == RenderShape.MODEL) {
            Level level = entity.level();
            if (renderState != level.getBlockState(entity.blockPosition()) && renderState.getRenderShape() != RenderShape.INVISIBLE) {
                poseStack.pushPose();
                Vec3 velocity = entity.getInterpolatedRenderVelocity(partialTicks);
                if (velocity.lengthSqr() > 1.0E-8) {
                    Vector3f modelFacing = new Vector3f(
                            entity.getFacing().getStepX(),
                            entity.getFacing().getStepY(),
                            entity.getFacing().getStepZ());
                    Vector3f motionFacing = velocity.toVector3f().normalize();
                    poseStack.mulPose(new Quaternionf().rotationTo(modelFacing, motionFacing));
                }
                poseStack.translate(0, -0.5, 0);
                poseStack.pushPose();
                poseStack.translate(-.5, 0.0, -.5);

                BakedModel model = this.dispatcher.getBlockModel(renderState);

                Minecraft.getInstance()
                        .getItemRenderer()
                        .renderModelLists(model, ItemStack.EMPTY, packedLight, OverlayTexture.NO_OVERLAY, poseStack, buffer.getBuffer(RenderType.cutout()));
                poseStack.popPose();
                poseStack.popPose();
                super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
            }
        }
    }

    public ResourceLocation getTextureLocation(T entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }

}
