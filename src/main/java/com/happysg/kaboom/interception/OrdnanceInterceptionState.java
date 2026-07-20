package com.happysg.kaboom.interception;

import com.happysg.kaboom.config.KaboomConfig;
import com.happysg.kaboom.registry.ModTags;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;

/**
 * Server-authoritative health and lethal-resolution state shared by
 * interceptable ordnance entities.
 */
public final class OrdnanceInterceptionState {
    private static final String HEALTH_TAG = "KaboomInterceptionHealth";
    private static final String RESOLVED_TAG = "KaboomInterceptionResolved";

    private float remainingHealth;
    private boolean resolved;

    public OrdnanceInterceptionState() {
        this.remainingHealth = configuredHealth();
    }

    /**
     * Applies raw interception damage and resolves a lethal hit exactly once.
     *
     * @return {@code true} when this was a valid interception hit, including a
     *         lethal hit; {@code false} when the damage should be ignored
     */
    public boolean hurt(Entity entity, DamageSource source, float amount, Runnable detonationCallback) {
        if (entity.level().isClientSide()
                || entity.isRemoved()
                || resolved
                || !Float.isFinite(amount)
                || amount <= 0.0F
                || !source.is(ModTags.DamageTypes.INTERCEPTS_ORDNANCE)) {
            return false;
        }

        remainingHealth = Math.min(getRemainingHealth(), configuredHealth());
        remainingHealth -= amount;
        if (remainingHealth > 0.0F) {
            return true;
        }

        remainingHealth = 0.0F;
        resolved = true;
        try {
            if (entity.getRandom().nextFloat() < configuredDetonationChance()) {
                detonationCallback.run();
            }
        } finally {
            entity.discard();
        }
        return true;
    }

    public void save(CompoundTag tag) {
        tag.putFloat(HEALTH_TAG, getRemainingHealth());
        if (resolved) {
            tag.putBoolean(RESOLVED_TAG, true);
        }
    }

    public void load(CompoundTag tag) {
        float configuredHealth = configuredHealth();
        if (tag.contains(HEALTH_TAG, Tag.TAG_ANY_NUMERIC)) {
            float savedHealth = tag.getFloat(HEALTH_TAG);
            remainingHealth = Float.isFinite(savedHealth)
                    ? Math.max(0.0F, Math.min(savedHealth, configuredHealth))
                    : configuredHealth;
        } else {
            remainingHealth = configuredHealth;
        }
        resolved = tag.getBoolean(RESOLVED_TAG);
    }

    public float getRemainingHealth() {
        return remainingHealth;
    }

    public boolean isResolved() {
        return resolved;
    }

    private static float configuredHealth() {
        if (KaboomConfig.server() == null) {
            return 20.0F;
        }
        return Math.max(1.0F, KaboomConfig.server().ordnanceHealth.getF());
    }

    private static float configuredDetonationChance() {
        if (KaboomConfig.server() == null) {
            return 0.5F;
        }
        return Math.clamp(KaboomConfig.server().interceptedDetonationChance.getF(), 0.0F, 1.0F);
    }
}
