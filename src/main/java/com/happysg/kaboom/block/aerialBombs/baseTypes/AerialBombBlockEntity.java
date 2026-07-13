package com.happysg.kaboom.block.aerialBombs.baseTypes;

import com.happysg.kaboom.compat.vs2.VS2Utils;
import com.happysg.kaboom.registry.ModProjectiles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3dc;
import rbasamoyai.createbigcannons.munitions.big_cannon.FuzedBlockEntity;
import com.happysg.kaboom.block.aerialBombs.tiny.TinyAerialBombBlock;

public class AerialBombBlockEntity extends FuzedBlockEntity {

    private final NonNullList<ItemStack> fuzes = NonNullList.withSize(9, ItemStack.EMPTY);
    private boolean updatingContainer = false;
    private boolean loadingNBT = false;
    public boolean creativeBroken = false;

    public AerialBombBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public ItemStack getFuze(int index) {
        if (index >= 0 && index < 9) {
            return fuzes.get(index);
        }
        return ItemStack.EMPTY;
    }

    public void setFuze(int index, ItemStack stack) {
        if (index >= 0 && index < 9) {
            fuzes.set(index, stack);
            if (index == 0) {
                boolean old = updatingContainer;
                updatingContainer = true;
                try {
                    super.setFuze(stack);
                } finally {
                    updatingContainer = old;
                }
            }
        }
    }

