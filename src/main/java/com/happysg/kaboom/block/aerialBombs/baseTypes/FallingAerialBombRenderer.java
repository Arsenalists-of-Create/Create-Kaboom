package com.happysg.kaboom.block.aerialBombs.baseTypes;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

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

        if (renderState.hasProperty(AerialBombBlock.SIZE)) {
            renderState = renderState.setValue(AerialBombBlock.COUNT,1);
        }
        if (renderState.getRenderShape() == RenderShape.MODEL) {
            Level level = entity.level();
            if (renderState != level.getBlockState(entity.blockPosition()) && renderState.getRenderShape() != RenderShape.INVISIBLE) {
                float i = Math.min(1.0f, (float) entity.getTime() / entity.getTimeRequired());
                poseStack.pushPose();
                Direction facing = entity.getFacing();
                switch (facing) {
                    case NORTH:
                        poseStack.mulPose(Axis.XN.rotationDegrees(90*i));
                        break;
                    case SOUTH:
                        poseStack.mulPose(Axis.XP.rotationDegrees(90*i));
                        break;
                    case WEST:
                        poseStack.mulPose(Axis.ZP.rotationDegrees(90*i));
                        break;
                    case EAST:
                        poseStack.mulPose(Axis.ZN.rotationDegrees(90*i));
                        break;
                    default:
                        break;
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


    /**
     * Returns the location of an entity's texture.
     */
    public ResourceLocation getTextureLocation(T entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

}