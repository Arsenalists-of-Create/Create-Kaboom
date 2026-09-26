package com.happysg.kaboom.block.seamine;

import com.happysg.kaboom.CreateKaboom;
import com.happysg.kaboom.registry.ModBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.model.data.ModelData;

@OnlyIn(Dist.CLIENT)
public class SeaMineEntityRenderer extends EntityRenderer<SeaMineEntity> {

    private static final ModelResourceLocation ANCHOR_MODEL_LOC =
            ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(CreateKaboom.MODID, "block/chain_anchor"));

    private static final ModelResourceLocation BODY_MODEL_LOC =
            ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(CreateKaboom.MODID, "block/sea_mine_body"));

    private static final ModelResourceLocation HORNS_MODEL_LOC =
            ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(CreateKaboom.MODID, "block/sea_mine_horns"));

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(CreateKaboom.MODID, "textures/block/seamine.png");

    public SeaMineEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(SeaMineEntity entity) {
        return TEXTURE;
    }

    @Override
    public boolean shouldRender(SeaMineEntity entity, Frustum camera, double camX, double camY, double camZ) {
        return true;
    }

    @Override
    public void render(SeaMineEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        BakedModel bodyModel = Minecraft.getInstance().getModelManager().getModel(BODY_MODEL_LOC);
        BakedModel hornsModel = Minecraft.getInstance().getModelManager().getModel(HORNS_MODEL_LOC);
        BakedModel missingModel = Minecraft.getInstance().getModelManager().getMissingModel();

        if (bodyModel != null && hornsModel != null && bodyModel != missingModel && hornsModel != missingModel) {
            poseStack.pushPose();
            poseStack.translate(-0.5, 0.0, -0.5);
            Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(
                    poseStack.last(),
                    buffer.getBuffer(RenderType.cutoutMipped()),
                    ModBlocks.SEA_MINE.get().defaultBlockState(),
                    bodyModel,
                    1.0f, 1.0f, 1.0f,
                    packedLight,
                    OverlayTexture.NO_OVERLAY,
                    ModelData.EMPTY,
                    RenderType.cutoutMipped()
            );
            poseStack.popPose();

            float progress = entity.getTriggerProgress(partialTick);
            float hornScale = Mth.lerp(progress, 1.0f, 0.82f);

            poseStack.pushPose();
            poseStack.translate(0.0, 0.5, 0.0);
            poseStack.scale(hornScale, hornScale, hornScale);
            poseStack.translate(0.0, -0.5, 0.0);
            poseStack.translate(-0.5, 0.0, -0.5);
            Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(
                    poseStack.last(),
                    buffer.getBuffer(RenderType.cutoutMipped()),
                    ModBlocks.SEA_MINE.get().defaultBlockState(),
                    hornsModel,
                    1.0f, 1.0f, 1.0f,
                    packedLight,
                    OverlayTexture.NO_OVERLAY,
                    ModelData.EMPTY,
                    RenderType.cutoutMipped()
            );
            poseStack.popPose();
        } else {
            BakedModel mineModel = Minecraft.getInstance().getBlockRenderer().getBlockModel(ModBlocks.SEA_MINE.get().defaultBlockState());
            if (mineModel != null) {
                poseStack.pushPose();
                poseStack.translate(-0.5, 0.0, -0.5);
                Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(
                        poseStack.last(),
                        buffer.getBuffer(RenderType.cutoutMipped()),
                        ModBlocks.SEA_MINE.get().defaultBlockState(),
                        mineModel,
                        1.0f, 1.0f, 1.0f,
                        packedLight,
                        OverlayTexture.NO_OVERLAY,
                        ModelData.EMPTY,
                        RenderType.cutoutMipped()
                );
                poseStack.popPose();
            }
        }

        BlockPos anchor = entity.getAnchorPos();
        if (anchor != null && !anchor.equals(BlockPos.ZERO)) {
            double dx = (anchor.getX() + 0.5) - entity.getX();
            double dy = anchor.getY() - entity.getY();
            double dz = (anchor.getZ() + 0.5) - entity.getZ();
            float chainDist = (float) Math.abs(dy);

            if (chainDist > 0.05f) {
                int lightAnchor = LightTexture.pack(
                        entity.level().getBrightness(LightLayer.BLOCK, anchor),
                        entity.level().getBrightness(LightLayer.SKY, anchor)
                );

                BakedModel anchorModel = Minecraft.getInstance().getModelManager().getModel(ANCHOR_MODEL_LOC);
                if (anchorModel != null && anchorModel != missingModel) {
                    poseStack.pushPose();
                    poseStack.translate(dx, dy, dz);
                    poseStack.translate(0.5, 0.0, 0.5);
                    poseStack.translate(-0.5, 0.0, -0.5);
                    VertexConsumer vc = buffer.getBuffer(RenderType.solid());
                    Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(
                            poseStack.last(),
                            vc,
                            null,
                            anchorModel,
                            1f, 1f, 1f,
                            lightAnchor,
                            OverlayTexture.NO_OVERLAY,
                            ModelData.EMPTY,
                            RenderType.solid()
                    );
                    poseStack.popPose();
                }

                poseStack.pushPose();
                poseStack.translate(dx - 0.5, dy, dz - 0.5);
                ChainConveyorRenderer.renderChain(poseStack, buffer, 0f, chainDist, lightAnchor, packedLight, false);
                poseStack.popPose();
            }
        }

        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }
}
