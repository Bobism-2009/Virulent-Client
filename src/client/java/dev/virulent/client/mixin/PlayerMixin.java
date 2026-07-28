package dev.virulent.client.mixin;

import dev.virulent.client.module.modules.movement.SafeWalk;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
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
}
