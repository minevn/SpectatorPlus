package com.hpfxd.spectatorplus.fabric.client.mixin;

import com.hpfxd.spectatorplus.fabric.client.util.SpecUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LevelExtractor.class)
public abstract class LevelExtractorMixin {
    @Shadow @Final private Minecraft minecraft;

    @ModifyArg(method = "extractPlayerState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/extract/LevelExtractor;extractEntity(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;"), index = 0)
    private Entity spectatorplus$extractSpectatedAvatar(Entity entity) {
        // first person hands are rendered from this avatar state (skin, arm, swing, sleeves, invisibility)

        final AbstractClientPlayer spectated = SpecUtil.getCameraPlayer(this.minecraft);
        if (spectated != null) {
            return spectated;
        }

        return entity;
    }

    // Redirect screen effect calls from mc.player to the current camera entity

    @Redirect(method = "extractPlayerState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isEyeInFluid(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean spectatorplus$modifyIsEyeInFluid(LocalPlayer instance, TagKey<Fluid> tagKey) {
        Entity camera = this.minecraft.getCameraEntity();
        return camera != null ? camera.isEyeInFluid(tagKey) : instance.isEyeInFluid(tagKey);
    }

    @Redirect(method = "extractPlayerState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isOnFire()Z"))
    private boolean spectatorplus$modifyIsOnFire(LocalPlayer instance) {
        Entity camera = this.minecraft.getCameraEntity();
        return camera != null ? camera.isOnFire() : instance.isOnFire();
    }
}
