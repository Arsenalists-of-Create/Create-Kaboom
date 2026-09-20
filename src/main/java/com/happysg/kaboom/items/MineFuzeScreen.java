package com.happysg.kaboom.items;

import com.happysg.kaboom.CreateKaboom;
import com.happysg.kaboom.networking.NetworkHandler;
import com.happysg.kaboom.registry.ModGuiTextures;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.simibubi.create.foundation.gui.widget.Label;
import com.simibubi.create.foundation.gui.widget.ScrollInput;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;

public class MineFuzeScreen extends AbstractSimiScreen {
    private ScrollInput sensitivityScroll;
    protected ModGuiTextures background;
    private Label sensitivityLabel;

    private final InteractionHand hand;

    private final int initialSensitivity;
    private int sensitivityValue;

    private IconButton confirm;
    private boolean sent;

    public MineFuzeScreen(InteractionHand hand, int initialSensitivity) {
        this.hand = hand;
        this.background = ModGuiTextures.ALT_FUSE_SCREEN; 

        this.initialSensitivity = Mth.clamp(initialSensitivity, MineFuze.MIN_SENSITIVITY, MineFuze.MAX_SENSITIVITY);
        this.sensitivityValue = this.initialSensitivity;
    }

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int x = guiLeft;
        int y = guiTop;

        background.render(graphics, x, y);
        MutableComponent header = Component.translatable(CreateKaboom.MODID + ".mine_fuze.title");
        graphics.drawString(font, header, x + background.width / 2 - font.width(header) / 2, y + 4, 0, false);

        PoseStack ms = graphics.pose();
        ms.pushPose();
        ms.translate(0, guiTop + 46, 0);
        ms.translate(0, 21, 0);
        ms.popPose();

        ms.pushPose();
        TransformStack.of(ms)
                .pushPose()
                .translate(x + background.width + 4, y + background.height + 4, 100)
                .scale(40)
                .rotateX(-22)
                .rotateY(63);
        ms.popPose();
    }

    @Override
    protected void init() {
        setWindowSize(background.width, background.height);
        super.init();
        clearWidgets();

        int x = guiLeft;
        int y = guiTop;

        sensitivityLabel = new Label(x + 48, y + 28, Component.literal(String.valueOf(sensitivityValue)));
        sensitivityLabel.withShadow();
        addRenderableWidget(sensitivityLabel);

        sensitivityScroll = new ScrollInput(x + 47, y + 28, 100, 18)
                .withRange(MineFuze.MIN_SENSITIVITY, MineFuze.MAX_SENSITIVITY + 1)
                .withShiftStep(10)
                .titled(Component.translatable(CreateKaboom.MODID + ".mine_fuze.sensitivity_input"))
                .writingTo(sensitivityLabel)
                .calling(this::onSensitivityChanged);

        sensitivityScroll.setState(sensitivityValue);
        sensitivityScroll.onChanged();
        addRenderableWidget(sensitivityScroll);

        confirm = new IconButton(x + 155, y + 55, ModGuiTextures.CHECK);
        confirm.withCallback(this::commitToServer);
        addRenderableWidget(confirm);
    }

    private void onSensitivityChanged(int value) {
        sensitivityValue = Mth.clamp(value, MineFuze.MIN_SENSITIVITY, MineFuze.MAX_SENSITIVITY);
        sensitivityLabel.text = Component.literal(String.valueOf(sensitivityValue));
    }

    private void commitToServer() {
        commitToServerIfDirty();
        onClose();
    }

    private void commitToServerIfDirty() {
        if (sent) return;
        if (sensitivityValue == initialSensitivity) return;

        sent = true;
        NetworkHandler.sendToServer(new MineFuzePacket(hand, sensitivityValue));
    }

    @Override
    public void onClose() {
        commitToServerIfDirty();
        super.onClose();
    }
}
