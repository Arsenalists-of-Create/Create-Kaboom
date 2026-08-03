package com.happysg.kaboom.items.rocket;

import com.happysg.kaboom.registry.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class UnguidedRocketRenderer extends EntityRenderer<UnguidedRocketProjectile> {
    private final ItemRenderer itemRenderer;

    public UnguidedRocketRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public boolean shouldRender(UnguidedRocketProjectile entity, Frustum frustum,
                                double cameraX, double cameraY, double cameraZ) {
        return true;
    }

    @Override
    public void render(UnguidedRocketProjectile entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        Vec3 heading = entity.getOrientation();
        if (heading == null || heading.lengthSqr() <= 1.0E-10) {
            heading = entity.getDeltaMovement();
        }
        if (heading.lengthSqr() <= 1.0E-10) {
            heading = new Vec3(0.0, 0.0, -1.0);
        }

        Vector3f modelForward = new Vector3f(0.0F, 0.0F, -1.0F);
        Vector3f motionForward = heading.toVector3f().normalize();
        ItemStack renderStack = entity.getRocketStack();
        if (renderStack.isEmpty()) {
            renderStack = ModItems.ROCKET.get().getDefaultInstance();
        }

        poseStack.pushPose();
        poseStack.mulPose(new Quaternionf().rotationTo(modelForward, motionForward));
        this.itemRenderer.renderStatic(
                renderStack,
                ItemDisplayContext.NONE,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                buffers,
                entity.level(),
                entity.getId());
        poseStack.popPose();

        super.render(entity, entityYaw, partialTicks, poseStack, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(UnguidedRocketProjectile entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
