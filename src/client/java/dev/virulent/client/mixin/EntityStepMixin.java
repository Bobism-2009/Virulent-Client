package dev.virulent.client.mixin;

import dev.virulent.client.module.modules.movement.Step;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class EntityStepMixin {
	@Inject(method = "maxUpStep", at = @At("HEAD"), cancellable = true)
	private void virulent$stepHeight(CallbackInfoReturnable<Float> cir) {
		if (Step.isActive()) {
			cir.setReturnValue(Step.getHeight());
		}
	}
}
