package dev.virulent.client.mixin;

import dev.virulent.client.module.modules.performance.FpsBooster;
import dev.virulent.client.module.modules.render.NoFire;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class InGameHudMixin {
	@Inject(method = "extractTextureOverlay", at = @At("HEAD"), cancellable = true)
	private void virulent$noFire(GuiGraphicsExtractor context, Identifier texture, float opacity, CallbackInfo ci) {
		if (texture == null) {
			return;
		}
		String path = texture.getPath();
		if (NoFire.isActive() && path.contains("fire")) {
			ci.cancel();
			return;
		}
		if (FpsBooster.hidePumpkinOverlay() && path.contains("pumpkin")) {
			ci.cancel();
		}
	}
}
