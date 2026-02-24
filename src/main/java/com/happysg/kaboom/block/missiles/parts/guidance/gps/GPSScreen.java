package com.happysg.kaboom.block.missiles.parts.guidance.gps;

import com.happysg.kaboom.CreateKaboom;
import com.happysg.kaboom.networking.NetworkHandler;
import com.happysg.kaboom.registry.ModGuiTextures;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

public class GPSScreen extends AbstractSimiScreen {
    private final BlockPos pos;
    private EditBox xBox;
    private EditBox yBox;
    private EditBox zBox;
    private boolean sent;

    private final ModGuiTextures background = ModGuiTextures.GPS_SCREEN_BG;

    public GPSScreen(BlockPos pos) {
        this.pos = pos;

    }

    @Override
    protected void init() {
        setWindowSize(background.width, background.height);
        super.init();
        clearWidgets();

        int x = guiLeft;
        int y = guiTop;

        xBox = createNumberBox(x + 40, y + 30);
        yBox = createNumberBox(x + 80, y + 30);
        zBox = createNumberBox(x + 120, y + 30);

        Vec3 initial = getInitialTarget();
        xBox.setValue(formatNumber(initial.x));
        yBox.setValue(formatNumber(initial.y));
        zBox.setValue(formatNumber(initial.z));

        addRenderableWidget(xBox);
        addRenderableWidget(yBox);
        addRenderableWidget(zBox);
    }

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int x = guiLeft;
        int y = guiTop;

        background.render(graphics, x, y);
        Component header = Component.translatable(CreateKaboom.MODID + ".gps.title");
        graphics.drawString(font, header, x + background.width / 2 - font.width(header) / 2, y + 4, 0, false);
    }

    @Override
    public void onClose() {
        commitToServerIfDirty();
        super.onClose();
    }

    private void commitToServerIfDirty() {
        if (sent) return;

        double x = parseNumber(xBox, 0.0);
        double y = parseNumber(yBox, 0.0);
        double z = parseNumber(zBox, 0.0);

        sent = true;
        NetworkHandler.CHANNEL.sendToServer(new GPSGuidancePacket(pos, x, y, z));
    }

    private EditBox createNumberBox(int x, int y) {
        EditBox box = new EditBox(font, x, y, 36, 10, Component.empty());
        box.setMaxLength(16);
        box.setValue("");
        box.setBordered(false);
        box.setFilter(GPSScreen::isNumberInput);
        return box;
    }

    private static boolean isNumberInput(String text) {
        if (text.isEmpty()) return true;
        if (text.equals("-") || text.equals(".") || text.equals("-.")) return true;
        try {
            Double.parseDouble(text);
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private Vec3 getInitialTarget() {
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) return Vec3.ZERO;

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof GPSGuidanceBlockEntity gps) {
            return gps.getTarget();
        }
        return Vec3.ZERO;
    }

    private static String formatNumber(double v) {
        if (Math.abs(v) < 0.00001) return "0";
        return Double.toString(v);
    }

    private static double parseNumber(EditBox box, double fallback) {
        String text = box.getValue();
        if (text == null || text.isBlank()) return fallback;
        if (text.equals("-") || text.equals(".") || text.equals("-.")) return fallback;
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