    public ItemStack removeFuze(int index) {
        if (index >= 0 && index < 9) {
            ItemStack stack = fuzes.set(index, ItemStack.EMPTY);
            if (index == 0) {
                boolean old = updatingContainer;
                updatingContainer = true;
                try {
                    super.setFuze(ItemStack.EMPTY);
                } finally {
                    updatingContainer = old;
                }
            }
            return stack;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack getFuze() {
        for (ItemStack stack : fuzes) {
            if (!stack.isEmpty()) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void setFuze(ItemStack stack) {
        if (updatingContainer) {
            super.setFuze(stack);
            return;
        }
        updatingContainer = true;
        try {
            if (stack.isEmpty()) {
                for (int i = 0; i < 9; i++) {
                    fuzes.set(i, ItemStack.EMPTY);
                }
            } else {
                fuzes.set(0, stack);
            }
            super.setFuze(stack);
        } finally {
            updatingContainer = false;
        }
    }

    @Override
    public ItemStack getItem(int slot) {
        if (slot == 1) {
            return getFuze();
        }
        return super.getItem(slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot == 1) {
            if (loadingNBT) {
                super.setItem(slot, stack);
                return;
            }
            if (updatingContainer) {
                super.setItem(slot, stack);
                return;
            }
            updatingContainer = true;
            try {
                setFuze(stack);
            } finally {
                updatingContainer = false;
            }
        } else {
            super.setItem(slot, stack);
        }
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (slot == 1) {
            ItemStack stack = getFuze();
            if (!stack.isEmpty()) {
                for (int i = 0; i < 9; i++) {
                    if (fuzes.get(i) == stack) {
                        fuzes.set(i, ItemStack.EMPTY);
                        break;
                    }
                }
                boolean old = updatingContainer;
                updatingContainer = true;
                try {
                    super.setItem(1, ItemStack.EMPTY);
                } finally {
                    updatingContainer = old;
                }
            }
            return stack;
        }
        return super.removeItem(slot, amount);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag list = new ListTag();
        for (int i = 0; i < 9; i++) {
            ItemStack fuze = fuzes.get(i);
            if (!fuze.isEmpty()) {
                CompoundTag fuzeTag = new CompoundTag();
                fuzeTag.putInt("Index", i);
                fuzeTag.put("Fuze", fuze.save(registries));
                list.add(fuzeTag);
            }
        }
        tag.put("KaboomFuzes", list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        loadingNBT = true;
        try {
            super.loadAdditional(tag, registries);
        } finally {
            loadingNBT = false;
        }
        for (int i = 0; i < 9; i++) {
            fuzes.set(i, ItemStack.EMPTY);
        }
        if (tag.contains("KaboomFuzes", 9)) {
            ListTag list = tag.getList("KaboomFuzes", 10);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag fuzeTag = list.getCompound(i);
                int idx = fuzeTag.getInt("Index");
                if (idx >= 0 && idx < 9) {
                    fuzes.set(idx, ItemStack.parseOptional(registries, fuzeTag.getCompound("Fuze")));
                }
            }
        } else {
            fuzes.set(0, super.getItem(1));
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        ListTag list = new ListTag();
        for (int i = 0; i < 9; i++) {
            ItemStack fuze = fuzes.get(i);
            if (!fuze.isEmpty()) {
                CompoundTag fuzeTag = new CompoundTag();
                fuzeTag.putInt("Index", i);
                fuzeTag.put("Fuze", fuze.save(registries));
                list.add(fuzeTag);
            }
        }
        tag.put("KaboomFuzes", list);
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection connection, net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        CompoundTag tag = packet.getTag();
        if (tag != null) {
            loadAdditional(tag, registries);
        }
    }

    public boolean activated = false;

    public void activate() {
        if (level == null || level.isClientSide) return;

        BlockState state = getBlockState();

        AerialBombProjectile projectile = createConfiguredProjectile(state);
        if (projectile == null) return;

        projectile.setPos(VS2Utils.getWorldPos(this).below().getCenter());
        Vector3dc shipVel = VS2Utils.getVelocity(level,this.worldPosition);
        if(shipVel != null) {
            projectile.setDeltaMovement(new Vec3(shipVel.x(), shipVel.y(), shipVel.z()));
        }

        level.addFreshEntity(projectile);

        int count = state.getValue(AerialBombBlock.COUNT);
        int activeIndex = count - 1;
        boolean isTiny = state.getBlock() instanceof TinyAerialBombBlock;
        int globalIndex = AerialBombBlock.getGlobalIndex(isTiny, count, activeIndex);
        setFuze(globalIndex, ItemStack.EMPTY);
        notifyUpdate();

        if (count > 1) {
            level.setBlock(worldPosition, state.setValue(AerialBombBlock.COUNT, count - 1), 3);
        } else {
            activated = true;
            level.destroyBlock(worldPosition, false);
        }
    }

    public void detonateOnSpot(Direction signalDirection) {
        if (level == null || level.isClientSide) return;

        BlockState state = getBlockState();
        AerialBombProjectile projectile = createConfiguredProjectile(state);
        if (projectile == null) return;

        projectile.setPos(Vec3.atCenterOf(worldPosition));
        projectile.setDeltaMovement(new Vec3(signalDirection.step()).scale(0.5D));

        activated = true;
        level.setBlock(worldPosition, Blocks.AIR.defaultBlockState(), 3);
        projectile.detonate(projectile.position());
    }

    protected AerialBombProjectile createConfiguredProjectile(BlockState state) {
        AerialBombProjectile projectile = ModProjectiles.AERIAL_BOMB_PROJECTILE.create(level);
        if (projectile == null) return null;

        projectile.setState(state);

        int count = state.hasProperty(AerialBombBlock.COUNT) ? state.getValue(AerialBombBlock.COUNT) : 1;
        int activeIndex = count - 1;
        boolean isTiny = state.getBlock() instanceof TinyAerialBombBlock;
        int globalIndex = AerialBombBlock.getGlobalIndex(isTiny, count, activeIndex);

        ItemStack fuzeStack = getFuze(globalIndex);
        projectile.setFuzeStack(fuzeStack.isEmpty() ? ItemStack.EMPTY : fuzeStack.copy());

        AerialBombProjectile.BombType bombType = AerialBombProjectile.BombType.HE;
        int bombSize = 1;

        if (state.getBlock() instanceof AerialBombBlock bomb) {
            bombType = bomb.getBombType();
            bombSize = bomb.getBombSize();
        }

        projectile.setBombType(bombType);
        projectile.setSize(bombSize);
        return projectile;
    }

}

