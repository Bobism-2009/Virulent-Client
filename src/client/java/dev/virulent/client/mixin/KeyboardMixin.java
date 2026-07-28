package dev.virulent.client.mixin;

import dev.virulent.client.VirulentClient;
import dev.virulent.client.event.events.KeyEvent;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class KeyboardMixin {
	@Inject(method = "keyPress", at = @At("HEAD"))
	private void onKeyPress(long window, int action, net.minecraft.client.input.KeyEvent event, CallbackInfo ci) {
		VirulentClient instance = VirulentClient.getInstance();
		if (instance == null) {
			return;
		}

		KeyEvent keyEvent = new KeyEvent(event.key(), action);
		instance.getEventBus().post(keyEvent);
		instance.getModuleManager().onKey(keyEvent);
	}
}
