package com.happysg.kaboom.block.seamine;

import com.happysg.kaboom.CreateKaboom;
import com.happysg.kaboom.items.MineFuze;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import rbasamoyai.createbigcannons.munitions.fuzes.FuzeItem;

public class SeaMinePacket implements CustomPacketPayload {
    public static final Type<SeaMinePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateKaboom.MODID, "sea_mine_packet"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SeaMinePacket> STREAM_CODEC =
            StreamCodec.ofMember(SeaMinePacket::encode, SeaMinePacket::decode);

    private final BlockPos pos;
    private final int sensitivity;
    private final int chainLength;
    private final int slotAction;

    public SeaMinePacket(BlockPos pos, int sensitivity, int chainLength, int slotAction) {
        this.pos = pos;
        this.sensitivity = sensitivity;
        this.chainLength = chainLength;
        this.slotAction = slotAction;
    }

    public static void encode(SeaMinePacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeInt(msg.sensitivity);
        buf.writeInt(msg.chainLength);
        buf.writeInt(msg.slotAction);
    }

    public static SeaMinePacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int sensitivity = buf.readInt();
        int chainLength = buf.readInt();
        int slotAction = buf.readInt();
        return new SeaMinePacket(pos, sensitivity, chainLength, slotAction);
    }

    public static void handle(SeaMinePacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            BlockEntity be = player.level().getBlockEntity(msg.pos);
            if (!(be instanceof SeaMineBlockEntity seaMine)) return;

            seaMine.setSensitivity(msg.sensitivity);
            seaMine.setChainLength(msg.chainLength);

            if (msg.slotAction == 1) {
                ItemStack existing = seaMine.getFuze();
                if (!existing.isEmpty()) {
                    seaMine.setFuze(ItemStack.EMPTY);
                    if (!player.addItem(existing)) {
                        player.drop(existing, false);
                    }
                } else {
                    for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                        ItemStack stack = player.getInventory().getItem(i);
                        if (stack.getItem() instanceof MineFuze || stack.getItem() instanceof FuzeItem) {
                            seaMine.setFuze(stack.copyWithCount(1));
                            if (!player.isCreative()) {
                                stack.shrink(1);
                            }
                            break;
                        }
                    }
                }
            } else if (msg.slotAction == 2) {
                ItemStack existing = seaMine.getChainStack();
                if (!existing.isEmpty()) {
                    seaMine.setChainStack(ItemStack.EMPTY);
                    if (!player.addItem(existing)) {
                        player.drop(existing, false);
                    }
                } else {
                    for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                        ItemStack stack = player.getInventory().getItem(i);
                        if (stack.is(Items.CHAIN)) {
                            int count = Math.min(stack.getCount(), 64);
                            seaMine.setChainStack(stack.copyWithCount(count));
                            if (!player.isCreative()) {
                                stack.shrink(count);
                            }
                            break;
                        }
                    }
                }
            }

            BlockState currentState = seaMine.getBlockState();
            boolean shouldBeFuzed = !seaMine.getFuze().isEmpty();
            boolean shouldBeAnchored = !seaMine.getChainStack().isEmpty();
            if (currentState.hasProperty(SeaMineBlock.FUZED) && currentState.hasProperty(SeaMineBlock.ANCHORED)) {
                if (currentState.getValue(SeaMineBlock.FUZED) != shouldBeFuzed || currentState.getValue(SeaMineBlock.ANCHORED) != shouldBeAnchored) {
                    player.level().setBlock(msg.pos, currentState.setValue(SeaMineBlock.FUZED, shouldBeFuzed).setValue(SeaMineBlock.ANCHORED, shouldBeAnchored), 3);
                }
            }

            seaMine.setChanged();
            seaMine.sendUpdate();
            player.inventoryMenu.broadcastChanges();
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
