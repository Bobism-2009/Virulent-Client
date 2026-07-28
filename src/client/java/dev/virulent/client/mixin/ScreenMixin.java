package dev.virulent.client.mixin;

import dev.virulent.client.module.modules.misc.AutoReconnect;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ScreenMixin {
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void virulent$drawReconnect(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		Screen self = (Screen) (Object) this;
		if (!(self instanceof DisconnectedScreen)) {
			return;
		}

		AutoReconnect module = AutoReconnect.get();
		if (module == null || !module.isEnabled()) {
			return;
		}

		String target = module.targetLabel();
		var font = self.getFont();
		int width = self.width;
		int height = self.height;

		if (target.isEmpty()) {
			context.centeredText(font, "AutoReconnect: join a server once to remember it", width / 2, height - 40, 0xFFFFAA66);
			return;
		}

		if (module.isExhausted()) {
			context.centeredText(font, "AutoReconnect: max attempts reached  →  " + target, width / 2, height - 40, 0xFFFF8888);
			return;
		}

		int seconds = module.secondsRemaining();
		int attempts = module.getAttempts();
		String line;
		if (seconds < 0) {
			line = "AutoReconnect: idle  →  " + target;
		} else if (seconds == 0) {
			line = "AutoReconnect: joining " + target + "...";
		} else {
			line = "AutoReconnect in " + seconds + "s  →  " + target
				+ (attempts > 0 ? "  (try #" + (attempts + 1) + ")" : "");
		}
		context.centeredText(font, line, width / 2, height - 40, 0xFF88FF88);
		context.centeredText(font, "Leave this screen to cancel", width / 2, height - 28, 0xFFAAAAAA);
	}
}
