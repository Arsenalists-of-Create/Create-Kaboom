package com.happysg.kaboom.client.model;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.happysg.kaboom.CreateKaboom;
import com.happysg.kaboom.items.rocket.RocketGuidanceType;
import com.happysg.kaboom.items.rocket.RocketItem;
import com.happysg.kaboom.items.rocket.RocketPayload;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Retextures one of two rocket model templates from the rocket stack's
 * guidance, payload, and fuze components.
 */
public final class RocketModel implements IUnbakedGeometry<RocketModel> {
    private static final ResourceLocation DEFAULT_BODY_TEXTURE = CreateKaboom.asResource(
            "item/rocket/unguided/high_explosive_rocket");

    private final BlockModel baseModel;
    private final BlockModel fuzedModel;
    private final ResourceLocation bodyPlaceholder;

    private RocketModel(BlockModel baseModel, BlockModel fuzedModel, ResourceLocation bodyPlaceholder) {
        this.baseModel = baseModel;
        this.fuzedModel = fuzedModel;
        this.bodyPlaceholder = bodyPlaceholder;
    }

    @Override
    public BakedModel bake(IGeometryBakingContext context,
                           ModelBaker baker,
                           Function<Material, TextureAtlasSprite> spriteGetter,
                           ModelState modelState,
                           ItemOverrides overrides) {
        BakedModel defaultModel = this.bakeVariant(
                this.baseModel, DEFAULT_BODY_TEXTURE, baker, spriteGetter, modelState);
        RocketOverrides rocketOverrides = new RocketOverrides(
                overrides, baker, modelState, this, defaultModel);
        return new OverrideModel(defaultModel, rocketOverrides);
    }

    @Override
    public void resolveParents(Function<ResourceLocation, UnbakedModel> modelGetter,
                               IGeometryBakingContext context) {
        this.baseModel.resolveParents(modelGetter);
        this.fuzedModel.resolveParents(modelGetter);
    }

    private BakedModel bakeVariant(BlockModel template,
                                   ResourceLocation bodyTexture,
                                   ModelBaker baker,
                                   Function<Material, TextureAtlasSprite> spriteGetter,
                                   ModelState modelState) {
        Material replacement = new Material(InventoryMenu.BLOCK_ATLAS, bodyTexture);
        Function<Material, TextureAtlasSprite> remappingGetter = material ->
                material.atlasLocation().equals(InventoryMenu.BLOCK_ATLAS)
                        && material.texture().equals(this.bodyPlaceholder)
                        ? spriteGetter.apply(replacement)
                        : spriteGetter.apply(material);
        return template.bake(baker, template, remappingGetter, modelState, true);
    }

    private static ResourceLocation resolveBodyTexture(ItemStack stack) {
        RocketPayload payloadType = RocketItem.getPayload(stack);
        if (payloadType == null) {
            return DEFAULT_BODY_TEXTURE;
        }
        String payload = switch (payloadType) {
            case HE -> "high_explosive";
            case AP -> "armor_piercing";
            case SHRAPNEL -> "fragmentation";
            case SMOKE -> "smoke";
            case FLUID -> "fluid";
        };

        RocketGuidanceType guidance = RocketItem.getGuidanceType(stack);
        String path;
        if (guidance == null) {
            path = "unguided/" + payload + "_rocket";
        } else {
            path = switch (guidance) {
                case COMMAND -> "command/command_guided_" + payload + "_rocket";
                case RADAR -> "radar/radar_seeker_" + payload + "_rocket";
                case ARAD -> "unguided/" + payload + "_rocket";
            };
        }
        return CreateKaboom.asResource("item/rocket/" + path);
    }

    private static boolean hasFuze(ItemStack stack) {
        return RocketItem.hasPayload(stack) && !RocketItem.getAttachedFuze(stack).isEmpty();
    }

    public static final class Loader implements IGeometryLoader<RocketModel> {
        public static final Loader INSTANCE = new Loader();

        private Loader() {
        }

        @Override
        public RocketModel read(JsonObject json, JsonDeserializationContext context) throws JsonParseException {
            BlockModel base = readChildModel(json, context, "base");
            BlockModel fuzed = readChildModel(json, context, "fuzed");
            String bodyTexture = GsonHelper.getAsString(json, "body_texture");
            try {
                return new RocketModel(base, fuzed, ResourceLocation.parse(bodyTexture));
            } catch (RuntimeException exception) {
                throw new JsonParseException("Invalid rocket body_texture: " + bodyTexture, exception);
            }
        }

        private static BlockModel readChildModel(JsonObject json,
                                                 JsonDeserializationContext context,
                                                 String name) {
            if (!json.has(name) || !json.get(name).isJsonObject()) {
                throw new JsonParseException("Rocket model requires a '" + name + "' model object");
            }
            return context.deserialize(json.getAsJsonObject(name), BlockModel.class);
        }
    }

    private static final class RocketOverrides extends ItemOverrides {
        private final Map<VariantKey, BakedModel> cache = new HashMap<>();
        private final ItemOverrides nested;
        private final ModelBaker baker;
        private final ModelState modelState;
        private final RocketModel parent;

        private RocketOverrides(ItemOverrides nested,
                                ModelBaker baker,
                                ModelState modelState,
                                RocketModel parent,
                                BakedModel defaultModel) {
            this.nested = nested;
            this.baker = baker;
            this.modelState = modelState;
            this.parent = parent;
            this.cache.put(new VariantKey(DEFAULT_BODY_TEXTURE, false), defaultModel);
        }

        @Override
        public BakedModel resolve(BakedModel originalModel,
                                  ItemStack stack,
                                  @Nullable ClientLevel level,
                                  @Nullable LivingEntity entity,
                                  int seed) {
            BakedModel nestedModel = this.nested.resolve(originalModel, stack, level, entity, seed);
            if (nestedModel != originalModel) {
                return nestedModel;
            }

            VariantKey key = new VariantKey(resolveBodyTexture(stack), hasFuze(stack));
            return this.cache.computeIfAbsent(key, variant -> this.parent.bakeVariant(
                    variant.fuzed() ? this.parent.fuzedModel : this.parent.baseModel,
                    variant.bodyTexture(),
                    this.baker,
                    Material::sprite,
                    this.modelState));
        }
    }

    private static final class OverrideModel extends BakedModelWrapper<BakedModel> {
        private final ItemOverrides overrides;

        private OverrideModel(BakedModel originalModel, ItemOverrides overrides) {
            super(originalModel);
            this.overrides = overrides;
        }

        @Override
        public ItemOverrides getOverrides() {
            return this.overrides;
        }
    }

    private record VariantKey(ResourceLocation bodyTexture, boolean fuzed) {
    }
}
