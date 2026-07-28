package dev.virulent.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.virulent.client.module.modules.movement.Flight;
import dev.virulent.client.module.modules.movement.NoClip;
import dev.virulent.client.module.modules.movement.NoFall;
import dev.virulent.client.module.modules.movement.NoSlow;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public class LocalPlayerMixin {
	/** Item-use slowdown is applied in modifyInput via isUsingItem(), not isMovingSlowly. */
	@ModifyExpressionValue(
		method = "modifyInput",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z")
	)
	private boolean virulent$noSlowItemUse(boolean usingItem) {
		return usingItem && !NoSlow.isActive();
	}

	/**
	 * Shift while flying still counts as crouching for isMovingSlowly, which applies sneak speed.
	 * That makes descent feel broken — skip sneak slowdown while abilities-flying.
	 */
	@ModifyExpressionValue(
		method = "modifyInput",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isMovingSlowly()Z")
	)
	private boolean virulent$noSneakSlowWhileFlying(boolean slowly) {
		LocalPlayer self = (LocalPlayer) (Object) this;
		if (self.getAbilities().flying) {
			return false;
		}
		return slowly;
	}

	/**
	 * Vanilla cancels creative-style flight when onGround. Crouch-descend touches ground and
	 * flickers flying off — keep Flight enabled through that check.
	 */
	@ModifyExpressionValue(
		method = "aiStep",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;onGround()Z", ordinal = 1)
	)
	private boolean virulent$flightIgnoreGroundCancel(boolean onGround) {
		return onGround && !Flight.isActive() && !NoClip.isActive();
	}

	/** Keep sprint available while using items (food, bow, shields, etc.). */
	@Inject(method = "isSlowDueToUsingItem", at = @At("HEAD"), cancellable = true)
	private void virulent$noSlowSprintWhileUsing(CallbackInfoReturnable<Boolean> cir) {
		if (NoSlow.isActive()) {
			cir.setReturnValue(false);
		}
	}

	/** Re-assert flight after aiStep in case jump-toggle or packets cleared it. */
	@Inject(method = "aiStep", at = @At("RETURN"))
	private void virulent$keepFlight(CallbackInfo ci) {
		if (!Flight.isActive() && !NoClip.isActive()) {
			return;
		}
		LocalPlayer self = (LocalPlayer) (Object) this;
		var abilities = self.getAbilities();
		abilities.mayfly = true;
		if (!abilities.flying) {
			abilities.flying = true;
		}
		if (NoClip.isActive()) {
			self.noPhysics = true;
			self.setOnGround(false);
		}
	}

	@Inject(method = "sendPosition", at = @At("HEAD"))
	private void virulent$noFallBegin(CallbackInfo ci) {
		NoFall.beginSpoof();
	}

	@Inject(method = "sendPosition", at = @At("RETURN"))
	private void virulent$noFallEnd(CallbackInfo ci) {
		NoFall.endSpoof();
		if (NoFall.isActive()) {
			((Entity) (Object) this).fallDistance = 0.0f;
		}
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void virulent$noFallReset(CallbackInfo ci) {
		if (NoFall.isActive()) {
			((Entity) (Object) this).fallDistance = 0.0f;
		}
	}
}
