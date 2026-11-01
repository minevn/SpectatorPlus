package com.hpfxd.spectatorplus.fabric.client.mixin;

import com.hpfxd.spectatorplus.fabric.client.SpectatorClientMod;
import com.hpfxd.spectatorplus.fabric.client.util.SpecUtil;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.Pose;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Shadow
    @Final
    private Minecraft minecraft;
    @Shadow
    @Final
    private RenderBuffers renderBuffers;
    @Shadow
    @Final
    private SubmitNodeStorage handAndScreenSubmitNodeStorage;

    @Unique
    private float bob;
    @Unique
    private float bobO;
    @Unique
    private float walkDist;
    @Unique
    private float walkDistO;

    // @Redirect(method = "renderItemInHand", at = @At(value = "INVOKE", target =
    // "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Matrix4fc;)V",
    // ordinal = 0))
    // private void redirectMulPose(PoseStack poseStack, Matrix4fc matrix) {
    // // In spectator mode, the projection matrix contains rotation data from the
    // spectated player,
    // // while arm rendering calculations are based on the localPlayer's viewpoint.
    // // We must skip this mulPose operation when spectating to avoid rendering
    // arms with incorrect orientation.
    // if (this.minecraft.player == null
    // || this.minecraft.player.gameMode() != GameType.SPECTATOR
    // || !this.minecraft.options.getCameraType().isFirstPerson()) {
    // poseStack.mulPose(matrix);
    // }
    // }

    @ModifyExpressionValue(method = "renderItemInHand", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;getPlayerMode()Lnet/minecraft/world/level/GameType;"))
    private GameType spectatorplus$spoofPlayerMode(GameType original) {
        if (original == GameType.SPECTATOR && SpectatorClientMod.config.renderArms) {
            final AbstractClientPlayer spectated = SpecUtil.getCameraPlayer(this.minecraft);
            if (spectated != null && !spectated.isSpectator()) {
                return GameType.SURVIVAL;
            }
        }
        return original;
    }

    @Inject(method = "tick()V", at = @At("HEAD"))
    private void spectatorplus$tick(CallbackInfo ci) {
        final AbstractClientPlayer spectated = SpecUtil.getCameraPlayer(this.minecraft);
        if (spectated != null) {
            // View bobbing

            // get horizontal distance between current and last pos
            final Vec3 pos = spectated.position().with(Direction.Axis.Y, 0);
            final Vec3 posO = spectated.getPosition(0F).with(Direction.Axis.Y, 0);
            final float deltaMovement = (float) posO.distanceTo(pos);

            this.walkDistO = this.walkDist;
            this.walkDist += deltaMovement * 0.6F;

            this.bobO = this.bob;
            if (spectated.isPassenger() || !spectated.onGround() || spectated.isDeadOrDying()
                    || spectated.isSwimming()) {
                this.bob = 0.0F;
            } else {
                this.bob += (Math.min(0.1F, deltaMovement) - this.bob) * 0.4F;
            }
        } else {
            this.bob = this.bobO = this.walkDist = this.walkDistO = 0;
        }
    }

    @ModifyExpressionValue(method = "bobView", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/state/level/CameraEntityRenderState;backwardsInterpolatedWalkDistance:F"))
    float spectatorplus$modifyBobWalkDist(float original) {
        if (minecraft.getCameraEntity() == this.minecraft.player)
            return original;
        float f = this.walkDist - this.walkDistO;
        return -(this.walkDist + f);
    }

    @ModifyExpressionValue(method = "bobView", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/state/level/CameraEntityRenderState;bob:F"))
    float spectatorplus$modifyBobValue(float original) {
        if (minecraft.getCameraEntity() == this.minecraft.player)
            return original;
        return Mth.lerp(1.0F, this.bobO, this.bob);
    }
}
