package com.happysg.kaboom.registry;

import com.happysg.kaboom.CreateKaboom;
import com.mojang.blaze3d.systems.RenderSystem;
import net.createmod.catnip.gui.UIRenderHelper;
import net.createmod.catnip.gui.element.ScreenElement;
import net.createmod.catnip.theme.Color;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

public enum ModGuiTextures implements ScreenElement {
    ALT_FUSE_SCREEN("altitude_fuze_gui",187,78),
    CHECK("altitude_fuze_gui",156,56,16,16),
    GPS_SCREEN_BG("gps_guidance_gui",179,83),
    TARGET("gps_guidance_gui",147,60,16,16);





    public static final int FONT_COLOR = 0x575F7A;

    public final ResourceLocation location;
    public final int width, height;
    public final int startX, startY;
    public final int textureWidth, textureHeight;

    ModGuiTextures(String location, int width, int height) {
        this(location, 0, 0, width, height);
    }

    ModGuiTextures(int startX, int startY) {
        this("icons", startX * 16, startY * 16, 16, 16);
    }

    ModGuiTextures(String location, int startX, int startY, int width, int height) {
        this(CreateKaboom.MODID, location, startX, startY, width, height, 256, 256);
    }

    ModGuiTextures(String namespace, String location, int startX, int startY, int width, int height, int textureWidth, int textureHeight) {
        this.location = new ResourceLocation(namespace, "textures/gui/" + location + ".png");
        this.width = width;
        this.height = height;
        this.startX = startX;
        this.startY = startY;
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
    }

    @OnlyIn(Dist.CLIENT)
    public void bind() {
        RenderSystem.setShaderTexture(0, location);
    }

    @OnlyIn(Dist.CLIENT)
    public void render(GuiGraphics graphics, int x, int y) {
        graphics.blit(location, x, y, startX, startY, width, height);
    }

    @OnlyIn(Dist.CLIENT)
    public void render(GuiGraphics graphics, int x, int y, Color c) {
        bind();
        UIRenderHelper.drawColoredTexture(graphics, c, x, y, startX, startY, width, height);
    }
}
