package com.happysg.kaboom.block.aerialBombs.cluster;


import com.happysg.kaboom.block.aerialBombs.cluster.ClusterBombletProjectile;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class ClusterRenderer extends EntityRenderer<ClusterBombletProjectile> {

    private final ItemRenderer itemRenderer;
    private final ItemStack renderStack = new ItemStack(Items.COBBLESTONE); // “tiny stone”

    public ClusterRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.itemRenderer = ctx.getItemRenderer();
        this.shadowRadius = 0.0f; // no shadow (it’s tiny)
    }

    @Override
    public void render(ClusterBombletProjectile entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {

        poseStack.pushPose();

        // Follow motion: yaw/pitch from entity rotation
        float yaw = Mth.lerp(partialTicks, entity.yRotO, entity.getYRot());
        float pitch = Mth.lerp(partialTicks, entity.xRotO, entity.getXRot());

        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0f - yaw));
        poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(pitch));

        // Make it tiny
        float scale = 0.18f;
        poseStack.scale(scale, scale, scale);

        // Render as a held item (flat-ish), looks like a chunk spinning through the air
        itemRenderer.renderStatic(
                renderStack,
                ItemDisplayContext.GROUND,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                buffer,
                entity.level(),
                entity.getId()
        );

        poseStack.popPose();

        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }


    @Override
    public ResourceLocation getTextureLocation(ClusterBombletProjectile entity) {
        return ResourceLocation.tryBuild(ResourceLocation.DEFAULT_NAMESPACE,"textures/item/cobblestone.png");
    }
}