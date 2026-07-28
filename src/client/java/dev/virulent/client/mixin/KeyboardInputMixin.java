package dev.virulent.client.mixin;

import dev.virulent.client.util.BotMovement;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public class KeyboardInputMixin {
	@Inject(method = "tick", at = @At("RETURN"))
	private void virulent$applyBotMovement(CallbackInfo ci) {
		ClientInput input = (ClientInput) (Object) this;
		BotMovement.apply(input, (ClientInputAccessor) (Object) input);
	}
}
