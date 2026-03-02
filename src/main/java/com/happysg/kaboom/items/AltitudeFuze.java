package com.happysg.kaboom.items;

import com.happysg.kaboom.CreateKaboom;
import com.simibubi.create.foundation.item.TooltipHelper;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile;
import rbasamoyai.createbigcannons.munitions.fuzes.FuzeItem;

import java.util.List;

public class AltitudeFuze extends FuzeItem {
    public static final String TAG_HEIGHT = "HeightBlocks";

    // Per-projectile fuze state stored on the fuze stack
    private static final String TAG_INIT = "AltitudeFuzeInitialized";
    private static final String TAG_ARMED = "AltitudeFuzeArmed";

    public static final int DEFAULT_HEIGHT = 8;
    public static final int MIN_HEIGHT = 1;
    public static final int MAX_HEIGHT = 256;

    // How far down to search for ground
    private static final double MAX_TRACE_DOWN = 512.0;
    private static final double DETONATION_EPSILON = 0.01;

    public AltitudeFuze(Item.Properties properties) {
        super(properties);
    }

    @Override
    public boolean onProjectileTick(ItemStack stack, AbstractCannonProjectile projectile) {
        Level level = projectile.level();
        if (level.isClientSide) return false;
        if (projectile.isInGround()) return false; // optional: don't airburst after embedding

        int height = getHeight(stack);

        double dist = distanceToGround(level, projectile.position(), MAX_TRACE_DOWN);

        CompoundTag tag = stack.getOrCreateTag();


        if (!tag.getBoolean(TAG_INIT)) {
            tag.putBoolean(TAG_INIT, true);


            boolean startsAboveHeight = dist == Double.POSITIVE_INFINITY || dist > (double) height + DETONATION_EPSILON;
            tag.putBoolean(TAG_ARMED, startsAboveHeight);
        }

        boolean armed = tag.getBoolean(TAG_ARMED);

        // If not armed yet, arm only after rising above the set height
        if (!armed) {
            if (dist == Double.POSITIVE_INFINITY || dist > (double) height + DETONATION_EPSILON) {
                tag.putBoolean(TAG_ARMED, true);
            }
            return false;
        }

        // Once armed, detonate the next time it drops to/below the set height
        return dist != Double.POSITIVE_INFINITY && dist <= (double) height + DETONATION_EPSILON;
    }

    public static int getHeight(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        int h = tag.contains(TAG_HEIGHT) ? tag.getInt(TAG_HEIGHT) : DEFAULT_HEIGHT;
        return Mth.clamp(h, MIN_HEIGHT, MAX_HEIGHT);
    }

    public static void setHeight(ItemStack stack, int height) {
        stack.getOrCreateTag().putInt(TAG_HEIGHT, Mth.clamp(height, MIN_HEIGHT, MAX_HEIGHT));
    }

    private static double distanceToGround(Level level, Vec3 from, double maxDown) {
        Vec3 to = from.subtract(0, maxDown, 0);


        ClipContext ctx = new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null);

        HitResult hit = level.clip(ctx);
        if (hit.getType() != HitResult.Type.BLOCK) return Double.POSITIVE_INFINITY;

        return from.y - hit.getLocation().y;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);

        int h = getHeight(stack);

        tooltip.add(Component.translatable(CreateKaboom.MODID + ".item.altitude_fuse.detonation_alt").append("" + h)
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(CreateKaboom.MODID + ".item.altitude_fuse.base_info")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public void addExtraInfo(List<Component> tooltip, boolean isSneaking, ItemStack stack) {
        super.addExtraInfo(tooltip, isSneaking, stack);
        int h = getHeight(stack);

        MutableComponent info = CreateLang.builder(CreateKaboom.MODID)
                .translate("item.altitude_fuze.tooltip").add(Component.literal("" + h))
                .component();

        tooltip.addAll(TooltipHelper.cutTextComponent(info, Style.EMPTY, Style.EMPTY, 6));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level.isClientSide) {
            Minecraft.getInstance().setScreen(new AltitudeFuzeScreen(hand, getHeight(stack)));
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}