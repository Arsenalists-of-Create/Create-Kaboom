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

public class AltitudeFuzeScreen extends AbstractSimiScreen {
    private ScrollInput heightScroll;
    protected ModGuiTextures background;
    private Label heightLabel;

    private final InteractionHand hand;

    private final int initialHeight;
    private int heightValue;

    private IconButton confirm;
    private boolean sent;

    public AltitudeFuzeScreen(InteractionHand hand, int initialHeight) {
        this.hand = hand;
        this.background = ModGuiTextures.ALT_FUSE_SCREEN;

        this.initialHeight = Mth.clamp(initialHeight, 1, 256);
        this.heightValue = this.initialHeight;
    }

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int x = guiLeft;
        int y = guiTop;

        background.render(graphics, x, y);
        MutableComponent header = Component.translatable(CreateKaboom.MODID + ".alt_fuze.title");
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

        heightLabel = new Label(x + 48, y + 28, Component.literal(String.valueOf(heightValue)));
        heightLabel.withShadow();
        addRenderableWidget(heightLabel);

        heightScroll = new ScrollInput(x + 47, y + 28, 25, 18)
                .withRange(1, 257) // [min, maxExclusive) -> 1..256
                .titled(Component.translatable(CreateKaboom.MODID + ".altitude_screen.destination_alt_input"))

                .writingTo(heightLabel)
                .calling(this::onHeightChanged);

        heightScroll.setState(heightValue);
        heightScroll.onChanged();
        addRenderableWidget(heightScroll);

        confirm = new IconButton(x + 155, y + 55, ModGuiTextures.CHECK);
        confirm.withCallback(this::commitToServer);
        addRenderableWidget(confirm);
    }

    private void onHeightChanged(int value) {
        heightValue = Mth.clamp(value, 1, 256);
        heightLabel.text = Component.literal(String.valueOf(heightValue));
    }

    private void commitToServer() {
        commitToServerIfDirty();
        onClose();
    }

    private void commitToServerIfDirty() {
        if (sent) return;
        if (heightValue == initialHeight) return;

        sent = true;
        NetworkHandler.CHANNEL.sendToServer(new AltitudeFuzePacket(hand, heightValue));
    }

    @Override
    public void onClose() {
        // Auto-save on ESC / inventory-close too
        commitToServerIfDirty();
        super.onClose();
    }
}