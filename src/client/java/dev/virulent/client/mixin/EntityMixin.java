package dev.virulent.client.mixin;

import dev.virulent.client.module.modules.combat.Velocity;
import dev.virulent.client.module.modules.movement.NoFall;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class EntityMixin {
	@Inject(method = "onGround", at = @At("HEAD"), cancellable = true)
	private void virulent$noFallSpoof(CallbackInfoReturnable<Boolean> cir) {
		if (NoFall.shouldSpoofGround((Entity) (Object) this)) {
			cir.setReturnValue(true);
		}
	}

	/** Scale mob/player collision push (swarm knockaround) when Velocity entity-push is on. */
	@ModifyVariable(method = "push(DDD)V", at = @At("HEAD"), ordinal = 0, argsOnly = true)
	private double virulent$scaleEntityPushX(double x) {
		return scaleEntityPush(x);
	}

	@ModifyVariable(method = "push(DDD)V", at = @At("HEAD"), ordinal = 2, argsOnly = true)
	private double virulent$scaleEntityPushZ(double z) {
		return scaleEntityPush(z);
	}

	private double scaleEntityPush(double value) {
		if (!Velocity.reducesEntityPush()) {
			return value;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || (Object) this != client.player) {
			return value;
		}
		return value * Velocity.entityPushScale();
	}
}
