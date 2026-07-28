package dev.virulent.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.virulent.client.module.modules.render.HandView;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {
	@ModifyVariable(
		method = "swing(Lnet/minecraft/world/InteractionHand;Z)V",
		at = @At("HEAD"),
		argsOnly = true,
		ordinal = 0
	)
	private InteractionHand virulent$swingMode(InteractionHand hand) {
		LivingEntity self = (LivingEntity) (Object) this;
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || self != client.player) {
			return hand;
		}

		HandView module = HandView.get();
		if (module == null || !module.isEnabled()) {
			return hand;
		}

		return switch (module.getSwingMode()) {
			case "Offhand" -> InteractionHand.OFF_HAND;
			case "Mainhand" -> InteractionHand.MAIN_HAND;
			default -> hand;
		};
	}

	@ModifyExpressionValue(
		method = "getCurrentSwingDuration",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/component/SwingAnimation;duration()I")
	)
	private int virulent$swingSpeed(int original) {
		LivingEntity self = (LivingEntity) (Object) this;
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || self != client.player) {
			return original;
		}

		HandView module = HandView.get();
		if (module == null || !module.isEnabled() || !client.options.getCameraType().isFirstPerson()) {
			return original;
		}
		return module.getSwingSpeed();
	}
}
