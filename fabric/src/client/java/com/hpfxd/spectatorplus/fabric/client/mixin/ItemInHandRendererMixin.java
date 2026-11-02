package com.hpfxd.spectatorplus.fabric.client.mixin;

import com.google.common.base.MoreObjects;
import com.hpfxd.spectatorplus.fabric.client.util.SpecUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow private float mainHandHeight;
    @Shadow private float oMainHandHeight;
    @Shadow private float oOffHandHeight;
    @Shadow private float offHandHeight;
    @Shadow private ItemStack mainHandItem;
    @Shadow private ItemStack offHandItem;
    @Shadow @Final private ItemModelResolver itemModelResolver;

    @Shadow
    private static boolean isChargedCrossbow(ItemStack stack) {
        throw new AssertionError();
    }

    @Shadow
    private void submitArmWithItem(AbstractClientPlayer player, float partialTicks, float pitch, InteractionHand hand,
                                   float swingProgress, ItemStack itemStack, float equippedProgress, PoseStack poseStack,
                                   SubmitNodeCollector submitNodeCollector, int packedLightCoords) {
        throw new AssertionError();
    }

    @Unique private int spectatedId = -1;

    @Unique private float xBob;
    @Unique private float yBob;
    @Unique private float xBobO;
    @Unique private float yBobO;

    @Inject(at = @At("HEAD"), method = "tick", cancellable = true)
    public void spectatorplus$fixSpectatorHandHeight(CallbackInfo ci) {
        final AbstractClientPlayer spectated = SpecUtil.getCameraPlayer(this.minecraft);
        if (spectated != null) {
            ci.cancel();

            this.oMainHandHeight = this.mainHandHeight;
            this.oOffHandHeight = this.offHandHeight;

            final ItemStack mainHandItem = spectated.getMainHandItem();
            final ItemStack offHandItem = spectated.getOffhandItem();

            if (ItemStack.matches(this.mainHandItem, mainHandItem)) {
                this.mainHandItem = mainHandItem;
            }

            if (ItemStack.matches(this.offHandItem, offHandItem)) {
                this.offHandItem = offHandItem;
            }

            if (this.spectatedId == spectated.getId()) {
                boolean sleeping = spectated.isSleeping() || spectated.getPose() == Pose.SLEEPING;
                float f = spectated.getAttackStrengthScale(1.0F);
                float g = (this.mainHandItem != mainHandItem || sleeping) ? 0.0F : f * f * f;
                float h = (this.offHandItem != offHandItem || sleeping) ? 0.0F : 1.0F;
                this.mainHandHeight = this.mainHandHeight + Mth.clamp(g - this.mainHandHeight, -0.4F, 0.4F);
                this.offHandHeight = this.offHandHeight + Mth.clamp(h - this.offHandHeight, -0.4F, 0.4F);

                if (this.mainHandHeight < 0.1F) {
                    this.mainHandItem = mainHandItem;
                }

                if (this.offHandHeight < 0.1F) {
                    this.offHandItem = offHandItem;
                }
            } else {
                // this is the first tick of spectating a new player

                this.spectatedId = spectated.getId();
                this.mainHandHeight = 1F;
                this.offHandHeight = 1F;
                this.mainHandItem = mainHandItem;
                this.offHandItem = offHandItem;
            }

            // Hand swaying
            this.yBobO = this.yBob;
            this.xBobO = this.xBob;
            this.xBob += Mth.degreesDifference(this.xBob, spectated.getXRot()) * 0.5F;
            this.yBob += Mth.degreesDifference(this.yBob, spectated.getYRot()) * 0.5F;
        } else {
            this.spectatedId = -1;
            this.xBob = this.yBob = this.xBobO = this.yBobO = 0;
        }
    }

    // Hooked here instead of at the GameRenderer call site: with a shader pack, Iris skips that call
    // and renders hands itself through this method
    @Inject(method = "submitHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void spectatorplus$renderSpectatedArms(float partialTicks, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, LocalPlayer localPlayer, int packedLightCoords, CallbackInfo ci) {
        final AbstractClientPlayer spectated = SpecUtil.getArmsRenderPlayer(this.minecraft);
        if (spectated == null) {
            return;
        }
        ci.cancel();

        final float attackAnim = spectated.getAttackAnim(partialTicks);
        final InteractionHand interactionHand = MoreObjects.firstNonNull(spectated.swingingArm, InteractionHand.MAIN_HAND);
        final float pitch = Mth.lerp(partialTicks, spectated.xRotO, spectated.getXRot());

        poseStack.pushPose();
        poseStack.mulPose(Axis.XP.rotationDegrees(
                (spectated.getViewXRot(partialTicks) - Mth.lerp(partialTicks, this.xBobO, this.xBob)) * 0.1F));
        poseStack.mulPose(
                Axis.YP.rotationDegrees(Mth.degreesDifference(Mth.lerp(partialTicks, this.yBobO, this.yBob),
                        Mth.rotLerp(partialTicks, spectated.yRotO, spectated.getYRot())) * 0.1F));

        final ItemInHandRenderer.HandRenderSelection handRenderSelection = evaluateWhichHandsToRender(spectated);

        if (handRenderSelection.renderMainHand) {
            final float swingProgress = interactionHand == InteractionHand.MAIN_HAND ? attackAnim : 0.0F;
            final float equippedProgress = this.itemModelResolver.swapAnimationScale(this.mainHandItem)
                    * (1F - Mth.lerp(partialTicks, this.oMainHandHeight, this.mainHandHeight));

            this.submitArmWithItem(spectated, partialTicks, pitch, InteractionHand.MAIN_HAND, swingProgress,
                    this.mainHandItem, equippedProgress, poseStack, submitNodeCollector, packedLightCoords);
        }

        if (handRenderSelection.renderOffHand) {
            final float swingProgress = interactionHand == InteractionHand.OFF_HAND ? attackAnim : 0.0F;
            final float equippedProgress = this.itemModelResolver.swapAnimationScale(this.offHandItem)
                    * (1F - Mth.lerp(partialTicks, this.oOffHandHeight, this.offHandHeight));

            this.submitArmWithItem(spectated, partialTicks, pitch, InteractionHand.OFF_HAND, swingProgress,
                    this.offHandItem, equippedProgress, poseStack, submitNodeCollector, packedLightCoords);
        }

        poseStack.popPose();
    }

    @Unique
    private static ItemInHandRenderer.HandRenderSelection evaluateWhichHandsToRender(AbstractClientPlayer player) {
        // see ItemInHandRenderer#evaluateWhichHandsToRender
        // could not just call into the original method due to the argument being LocalPlayer

        final ItemStack mainHandItem = player.getMainHandItem();
        final ItemStack offHandItem = player.getOffhandItem();
        final boolean holdingBow = mainHandItem.is(Items.BOW) || offHandItem.is(Items.BOW);
        final boolean holdingCrossbow = mainHandItem.is(Items.CROSSBOW) || offHandItem.is(Items.CROSSBOW);
        if (!holdingBow && !holdingCrossbow) {
            return ItemInHandRenderer.HandRenderSelection.RENDER_BOTH_HANDS;
        } else if (player.isUsingItem()) {
            final ItemStack useItem = player.getUseItem();
            final InteractionHand usedHand = player.getUsedItemHand();
            if (!useItem.is(Items.BOW) && !useItem.is(Items.CROSSBOW)) {
                return usedHand == InteractionHand.MAIN_HAND && isChargedCrossbow(offHandItem)
                        ? ItemInHandRenderer.HandRenderSelection.RENDER_MAIN_HAND_ONLY
                        : ItemInHandRenderer.HandRenderSelection.RENDER_BOTH_HANDS;
            } else {
                return ItemInHandRenderer.HandRenderSelection.onlyForHand(usedHand);
            }
        } else {
            return isChargedCrossbow(mainHandItem)
                    ? ItemInHandRenderer.HandRenderSelection.RENDER_MAIN_HAND_ONLY
                    : ItemInHandRenderer.HandRenderSelection.RENDER_BOTH_HANDS;
        }
    }

    @ModifyVariable(method = "renderPlayerArm(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IFFLnet/minecraft/world/entity/HumanoidArm;)V", at = @At("STORE"))
    private AbstractClientPlayer setArmPlayer(AbstractClientPlayer in) {
        // render the arm as the camera entity, instead of always as the client player

        final AbstractClientPlayer spectated = SpecUtil.getCameraPlayer(this.minecraft);
        if (spectated != null) {
            return spectated;
        }

        return in;
    }

    @Redirect(
            method = "renderMapHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/world/entity/HumanoidArm;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;getPlayerRenderer(Lnet/minecraft/client/player/AbstractClientPlayer;)Lnet/minecraft/client/renderer/entity/player/AvatarRenderer;")
    )
    private AvatarRenderer<AbstractClientPlayer> spectatorplus$mapHandUseCameraAvatarRenderer(net.minecraft.client.renderer.entity.EntityRenderDispatcher dispatcher, AbstractClientPlayer ignoredPlayer) {
        Entity cameraEntity = this.minecraft.getCameraEntity();
        if (cameraEntity instanceof AbstractClientPlayer cameraPlayer) {
            return dispatcher.getPlayerRenderer(cameraPlayer);
        }
        return dispatcher.getPlayerRenderer(this.minecraft.player);
    }

        @Redirect(
            method = "renderMapHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/world/entity/HumanoidArm;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isModelPartShown(Lnet/minecraft/world/entity/player/PlayerModelPart;)Z")
        )
        private boolean spectatorplus$mapHandFixPartVisibility(LocalPlayer instance, PlayerModelPart playerModelPart) {
        Entity cameraEntity = this.minecraft.getCameraEntity();
        if (cameraEntity instanceof Player cameraPlayer) {
            return cameraPlayer.isModelPartShown(playerModelPart);
        }
        return instance.isModelPartShown(playerModelPart);
    }

        @Redirect(method = {
            "renderOneHandedMap(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IFLnet/minecraft/world/entity/HumanoidArm;FLnet/minecraft/world/item/ItemStack;)V",
            "renderTwoHandedMap(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IFFF)V",
        }, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isInvisible()Z"))
        private boolean spectatorplus$spectatedInvisibility(LocalPlayer instance) {
        Entity cameraEntity = this.minecraft.getCameraEntity();
        if (cameraEntity instanceof net.minecraft.world.entity.Entity entity) {
            return entity.isInvisible();
        }
        return instance.isInvisible();
    }
}
