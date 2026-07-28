package dev.virulent.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.blaze3d.vertex.PoseStack;
import com.google.common.base.MoreObjects;
import dev.virulent.client.module.modules.render.HandView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
	@Shadow
	private float mainHandHeight;

	@Shadow
	private float offHandHeight;

	@Shadow
	private ItemStack mainHandItem;

	@Shadow
	private ItemStack offHandItem;

	@Shadow
	protected abstract boolean shouldInstantlyReplaceVisibleItem(ItemStack currentlyVisibleItem, ItemStack expectedItem);

	@ModifyExpressionValue(
		method = "renderHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/player/LocalPlayer;I)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getAttackAnim(F)F")
	)
	private float virulent$modifySwing(float attackValue) {
		HandView module = HandView.get();
		if (module == null || !module.isEnabled()) {
			return attackValue;
		}

		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return attackValue;
		}

		InteractionHand hand = MoreObjects.firstNonNull(player.swingingArm, InteractionHand.MAIN_HAND);
		if (module.swordSlash() && hand == InteractionHand.MAIN_HAND && mainHandItem.is(ItemTags.SWORDS)) {
			return 0.0f;
		}
		if (hand == InteractionHand.OFF_HAND && !offHandItem.isEmpty()) {
			return attackValue + module.getOffProgress();
		}
		if (hand == InteractionHand.MAIN_HAND && !mainHandItem.isEmpty()) {
			return attackValue + module.getMainProgress();
		}
		return attackValue;
	}

	@ModifyReturnValue(method = "shouldInstantlyReplaceVisibleItem", at = @At("RETURN"))
	private boolean virulent$skipSwap(boolean original) {
		return original || (HandView.get() != null && HandView.get().skipSwapping());
	}

	@ModifyArg(
		method = "tick",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;clamp(FFF)F", ordinal = 2),
		index = 0
	)
	private float virulent$mainEquipProgress(float value) {
		HandView module = HandView.get();
		LocalPlayer player = Minecraft.getInstance().player;
		if (module == null || !module.isEnabled() || player == null) {
			return value;
		}
		if (module.swordSlash() && player.getMainHandItem().is(ItemTags.SWORDS)) {
			return value;
		}

		float swap = player.getItemSwapScale(1.0f);
		float modified = module.oldAnimations() ? 1.0f : swap * swap * swap;
		return (shouldInstantlyReplaceVisibleItem(mainHandItem, player.getMainHandItem()) ? modified : 0.0f) - mainHandHeight;
	}

	@ModifyArg(
		method = "tick",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;clamp(FFF)F", ordinal = 3),
		index = 0
	)
	private float virulent$offEquipProgress(float value) {
		HandView module = HandView.get();
		LocalPlayer player = Minecraft.getInstance().player;
		if (module == null || !module.isEnabled() || player == null) {
			return value;
		}
		return (shouldInstantlyReplaceVisibleItem(offHandItem, player.getOffhandItem()) ? 1.0f : 0.0f) - offHandHeight;
	}

	@Inject(
		method = "renderArmWithItem",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
			shift = At.Shift.BEFORE
		)
	)
	private void virulent$onRenderItem(
		AbstractClientPlayer player,
		float frameInterp,
		float xRot,
		InteractionHand hand,
		float attack,
		ItemStack itemStack,
		float inverseArmHeight,
		PoseStack poseStack,
		SubmitNodeCollector submitNodeCollector,
		int lightCoords,
		CallbackInfo ci
	) {
		if (HandView.get() != null) {
			HandView.get().applyHeldItemTransforms(hand, poseStack);
		}
	}

	@Inject(
		method = "renderArmWithItem",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderPlayerArm(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IFFLnet/minecraft/world/entity/HumanoidArm;)V"
		)
	)
	private void virulent$onRenderArm(
		AbstractClientPlayer player,
		float frameInterp,
		float xRot,
		InteractionHand hand,
		float attack,
		ItemStack itemStack,
		float inverseArmHeight,
		PoseStack poseStack,
		SubmitNodeCollector submitNodeCollector,
		int lightCoords,
		CallbackInfo ci
	) {
		if (HandView.get() != null) {
			HandView.get().applyArmTransforms(poseStack);
		}
	}

	@Inject(method = "applyEatTransform", at = @At("HEAD"), cancellable = true)
	private void virulent$disableEating(
		PoseStack poseStack,
		float frameInterp,
		HumanoidArm arm,
		ItemStack itemStack,
		Player player,
		CallbackInfo ci
	) {
		if (HandView.get() != null && HandView.get().disableEating()) {
			ci.cancel();
		}
	}
}
