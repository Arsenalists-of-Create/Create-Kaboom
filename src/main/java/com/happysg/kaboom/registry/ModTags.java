package com.happysg.kaboom.registry;

import com.happysg.kaboom.CreateKaboom;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.level.block.Block;

public class ModTags {
    public static final class Blocks {
        public static final TagKey<Block> FRAG_SHATTERS =
                TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(CreateKaboom.MODID, "frag_shatters"));
        public static final TagKey<Block> BLAST_TRANSPARENT =
                TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(CreateKaboom.MODID, "blast_transparent"));
    }

    public static final class DamageTypes {
        public static final TagKey<DamageType> INTERCEPTS_ORDNANCE =
                TagKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(CreateKaboom.MODID, "intercepts_ordnance"));
    }
}
