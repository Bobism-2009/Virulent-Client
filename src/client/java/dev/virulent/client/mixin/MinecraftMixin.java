package dev.virulent.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.virulent.client.module.modules.misc.Teleport;
import dev.virulent.client.module.modules.player.MultiTask;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class MinecraftMixin {
	@Shadow
	@Nullable
	public LocalPlayer player;

	@Shadow
	@Nullable
	public MultiPlayerGameMode gameMode;

	@Shadow
	@Final
	public Options options;

	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	private void virulent$clickTpAttack(CallbackInfoReturnable<Boolean> cir) {
		if (Teleport.handleAttackClick()) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
	private void virulent$clickTpUse(CallbackInfo ci) {
		if (Teleport.handleUseClick()) {
			ci.cancel();
		}
	}

	@ModifyExpressionValue(
		method = "startUseItem",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;isDestroying()Z")
	)
	private boolean virulent$multiTaskUseWhileBreaking(boolean original) {
		return !MultiTask.isActive() && original;
	}

	@ModifyExpressionValue(
		method = "continueAttack",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z")
	)
	private boolean virulent$multiTaskBreakWhileUsing(boolean original) {
		return !MultiTask.isActive() && original;
	}

	@ModifyExpressionValue(
		method = "handleKeybinds",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z", ordinal = 0)
	)
	private boolean virulent$multiTaskAttackWhileUsing(boolean original) {
		return !MultiTask.attackingEntities() && original;
	}

	@Inject(
		method = "handleKeybinds",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z",
			ordinal = 0,
			shift = At.Shift.BEFORE
		)
	)
	private void virulent$multiTaskReleaseUse(CallbackInfo ci) {
		if (!MultiTask.attackingEntities() || player == null || gameMode == null || options == null) {
			return;
		}
		if (!player.isUsingItem()) {
			return;
		}
		if (!options.keyUse.isDown()) {
			gameMode.releaseUsingItem(player);
		}
		while (options.keyUse.consumeClick()) {
			// drain clicks so use doesn't re-trigger while multitasking attacks
		}
	}
}
