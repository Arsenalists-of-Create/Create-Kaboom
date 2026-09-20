package com.happysg.kaboom.block.landmine;

import com.happysg.kaboom.registry.ModBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class LandmineBlockEntity extends BlockEntity {

    private ItemStack fuze = ItemStack.EMPTY;
    private BlockState camouflage = null;
    private BlockState copycatMaterial = null;
    private ItemStack copycatConsumedItem = ItemStack.EMPTY;

    public LandmineBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public void setFuze(ItemStack stack) {
        this.fuze = stack;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public ItemStack getFuze() {
        return this.fuze;
    }

    public void setCamouflage(BlockState state) {
        this.camouflage = state;
        if (state == null || !(state.getBlock() instanceof com.simibubi.create.content.decoration.copycat.CopycatBlock)) {
            this.copycatMaterial = null;
            this.copycatConsumedItem = ItemStack.EMPTY;
        }
        setChanged();
        requestModelDataUpdate();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public BlockState getCamouflage() {
        return this.camouflage;
    }

    public void setCopycatMaterial(BlockState material, ItemStack consumedItem) {
        this.copycatMaterial = material;
        this.copycatConsumedItem = consumedItem;
        setChanged();
        requestModelDataUpdate();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public BlockState getCopycatMaterial() {
        return this.copycatMaterial;
    }

    public ItemStack getCopycatConsumedItem() {
        return this.copycatConsumedItem;
    }

    @Override
    public net.neoforged.neoforge.client.model.data.ModelData getModelData() {
        if (camouflage != null && camouflage.getBlock() instanceof com.simibubi.create.content.decoration.copycat.CopycatBlock && copycatMaterial != null) {
            return net.neoforged.neoforge.client.model.data.ModelData.builder()
                    .with(com.simibubi.create.content.decoration.copycat.CopycatModel.MATERIAL_PROPERTY, copycatMaterial)
                    .build();
        }
        return super.getModelData();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!fuze.isEmpty()) {
            tag.put("MineFuze", fuze.saveOptional(registries));
        }
        if (camouflage != null) {
            tag.put("CamouflageState", net.minecraft.nbt.NbtUtils.writeBlockState(camouflage));
        }
        if (copycatMaterial != null) {
            tag.put("CopycatMaterial", net.minecraft.nbt.NbtUtils.writeBlockState(copycatMaterial));
        }
        if (!copycatConsumedItem.isEmpty()) {
            tag.put("CopycatItem", copycatConsumedItem.saveOptional(registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("MineFuze")) {
            this.fuze = ItemStack.parseOptional(registries, tag.getCompound("MineFuze"));
        } else {
            this.fuze = ItemStack.EMPTY;
        }
        if (tag.contains("CamouflageState")) {
            this.camouflage = net.minecraft.nbt.NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(), tag.getCompound("CamouflageState"));
        } else {
            this.camouflage = null;
        }
        if (tag.contains("CopycatMaterial")) {
            this.copycatMaterial = net.minecraft.nbt.NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(), tag.getCompound("CopycatMaterial"));
        } else {
            this.copycatMaterial = null;
        }
        if (tag.contains("CopycatItem")) {
            this.copycatConsumedItem = ItemStack.parseOptional(registries, tag.getCompound("CopycatItem"));
        } else {
            this.copycatConsumedItem = ItemStack.EMPTY;
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection connection, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        CompoundTag tag = packet.getTag();
        if (tag != null) {
            loadAdditional(tag, registries);
            requestModelDataUpdate();
            if (level != null) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.handleUpdateTag(tag, registries);
        loadAdditional(tag, registries);
        requestModelDataUpdate();
    }

    public void tick() {
        if (level == null || level.isClientSide) return;
        BlockState state = getBlockState();
        if (state.hasProperty(LandmineBlock.FUZED) && state.getValue(LandmineBlock.FUZED)) {
            if (fuze.getItem() instanceof com.happysg.kaboom.items.MineFuze) {
                int sensitivity = com.happysg.kaboom.items.MineFuze.getSensitivity(fuze);
                
                net.minecraft.world.phys.AABB aabb = new net.minecraft.world.phys.AABB(worldPosition);
                java.util.List<net.minecraft.world.entity.Entity> entities = level.getEntities((net.minecraft.world.entity.Entity) null, aabb);
                for (net.minecraft.world.entity.Entity entity : entities) {
                    String entityName = entity.getType().getDescriptionId().toLowerCase();
                    boolean isMassiveEntity = entityName.contains("sable") ||
                                              entityName.contains("ship") ||
                                              entityName.contains("contraption");

                    double centerX = worldPosition.getX() + 0.5;
                    double centerZ = worldPosition.getZ() + 0.5;
                    double dx = Math.abs(entity.getX() - centerX);
                    double dz = Math.abs(entity.getZ() - centerZ);
                    
                    if (isMassiveEntity || (dx < 0.45 && dz < 0.45)) {
                        float weight = -1.0f;
                        if (isMassiveEntity) {
                            weight = 1000.0f;
                        } else if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
                            float width = living.getBbWidth();
                            float height = living.getBbHeight();
                            weight = width * width * height * 100.0f;
                        }
                        
                        if (weight >= sensitivity) {
                            if (getBlockState().getBlock() instanceof LandmineBlock landmine) {
                                landmine.explodeMine(level, worldPosition);
                            }
                            return;
                        }
                    }
                }
                
                net.minecraft.world.phys.AABB aboveAabb = new net.minecraft.world.phys.AABB(
                    worldPosition.getX() + 0.05, worldPosition.getY() + 0.25, worldPosition.getZ() + 0.05,
                    worldPosition.getX() + 0.95, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.95
                );
                
                Iterable<net.minecraft.world.phys.shapes.VoxelShape> collisions = level.getBlockCollisions(null, aboveAabb);
                boolean isCrushed = false;
                for (net.minecraft.world.phys.shapes.VoxelShape shape : collisions) {
                    if (!shape.isEmpty()) {
                        isCrushed = true;
                        break;
                    }
                }
                
                if (!isCrushed) {
                    double[] offsets = {0.5, 0.2, 0.8};
                    for (double ox : offsets) {
                        for (double oz : offsets) {
                            if (isCrushed) break;
                            net.minecraft.world.phys.Vec3 start = new net.minecraft.world.phys.Vec3(worldPosition.getX() + ox, worldPosition.getY() + 0.25, worldPosition.getZ() + oz);
                            net.minecraft.world.phys.Vec3 end = new net.minecraft.world.phys.Vec3(worldPosition.getX() + ox, worldPosition.getY() + 0.35, worldPosition.getZ() + oz);
                            net.minecraft.world.level.ClipContext context = new net.minecraft.world.level.ClipContext(
                                start, end,
                                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                                net.minecraft.world.level.ClipContext.Fluid.NONE,
                                net.minecraft.world.phys.shapes.CollisionContext.empty()
                            );
                            net.minecraft.world.phys.BlockHitResult hitResult = level.clip(context);
                            if (hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
                                isCrushed = true;
                            }
                        }
                    }
                }
                
                if (!isCrushed) {
                    if (net.neoforged.fml.ModList.get().isLoaded("sable")) {
                        isCrushed = com.happysg.kaboom.compat.SableCompatibility.checkWheelMeshes(level, worldPosition);
                    }
                }
                
                if (isCrushed) {
                    if (getBlockState().getBlock() instanceof LandmineBlock landmine) {
                        landmine.explodeMine(level, worldPosition);
                    }
                }
            }
        }
    }
}
