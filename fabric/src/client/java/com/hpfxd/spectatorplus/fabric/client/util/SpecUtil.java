package com.hpfxd.spectatorplus.fabric.client.util;

import com.hpfxd.spectatorplus.fabric.client.SpectatorClientMod;
import com.hpfxd.spectatorplus.fabric.client.sync.ClientSyncController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;

public final class SpecUtil {
    private SpecUtil() {
    }

    public static AbstractClientPlayer getCameraPlayer(Minecraft minecraft) {
        if (minecraft.gameMode != null && minecraft.gameMode.getPlayerMode() == GameType.SPECTATOR
                && minecraft.getCameraEntity() instanceof final AbstractClientPlayer spectated
                && spectated != minecraft.player) {
            return spectated;
        }
        return null;
    }

    /**
     * Returns the spectated player whose first-person arms should be rendered, or null.
     */
    public static AbstractClientPlayer getArmsRenderPlayer(Minecraft minecraft) {
        if (!SpectatorClientMod.config.renderArms) {
            return null;
        }
        final AbstractClientPlayer spectated = getCameraPlayer(minecraft);
        if (spectated == null || spectated.isSpectator()) {
            return null;
        }
        return spectated;
    }

    // /**
    //  * Returns the synced MobEffectInstance for the given entity and effect, or null if not found.
    //  */
    // public static net.minecraft.world.effect.MobEffectInstance getSyncedEffect(LivingEntity entity, Holder<MobEffect> effect) {
    //     if (ClientSyncController.syncData == null || ClientSyncController.syncData.effects == null) return null;
    //     String queryKey = effect.unwrapKey().get().identifier().toString();
    //     for (com.hpfxd.spectatorplus.fabric.sync.SyncedEffect synced : ClientSyncController.syncData.effects) {
    //         if (queryKey.equals(synced.effectKey)) {
    //             return new net.minecraft.world.effect.MobEffectInstance(effect, synced.duration, synced.amplifier);
    //         }
    //     }
    //     return null;
    // }
}
