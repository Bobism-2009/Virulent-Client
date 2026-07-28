package dev.virulent.client.mixin;

import dev.virulent.client.module.modules.movement.NoClip;
import dev.virulent.client.module.modules.movement.SafeWalk;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public class PlayerMixin {
	/**
	 * Vanilla only clips at ledges while sneak ({@code isStayingOnGroundSurface}).
	 * SafeWalk enables that clip without forcing sneak speed or crouch pose.
	 */
	@Inject(method = "isStayingOnGroundSurface", at = @At("HEAD"), cancellable = true)
	private void virulent$safeWalk(CallbackInfoReturnable<Boolean> cir) {
		if (SafeWalk.shouldStayOnGround((Player) (Object) this)) {
			cir.setReturnValue(true);
		}
	}

	/**
	 * Vanilla resets {@code noPhysics} from {@code isSpectator()} every tick before move.
	 * Re-assert right before {@code Avatar.tick()} so NoClip can phase through blocks.
	 */
	@Inject(
		method = "tick",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/Avatar;tick()V"
		)
	)
	private void virulent$keepNoClip(CallbackInfo ci) {
		Player self = (Player) (Object) this;
		if (!NoClip.isActive() || self != Minecraft.getInstance().player) {
			return;
		}
		((Entity) (Object) this).noPhysics = true;
		self.setOnGround(false);
	}
}
