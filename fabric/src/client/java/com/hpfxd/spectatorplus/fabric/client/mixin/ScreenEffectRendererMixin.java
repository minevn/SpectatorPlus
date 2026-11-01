package com.hpfxd.spectatorplus.fabric.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Redirect calls from mc.player to the current camera entity, see LevelExtractorMixin for the extracted overlay state

@Mixin(ScreenEffectRenderer.class)
public class ScreenEffectRendererMixin {
    @ModifyExpressionValue(method = "submit", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;isSpectator:Z"))
    private boolean spectatorplus$modifyIsSpectator(boolean original) {
        Entity camera = Minecraft.getInstance().getCameraEntity();
        return camera != null ? camera.isSpectator() : original;
    }
}
