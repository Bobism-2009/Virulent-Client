package dev.virulent.client.mixin;

import dev.virulent.client.VirulentClient;
import dev.virulent.client.module.modules.misc.Freecam;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public class CameraMixin {
	@Inject(method = "update", at = @At("RETURN"))
	private void virulent$freecam(DeltaTracker deltaTracker, CallbackInfo ci) {
		if (!Freecam.isActive()) {
			return;
		}

		var module = VirulentClient.getInstance().getModuleManager().getModule("Freecam");
		if (!(module instanceof Freecam freecam)) {
			return;
		}

		CameraAccessor camera = (CameraAccessor) (Object) this;
		camera.virulent$setPosition(freecam.getCamX(), freecam.getCamY(), freecam.getCamZ());
	}
}
