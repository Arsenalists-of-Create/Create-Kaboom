package com.happysg.kaboom.block.seamine;

import com.happysg.kaboom.CreateKaboom;
import com.happysg.kaboom.networking.NetworkHandler;
import com.happysg.kaboom.registry.ModGuiTextures;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.simibubi.create.foundation.gui.widget.Label;
import com.simibubi.create.foundation.gui.widget.ScrollInput;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

public class SeaMineScreen extends AbstractSimiScreen {

    private final BlockPos pos;
    private final ModGuiTextures background = ModGuiTextures.SEA_MINE_SCREEN;
    private ScrollInput sensitivityScroll;
    private Label sensitivityLabel;
    private ScrollInput chainScroll;
    private Label chainLabel;
    private IconButton confirm;

    private int sensitivityValue;
    private int chainValue;
    private ItemStack fuzeStack = ItemStack.EMPTY;
    private ItemStack chainStack = ItemStack.EMPTY;

    public SeaMineScreen(BlockPos pos) {
        this.pos = pos;
        if (Minecraft.getInstance().level != null && Minecraft.getInstance().level.getBlockEntity(pos) instanceof SeaMineBlockEntity seaMine) {
            this.sensitivityValue = seaMine.getSensitivity();
            this.chainValue = seaMine.getChainLength();
            this.fuzeStack = seaMine.getFuze().copy();
            this.chainStack = seaMine.getChainStack().copy();
        } else {
            this.sensitivityValue = 50;
            this.chainValue = 16;
        }
    }

    @Override
    protected void init() {
        setWindowSize(background.width, background.height);
        super.init();
        clearWidgets();

        int x = guiLeft;
        int y = guiTop;

        sensitivityLabel = new Label(x + 50, y + 28, Component.literal(sensitivityValue + " kg"));
        sensitivityLabel.withShadow();
        addRenderableWidget(sensitivityLabel);

        sensitivityScroll = new ScrollInput(x + 46, y + 24, 108, 17)
                .withRange(1, 1001)
                .withShiftStep(10)
                .titled(Component.translatable(CreateKaboom.MODID + ".sea_mine.sensitivity_input"))
                .writingTo(sensitivityLabel)
                .calling(this::onSensitivityChanged);

        sensitivityScroll.setState(sensitivityValue);
        sensitivityScroll.onChanged();
        addRenderableWidget(sensitivityScroll);

        chainLabel = new Label(x + 50, y + 50, Component.literal(chainValue + " m"));
        chainLabel.withShadow();
        addRenderableWidget(chainLabel);

        chainScroll = new ScrollInput(x + 46, y + 46, 108, 17)
                .withRange(0, 65)
                .withShiftStep(5)
                .titled(Component.translatable(CreateKaboom.MODID + ".sea_mine.chain_input"))
                .writingTo(chainLabel)
                .calling(this::onChainChanged);

        chainScroll.setState(chainValue);
        chainScroll.onChanged();
        addRenderableWidget(chainScroll);

        confirm = new IconButton(x + 155, y + 77, ModGuiTextures.SEA_MINE_CHECK);
        confirm.withCallback(this::commitAndClose);
        addRenderableWidget(confirm);
    }

    private void onSensitivityChanged(int value) {
        sensitivityValue = Mth.clamp(value, 1, 1000);
        sensitivityLabel.text = Component.literal(sensitivityValue + " kg");
    }

    private void onChainChanged(int value) {
        chainValue = Mth.clamp(value, 0, 64);
        chainLabel.text = Component.literal(chainValue + " m");
    }

    @Override
    public void tick() {
        super.tick();
        if (minecraft != null && minecraft.level != null && minecraft.level.getBlockEntity(pos) instanceof SeaMineBlockEntity seaMine) {
            this.fuzeStack = seaMine.getFuze().copy();
            this.chainStack = seaMine.getChainStack().copy();
        }
    }

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int x = guiLeft;
        int y = guiTop;

        background.render(graphics, x, y);
        MutableComponent header = Component.translatable(CreateKaboom.MODID + ".sea_mine.title");
        graphics.drawString(font, header, x + background.width / 2 - font.width(header) / 2, y + 6, ModGuiTextures.FONT_COLOR, false);

        if (!fuzeStack.isEmpty()) {
            graphics.renderItem(fuzeStack, x + 22, y + 24);
        }
        if (!chainStack.isEmpty()) {
            graphics.renderItem(chainStack, x + 22, y + 46);
            graphics.renderItemDecorations(font, chainStack, x + 22, y + 46);
        }

        Component statusComp = Component.translatable(CreateKaboom.MODID + ".sea_mine.status_armed");
        int statusColor = 0x2E7D32;
        graphics.drawString(font, statusComp, x + 22, y + 74, statusColor, false);

        Component anchorComp = Component.translatable(CreateKaboom.MODID + ".sea_mine.status_anchored", chainValue);
        int anchorColor = 0x1565C0;
        graphics.drawString(font, anchorComp, x + 22, y + 85, anchorColor, false);

        if (mouseX >= x + 21 && mouseX <= x + 38 && mouseY >= y + 23 && mouseY <= y + 40) {
            graphics.renderTooltip(font, Component.translatable(CreateKaboom.MODID + ".sea_mine.fuze_slot"), mouseX, mouseY);
        }
        if (mouseX >= x + 21 && mouseX <= x + 38 && mouseY >= y + 45 && mouseY <= y + 62) {
            graphics.renderTooltip(font, Component.translatable(CreateKaboom.MODID + ".sea_mine.chain_slot"), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = guiLeft;
        int y = guiTop;

        if (mouseX >= x + 21 && mouseX <= x + 38 && mouseY >= y + 23 && mouseY <= y + 40) {
            NetworkHandler.sendToServer(new SeaMinePacket(pos, sensitivityValue, chainValue, 1));
            playClickSound();
            return true;
        }

        if (mouseX >= x + 21 && mouseX <= x + 38 && mouseY >= y + 45 && mouseY <= y + 62) {
            NetworkHandler.sendToServer(new SeaMinePacket(pos, sensitivityValue, chainValue, 2));
            playClickSound();
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void commitAndClose() {
        NetworkHandler.sendToServer(new SeaMinePacket(pos, sensitivityValue, chainValue, 0));
        onClose();
    }

    private void playClickSound() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}
