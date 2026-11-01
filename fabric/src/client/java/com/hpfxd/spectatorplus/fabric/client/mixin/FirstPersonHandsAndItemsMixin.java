package com.hpfxd.spectatorplus.fabric.client.mixin;

import com.hpfxd.spectatorplus.fabric.client.util.SpecUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.FirstPersonHandsAndItems;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FirstPersonHandsAndItems.class)
public abstract class FirstPersonHandsAndItemsMixin {
    @Shadow private float mainHandHeight;
    @Shadow private float oMainHandHeight;
    @Shadow private float oOffHandHeight;
    @Shadow private float offHandHeight;
    @Shadow private ItemStack mainHandItem;
    @Shadow private ItemStack offHandItem;

    @Shadow
    private static boolean isChargedCrossbow(ItemStack item) {
        throw new AssertionError();
    }

    @Unique private int spectatedId = -1;

    @Unique private float xBob;
    @Unique private float yBob;
    @Unique private float xBobO;
    @Unique private float yBobO;

    @Inject(at = @At("HEAD"), method = "tick", cancellable = true)
    public void spectatorplus$fixSpectatorHandHeight(LocalPlayer player, CallbackInfo ci) {
        final AbstractClientPlayer spectated = SpecUtil.getCameraPlayer(Minecraft.getInstance());
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
                float attackStrength = spectated.getAttackStrengthScale(1.0F);
                float mainHandTargetHeight = (this.mainHandItem != mainHandItem || sleeping) ? 0.0F : attackStrength * attackStrength * attackStrength;
                float offHandTargetHeight = (this.offHandItem != offHandItem || sleeping) ? 0.0F : 1.0F;
                this.mainHandHeight = this.mainHandHeight + Mth.clamp(mainHandTargetHeight - this.mainHandHeight, -0.4F, 0.4F);
                this.offHandHeight = this.offHandHeight + Mth.clamp(offHandTargetHeight - this.offHandHeight, -0.4F, 0.4F);

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

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void spectatorplus$extractSpectatedHands(LocalPlayer player, float partialTicks, FirstPersonHandsAndItemsRenderState state, CallbackInfo ci) {
        final AbstractClientPlayer spectated = SpecUtil.getCameraPlayer(Minecraft.getInstance());
        if (spectated == null) {
            return;
        }

        // vanilla reads these from the local player, replace them with the spectated player's values
        final LivingEntity.SwingDescription currentSwing = spectated.getCurrentSwing();
        state.attackHand = currentSwing == null ? InteractionHand.MAIN_HAND : currentSwing.hand();
        state.viewXRot = spectated.getViewXRot(partialTicks);
        state.viewYRot = Mth.rotLerp(partialTicks, spectated.yRotO, spectated.getYRot());
        state.xBob = Mth.lerp(partialTicks, this.xBobO, this.xBob);
        // the renderer sways by (viewYRot - yBob), keep that difference within a single turn
        state.yBob = state.viewYRot - Mth.degreesDifference(Mth.lerp(partialTicks, this.yBobO, this.yBob), state.viewYRot);
        state.isScoping = spectated.isScoping();
        state.useItemRemainingTicks = spectated.getUseItemRemainingTicks();
        state.handRenderSelection = evaluateWhichHandsToRender(spectated);

        final boolean isMainHandRight = spectated.getMainArm() == HumanoidArm.RIGHT;
        final ItemDisplayContext mainHandDisplayContext = isMainHandRight ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        final ItemDisplayContext offHandDisplayContext = isMainHandRight ? ItemDisplayContext.FIRST_PERSON_LEFT_HAND : ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
        final ItemModelResolver itemModelResolver = Minecraft.getInstance().getItemModelResolver();
        state.mainHandRenderState.clear();
        state.offHandRenderState.clear();
        itemModelResolver.updateForTopItem(state.mainHandRenderState, state.mainHandItem, mainHandDisplayContext, spectated.level(), spectated, spectated.getId() + mainHandDisplayContext.ordinal());
        itemModelResolver.updateForTopItem(state.offHandRenderState, state.offHandItem, offHandDisplayContext, spectated.level(), spectated, spectated.getId() + offHandDisplayContext.ordinal());
        state.mainHandUseDuration = state.mainHandItem.getUseDuration(spectated);
        state.offHandUseDuration = state.offHandItem.getUseDuration(spectated);
        state.mainHandChargeDuration = CrossbowItem.getChargeDuration(state.mainHandItem, spectated);
        state.offHandChargeDuration = CrossbowItem.getChargeDuration(state.offHandItem, spectated);
    }

    @Unique
    private static FirstPersonHandsAndItemsRenderState.HandRenderSelection evaluateWhichHandsToRender(AbstractClientPlayer player) {
        // see FirstPersonHandsAndItems#evaluateWhichHandsToRender
        // could not just call into the original method due to the argument being LocalPlayer

        final ItemStack mainHandItem = player.getMainHandItem();
        final ItemStack offHandItem = player.getOffhandItem();
        final boolean holdsBow = mainHandItem.is(Items.BOW) || offHandItem.is(Items.BOW);
        final boolean holdsCrossbow = mainHandItem.is(Items.CROSSBOW) || offHandItem.is(Items.CROSSBOW);
        if (!holdsBow && !holdsCrossbow) {
            return FirstPersonHandsAndItemsRenderState.HandRenderSelection.RENDER_BOTH_HANDS;
        } else if (!player.isUsingItem()) {
            return isChargedCrossbow(mainHandItem)
                    ? FirstPersonHandsAndItemsRenderState.HandRenderSelection.RENDER_MAIN_HAND_ONLY
                    : FirstPersonHandsAndItemsRenderState.HandRenderSelection.RENDER_BOTH_HANDS;
        } else {
            final ItemStack usedItemStack = player.getUseItem();
            final InteractionHand usedHand = player.getUsedItemHand();
            if (!usedItemStack.is(Items.BOW) && !usedItemStack.is(Items.CROSSBOW)) {
                return usedHand == InteractionHand.MAIN_HAND && isChargedCrossbow(offHandItem)
                        ? FirstPersonHandsAndItemsRenderState.HandRenderSelection.RENDER_MAIN_HAND_ONLY
                        : FirstPersonHandsAndItemsRenderState.HandRenderSelection.RENDER_BOTH_HANDS;
            } else {
                return FirstPersonHandsAndItemsRenderState.HandRenderSelection.onlyForHand(usedHand);
            }
        }
    }
}
