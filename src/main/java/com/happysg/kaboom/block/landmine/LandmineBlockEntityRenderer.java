package com.happysg.kaboom.block.landmine;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.decoration.copycat.CopycatBlock;
import com.simibubi.create.content.decoration.copycat.CopycatModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.data.ModelData;

@OnlyIn(Dist.CLIENT)
public class LandmineBlockEntityRenderer implements BlockEntityRenderer<LandmineBlockEntity> {

    public LandmineBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(LandmineBlockEntity be, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int combinedLight, int combinedOverlay) {
        BlockState camouflage = be.getCamouflage();
        if (camouflage != null && be.getLevel() != null) {
            poseStack.pushPose();
            if (camouflage.getBlock() instanceof CopycatBlock) {
                poseStack.translate(0, 0.001, 0);
                BlockState material = be.getCopycatMaterial();
                ModelData modelData;
                if (material != null) {
                    modelData = ModelData.builder()
                            .with(CopycatModel.MATERIAL_PROPERTY, material)
                            .build();
                } else {
                    modelData = ModelData.EMPTY;
                }

                BakedModel targetModel = Minecraft.getInstance().getBlockRenderer().getBlockModel(material != null ? material : camouflage);
                ChunkRenderTypeSet renderTypes = targetModel.getRenderTypes(material != null ? material : camouflage, be.getLevel().random, modelData);
                if (renderTypes.isEmpty()) {
                    Minecraft.getInstance().getBlockRenderer().renderBatched(
                            camouflage,
                            be.getBlockPos(),
                            be.getLevel(),
                            poseStack,
                            buffer.getBuffer(RenderType.solid()),
                            false,
                            be.getLevel().random,
                            modelData,
                            RenderType.solid()
                    );
                } else {
                    for (RenderType type : renderTypes) {
                        Minecraft.getInstance().getBlockRenderer().renderBatched(
                                camouflage,
                                be.getBlockPos(),
                                be.getLevel(),
                                poseStack,
                                buffer.getBuffer(type),
                                false,
                                be.getLevel().random,
                                modelData,
                                type
                        );
                    }
                }
            } else {
                Minecraft.getInstance().getBlockRenderer().renderBatched(
                        camouflage,
                        be.getBlockPos(),
                        be.getLevel(),
                        poseStack,
                        buffer.getBuffer(RenderType.cutoutMipped()),
                        false,
                        be.getLevel().random,
                        ModelData.EMPTY,
                        RenderType.cutoutMipped()
                );
            }
            poseStack.popPose();
        }
    }
}
