package com.hpfxd.spectatorplus.fabric.client.mixin;

import com.hpfxd.spectatorplus.fabric.client.util.SpecUtil;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

// With a shader pack active, Iris renders hands itself and skips them in spectator mode
@Pseudo
@Mixin(targets = "net.irisshaders.iris.pathways.HandRenderer")
public abstract class IrisHandRendererMixin {
    // require = 0: optional compat, an Iris update that changes canRender must not crash the game
    @ModifyExpressionValue(method = "canRender", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;getPlayerMode()Lnet/minecraft/world/level/GameType;"), require = 0)
    private GameType spectatorplus$spoofPlayerMode(GameType original) {
        if (SpecUtil.getArmsRenderPlayer(Minecraft.getInstance()) != null) {
            return GameType.SURVIVAL;
        }
        return original;
    }
}
